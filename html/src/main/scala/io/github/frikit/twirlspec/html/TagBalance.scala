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
import org.jsoup.parser.Parser

import java.nio.file.Path
import scala.annotation.tailrec
import scala.collection.mutable

/** Whether every element that was opened was closed, read from the source.
  *
  * By the time a parser hands back a tree the question cannot be asked: an
  * unclosed `<div>` has already been closed for you. Jsoup does notice, but it
  * reports the symptom — "Unexpected EndTag [</main>]" — rather than the cause,
  * which leaves you counting tags by hand. This reads the markup as written and
  * names the element and the line it was opened on.
  *
  * It reads the source as the tokenizer of the HTML Living Standard, as of
  * November 2026, reads it. Comments, doctypes, processing instructions, CDATA
  * in SVG and MathML, quoted attribute values and the elements whose content is
  * text, such as a script, a style or a textarea, open nothing. It departs from
  * the standard twice. A quoted value that never closes ends its tag at the
  * tag's first `>`, so the element is still reported rather than the rest of
  * the page vanishing into the value. And a `noscript` is read as markup, as a
  * browser with scripting off reads it.
  *
  * It nests elements by their tags, knowing what HTML lets you leave out: void
  * elements, the optional end tags, a paragraph ended by the start of a block
  * (though not left open at the end of a link, a media element, an edit, a map,
  * a `noscript`, a custom element or an SVG or MathML parent), and a slash
  * closing only an SVG or MathML element. These are HTML's rules, so an SVG or
  * MathML element of the same name keeps its own. It puts each element in the
  * namespace a parser would: SVG and MathML, and HTML again at their
  * integration points.
  *
  * It does not repeat a parser's error recovery, such as breaking out of SVG,
  * closing by scope, the adoption agency or foster parenting: it also reads
  * template fragments with no page around them, and its job is to name the
  * mistake in the source, not the tree a browser repairs it into.
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

  /** A `</p>` whose paragraph the start of a block had already ended, so it
    * closes nothing, and a browser renders it as an empty paragraph of its own.
    */
  final case class EndedParagraph(
      line: Int,
      endedBy: String,
      endedOn: Int,
      openedOn: Int
  ) extends Problem {

    def message =
      s"</p> on line $line closes nothing that was open: <$endedBy> on line $endedOn ended the <p> from line $openedOn"

  }

  /** An element left open, the namespace it was put in (`html`, `svg` or
    * `math`), and whether HTML's rules apply inside it again.
    */
  final private case class Open(
      name: String,
      line: Int,
      space: String,
      handsBackToHtml: Boolean
  )

  /** A tag as the tokenizer reads it: its name in lower case, its attributes
    * (the first of a name kept), whether a slash closes it, and where it ends.
    */
  final private case class Tag(
      name: String,
      attributes: Map[String, String],
      selfClosing: Boolean,
      end: Int
  )

  /** A paragraph the start of a block ended: which block, on which line, and
    * the element the paragraph was in.
    */
  final private case class Ended(
      paragraph: Open,
      by: String,
      on: Int,
      parent: Option[Open]
  )

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

  /** The problems in a piece of rendered markup, in line order. */
  def check(source: String): List[Problem] = new Scan(source).problems

  /** Each element the source opens, in order, with the namespace it is put in:
    * `html`, `svg` or `math`.
    */
  private[twirlspec] def elementsOf(source: String): List[(String, String)] =
    new Scan(source).elements

  private val ForeignRoots = Set("svg", "math")

  /** Where SVG hands back to HTML, and where a MathML token does. */
  private val SvgIntegrationPoints = Set("foreignobject", "desc", "title")
  private val MathTextIntegrationPoints = Set("mi", "mo", "mn", "ms", "mtext")

  /** The elements whose content the tokenizer reads as text, up to their own
    * end tag. A script has escapes of its own, and plaintext has no end.
    */
  private val ReadAsText =
    HtmlVocabulary.rawText ++ Set("iframe", "noembed", "noframes", "xmp")

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

  /** Whether HTML's rules apply inside an element again: SVG's foreignObject,
    * desc and title, and an annotation-xml whose encoding, its character
    * references decoded, is HTML.
    */
  private def handsBackToHtml(space: String, tag: Tag): Boolean =
    (space == "svg" && SvgIntegrationPoints(tag.name)) ||
      (space == "math" && tag.name == "annotation-xml" &&
        tag.attributes
          .get("encoding")
          .map(v => asciiLower(Parser.unescapeEntities(v, true)))
          .exists(Set("text/html", "application/xhtml+xml")))

  /** Whether HTML's rules apply to this start tag inside a foreign parent: at
    * an integration point, though straight inside a MathML token a glyph or an
    * align mark stays MathML, and to an svg inside an annotation-xml.
    */
  private def htmlRulesIn(parent: Open, name: String): Boolean =
    parent.handsBackToHtml || (parent.space == "math" && (
      (MathTextIntegrationPoints(parent.name) &&
        name != "mglyph" && name != "malignmark") ||
        (parent.name == "annotation-xml" && name == "svg")
    ))

  /** Whether HTML lets this element's end tag be left off: an HTML element's
    * only, never an SVG or MathML one's of the same name.
    */
  private def endMayBeLeftOff(o: Open): Boolean =
    o.space == "html" && HtmlVocabulary.optionalEndTag.contains(o.name)

  /** The tokenizer folds the case of ASCII letters, and of nothing else. */
  private def asciiLower(s: String): String =
    s.map(c => if (c >= 'A' && c <= 'Z') (c + 32).toChar else c)

  private def isSpace(c: Char): Boolean = " \t\n\f\r".indexOf(c.toInt) >= 0

  private def endsName(c: Char): Boolean = isSpace(c) || c == '/' || c == '>'

  /** One reading of a source, from its first character to its last. */
  final private class Scan(source: String) {

    private val open = mutable.Stack.empty[Open]
    private val found = mutable.ListBuffer.empty[Problem]
    private val opened = mutable.ListBuffer.empty[(String, String)]
    private val ended = mutable.ListBuffer.empty[Ended]
    private val newlines = source.indices.filter(source.charAt(_) == '\n')

    private var at = 0
    while (at < source.length) {
      val lt = source.indexOf('<', at)
      at = if (lt < 0) source.length else markup(lt)
    }
    // what is still open was never closed, save where HTML lets its end go
    found ++= open.toList
      .filterNot(endMayBeLeftOff)
      .map(o => Unclosed(o.name, o.line))

    val problems: List[Problem] = found.toList.sortBy(_.line)
    val elements: List[(String, String)] = opened.toList

    /** Read what starts at this `<`, and say where reading goes on. */
    private def markup(lt: Int): Int =
      if (source.startsWith("<!--", lt)) commentEnd(lt + 4)
      else if (
        source.startsWith("<![CDATA[", lt) &&
        open.headOption.exists(_.space != "html")
      ) past(lt + 9, "]]>")
      else if (source.startsWith("</", lt) && letterAt(lt + 2)) endTag(lt)
      // a doctype, or a bogus comment: <!x>, <?x>, </ x>, and CDATA in HTML
      else if (Seq("<!", "<?", "</").exists(source.startsWith(_, lt)))
        past(lt + 2, ">")
      else if (letterAt(lt + 1)) startTag(lt)
      else lt + 1

    /** Past a comment whose `<!--` ends here: `<!-->` and `<!--->` end at once,
      * any other at its first `-->` or `--!>`.
      */
    private def commentEnd(from: Int): Int =
      if (source.startsWith(">", from)) from + 1
      else if (source.startsWith("->", from)) from + 2
      else math.min(past(from, "-->"), past(from, "--!>"))

    private def startTag(lt: Int): Int =
      readTag(lt + 1).fold(source.length) { tag =>
        val space = spaceFor(tag.name)
        opened += tag.name -> space
        // HTML's rules, for HTML's elements: a block's start ends a paragraph
        // left open, and a void element has no content to close
        if (
          space == "html" && ParagraphClosers(tag.name) &&
          open.headOption.exists(o => o.name == "p" && o.space == "html")
        ) {
          val paragraph = open.pop()
          ended += Ended(paragraph, tag.name, lineOf(lt), open.headOption)
        }
        if (space == "html" && HtmlVocabulary.void.contains(tag.name)) tag.end
        // a slash closes only an element of SVG or MathML: <div/> stays open
        else if (space != "html" && tag.selfClosing) tag.end
        else {
          open.push(
            Open(tag.name, lineOf(lt), space, handsBackToHtml(space, tag))
          )
          if (space == "html") textEnd(tag.name, tag.end) else tag.end
        }
      }

    private def endTag(lt: Int): Int =
      readTag(lt + 2).fold(source.length) { tag =>
        closeTag(tag.name, lineOf(lt))
        tag.end
      }

    /** The namespace an element of this name is put in here. Inside an SVG or
      * MathML parent it takes the parent's, unless HTML's rules apply there;
      * anywhere else svg and math start their own, and the rest is HTML.
      */
    private def spaceFor(name: String): String = open.headOption match {
      case Some(parent)
          if parent.space != "html" && !htmlRulesIn(parent, name) =>
        parent.space
      case _ if ForeignRoots(name) => name
      case _                       => "html"
    }

    /** The tag whose name starts here, read as the tokenizer reads it. A tag
      * the source ends inside is no tag, unless what never closes is a quoted
      * value: then the tag ends at its first `>`.
      */
    private def readTag(from: Int): Option[Tag] = {
      val name = asciiLower(source.substring(from, upTo(from, endsName)))

      @tailrec
      def attributes(i: Int, read: Map[String, String]): Option[Tag] = {
        val at = upTo(i, !isSpace(_))
        if (at == source.length) None
        else if (source.startsWith(">", at))
          Some(Tag(name, read, selfClosing = false, end = at + 1))
        else if (source.startsWith("/>", at))
          Some(Tag(name, read, selfClosing = true, end = at + 2))
        // a slash anywhere else is nothing
        else if (source.startsWith("/", at)) attributes(at + 1, read)
        else {
          // a name may start with =, and runs to a space, a slash, a > or an =
          val keyEnd = upTo(at + 1, c => endsName(c) || c == '=')
          val key = asciiLower(source.substring(at, keyEnd))
          val eq = upTo(keyEnd, !isSpace(_))
          // the first attribute of a name is the one kept
          if (!source.startsWith("=", eq))
            attributes(eq, Map(key -> "") ++ read)
          else {
            val v = upTo(eq + 1, !isSpace(_))
            if (source.startsWith("\"", v) || source.startsWith("'", v)) {
              val close = source.indexOf(source.charAt(v).toInt, v + 1)
              if (close >= 0)
                attributes(
                  close + 1,
                  Map(key -> source.substring(v + 1, close)) ++ read
                )
              else
                Option(source.indexOf('>', from))
                  .filter(_ >= 0)
                  .map(gt => Tag(name, read, selfClosing = false, end = gt + 1))
            } else {
              // unquoted, a value runs to a space or a >, a slash and all
              val valueEnd = upTo(v, c => isSpace(c) || c == '>')
              attributes(
                valueEnd,
                Map(key -> source.substring(v, valueEnd)) ++ read
              )
            }
          }
        }
      }

      attributes(from + name.length, Map.empty)
    }

    /** Where an element read as text ends: at the end tag the tokenizer takes
      * for its own, through a script's escapes, or nowhere for plaintext.
      */
    private def textEnd(name: String, from: Int): Int = name match {
      case "script"              => scriptEnd(from)
      case "plaintext"           => source.length
      case _ if ReadAsText(name) => endTagFrom(name, from)
      case _                     => from
    }

    private def endTagFrom(name: String, from: Int): Int = {
      var i = source.indexOf("</", from)
      while (i >= 0 && !endTagAt(name, i)) i = source.indexOf("</", i + 2)
      if (i < 0) source.length else i
    }

    /** Where a script's text ends. Inside `<!--` a `<script>` makes the next
      * `</script>` text, until a `-->` ends the escape.
      */
    private def scriptEnd(from: Int): Int = {
      // 0 reads script data, 1 escaped, 2 double escaped
      var state = 0
      var i = from
      var end = -1
      while (end < 0 && i < source.length) {
        if (state == 0 && source.startsWith("<!--", i)) { state = 1; i += 2 }
        else if (state > 0 && source.startsWith("-->", i)) {
          state = 0; i += 3
        } else if (state < 2 && endTagAt("script", i)) end = i
        else if (
          state == 1 && source.startsWith("<", i) && nameAt("script", i + 1)
        ) { state = 2; i += 7 }
        else if (state == 2 && endTagAt("script", i)) { state = 1; i += 8 }
        else i += 1
      }
      if (end < 0) source.length else end
    }

    /** Whether the end tag the tokenizer takes as this element's starts here.
      */
    private def endTagAt(name: String, i: Int): Boolean =
      source.startsWith("</", i) && nameAt(name, i + 2)

    /** Whether this name, in any ASCII case, starts here and is followed by a
      * space, a slash or a `>`.
      */
    private def nameAt(name: String, i: Int): Boolean = {
      val end = i + name.length
      end < source.length &&
      asciiLower(source.substring(i, end)) == name &&
      endsName(source.charAt(end))
    }

    private def closeTag(name: String, line: Int): Unit =
      // Nothing of this name is open anywhere, so the tag itself is the mistake.
      if (!open.exists(_.name == name)) found += closingNothing(name, line)
      else if (open.top.name == name) open.pop()
      // Something of this name is open further down, so everything above it was left hanging.
      else unwind(name, line)

    /** An end tag that closes nothing. A `</p>` whose paragraph the start of a
      * block ended names that block, while the element the paragraph was in is
      * still open; any other is stray.
      */
    private def closingNothing(name: String, line: Int): Problem = {
      val i =
        if (name == "p")
          ended.lastIndexWhere(_.parent.forall(p => open.exists(_ eq p)))
        else -1
      if (i < 0) StrayClose(name, line)
      else {
        val e = ended.remove(i)
        EndedParagraph(line, e.by, e.on, e.paragraph.line)
      }
    }

    @tailrec
    private def unwind(name: String, line: Int): Unit = {
      val top = open.pop()
      if (top.name == name) ()
      else {
        // a paragraph's end may be left off only where its parent is an HTML
        // element, and not one of these or a custom one
        val keptOpenByParent = top.name == "p" &&
          open.headOption.exists(parent =>
            parent.space != "html" || KeepsParagraphOpen(parent.name) ||
              HtmlVocabulary.isCustomElementName(parent.name)
          )
        if (!endMayBeLeftOff(top) || keptOpenByParent)
          found += MisNested(name, line, top.name, top.line)
        unwind(name, line)
      }
    }

    /** Just past the first `closer` from here, or the end of the source. */
    private def past(from: Int, closer: String): Int = {
      val i = source.indexOf(closer, from)
      if (i < 0) source.length else i + closer.length
    }

    /** The first index from here where this holds, or the end of the source. */
    private def upTo(from: Int, stop: Char => Boolean): Int = {
      val i = source.indexWhere(stop, from)
      if (i < 0) source.length else i
    }

    private def letterAt(i: Int): Boolean =
      i < source.length && {
        val c = source.charAt(i)
        (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
      }

    private def lineOf(offset: Int): Int =
      newlines.search(offset).insertionPoint + 1

  }

}
