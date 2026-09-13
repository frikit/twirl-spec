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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page

/** Control state, form values and reading order. */
class ControlStateSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val html =
    """<!DOCTYPE html>
      |<html lang="en"><head><title>t</title></head>
      |<body><main><h1>A</h1>
      |  <div class="govuk-error-summary"><a href="#email">Enter an email address</a></div>
      |  <form action="/x" method="post">
      |    <label for="email">Email</label>
      |    <div id="email-hint">We only use this to contact you</div>
      |    <input id="email" name="email" value="ada@example.com" required aria-invalid="true"
      |           aria-describedby="email-hint">
      |    <label for="notes">Notes</label>
      |    <textarea id="notes" name="notes">Some notes</textarea>
      |    <label for="country">Country</label>
      |    <select id="country" name="country">
      |      <option value="GB" selected>United Kingdom</option><option value="FR">France</option>
      |    </select>
      |    <input type="radio" id="c1" name="contact" value="email" checked><label for="c1">Email</label>
      |    <input type="radio" id="c2" name="contact" value="post"><label for="c2">Post</label>
      |    <fieldset disabled>
      |      <legend>Locked</legend>
      |      <label for="locked">Locked field</label><input id="locked" name="locked">
      |    </fieldset>
      |    <label for="free">Free</label><input id="free" name="free">
      |    <button type="submit">Continue</button>
      |  </form>
      |</main></body></html>""".stripMargin

  private lazy val page: Page = Page.fromString(html, english, messages)

  private def check(e: io.github.frikit.twirlspec.expect.Expectation) =
    e.check(page).map(_.rule)

  "form values" should {

    "read every named control the way a browser would submit it" in {
      check(
        formValues(
          "email" -> "ada@example.com",
          "notes" -> "Some notes",
          "country" -> "GB",
          "contact" -> "email"
        )
      ) mustBe empty
    }

    "report a value that differs, and a name that is not there" in {
      check(formValues("email" -> "someone@else.com")) mustBe Seq(
        "formValues(email)"
      )
      check(formValues("nope" -> "x")) mustBe Seq("formValues(nope)")
      checkPage(
        Page.fromString(
          "<html><body><h1>A</h1></body></html>",
          english,
          messages
        ),
        Seq(formValues("a" -> "b"))
      ).message must include("(no named controls)")
    }
  }

  "control state" should {

    "see a control disabled through its fieldset, not just its own attribute" in {
      check(disabled("locked")) mustBe empty
      check(enabled("free")) mustBe empty
      check(enabled("locked")) mustBe Seq("enabled(locked)")
      check(disabled("free")) mustBe Seq("disabled(free)")
    }

    "read required and invalid" in {
      check(required("email")) mustBe empty
      check(invalid("email")) mustBe empty
      // notes is a <textarea>: the state expectations cover any control, not
      // just <input>, so these fail because it is neither, not because it is
      // missing.
      check(required("notes")) mustBe Seq("required(notes)")
      check(invalid("notes")) mustBe Seq("invalid(notes)")
      page.formControl("notes").nonEmpty mustBe true
    }

    "accept aria-required as well as the required attribute" in {
      val aria = Page.fromString(
        """<html lang="en"><head><title>t</title></head><body><main><h1>A</h1>
          |<label for="a">A</label><input id="a" name="a" aria-required="true"></main></body></html>""".stripMargin,
        english,
        messages
      )
      required("a").check(aria) mustBe empty
    }

    "read the description announced after the name" in {
      check(describedAs("email", "kitchenSink.email.hint")) mustBe Seq(
        "describedAs(email)"
      )
      page.accessibleDescription(
        page.input("email")(0)
      ) mustBe "We only use this to contact you"
    }

    "report a control that is not on the page" in {
      check(disabled("nope")) mustBe Seq("disabled(nope)")
      check(enabled("nope")) mustBe Seq("enabled(nope)")
      check(required("nope")) mustBe Seq("required(nope)")
      check(invalid("nope")) mustBe Seq("invalid(nope)")
      check(describedAs("nope", "kitchenSink.email.hint")) mustBe Seq(
        "describedAs(nope)"
      )
    }
  }

  "reading order" should {

    "accept an error summary that comes before the form it describes" in {
      check(appearsBefore(".govuk-error-summary", "form")) mustBe empty
    }

    "reject the reverse, and report either side being absent" in {
      check(appearsBefore("form", ".govuk-error-summary")) mustBe
        Seq("order(form before .govuk-error-summary)")
      check(appearsBefore(".nope", "form")) mustBe Seq(
        "order(.nope before form)"
      )
      check(appearsBefore("form", ".nope")) mustBe Seq(
        "order(form before .nope)"
      )
    }
  }

}
