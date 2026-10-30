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

import io.github.frikit.twirlspec.expect.Expectation
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{DefaultMessagesApi, Lang, MessagesImpl}

/** The error markup GOV.UK components render: a summary linking to a group's
  * first control, and an inline error named after something other than the
  * field.
  */
class ErrorMarkupSpec extends AnyWordSpec with Matchers {

  private val dsl = new TwirlSpecDsl {}

  private val messages = MessagesImpl(
    Lang("en"),
    new DefaultMessagesApi(
      Map("default" -> Map("site.yes" -> "Yes", "site.no" -> "No"))
    )
  )

  private def pageOf(body: String) =
    Page.fromString(body, Lang("en"), messages)

  private def issues(body: String, check: Expectation) =
    check.check(pageOf(body)).map(_.message)

  private def summary(links: (String, String)*) =
    links
      .map { case (target, words) => s"""<a href="#$target">$words</a>""" }
      .mkString("""<div class="govuk-error-summary">""", "", "</div>")

  private val radios =
    """<input type="radio" id="value-yes" name="value" value="y"><input type="radio" id="value-no" name="value" value="n">"""

  "errorSummary" should {

    "accept a link to a radio group's first radio" in {
      issues(
        summary("value-yes" -> "Yes") + radios,
        dsl.errorSummary("value" -> "site.yes")
      ) mustBe empty
    }

    "accept a link to a first radio whose id says nothing of the field" in {
      issues(
        summary("yes-no" -> "Yes") +
          """<input type="radio" id="yes-no" name="value" value="y">""",
        dsl.errorSummary("value" -> "site.yes")
      ) mustBe empty
    }

    "accept a link to a date input's day, under either convention" in {
      issues(
        summary(
          "value.day" -> "Yes"
        ) + """<input id="value.day" name="value.day">""",
        dsl.errorSummary("value" -> "site.yes")
      ) mustBe empty
      issues(
        summary("dob-day" -> "Yes") + """<input id="dob-day" name="dob-day">""",
        dsl.errorSummary("dob" -> "site.yes")
      ) mustBe empty
    }

    "not accept a link to another field" in {
      val found = issues(
        summary("other" -> "Yes") + """<input id="other" name="other">""",
        dsl.errorSummary("value" -> "site.yes")
      )
      found must contain("the error summary has no entry linking to this field")
      found.mkString must include("not expected: other")
    }

    "not take a link to a control of another field for a surplus entry" in {
      issues(
        summary("value-yes" -> "Yes", "b" -> "No") + radios +
          """<input id="b" name="b">""",
        dsl.errorSummary("value" -> "site.yes")
      ).mkString must include("not expected: b")
    }

    "let one link answer for one entry, not two" in {
      val found = issues(
        summary("a-b" -> "Yes") + """<input id="a-b" name="a-b">""",
        dsl.errorSummary("a" -> "site.yes", "a-b" -> "site.yes")
      )
      found must contain("the error summary has no entry linking to this field")
    }

    "not take a field that begins the same way for a part of this one" in {
      val found = issues(
        summary("a-b" -> "Yes") +
          """<input id="a" name="a"><input id="a-b" name="a-b">""",
        dsl.errorSummary("a" -> "site.yes")
      )
      found must contain("the error summary has no entry linking to this field")
      found.mkString must include("not expected: a-b")
    }

    "take a second link to one field for an entry nobody expected" in {
      issues(
        summary("a" -> "Yes", "a" -> "No") + """<input id="a" name="a">""",
        dsl.errorSummary("a" -> "site.yes")
      ).mkString must include("not expected: a")
    }

    "prefer a link to the field itself over one to a control of it" in {
      issues(
        summary("a-b" -> "No", "a" -> "Yes") +
          """<input id="a" name="a"><input id="a-b" name="a-b">""",
        dsl.errorSummary("a-b" -> "site.no", "a" -> "site.yes")
      ) mustBe empty
    }

    "hold the entries to the order the summary lists them in" in {
      val page = summary("a" -> "Yes", "b" -> "No") +
        """<input id="a" name="a"><input id="b" name="b">"""
      issues(page, dsl.errorSummary("a" -> "site.yes", "b" -> "site.no")) mustBe
        empty
      issues(page, dsl.errorSummary("b" -> "site.no", "a" -> "site.yes")) mustBe
        Seq("the error summary lists these in a different order")
    }

    "not ask for an order of the one that names only some of them" in {
      val page = summary("a" -> "Yes", "b" -> "No") +
        """<input id="a" name="a"><input id="b" name="b">"""
      issues(page, dsl.errorSummaryContaining("b", "site.no")) mustBe empty
    }
  }

  "fieldError" should {
    val error =
      """<p id="yes-no-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Yes</p>"""

    "follow a radio group's fieldset to the error it announces" in {
      issues(
        s"""<fieldset aria-describedby="yes-no-error">$error$radios</fieldset>""",
        dsl.fieldError("value", "site.yes")
      ) mustBe empty
    }

    "follow a control to the error it announces" in {
      issues(
        s"""$error<input name="q" aria-describedby="yes-no-hint yes-no-error"><p id="yes-no-hint">Hint</p>""",
        dsl.fieldError("q", "site.yes")
      ) mustBe empty
    }

    "still find an error by the convention, with no control to follow" in {
      issues(
        """<p id="q-error" class="govuk-error-message">Yes</p>""",
        dsl.fieldError("q", "site.yes")
      ) mustBe empty
    }

    "report different words" in {
      issues(
        s"""<fieldset aria-describedby="yes-no-error">$error$radios</fieldset>""",
        dsl.fieldError("value", "site.no")
      ) must not be empty
    }

    "follow a date input's fields to the error their fieldset announces" in {
      val date =
        """<fieldset aria-describedby="custom-error"><p id="custom-error" class="govuk-error-message">Yes</p>
          |<div id="dob" class="govuk-date-input"><input id="dob.day" name="dob.day"><input id="dob.month" name="dob.month"></div></fieldset>""".stripMargin
      issues(date, dsl.fieldError("dob", "site.yes")) mustBe empty
      issues(
        """<fieldset aria-describedby="custom-error"><p id="custom-error" class="govuk-error-message">Yes</p><input id="dob-day"></fieldset>""",
        dsl.fieldError("dob", "site.yes")
      ) mustBe empty
    }

    "not take the error of a field that begins the same way" in {
      issues(
        """<p id="other" class="govuk-error-message">Yes</p>
          |<input name="a"><input name="a-b" aria-describedby="other">""".stripMargin,
        dsl.fieldError("a", "site.yes")
      ) mustBe Seq("no inline error message was rendered for this field")
    }

    "prefer the field's own control over a date part of the same name" in {
      issues(
        """<p id="own" class="govuk-error-message">Yes</p><p id="other" class="govuk-error-message">No</p>
          |<input name="a-day" aria-describedby="other"><input name="a" aria-describedby="own">""".stripMargin,
        dsl.fieldError("a", "site.yes")
      ) mustBe empty
    }

    "report no error when what the control announces is not one" in {
      val found = dsl
        .fieldError("q", "site.yes")
        .check(
          pageOf(
            """<input name="q" aria-describedby="h"><p id="h">Yes</p><p id="x-error" class="govuk-error-message">Yes</p>"""
          )
        )
      found.map(_.message) mustBe
        Seq("no inline error message was rendered for this field")
      found.head.hint.mkString must include("aria-describedby")
    }
  }

  "timeoutDialog" should {

    "find the meta element play-frontend-hmrc renders" in {
      issues(
        """<html><head><meta name="hmrc-timeout-dialog" content="hmrc-timeout-dialog"></head><body></body></html>""",
        dsl.timeoutDialog
      ) mustBe empty
    }

    "still find a module, and report neither" in {
      issues(
        """<div data-module="hmrc-timeout-dialog"></div>""",
        dsl.timeoutDialog
      ) mustBe empty
      issues("<p>nothing</p>", dsl.timeoutDialog) must not be empty
    }
  }

}
