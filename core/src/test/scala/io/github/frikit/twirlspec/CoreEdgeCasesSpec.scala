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
import io.github.frikit.twirlspec.expect.Severity
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{Criterion, Level, Rule, WcagVersion}

/** The branches the main specs reach past: components that are absent rather
  * than wrong, keys that do not resolve, and the "nothing to show" half of a
  * few failure messages.
  */
class CoreEdgeCasesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def pageOf(body: String): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body><main id="main-content">$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def check(p: Page, e: io.github.frikit.twirlspec.expect.Expectation) =
    e.check(p).map(_.rule)

  "expectations on a missing element" should {

    "report the element rather than compare its text" in {
      check(
        pageOf("<h1>A</h1>"),
        elementWithText("nope", "site.continue")
      ) mustBe Seq("element(nope)")
    }
  }

  "expectations given a key that does not resolve" should {

    "report the key for every content expectation" in {
      val p = pageOf("<h1>A</h1><p>Something</p>")
      check(p, content("kitchenSink.notAKey"))   mustBe Seq("content")
      check(p, paragraph("kitchenSink.notAKey")) mustBe Seq("paragraph")
    }

    "report the key for a summary row's actions, once the row itself is found" in {
      val p = pageOf(
        """<h1>A</h1><dl class="govuk-summary-list"><div class="govuk-summary-list__row">
          |<dt class="govuk-summary-list__key">Name</dt>
          |<dd class="govuk-summary-list__value">Ada</dd>
          |<dd class="govuk-summary-list__actions"><a href="/change">Change</a></dd>
          |</div></dl>""".stripMargin
      )
      check(
        p,
        summaryRow("checkAnswers.name").withActions("kitchenSink.notAKey")
      )   must
        contain("summaryRow(messages(checkAnswers.name))")
      check(
        p,
        summaryRow("checkAnswers.name").withActions("checkAnswers.change")
      ) mustBe empty
      check(
        p,
        summaryRow("checkAnswers.name").withChangeLinkTo("/elsewhere")
      )   must
        contain("summaryRow(messages(checkAnswers.name)) change link")
    }
  }

  "form expectations" should {

    "accept a control that is labelled by a legend rather than a label" in {
      val p = pageOf(
        """<h1>A</h1><fieldset><legend>When</legend><input id="d" name="d"></fieldset>"""
      )
      check(p, textInput("d").labelledByLegend) mustBe empty
      check(p, textInput("d"))                  mustBe Seq("input(d) label")
    }

    "match a dropdown that has a name but no id" in {
      val p =
        pageOf(
          """<h1>A</h1><label for="c">Country</label><select name="c"><option value="GB">GB</option></select>"""
        )
      check(
        p,
        dropdown("c").withOptionValues("GB")
      ) must not contain "dropdown(c)"
    }

    "report a hint the field does not reference" in {
      val p = pageOf(
        """<h1>A</h1><label for="e">Email</label>
          |<div id="e-hint" class="govuk-hint">We only use this to contact you</div>
          |<input id="e" name="e">""".stripMargin
      )
      check(
        p,
        textInput("e").hintedText("We only use this to contact you")
      ) mustBe Seq("input(e) hint")
    }

    "cope with a radio group whose fieldset declares no hint" in {
      val p = pageOf(
        """<h1>A</h1><fieldset><legend>Colours</legend>
          |<input type="radio" id="c" name="c" value="red"><label for="c">Red</label>
          |<input type="radio" id="c-2" name="c" value="blue"><label for="c-2">Blue</label>
          |</fieldset>""".stripMargin
      )
      check(p, radioGroup("c").hinted("kitchenSink.p1")) mustBe Seq(
        "radioGroup(c) hint"
      )
    }

    "say so when an error summary has no links at all" in {
      val p = pageOf(
        """<h1>A</h1><div class="govuk-error-summary" data-module="govuk-error-summary">
          |<h2 class="govuk-error-summary__title">There is a problem</h2></div>""".stripMargin
      )
      checkPage(
        p,
        Seq(errorSummaryContaining("x", "kitchenSink.error.email.required"))
      ).message must
        include("(summary has no links)")
    }

    "report a summary entry whose link is right but whose wording is wrong" in {
      val p = pageOf(
        """<h1>A</h1><div class="govuk-error-summary" data-module="govuk-error-summary">
          |<h2 class="govuk-error-summary__title">There is a problem</h2>
          |<ul><li><a href="#email">Something else entirely</a></li></ul></div>""".stripMargin
      )
      // the link also dangles, which is a separate finding
      check(
        p,
        errorSummaryContaining("email", "kitchenSink.error.email.required")
      ) must
        contain("errorSummary(email)")
    }
  }

  "the browser title" should {
    "have its error prefix stripped only when one is present" in {
      check(pageOf("<h1>A</h1>"), titleText("t")) mustBe empty
      val prefixed = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>Error: t</title></head>
          |<body><main><h1>A</h1></main></body></html>""".stripMargin,
        english,
        messages
      )
      check(prefixed, titleText("t")) mustBe empty
    }
  }

  "the outline" should {
    "print nothing for a fragment with no page furniture at all" in {
      val fragment =
        Page.fromString("<p>just a component</p>", english, messages)
      fragment.outline must not include "page"
    }
  }

  "a rule" should {
    "expose the level and version of the criterion it enforces" in {
      val tagged = Rule(
        "x",
        "y",
        criterion = Some(
          Criterion("1.1.1", "Non-text Content", Level.A, WcagVersion.V2_0)
        )
      )(_ => Nil)
      tagged.level       mustBe Some(Level.A)
      tagged.wcagVersion mustBe Some(WcagVersion.V2_0)
      tagged.toString      must include("WCAG 1.1.1")

      val untagged = Rule("x", "y")(_ => Nil)
      untagged.level       mustBe None
      untagged.wcagVersion mustBe None
      untagged.toString    mustBe "x — y"
    }
  }

  "warnings" should {
    "not fail a page by default" in {
      val warned = checkPage(
        pageOf("<h1>A</h1>"),
        Seq(heading("kitchenSink.absent").asWarning)
      )
      warned.passed                   mustBe true
      warned.warnings.map(_.severity) mustBe Seq(Severity.Warning)
      warned.message                    must include("warnings:")
    }
  }

  "the language toggle" should {

    "not count a link that declares the language the page is already in" in {
      // a "report a problem" link commonly carries hreflang for the page it is already in
      pageOf(
        """<a href="/help" lang="en" hreflang="en">Report a problem</a>"""
      ).languageToggle mustBe empty
    }

    "count a link offering a different language" in {
      pageOf(
        """<a href="/?lang=cy" hreflang="cy">Cymraeg</a>"""
      ).languageToggle.size mustBe 1
    }

    "count a language select whatever it links to" in {
      pageOf(
        """<nav class="hmrc-language-select"><a href="/?lang=cy">Cymraeg</a></nav>"""
      ).languageToggle must not be empty
    }
  }

  "the outline" should {

    "say which choice is already made" in {
      val outline = pageOf(
        """<form method="post" action="/answer">
          |<input type="radio" name="value" id="value-yes" value="yes" checked>
          |<input type="radio" name="value" id="value-no" value="no">
          |</form>""".stripMargin
      ).outline
      outline                                              must include("checked")
      outline.linesIterator.count(_.contains("checked")) mustBe 1
    }
  }

  "the defaults the DSL fills in" should {

    "name the control `value`, as Play's form helpers do" in {
      radioGroup().description    mustBe "radioGroup(value)"
      checkboxGroup().description mustBe "checkboxGroup(value)"
      dateInput().description     mustBe "dateInput(value)"
    }

    "give a page built directly no parse errors unless it is handed some" in {
      val p = pageOf("<h1>A</h1>")
      new Page(p.document, english, messages, p.source).parseErrors mustBe Nil
    }
  }

  "a wrapped expectation" should {

    "say how it was wrapped" in {
      heading("site.continue").asWarning.description       must endWith(
        "(warning only)"
      )
      heading("site.continue").when(_ => true).description must endWith(
        "(conditional)"
      )
    }

    "not run at all when its condition is false" in {
      check(
        pageOf("<p>no heading</p>"),
        heading("site.continue").when(_ => false)
      ) mustBe empty
    }
  }

  "an autocomplete expectation" should {

    "report a wrong token and a missing one differently" in {
      val wrong = pageOf(
        """<h1>A</h1><label for="e">Email</label><input id="e" name="e" autocomplete="tel">"""
      )
      val none  = pageOf(
        """<h1>A</h1><label for="e">Email</label><input id="e" name="e">"""
      )
      checkPage(
        wrong,
        Seq(textInput("e").withAutocomplete("email"))
      ).message must include("\"tel\"")
      checkPage(
        none,
        Seq(textInput("e").withAutocomplete("email"))
      ).message must include("(absent)")
    }
  }

  "a hint expectation" should {

    "find a hint that sits inside the group's fieldset without an id of its own" in {
      val p = pageOf(
        s"""<h1>A</h1><fieldset id="c" aria-describedby="c-hint"><legend>Colours</legend>
           |<div class="govuk-hint">${messages("kitchenSink.p1")}</div>
           |<input type="radio" id="c-1" name="c" value="red"><label for="c-1">Red</label>
           |</fieldset>""".stripMargin
      )
      check(p, radioGroup("c").hinted("kitchenSink.p1")) mustBe empty
    }

    "name what the field's aria-describedby does point at when it is not the hint" in {
      val p = pageOf(
        """<h1>A</h1><label for="e">Email</label>
          |<div id="e-hint" class="govuk-hint">We only use this to contact you</div>
          |<input id="e" name="e" aria-describedby="something-else">""".stripMargin
      )
      checkPage(
        p,
        Seq(textInput("e").hintedText("We only use this to contact you"))
      ).message must
        include("something-else")
    }
  }

  "the accessible name" should {

    "come from aria-labelledby, or from a label wrapped around the control" in {
      val p = pageOf(
        """<h1>A</h1><span id="lbl">Given name</span><input id="g" aria-labelledby="lbl">
          |<label>Family name <input id="f"></label>""".stripMargin
      )
      p.accessibleName(p.byId("g")(0)) mustBe "Given name"
      p.accessibleName(p.byId("f")(0)) mustBe "Family name"
    }
  }

}
