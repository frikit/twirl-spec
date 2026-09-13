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

package io.github.frikit.twirlspec.expect

import org.jsoup.nodes.Element
import io.github.frikit.twirlspec.page.{Page, Selection, Text}

/** Shared plumbing for turning "expected this, found that" into violations. */
private[twirlspec] object Matching {

  sealed trait Mode { def verb: String }
  case object Exact extends Mode { val verb = "equal" }
  case object Contains extends Mode { val verb = "contain" }
  case object StartsWith extends Mode { val verb = "start with" }

  /** GOV.UK prefixes the browser title of a page in an error state. */
  def errorTitlePrefixes(page: Page): Seq[String] =
    (page.message("error.browser.title.prefix").toSeq ++ Seq("Error:", "Gwall:")).map(Text.normalise)

  def stripErrorPrefix(page: Page, title: String): String = {
    val t = Text.normalise(title)
    errorTitlePrefixes(page).find(p => t.startsWith(p)).fold(t)(p => t.drop(p.length).trim)
  }

  def compare(rule: String, expected: Expected, actual: String, page: Page, mode: Mode): Seq[Violation] =
    expected.resolve(page) match {
      case Left(v)      => Seq(v.copy(rule = rule))
      case Right(value) =>
        val a  = Text.normalise(actual)
        val ok = expected match {
          case Expected.Anything       => a.nonEmpty
          case Expected.Pattern(regex) => regex.findFirstIn(a).isDefined
          case _                       =>
            mode match {
              case Exact      => a == value
              case Contains   => a.contains(value)
              case StartsWith => a.startsWith(value)
            }
        }
        if (ok) Nil
        else
          Seq(
            Violation(
              rule = rule,
              message = s"text did not ${mode.verb} the expected value",
              expected = Some(value),
              actual = Some(if (a.isEmpty) "(empty)" else a)
            )
          )
    }

  /** Resolve a selection that is meant to identify one element.
    *
    * Empty is a failure, and so is more than one: an assertion written against
    * "the email input" is not answerable when the page has two of them, and
    * silently taking the first is how a test ends up checking something other
    * than what it names. Where several matches are legitimate, assert on them
    * as a group with `cssSelector` and `elementCount`.
    */
  def exactlyOne(rule: String, selection: Selection): Either[Violation, Element] =
    selection.elements match {
      case Nil        => Left(Violation.missing(rule, selection.selector))
      case one :: Nil => Right(one)
      case many       =>
        Left(
          Violation(
            rule = rule,
            message = s"${many.size} elements matched, so this assertion is ambiguous",
            expected = Some("exactly one match"),
            actual = Some(many.map(describe).mkString(" | "))
          ).withHint("name the one you mean, or assert on the group with cssSelector and elementCount")
        )
    }

  private def describe(e: Element): String = {
    val id   = Option(e.id()).filter(_.nonEmpty).map("#" + _).getOrElse("")
    val name = Option(e.attr("name")).filter(_.nonEmpty).map(n => s"[name=$n]").getOrElse("")
    val text = Text.preview(e.text(), 40)
    s"<${e.tagName()}$id$name>${if (text.nonEmpty) s" $text" else ""}"
  }

  /** Assert a selection matched at least one element. */
  def present(rule: String, selection: Selection): Seq[Violation] =
    if (selection.nonEmpty) Nil
    else Seq(Violation.missing(rule, selection.selector))

  def absent(rule: String, selection: Selection): Seq[Violation] =
    if (selection.isEmpty) Nil
    else
      Seq(
        Violation(
          rule = rule,
          message = "element was rendered but should not have been",
          expected = Some("(absent)"),
          actual = Some(Text.preview(selection.text))
        ).at(selection.selector)
      )

}
