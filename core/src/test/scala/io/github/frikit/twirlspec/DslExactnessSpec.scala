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

import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Locale

/** What an expectation finds and what it asks of it, read as a browser reads
  * the page.
  */
class DslExactnessSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def page(body: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  "An expectation" should {

    "read a submit input's words from its value" in {
      submitButtonText("Continue").check(
        page("""<form><input type="submit" value="Continue"></form>""")
      ) mustBe empty
      // only what can submit is the submit button, a button with no type included
      submitButtonText("Continue").check(
        page(
          """<form><button type="button" class="govuk-button">Continue</button></form>"""
        )
      ) must not be empty
      submitButtonText("Continue").check(
        page("""<form><button>Continue</button></form>""")
      ) mustBe empty
      // with no type, one that invokes a command submits nothing
      hasSubmitButton.check(
        page("""<form><button command="--save">Save</button></form>""")
      ) must not be empty
      hasSubmitButton.check(
        page(
          """<form><button commandfor="help" command="show-modal">Help</button></form><dialog id="help">Help</dialog>"""
        )
      ) must not be empty
      // an image input says its alt
      submitButtonText("Continue").check(
        page(
          """<form><input type="image" id="submit" src="go.png" alt="Continue"></form>"""
        )
      ) mustBe empty
    }

    "take only a link for a link" in {
      linkWithId("help").check(page("""<div id="help">Help</div>""")) must
        not be empty
      linkWithId("help").check(page("""<a id="help">Help</a>""")) must
        not be empty
      linkWithId("help").check(
        page("""<a id="help" href="/help">Help</a>""")
      ) mustBe empty
    }

    "find a label by the field's id, never by its name" in {
      val expectation = textInput("x").labelledText("Name")
      expectation.check(
        page("""<label for="x">Name</label><input name="x">""")
      ) must not be empty
      expectation.check(
        page("""<label for="x">Name</label><input id="x" name="x">""")
      ) mustBe empty
      // a dropdown's label names its id too
      dropdown("x").check(
        page(
          """<label for="x">Name</label><select name="x"><option>A</option></select>"""
        )
      ) must not be empty
      dropdown("x").check(
        page(
          """<label for="x">Name</label><select id="x" name="x"><option>A</option></select>"""
        )
      ) mustBe empty
      // one its legend labels asks for no label
      textInput("x").labelledByLegend.check(
        page("""<fieldset><legend>Name</legend><input name="x"></fieldset>""")
      ) mustBe empty
    }

    "send a value as HTML's sanitisation leaves it" in {
      Seq(
        """type="text" value="a&#10;b"""" -> "ab",
        """type="url" value=" https://x """" -> "https://x",
        """type="email" value=" a@b """" -> "a@b",
        """type="email" multiple value=" a@b , c@d"""" -> "a@b,c@d",
        """type="number" value="abc"""" -> "",
        """type="number" value="1."""" -> "",
        """type="number" value="-1.5e3"""" -> "-1.5e3",
        """type="number" value="2e308"""" -> "",
        """type="date" value="2023-02-29"""" -> "",
        """type="date" value="2024-02-29"""" -> "2024-02-29",
        """type="date" value="2024-01-31"""" -> "2024-01-31",
        """type="date" value="2024-1-31"""" -> "",
        """type="month" value="2024-13"""" -> "",
        """type="month" value="2024-1"""" -> "",
        """type="week" value="2020-W53"""" -> "2020-W53",
        """type="week" value="2021-W53"""" -> "",
        """type="week" value="2020-53"""" -> "",
        """type="time" value="24:00"""" -> "",
        """type="datetime-local" value="2026-11-11"""" -> "",
        """type="datetime-local" value="2026-11-11 09:30:00.000"""" -> "2026-11-11T09:30",
        """type="datetime-local" value="2026-11-11T09:30:05.500"""" -> "2026-11-11T09:30:05.5",
        """type="range" value="abc"""" -> "abc",
        """type="hidden" value=" x """" -> " x "
      ).foreach { case (attributes, sent) =>
        page(s"""<form><input name="n" $attributes></form>""")
          .formValues("n") mustBe sent
      }
    }

    "keep a textarea's second leading newline, as the parser drops only one" in {
      page("<form><textarea name=\"n\">\n\nx</textarea></form>")
        .formValues("n") mustBe "\nx"
    }

    "write a date and time in ASCII digits whatever the default locale" in {
      val default = Locale.getDefault
      try {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        page(
          """<form><input name="n" type="datetime-local" value="2026-11-11 09:30:05"></form>"""
        ).formValues("n") mustBe "2026-11-11T09:30:05"
        // nor read a type in another language's case: FILE is not "fıle"
        Locale.setDefault(Locale.forLanguageTag("tr"))
        page(
          """<form><input name="n" type="FILE" value="x"></form>"""
        ).formValues("n") mustBe ""
      } finally Locale.setDefault(default)
    }

    "count each option, and read the label of every one" in {
      val twice =
        """<input type="radio" id="a" name="x" value="yes"><label for="a">Yes</label>
          |<input type="radio" id="b" name="x" value="yes"><label for="b">No</label>""".stripMargin
      radioGroup("x").withOptionValues("yes").check(page(twice)) must
        not be empty
      radioGroup("x")
        .containingOption("yes", "site.yes")
        .check(
          page(twice)
        ) must not be empty
    }

    "state a summary list's keys with their arguments, or as any expected value" in {
      val list = page(
        """<dl class="govuk-summary-list">
          |<div class="govuk-summary-list__row"><dt class="govuk-summary-list__key">Name</dt><dd class="govuk-summary-list__value">Ada</dd></div>
          |<div class="govuk-summary-list__row"><dt class="govuk-summary-list__key">Does Ada live in the UK?</dt><dd class="govuk-summary-list__value">Yes</dd></div>
          |</dl>""".stripMargin
      )
      summaryList(
        "checkAnswers.name" -> "Ada",
        messageKey("livesInUk.heading", "Ada") -> "Yes"
      ).check(list) mustBe empty
      summaryList(literal("Name") -> "Ada", matching("UK".r) -> "Yes")
        .check(list) mustBe empty
      // another argument is another key, and a failure shows each key as stated
      summaryList(
        "checkAnswers.name" -> "Ada",
        messageKey("livesInUk.heading", "Bob") -> "Yes"
      ).check(list) must not be empty
      summaryList(literal("Name") -> "Ada", anyText -> "No")
        .check(list)
        .flatMap(_.expected) mustBe List("Name = Ada | anything non-empty = No")
    }
  }

}
