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

package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.expect.{Severity, Violation}
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.wcag.{TwirlStandards, WcagStandards}

/** Every standards rule, proved to fire when it should and — just as importantly — to stay quiet on correct markup.
  */
class StandardsSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val allRules =
    WcagStandards.expectation(WcagStandards.all ++ TwirlStandards.all ++ GovukStandards.all)

  private def check(html: String): Seq[Violation] =
    allRules.check(Page.fromString(html, english, messages))

  private def rulesFired(html: String): Set[String] = check(html).map(_.rule).toSet

  private def page(body: String, lang: String = "en", title: String = "A page - Service - GOV.UK"): String =
    s"""<!DOCTYPE html><html lang="$lang"><head><title>$title</title></head>
       |<body><main id="main-content">$body</main></body></html>""".stripMargin

  private val cleanBody =
    """<h1>A page</h1>
      |<h2>A section</h2>
      |<form method="post" action="/x">
      |  <label for="name">Your name</label>
      |  <input id="name" name="name" type="text">
      |  <button type="submit">Continue</button>
      |</form>""".stripMargin

  "a correct page" should {
    "raise nothing at all" in {
      check(page(cleanBody)) mustBe empty
    }
  }

  "the page framing rules" should {

    "flag a page with no h1" in {
      rulesFired(page("<h2>Only a subheading</h2>")) must contain("one-h1")
    }

    "flag a page with two h1s" in {
      rulesFired(page("<h1>One</h1><h1>Two</h1>")) must contain("one-h1")
    }

    "flag a missing lang attribute" in {
      val html = """<!DOCTYPE html><html><head><title>t</title></head><body><main><h1>A</h1></main></body></html>"""
      rulesFired(html) must contain("html-lang")
    }

    "flag a lang attribute that contradicts the render language" in {
      rulesFired(page("<h1>A</h1>", lang = "cy")) must contain("html-lang")
    }

    "flag a skipped heading level" in {
      rulesFired(page("<h1>A</h1><h3>C</h3>")) must contain("heading-order")
    }

    "not flag heading levels that go back up" in {
      rulesFired(page("<h1>A</h1><h2>B</h2><h3>C</h3><h2>D</h2>")) must not contain "heading-order"
    }

    "flag an empty heading" in {
      rulesFired(page("<h1>A</h1><h2></h2>")) must contain("no-empty-headings")
    }

    "flag duplicate ids" in {
      rulesFired(page("""<h1>A</h1><p id="dup">x</p><p id="dup">y</p>""")) must contain("unique-ids")
    }

    "skip page level rules for a fragment" in {
      // A component spec renders no <html>, <title> or <h1> — and should not be
      // told off for any of them.
      rulesFired("""<div class="govuk-inset-text">Some guidance</div>""") mustBe empty
    }
  }

  "the error rules" should {

    "flag an error summary whose link goes nowhere" in {
      val html = page(
        """<h1>A</h1>
          |<div class="govuk-error-summary"><ul><li><a href="#missing">Enter a name</a></li></ul></div>""".stripMargin,
        title = "Error: A page - Service - GOV.UK"
      )
      rulesFired(html) must contain("error-summary-targets")
    }

    "flag an error state with an unprefixed title" in {
      val html = page(
        """<h1>A</h1><div class="govuk-error-summary" data-module="govuk-error-summary">
          |<ul><li><a href="#name">Enter a name</a></li></ul></div>
          |<label for="name">Name</label><input id="name" name="name" aria-describedby="name-error">
          |<p id="name-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Enter a name</p>""".stripMargin
      )
      rulesFired(html) must contain("error-title-prefix")
    }

    "flag an inline error the field does not reference" in {
      val html = page(
        """<h1>A</h1><label for="name">Name</label><input id="name" name="name">
          |<p id="name-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Enter a name</p>""".stripMargin
      )
      rulesFired(html) must contain("error-aria-describedby")
    }

    "accept a grouped control whose fieldset carries the error reference" in {
      // Exactly what govukDateInput emits: the wrapper div has id="value" but the fieldset is what references the erro
      val html = page(
        """<h1>A</h1>
          |<fieldset class="govuk-fieldset" role="group" aria-describedby="value-hint value-error">
          |  <legend>When did it start?</legend>
          |  <div id="value-hint" class="govuk-hint">For example, 31 3 1980</div>
          |  <p id="value-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Enter a date</p>
          |  <div class="govuk-date-input" id="value">
          |    <label for="value.day">Day</label><input id="value.day" name="value.day">
          |    <label for="value.month">Month</label><input id="value.month" name="value.month">
          |    <label for="value.year">Year</label><input id="value.year" name="value.year">
          |  </div>
          |</fieldset>""".stripMargin,
        title = "Error: A page - Service - GOV.UK"
      )
      rulesFired(html) must not contain "error-aria-describedby"
    }

    "not blame the field when the inline error points at one that does not exist" in {
      // The error message is `ghost-error`, but there is no `#ghost` to carry the aria-describedby.
      val html  = page(
        """<h1>A</h1>
          |<div class="govuk-error-summary" data-module="govuk-error-summary">
          |  <h2 class="govuk-error-summary__title">There is a problem</h2>
          |  <ul><li><a href="#ghost">Enter a name</a></li></ul>
          |</div>
          |<p id="ghost-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Enter a name</p>""".stripMargin,
        title = "Error: A page - Service - GOV.UK"
      )
      val fired = rulesFired(html)
      fired must contain("error-summary-targets")
      fired must not contain "error-aria-describedby"
    }

    "flag an inline error with no visually hidden prefix" in {
      val html = page(
        """<h1>A</h1><label for="name">Name</label><input id="name" name="name" aria-describedby="name-error">
          |<p id="name-error" class="govuk-error-message">Enter a name</p>""".stripMargin
      )
      rulesFired(html) must contain("error-hidden-prefix")
    }

    "accept a correctly wired error state" in {
      val html = page(
        """<h1>A</h1>
          |<div class="govuk-error-summary" data-module="govuk-error-summary">
          |  <h2 class="govuk-error-summary__title">There is a problem</h2>
          |  <ul><li><a href="#name">Enter a name</a></li></ul>
          |</div>
          |<label for="name">Name</label>
          |<p id="name-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Enter a name</p>
          |<input id="name" name="name" aria-describedby="name-error">""".stripMargin,
        title = "Error: A page - Service - GOV.UK"
      )
      check(html) mustBe empty
    }
  }

  "the form rules" should {

    "flag an input with no label" in {
      rulesFired(page("""<h1>A</h1><input id="name" name="name" type="text">""")) must contain("labelled-controls")
    }

    "accept an input labelled by aria-label" in {
      rulesFired(page("""<h1>A</h1><input id="q" name="q" aria-label="Search">""")) must
        not contain "labelled-controls"
    }

    "flag radios outside a fieldset" in {
      val html = page(
        """<h1>A</h1>
          |<label for="y">Yes</label><input type="radio" id="y" name="value" value="true">
          |<label for="n">No</label><input type="radio" id="n" name="value" value="false">""".stripMargin
      )
      rulesFired(html) must contain("grouped-choices")
    }

    "flag a fieldset with no legend" in {
      val html = page(
        """<h1>A</h1><fieldset>
          |<label for="y">Yes</label><input type="radio" id="y" name="value" value="true">
          |<label for="n">No</label><input type="radio" id="n" name="value" value="false">
          |</fieldset>""".stripMargin
      )
      rulesFired(html) must contain("grouped-choices")
    }

    "accept a correct radio group" in {
      val html = page(
        """<h1>A</h1><fieldset><legend>Do they live in the UK?</legend>
          |<label for="y">Yes</label><input type="radio" id="y" name="value" value="true">
          |<label for="n">No</label><input type="radio" id="n" name="value" value="false">
          |</fieldset>""".stripMargin
      )
      check(html) mustBe empty
    }
  }

  "the content rules" should {

    "flag an unresolved message key rendered to a citizen" in {
      val fired = rulesFired(page("""<h1>A</h1><p>whatIsYourName.someMissingKey</p>"""))
      fired must contain("no-raw-message-keys")
    }

    "not mistake a filename or a domain for a message key" in {
      val html = page("""<h1>A</h1><p>guidance.pdf</p><p>www.gov.uk</p><p>Ada Lovelace</p>""")
      rulesFired(html) must not contain "no-raw-message-keys"
    }

    "flag a Scala value leaking into the page" in {
      rulesFired(page("""<h1>A</h1><p>Some(Ada Lovelace)</p>""")) must contain("no-scala-leakage")
    }

    "warn about a table header with no scope" in {
      val html  = page("""<h1>A</h1><table><tr><th>Name</th></tr></table>""")
      val fired = check(html)
      fired.map(_.rule)                                            must contain("table-header-scope")
      fired.find(_.rule == "table-header-scope").map(_.severity) mustBe Some(Severity.Warning)
    }
  }

  "the link and media rules" should {

    "flag a link with no text" in {
      rulesFired(page("""<h1>A</h1><a href="/x"></a>""")) must contain("link-has-name")
    }

    "warn about vague link text" in {
      rulesFired(page("""<h1>A</h1><a href="/x">click here</a>""")) must contain("link-text-is-meaningful")
    }

    "warn when a link opens a new tab silently" in {
      rulesFired(page("""<h1>A</h1><a href="/x" target="_blank">Read the guidance</a>""")) must
        contain("new-tab-is-announced")
    }

    "accept a link that announces its new tab" in {
      rulesFired(page("""<h1>A</h1><a href="/x" target="_blank">Read the guidance (opens in new tab)</a>""")) must
        not contain "new-tab-is-announced"
    }

    "flag an image with no alt attribute" in {
      rulesFired(page("""<h1>A</h1><img src="/logo.png">""")) must contain("image-alt")
    }

    "accept a decorative image with an empty alt" in {
      rulesFired(page("""<h1>A</h1><img src="/logo.png" alt="">""")) must not contain "image-alt"
    }
  }

  "rule selection" should {

    "allow a service to switch a rule off" in {
      val html  = page("""<h1>A</h1><input id="name" name="name" type="text">""")
      val fired = WcagStandards
        .expectation(WcagStandards.allExcept("labelled-controls"))
        .check(Page.fromString(html, english, messages))
        .map(_.rule)
      fired must not contain "labelled-controls"
    }

    "expose every rule with a stable id" in {
      val ids = (WcagStandards.all ++ TwirlStandards.all ++ GovukStandards.all).map(_.id)
      ids.distinct.size mustBe ids.size
      ids                 must contain allOf ("one-h1", "labelled-controls", "no-raw-message-keys", "error-summary-targets")
    }
  }

}
