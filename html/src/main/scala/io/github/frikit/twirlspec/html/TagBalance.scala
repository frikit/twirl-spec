/*
 * Copyright 2026 frikiT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.frikit.twirlspec.html

import io.github.frikit.twirlspec.render.Templates

import java.nio.file.Path
import java.util.Locale
import scala.annotation.tailrec
import scala.collection.mutable

/** Whether every element that was opened was closed, read from the source.
  *
  * By the time a parser hands back a tree the question cannot be asked: an
  * unclosed `<div>` has already been closed for you. Jsoup does notice, but it
  * reports the symptom — "Unexpected EndTag [</main>]" — rather than the cause,
  * which leaves you counting tags by hand. This reads the markup as written and
  * names the element and the line it was opened on.
  */
object TagBalance {

  /** Something wrong with the balance of tags, with the line it was found on.
    */
  sealed trait Problem { def line: Int; def message: String }

  /** An element opened and never closed. */
  final case class Unclosed(name: String, line: Int) extends Problem {
    def message = s"<$name> opened on line $line was never closed"
  }

  /** A closing tag with nothing open to close. */
  final case class StrayClose(name: String, line: Int) extends Problem {
    def message = s"</$name> on line $line closes nothing that was open"
  }

  /** A closing tag that closed more than it named, so two elements overlap. */
  final case class MisNested(
      name: String,
      line: Int,
      closedInstead: String,
      openedOn: Int
  ) extends Problem {

    def message =
      s"</$name> on line $line closes <$closedInstead> from line $openedOn as well, so the two overlap"

  }

  /** An element left open, and, for a MathML annotation, whether its encoding
    * hands its content back to HTML.
    */
  final private case class Open(
      name: String,
      line: Int,
      handsBackToHtml: Boolean
  )

  /** A `>` inside a quoted attribute value does not end the tag, so an
    * attribute holding markup, as `srcdoc` does, is not read as tags of its
    * own. Where a quote never closes, the tag ends at the first `>`, so the
    * element is still seen.
    */
  private val tag =
    """<\s*(/?)\s*([a-zA-Z][^\s/>]*)((?:[^>"']|"[^"]*"|'[^']*')*?|[^>]*?)(/?)\s*>""".r

  /** The same check against a Twirl template rather than a rendered page.
    *
    * Cheap enough to run over a whole estate: no application, no render. It
    * sees only what the template writes literally, so markup arriving from a
    * helper is invisible, and a template that opens an element in one branch
    * and closes it in another looks unbalanced, because on any single render it
    * is.
    */
  def checkTemplate(template: String): List[Problem] = check(
    TwirlSource.htmlOnly(template)
  )

  /** Every Twirl template under this directory, in a stable order, or none
    * where it is not a directory.
    */
  def templatesUnder(root: Path): List[Path] = Templates.under(root)

  /** The problems in a piece of rendered markup, in line order, with comments,
    * doctypes and raw-text elements ignored.
    */
  def check(source: String): List[Problem] = {
    val stripped = blankOutSkippedRegions(source)
    val open = mutable.Stack.empty[Open]
    val problems = mutable.ListBuffer.empty[Problem]

    tag.findAllMatchIn(stripped).foreach { m =>
      val closing = m.group(1) == "/"
      val name = m.group(2).toLowerCase(Locale.ROOT)
      val selfClosed = m.group(4) == "/"
      val line = lineOf(stripped, m.start)

      if (closing) closeTag(name, line, open, problems)
      else {
        // a block's start ends a paragraph left open, as HTML lets it
        if (ParagraphClosers(name) && open.nonEmpty && open.top.name == "p")
          open.pop()
        if (HtmlVocabulary.void.contains(name)) ()
        // a slash closes only an element of SVG or MathML: <div/> stays open
        else if (selfClosed && (ForeignRoots(name) || inForeignContent(open)))
          ()
        else
          open.push(
            Open(
              name,
              line,
              name == "annotation-xml" && htmlEncoded(m.group(3))
            )
          )
      }
    }

    problems ++= open.toList
      .filterNot(o => HtmlVocabulary.optionalEndTag.contains(o.name))
      .map(o => Unclosed(o.name, o.line))
    problems.toList.sortBy(_.line)
  }

  private val ForeignRoots = Set("svg", "math")

  /** Where SVG and MathML hand back to HTML. */
  private val SvgIntegrationPoints = Set("foreignobject", "desc", "title")
  private val MathIntegrationPoints = Set("mi", "mo", "mn", "ms", "mtext")

  /** Whether the next element sits in SVG or MathML: the nearest of a foreign
    * root or one of its integration points decides. An annotation-xml is one
    * only where its encoding is HTML.
    */
  private def inForeignContent(open: mutable.Stack[Open]): Boolean = {
    val stack = open.toList
    def within(i: Int, root: String) = stack.drop(i + 1).exists(_.name == root)
    stack.zipWithIndex
      .collectFirst {
        case (o, _) if ForeignRoots(o.name)                             => true
        case (o, i) if SvgIntegrationPoints(o.name) && within(i, "svg") =>
          false
        case (o, i)
            if (MathIntegrationPoints(o.name) || o.handsBackToHtml) &&
              within(i, "math") =>
          false
      }
      .getOrElse(false)
  }

  private val Encoding =
    """(?i)\bencoding\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""".r

  /** Whether an annotation-xml's encoding hands its content back to HTML. */
  private def htmlEncoded(attributes: String): Boolean =
    Encoding
      .findFirstMatchIn(attributes)
      .flatMap(m => (1 to 3).map(m.group).find(_ != null))
      .exists(v =>
        Set("text/html", "application/xhtml+xml")(v.toLowerCase(Locale.ROOT))
      )

  /** The start tags that end a paragraph left open, as HTML lets them. */
  private val ParagraphClosers = Set(
    "address",
    "article",
    "aside",
    "blockquote",
    "details",
    "dialog",
    "div",
    "dl",
    "fieldset",
    "figcaption",
    "figure",
    "footer",
    "form",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hgroup",
    "hr",
    "main",
    "menu",
    "nav",
    "ol",
    "p",
    "pre",
    "search",
    "section",
    "table",
    "ul"
  )

  /** The parents before whose end a paragraph's end tag may not be left off. */
  private val KeepsParagraphOpen =
    Set("a", "audio", "del", "ins", "map", "noscript", "video")

  private def closeTag(
      name: String,
      line: Int,
      open: mutable.Stack[Open],
      problems: mutable.ListBuffer[Problem]
  ): Unit =
    if (open.isEmpty) problems += StrayClose(name, line)
    else if (open.top.name == name) open.pop()
    else if (!open.exists(_.name == name)) {
      // Nothing of this name is open anywhere, so the tag itself is the mistake.
      problems += StrayClose(name, line)
    } else {
      // Something of this name is open further down, so everything above it was left hanging.
      unwind(name, line, open, problems)
    }

  @tailrec
  private def unwind(
      name: String,
      line: Int,
      open: mutable.Stack[Open],
      problems: mutable.ListBuffer[Problem]
  ): Unit = {
    val top = open.pop()
    if (top.name == name) ()
    else {
      val keptOpenByParent = top.name == "p" && open.nonEmpty &&
        KeepsParagraphOpen(open.top.name)
      if (!HtmlVocabulary.optionalEndTag.contains(top.name) || keptOpenByParent)
        problems += MisNested(name, line, top.name, top.line)
      unwind(name, line, open, problems)
    }
  }

  /** Comments, doctypes and raw-text elements hold things that look like tags
    * but are not.
    */
  private def blankOutSkippedRegions(source: String): String = {
    val out = new StringBuilder(source)
    def blank(from: Int, to: Int): Unit =
      (from until math.min(to, out.length)).foreach(i =>
        if (out.charAt(i) != '\n') out.setCharAt(i, ' ')
      )

    // A comment or a doctype starts only between tags: a <!-- inside a quoted
    // attribute value is text.
    var i = 0
    while (i < source.length) {
      if (source.startsWith("<!--", i)) {
        val end = source.indexOf("-->", i + 4)
        val stop = if (end < 0) source.length else end + 3
        blank(i, stop); i = stop
      } else if (source.startsWith("<!", i)) {
        val end = source.indexOf('>', i + 2)
        val stop = if (end < 0) source.length else end + 1
        blank(i, stop); i = stop
      } else if (
        source.charAt(i) == '<' && i + 1 < source.length &&
        (source.charAt(i + 1).isLetter || source.charAt(i + 1) == '/')
      ) i = endOfTag(source, i)
      else i += 1
    }
    HtmlVocabulary.rawText.foreach { el =>
      val start = s"(?i)<$el\\b[^>]*>".r
      start.findAllMatchIn(source).foreach { m =>
        val closeAt = source.toLowerCase(Locale.ROOT).indexOf(s"</$el", m.end)
        if (closeAt > m.end) blank(m.end, closeAt)
      }
    }
    out.toString
  }

  /** Just past the tag starting here, reading past a > inside a quoted
    * attribute value. Where a quote never closes, the tag ends at its first >,
    * as the tag pattern reads it.
    */
  private def endOfTag(source: String, start: Int): Int = {
    var quote = 0.toChar
    var j = start + 1
    var end = -1
    while (end < 0 && j < source.length) {
      val c = source.charAt(j)
      if (quote != 0) { if (c == quote) quote = 0.toChar }
      else if (c == '"' || c == '\'') quote = c
      else if (c == '>') end = j + 1
      j += 1
    }
    if (end >= 0) end
    else {
      val first = source.indexOf('>', start)
      if (first < 0) source.length else first + 1
    }
  }

  private def lineOf(source: String, offset: Int): Int =
    source.substring(0, math.min(offset, source.length)).count(_ == '\n') + 1

}
