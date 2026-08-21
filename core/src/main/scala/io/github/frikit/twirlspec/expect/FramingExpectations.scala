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

package io.github.frikit.twirlspec.expect

import io.github.frikit.twirlspec.expect.Matching._
import io.github.frikit.twirlspec.page.{Page, Text}

/** Expectations about the frame of a GOV.UK page: what it is called, what it is headed, and the furniture the layout is responsible for.
  */
trait FramingExpectations {

  /** The browser title, ignoring the " - Service name - GOV.UK" suffix the layout appends and the translated "Error:" prefix an error state adds.
    */
  def title(key: String, args: Any*): Expectation = title(Expected.Key(key, args.toSeq))
  def titleText(literal: String): Expectation     = title(Expected.Literal(literal))

  def title(expected: Expected): Expectation = Expectation("title") { page =>
    compare("title", expected, stripErrorPrefix(page, page.title), page, StartsWith)
  }

  /** The browser title in full, including service name and " - GOV.UK". */
  def exactTitle(literal: String): Expectation = Expectation("exactTitle") { page =>
    compare("exactTitle", Expected.Literal(literal), page.title, page, Exact)
  }

  /** The single `<h1>`. */
  def heading(key: String, args: Any*): Expectation = heading(Expected.Key(key, args.toSeq))
  def headingText(literal: String): Expectation     = heading(Expected.Literal(literal))

  def heading(expected: Expected): Expectation = Expectation("heading") { page =>
    val h1 = page.h1
    if (h1.isEmpty) Seq(Violation.missing("heading", "h1"))
    else {
      // The caption is inside the h1 for GOV.UK "page heading with caption",
      // so compare against the h1 text with any caption text removed.
      val captionText = page.caption.texts.headOption.getOrElse("")
      val headingText =
        if (captionText.nonEmpty && h1.text.startsWith(captionText)) h1.text.drop(captionText.length).trim
        else h1.text
      compare("heading", expected, headingText, page, Exact)
    }
  }

  /** The caption rendered above (or inside) the h1. */
  def caption(key: String, args: Any*): Expectation = caption(Expected.Key(key, args.toSeq))
  def captionText(literal: String): Expectation     = caption(Expected.Literal(literal))

  def caption(expected: Expected): Expectation = Expectation("caption") { page =>
    present("caption", page.caption) match {
      case Nil  => compare("caption", expected, page.caption.text, page, Contains)
      case errs => errs
    }
  }

  /** The service or site name in the header. */
  def serviceName(key: String = "service.name"): Expectation = Expectation("serviceName") { page =>
    Matching.exactlyOne("serviceName", page.serviceName) match {
      case Left(v)  => Seq(v)
      case Right(e) => compare("serviceName", Expected.Key(key), e.text(), page, Exact)
    }
  }

  /** A GOV.UK back link is present. */
  val backLink: BackLinkExpectation = BackLinkExpectation(None)

  val noBackLink: Expectation = Expectation("noBackLink")(page => absent("noBackLink", page.backLink))

  /** A language switcher, however it is rendered. */
  val languageToggle: Expectation = Expectation("languageToggle") { page =>
    present("languageToggle", page.languageToggle)
  }

  val timeoutDialog: Expectation = Expectation("timeoutDialog") { page =>
    present("timeoutDialog", page.timeoutDialog)
  }

  val signOutLink: Expectation = Expectation("signOutLink") { page =>
    present("signOutLink", page.signOutLink)
  }

  val phaseBanner: Expectation = Expectation("phaseBanner") { page =>
    present("phaseBanner", page.phaseBanner)
  }

  /** An `h2` with the given message key. */
  def subheading(key: String, args: Any*): Expectation = headingAtLevel(2, Expected.Key(key, args.toSeq))

  def headingAtLevel(level: Int, expected: Expected): Expectation =
    Expectation(s"h$level") { page =>
      expected.resolve(page) match {
        case Left(v)      => Seq(v.copy(rule = s"h$level"))
        case Right(value) =>
          val found = page.headings.collect { case (l, t) if l == level => t }
          if (found.exists(Text.same(_, value))) Nil
          else
            Seq(
              Violation(
                rule = s"h$level",
                message = s"no <h$level> on the page had this text",
                expected = Some(value),
                actual = Some(if (found.isEmpty) "(no h" + level + " elements)" else found.mkString(" | "))
              )
            )
      }
    }

}

/** Back link check, optionally pinned to a target URL. */
final case class BackLinkExpectation(href: Option[String]) extends Expectation {

  def to(url: String): BackLinkExpectation = copy(href = Some(url))

  def description: String = href.fold("backLink")(u => s"backLink -> $u")

  def check(page: Page): Seq[Violation] =
    Matching.exactlyOne("backLink", page.backLink) match {
      case Left(v)  => Seq(v)
      case Right(e) =>
        href.toSeq.flatMap { url =>
          val actual = e.attr("href")
          if (actual == url) Nil
          else Seq(Violation.mismatch("backLink", url, actual, "back link pointed somewhere else"))
        }
    }

}
