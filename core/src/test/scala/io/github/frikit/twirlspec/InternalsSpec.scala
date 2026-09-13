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
import io.github.frikit.twirlspec.expect.Expected
import io.github.frikit.twirlspec.page.{Page, Text}

/** The small surfaces the bigger specs reach past: the escape hatches, the
  * "nothing found" halves of failure messages, and the text normalisation
  * everything else leans on.
  */
class InternalsSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def pageOf(body: String, lang: play.api.i18n.Lang = english): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="${lang.code}"><head><title>t</title></head>
         |<body><main id="main-content">$body</main></body></html>""".stripMargin,
      lang,
      messagesApi.preferred(Seq(lang))
    )

  "the raw escape hatches" should {
    "hand back the underlying document and element details" in {
      val page = pageOf("""<h1 class="govuk-heading-l" id="title">A</h1>""")
      page.doc.select("h1").size mustBe 1
      page.byId("title").id mustBe Some("title")
      page.byId("title").classes must contain("govuk-heading-l")
      page.byId("nope").id mustBe None
      page.byId("nope").classes mustBe empty
      page.byId("title")(0).tagName() mustBe "h1"
      page.dateInput("value").selector must include("value-day")
    }
  }

  "text normalisation" should {
    "cope with null and with the punctuation GOV.UK content uses" in {
      Text.normalise(null) mustBe ""
      Text.preview(null) mustBe ""
      Text.same("don’t", "don't") mustBe true
      Text.normalise("a b c") mustBe "a b c"
      Text.normalise("soft­hyphen") mustBe "softhyphen"
      Text.normalise("“quoted”") mustBe "\"quoted\""
      Text.containsText("The quick brown fox", "quick brown") mustBe true
    }
  }

  "failure messages with nothing to show" should {

    "say so rather than print an empty string" in {
      val empty = pageOf("""<h1></h1><form action="/x" method="post"></form>""")

      renderedMessage(empty, heading(literal("Something"))) must include(
        "(empty)"
      )
      renderedMessage(
        empty,
        errorSummaryContaining("x", "kitchenSink.error.email.required")
      ) must
        include("errorSummary")
      renderedMessage(
        pageOf("<h1>A</h1>"),
        summaryList("checkAnswers.name" -> "Ada")
      ) must
        include("(no summary list rows)")
      renderedMessage(
        pageOf("<h1>A</h1>"),
        tableHeaders("kitchenSink.col1")
      ) must include("(no table headers)")
      renderedMessage(pageOf("<h1>A</h1>"), tableRow("Ada")) must include(
        "(no table rows)"
      )
      renderedMessage(
        pageOf("<h1>A</h1>"),
        link("kitchenSink.link")
      ) must include("(no links on the page)")
      renderedMessage(
        pageOf("<h1>A</h1>"),
        summaryRow("checkAnswers.name")
      ) must
        include("(no summary list on the page)")
      renderedMessage(
        pageOf("<h1>A</h1>"),
        fieldError("email", "kitchenSink.error.email.required")
      ) must
        include("(no inline errors on the page)")
    }

    "name the fields that do have errors" in {
      val page = pageOf(
        """<h1>A</h1><label for="a">A</label>
          |<p id="a-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Boom</p>
          |<input id="a" name="a" aria-describedby="a-error">""".stripMargin
      )
      renderedMessage(
        page,
        fieldError("b", "kitchenSink.error.email.required")
      ) must include("errors on: a")
    }

    "report a checkbox selection mismatch both ways round" in {
      val page = pageOf(
        """<h1>A</h1><fieldset><legend>Colours</legend>
          |<input type="checkbox" id="c" name="c" value="red" checked><label for="c">Red</label>
          |<input type="checkbox" id="c-2" name="c" value="blue"><label for="c-2">Blue</label></fieldset>""".stripMargin
      )
      renderedMessage(page, checkboxGroup("c").nothingSelected) must include(
        "(nothing selected)"
      )
      val unselected = pageOf(
        """<h1>A</h1><fieldset><legend>Colours</legend>
          |<input type="checkbox" id="c" name="c" value="red"><label for="c">Red</label></fieldset>""".stripMargin
      )
      renderedMessage(
        unselected,
        checkboxGroup("c").selectedIs("red")
      ) must include("(nothing selected)")
    }
  }

  "an expected value" should {
    "describe itself with and without arguments" in {
      Expected.Key("a.b").describe mustBe "messages(a.b)"
      Expected.Key("a.b", Seq("x", "y")).describe mustBe "messages(a.b, x, y)"
    }
  }

  private def renderedMessage(
      page: Page,
      e: io.github.frikit.twirlspec.expect.Expectation
  ): String =
    checkPage(page, Seq(e)).message

}
