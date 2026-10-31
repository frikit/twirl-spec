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

package io.github.frikit.twirlspec

import io.github.frikit.twirlspec.expect.{Severity, Violation}
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** GOV.UK's guidance on errors, skip links and the names of links, as warnings.
  */
class ErrorPairingSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val rules = Set(
    "error-in-summary",
    "error-summary-has-inline",
    "error-summary-wording",
    "skip-link-target",
    "link-names-distinct"
  )

  private def page(body: String) =
    s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
       |<body><main id="main-content"><h1>H</h1>$body</main></body></html>""".stripMargin

  private def violations(html: String): Seq[Violation] =
    GovukStandards.all
      .filter(r => rules.contains(r.id))
      .flatMap(_.check(Page.fromString(html, english, messages)))

  private def on(body: String) = violations(page(body)).map(_.rule)

  private def summary(entries: (String, String)*) =
    entries
      .map { case (target, text) =>
        s"""<li><a href="#$target">$text</a></li>"""
      }
      .mkString(
        """<div class="govuk-error-summary"><ul class="govuk-error-summary__list">""",
        "",
        "</ul></div>"
      )

  private def error(id: String, text: String) =
    s"""<p id="$id" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> $text</p>"""

  private def input(id: String, attributes: String = "") =
    s"""<input id="$id" name="$id"$attributes>"""

  "A page's errors" should {

    "pair up as GOV.UK renders them" in {
      val name = input("name", """ aria-describedby="name-error"""")
      val radios =
        s"""<fieldset aria-describedby="value-error">${error(
            "value-error",
            "Select yes"
          )}
           |${input(
            "value"
          )}<input id="value-no" name="value"></fieldset>""".stripMargin
      val date =
        s"""<fieldset aria-describedby="dob-error">${error(
            "dob-error",
            "Enter a date"
          )}
           |${input("dob.day")}${input("dob.month")}</fieldset>""".stripMargin
      Seq(
        // A text input, by the id convention.
        summary("name" -> "Enter a name") +
          error("name-error", "Enter a name") + name,
        // Radios, with the link on the first and the error on the fieldset.
        summary("value" -> "Select yes") + radios,
        // A date, with the link on the day.
        summary("dob.day" -> "Enter a date") + date,
        // An error id from something other than the field's name.
        summary("name" -> "Enter a name") + error("other", "Enter a name") +
          input("name", """ aria-describedby="other"""")
      ).foreach(markup => withClue(markup)(on(markup) mustBe empty))
    }

    "warn of an inline error with no summary, or none for it" in {
      val stray = error("name-error", "Enter a name") + input("name")
      on(stray) mustBe Seq("error-in-summary")
      violations(page(stray)).map(_.severity) mustBe Seq(Severity.Warning)
      violations(page(stray)).flatMap(_.actual) mustBe Seq("Enter a name")
      on(
        summary("other" -> "Enter other") +
          error("other-error", "Enter other") + input("other") + stray
      ) mustBe Seq("error-in-summary")
    }

    "not ask a fragment for a summary that is on the page around it" in {
      violations(
        error("name-error", "Enter a name") + input("name")
      ) mustBe empty
    }

    "warn of a summary entry with no inline error" in {
      val markup = summary("name" -> "Enter a name") + input("name")
      on(markup) mustBe Seq("error-summary-has-inline")
      violations(page(markup)).map(_.severity) mustBe Seq(Severity.Warning)
      violations(page(markup)).flatMap(_.actual) mustBe Seq("Enter a name")
    }

    "leave to error-summary-targets an entry that lands on nothing" in {
      on(summary("gone" -> "Enter a name")) mustBe empty
      on(summary("" -> "Fix this")) mustBe empty
    }

    "warn of an error that reads differently in the two places" in {
      val found = violations(
        page(
          summary("name" -> "Enter a name") +
            error("name-error", "Name is required") + input("name")
        )
      )
      found.map(_.rule) mustBe Seq("error-summary-wording")
      found.map(_.severity) mustBe Seq(Severity.Warning)
      found.flatMap(_.expected) mustBe Seq("Enter a name")
      found.flatMap(_.actual) mustBe Seq("Name is required")
    }

    "go by what the target says describes it, before a conventional id" in {
      on(
        summary("a" -> "Enter b", "other" -> "Enter a") +
          error("a-error", "Enter a") + error("b-error", "Enter b") +
          """<input id="a" name="b" aria-describedby="b-error">""" +
          """<input id="other" name="a" aria-describedby="a-error">"""
      ) mustBe empty
    }

    "not ask a summary entry of an error the markup hides" in {
      on(
        s"""<div hidden>${error("name-error", "Enter a name")}</div>""" +
          input("name")
      ) mustBe empty
    }

    "not take the hidden prefix or spacing for different wording" in {
      on(
        summary("name" -> "Enter   a name") +
          error("name-error", "Enter a  name") + input("name")
      ) mustBe empty
    }
  }

  "skip-link-target" should {
    def skip(href: String) =
      s"""<a class="govuk-skip-link" href="$href">Skip to main content</a>"""

    "pass a skip link that lands on an element" in {
      on(skip("#main-content")) mustBe empty
    }

    "warn of one that lands on nothing, or goes nowhere" in {
      on(skip("#main")) mustBe Seq("skip-link-target")
      on(skip("#")) mustBe Seq("skip-link-target")
      violations(page(skip("#main"))).map(_.severity) mustBe
        Seq(Severity.Warning)
      violations(page(skip("#main"))).flatMap(_.actual) mustBe
        Seq("Skip to main content")
    }

    "leave alone one with no fragment to check, and any other link" in {
      on(skip("/page")) mustBe empty
      on("""<a href="#main">Other</a>""") mustBe empty
    }

    "not ask a fragment for the main content around it" in {
      violations(skip("#main-content")) mustBe empty
    }
  }

  "link-names-distinct" should {

    "warn of links called the same that go to different places" in {
      val found = violations(
        page(
          """<a href="/a">Change</a><a href="/b">Change</a><a href="/a">Change</a>"""
        )
      )
      found.map(_.rule) mustBe Seq("link-names-distinct")
      found.map(_.severity) mustBe Seq(Severity.Warning)
      found.flatMap(_.actual) mustBe Seq("/a | /b")
    }

    "go by the name announced, so hidden context tells them apart" in {
      on(
        """<a href="/a">Change<span class="govuk-visually-hidden"> name</span></a>
          |<a href="/b">Change<span class="govuk-visually-hidden"> date</span></a>""".stripMargin
      ) mustBe empty
    }

    "leave alone links called the same that go to the same place" in {
      on("""<a href="/a">Help</a><a href="/a">Help</a>""") mustBe empty
    }

    "leave to link-has-name the links with no name" in {
      on("""<a href="/a"></a><a href="/b"></a>""") mustBe empty
    }

    "leave out a link the markup hides" in {
      on(
        """<div hidden><a href="/a">Change</a></div><a href="/b">Change</a>"""
      ) mustBe empty
    }

    "treat a link and its full address as one place, where the page has a base" in {
      def based(links: String) =
        violations(
          s"""<!DOCTYPE html><html lang="en"><head><base href="https://example.gov.uk/"><title>t</title></head>
             |<body><main>$links</main></body></html>""".stripMargin
        ).map(_.rule)
      based(
        """<a href="/help">Help</a><a href="https://example.gov.uk/help">Help</a>"""
      ) mustBe empty
      based(
        """<a href="/help">Help</a><a href="https://example.gov.uk/other">Help</a>"""
      ) mustBe Seq("link-names-distinct")
    }

    "report in a stable order" in {
      violations(
        page(
          """<a href="/1">Z</a><a href="/2">Z</a><a href="/3">A</a><a href="/4">A</a>"""
        )
      ).flatMap(_.actual) mustBe Seq("/3 | /4", "/1 | /2")
    }
  }

}
