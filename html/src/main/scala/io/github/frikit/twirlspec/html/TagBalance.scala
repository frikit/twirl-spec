/*
 * Copyright 2026 Victor Osipov
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

  sealed trait Problem { def line: Int; def message: String }

  final case class Unclosed(name: String, line: Int) extends Problem {
    def message = s"<$name> opened on line $line was never closed"
  }

  final case class StrayClose(name: String, line: Int) extends Problem {
    def message = s"</$name> on line $line closes nothing that was open"
  }

  final case class MisNested(name: String, line: Int, closedInstead: String, openedOn: Int) extends Problem {

    def message =
      s"</$name> on line $line closes <$closedInstead> from line $openedOn as well, so the two overlap"

  }

  final private case class Open(name: String, line: Int)

  private val tag = """<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9:-]*)([^>]*?)(/?)\s*>""".r

  /** The same check against a Twirl template rather than a rendered page.
    *
    * Cheap enough to run over a whole estate: no application, no render. It sees
    * only what the template writes literally, so markup arriving from a helper is
    * invisible, and a template that opens an element in one branch and closes it
    * in another looks unbalanced, because on any single render it is.
    */
  def checkTemplate(template: String): List[Problem] = check(TwirlSource.htmlOnly(template))

  def check(source: String): List[Problem] = {
    val stripped = blankOutSkippedRegions(source)
    val open     = mutable.Stack.empty[Open]
    val problems = mutable.ListBuffer.empty[Problem]

    tag.findAllMatchIn(stripped).foreach { m =>
      val closing    = m.group(1) == "/"
      val name       = m.group(2).toLowerCase
      val selfClosed = m.group(4) == "/"
      val line       = lineOf(stripped, m.start)

      if (closing) closeTag(name, line, open, problems)
      else if (!selfClosed && !HtmlVocabulary.void.contains(name)) open.push(Open(name, line))
    }

    problems ++= open.toList
      .filterNot(o => HtmlVocabulary.optionalEndTag.contains(o.name))
      .map(o => Unclosed(o.name, o.line))
    problems.toList.sortBy(_.line)
  }

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
      if (!HtmlVocabulary.optionalEndTag.contains(top.name))
        problems += MisNested(name, line, top.name, top.line)
      unwind(name, line, open, problems)
    }
  }

  /** Comments, doctypes and raw-text elements hold things that look like tags but are not. */
  private def blankOutSkippedRegions(source: String): String = {
    val out                             = new StringBuilder(source)
    def blank(from: Int, to: Int): Unit =
      (from until math.min(to, out.length)).foreach(i => if (out.charAt(i) != '\n') out.setCharAt(i, ' '))

    blankBetween(source, "<!--", "-->", blank)
    blankBetween(source, "<!", ">", blank)
    HtmlVocabulary.rawText.foreach { el =>
      val start = s"(?i)<$el\\b[^>]*>".r
      start.findAllMatchIn(source).foreach { m =>
        val closeAt = source.toLowerCase.indexOf(s"</$el", m.end)
        if (closeAt > m.end) blank(m.end, closeAt)
      }
    }
    out.toString
  }

  private def blankBetween(source: String, from: String, to: String, blank: (Int, Int) => Unit): Unit = {
    var i = source.indexOf(from)
    while (i >= 0) {
      val end = source.indexOf(to, i + from.length)
      if (end < 0) { blank(i, source.length); i = -1 }
      else { blank(i, end + to.length); i = source.indexOf(from, end + to.length) }
    }
  }

  private def lineOf(source: String, offset: Int): Int =
    source.substring(0, math.min(offset, source.length)).count(_ == '\n') + 1

}
