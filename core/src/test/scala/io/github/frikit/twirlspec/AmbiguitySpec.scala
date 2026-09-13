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
import io.github.frikit.twirlspec.expect.Expectation
import io.github.frikit.twirlspec.page.Page

/** An expectation that names one element must find exactly one.
  *
  * Taking the first of several is how a test ends up asserting against
  * something other than the thing it names, and passing while it does so.
  */
class AmbiguitySpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def pageOf(body: String): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body><main id="main-content">$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def check(p: Page, e: Expectation) = e.check(p).map(_.rule)
  private def message(p: Page, e: Expectation) = checkPage(p, Seq(e)).message

  "a form control that appears twice" should {

    "be reported as ambiguous rather than silently resolved" in {
      val p = pageOf(
        """<h1>A</h1>
          |<label for="email">Work email</label><input id="email" name="email" value="work@example.com">
          |<label for="email2">Home email</label><input id="email2" name="email" value="home@example.com">""".stripMargin
      )
      check(p, textInput("email").withValue("work@example.com")) mustBe Seq(
        "input(email)"
      )
      message(p, textInput("email")) must include("2 elements matched")
      message(p, textInput("email")) must include("this assertion is ambiguous")
    }

    "name what matched, so the fix is obvious" in {
      val p = pageOf(
        """<h1>A</h1><label for="a">A</label><input id="a" name="dup">
          |<label for="b">B</label><input id="b" name="dup">""".stripMargin
      )
      val m = message(p, textInput("dup"))
      m must include("#a")
      m must include("#b")
      m must include("name the one you mean")
    }
  }

  "other singular expectations" should {

    "reject duplicates too" in {
      val dupIds = pageOf("""<h1>A</h1><p id="x">one</p><p id="x">two</p>""")
      check(dupIds, element("x")) mustBe Seq("element(x)")
      check(dupIds, elementWithText("x", "site.continue")) mustBe Seq(
        "element(x)"
      )
      check(dupIds, elementHasClass("x", "y")) mustBe Seq("class(x)")

      val twoBackLinks = pageOf(
        """<a href="/one" class="govuk-back-link">Back</a>
          |<a href="/two" class="govuk-back-link">Back</a><h1>A</h1>""".stripMargin
      )
      check(twoBackLinks, backLink) mustBe Seq("backLink")
      check(twoBackLinks, backLink.to("/one")) mustBe Seq("backLink")

      val twoServiceNames = pageOf(
        """<span class="govuk-header__service-name">Example Service</span>
          |<span class="govuk-service-navigation__service-name">Example Service</span><h1>A</h1>""".stripMargin
      )
      check(twoServiceNames, serviceName()) mustBe Seq("serviceName")

      val twoSelects = pageOf(
        """<h1>A</h1><label for="c1">C</label><select id="c1" name="c"><option value="GB">GB</option></select>
          |<label for="c2">C</label><select id="c2" name="c"><option value="FR">FR</option></select>""".stripMargin
      )
      check(twoSelects, dropdown("c")) mustBe Seq("dropdown(c)")

      val twoControls = pageOf(
        """<h1>A</h1><label for="d1">D</label><input id="d1" name="d" disabled>
          |<label for="d2">D</label><input id="d2" name="d">""".stripMargin
      )
      check(twoControls, disabled("d")) mustBe Seq("disabled(d)")
      check(twoControls, enabled("d")) mustBe Seq("enabled(d)")
      check(twoControls, required("d")) mustBe Seq("required(d)")
      check(twoControls, invalid("d")) mustBe Seq("invalid(d)")
      check(twoControls, describedAs("d", "site.continue")) mustBe Seq(
        "describedAs(d)"
      )
    }

    "reject a summary list with the same key twice" in {
      val p = pageOf(
        """<h1>A</h1><dl class="govuk-summary-list">
          |<div class="govuk-summary-list__row"><dt class="govuk-summary-list__key">Name</dt>
          |<dd class="govuk-summary-list__value">Ada</dd></div>
          |<div class="govuk-summary-list__row"><dt class="govuk-summary-list__key">Name</dt>
          |<dd class="govuk-summary-list__value">Grace</dd></div></dl>""".stripMargin
      )
      check(p, summaryRow("checkAnswers.name")) must contain(
        "summaryRow(messages(checkAnswers.name))"
      )
      message(p, summaryRow("checkAnswers.name")) must include(
        "2 summary list rows have this key"
      )
    }

    "reject an error summary with two titles" in {
      val p = pageOf(
        """<h1>A</h1><div class="govuk-error-summary">
          |<h2 class="govuk-error-summary__title">There is a problem</h2>
          |<h2 class="govuk-error-summary__title">There is a problem</h2></div>""".stripMargin
      )
      check(p, errorSummaryTitle()) mustBe Seq("errorSummaryTitle")
    }
  }

  "expectations that are plural by nature" should {

    "be unaffected" in {
      val p = pageOf(
        """<h1>A</h1>
          |<fieldset><legend>Colours</legend>
          |  <input type="radio" id="r1" name="c" value="red"><label for="r1">Red</label>
          |  <input type="radio" id="r2" name="c" value="blue"><label for="r2">Blue</label>
          |</fieldset>
          |<ul class="govuk-list govuk-list--bullet"><li>First bullet</li><li>Second bullet</li></ul>
          |<a href="/a">one</a><a href="/b">two</a>""".stripMargin
      )
      check(
        p,
        radioGroup("c")
          .withOptions("red" -> "kitchenSink.red", "blue" -> "kitchenSink.blue")
      ) mustBe empty
      check(p, bullets("kitchenSink.b1", "kitchenSink.b2")) mustBe empty
      check(p, cssSelector("a[href]")) mustBe empty
      check(p, elementCount("a[href]", 2)) mustBe empty
    }

    "remain the escape hatch when several matches are legitimate" in {
      val p = pageOf(
        """<h1>A</h1><input id="a" name="dup"><input id="b" name="dup">"""
      )
      check(p, textInput("dup")) mustBe Seq("input(dup)") // ambiguous
      check(
        p,
        elementCount("""[name="dup"]""", 2)
      ) mustBe empty // stated as a group instead
    }
  }

}
