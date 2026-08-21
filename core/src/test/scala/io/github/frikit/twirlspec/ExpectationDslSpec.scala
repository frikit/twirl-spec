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
import io.github.frikit.twirlspec.expect.Expectation
import io.github.frikit.twirlspec.page.Page

/** Every expectation in the DSL, on a page that satisfies it and on one that
  * does not.
  *
  * A testing library whose own assertions are untested is worse than no
  * library: a check that silently never fails looks exactly like a passing
  * suite. So each expectation here is exercised in both directions.
  */
class ExpectationDslSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val fixture =
    """<!DOCTYPE html>
      |<html lang="en">
      |  <head><title>Everything at once - Example Service - GOV.UK</title></head>
      |  <body>
      |    <div class="govuk-phase-banner">beta</div>
      |    <span class="govuk-service-navigation__service-name">Example Service</span>
      |    <nav class="hmrc-language-select"><a href="?lang=cy" hreflang="cy">Cymraeg</a></nav>
      |    <a href="/sign-out" id="sign-out">Sign out</a>
      |    <div data-module="hmrc-timeout-dialog"></div>
      |    <a href="/back" class="govuk-back-link">Back</a>
      |    <main id="main-content">
      |      <span class="govuk-caption-l">A caption</span>
      |      <h1 class="govuk-heading-l">Everything at once</h1>
      |      <p class="govuk-body">A paragraph of guidance.</p>
      |      <h2>A section heading</h2>
      |
      |      <div class="govuk-warning-text"><strong class="govuk-warning-text__text">You must tell us within 30 days.</strong></div>
      |      <div class="govuk-inset-text">This is inset text.</div>
      |      <div class="govuk-notification-banner">Your answers were saved.</div>
      |      <div class="govuk-panel"><h2 class="govuk-panel__title">Application complete</h2><div class="govuk-panel__body">Your reference is ABC123</div></div>
      |      <details class="govuk-details"><summary><span class="govuk-details__summary-text">Help with this question</span></summary></details>
      |
      |      <ul class="govuk-list govuk-list--bullet"><li>First bullet</li><li>Second bullet</li></ul>
      |      <ol class="govuk-list govuk-list--number"><li>First step</li><li>Second step</li></ol>
      |      <a class="govuk-link" id="guidance-link" href="/guidance">Read the guidance</a>
      |
      |      <form method="post" action="/kitchen-sink">
      |        <input type="hidden" name="csrfToken" value="abc123">
      |
      |        <label class="govuk-label" for="email">Email address</label>
      |        <div id="email-hint" class="govuk-hint">We will only use this to contact you</div>
      |        <input class="govuk-input" id="email" name="email" type="email" value="ada@example.com"
      |               autocomplete="email" aria-describedby="email-hint">
      |
      |        <label class="govuk-label" for="notes">Additional notes</label>
      |        <textarea class="govuk-textarea" id="notes" name="notes">Some notes</textarea>
      |
      |        <label class="govuk-label" for="country">Country</label>
      |        <select class="govuk-select" id="country" name="country">
      |          <option value="GB">United Kingdom</option>
      |          <option value="FR">France</option>
      |        </select>
      |
      |        <fieldset class="govuk-fieldset" aria-describedby="colours-hint">
      |          <legend>Choose your colours</legend>
      |          <div id="colours-hint" class="govuk-hint">Select all that apply</div>
      |          <input type="checkbox" id="colours" name="colours" value="red" checked>
      |          <label for="colours">Red</label>
      |          <input type="checkbox" id="colours-2" name="colours" value="blue">
      |          <label for="colours-2">Blue</label>
      |        </fieldset>
      |
      |        <label class="govuk-label" for="evidence">Upload a file</label>
      |        <input class="govuk-file-upload" id="evidence" name="evidence" type="file">
      |
      |        <table class="govuk-table">
      |          <thead><tr><th scope="col">Name</th><th scope="col">Year</th></tr></thead>
      |          <tbody><tr><td>Ada</td><td>1815</td></tr></tbody>
      |        </table>
      |
      |        <dl class="govuk-summary-list">
      |          <div class="govuk-summary-list__row">
      |            <dt class="govuk-summary-list__key">Name</dt>
      |            <dd class="govuk-summary-list__value">Ada Lovelace</dd>
      |            <dd class="govuk-summary-list__actions"><a href="/change/name">Change</a></dd>
      |          </div>
      |          <div class="govuk-summary-list__row">
      |            <dt class="govuk-summary-list__key">Date of birth</dt>
      |            <dd class="govuk-summary-list__value">27 March 1993</dd>
      |            <dd class="govuk-summary-list__actions"><a href="/change/dob">Change</a></dd>
      |          </div>
      |        </dl>
      |
      |        <button type="submit" class="govuk-button" id="submit">Continue</button>
      |        <button type="button" class="govuk-button" id="save">Save and come back later</button>
      |      </form>
      |    </main>
      |  </body>
      |</html>""".stripMargin

  private lazy val page: Page = Page.fromString(fixture, english, messages)

  private def check(e: Expectation): Seq[String] = e.check(page).map(_.rule)

  private def passes(name: String, e: Expectation): Unit =
    withClue(s"$name should have passed but reported: ${e.check(page).map(_.message).mkString("; ")} — ") {
      check(e) mustBe empty
    }

  private def fails(name: String, e: Expectation): Unit =
    withClue(s"$name should have failed but passed — ")(check(e) must not be empty)

  "framing expectations" should {
    "pass on a page that satisfies them" in {
      passes("title", title("kitchenSink.title"))
      passes("titleText", titleText("Everything at once"))
      passes("exactTitle", exactTitle("Everything at once - Example Service - GOV.UK"))
      passes("title literal", title(literal("Everything at once")))
      passes("heading", heading("kitchenSink.heading"))
      passes("headingText", headingText("Everything at once"))
      passes("heading anyText", heading(anyText))
      passes("caption", caption("kitchenSink.caption"))
      passes("captionText", captionText("A caption"))
      passes("serviceName", serviceName())
      passes("backLink", backLink)
      passes("backLink.to", backLink.to("/back"))
      passes("languageToggle", languageToggle)
      passes("timeoutDialog", timeoutDialog)
      passes("signOutLink", signOutLink)
      passes("phaseBanner", phaseBanner)
      passes("subheading", subheading("kitchenSink.section"))
    }

    "fail on a page that does not" in {
      fails("wrong title", title("kitchenSink.section"))
      fails("undefined key", title("kitchenSink.notAKey"))
      fails("wrong exactTitle", exactTitle("Nope"))
      fails("wrong heading", heading("kitchenSink.section"))
      fails("wrong caption", caption("kitchenSink.section"))
      fails("wrong backLink target", backLink.to("/elsewhere"))
      fails("noBackLink", noBackLink)
      fails("wrong subheading", subheading("kitchenSink.title"))
    }
  }

  "form expectations" should {
    "pass on a page that satisfies them" in {
      passes(
        "textInput",
        textInput("email")
          .labelled("kitchenSink.email")
          .hinted("kitchenSink.email.hint")
          .withValue("ada@example.com")
          .withAutocomplete("email")
          .ofType("email")
      )
      passes("textArea", textArea("notes").labelled("kitchenSink.notes").withValue("Some notes"))
      passes("textInput labelledText", textInput("email").labelledText("Email address"))
      passes("textInput hintedText", textInput("email").hintedText("We will only use this to contact you"))
      passes("textInput labelledByLegend", textInput("email").labelledByLegend)
      passes(
        "dropdown",
        dropdown("country").labelled("kitchenSink.country").withOptionValues("GB", "FR").withOptionCount(2)
      )
      passes(
        "checkboxGroup",
        checkboxGroup("colours")
          .legendIs("kitchenSink.colours")
          .withOptions("red" -> "kitchenSink.red", "blue" -> "kitchenSink.blue")
          .selectedIs("red")
      )
      passes("checkboxGroup withOptionValues", checkboxGroup("colours").withOptionValues("red", "blue"))
      passes("checkboxGroup containingOption", checkboxGroup("colours").containingOption("red", "kitchenSink.red"))
      passes("fileUpload", fileUpload("evidence"))
      passes("hiddenInput", hiddenInput("csrfToken", "abc123"))
      passes("submitButton", submitButton("site.continue"))
      passes("submitButtonText", submitButtonText("Continue"))
      passes("hasSubmitButton", hasSubmitButton)
      passes("button", button("save", "kitchenSink.save"))
      passes("formPostsTo", formPostsTo("/kitchen-sink"))
      passes("noErrors", noErrors)
    }

    "fail on a page that does not" in {
      fails("missing input", textInput("nope"))
      fails("wrong label", textInput("email").labelled("kitchenSink.notes"))
      fails("wrong hint", textInput("email").hinted("kitchenSink.p1"))
      fails("wrong value", textInput("email").withValue("someone@else.com"))
      fails("wrong autocomplete", textInput("email").withAutocomplete("name"))
      fails("wrong type", textInput("email").ofType("text"))
      fails("unlabelled input", textInput("csrfToken"))
      fails("missing textarea", textArea("nope"))
      fails("wrong dropdown options", dropdown("country").withOptionValues("DE"))
      fails("wrong dropdown count", dropdown("country").withOptionCount(9))
      fails("missing checkbox group", checkboxGroup("nope"))
      fails("wrong checkbox legend", checkboxGroup("colours").legendIs("kitchenSink.title"))
      fails("wrong checkbox options", checkboxGroup("colours").withOptionValues("green"))
      fails("wrong checkbox selection", checkboxGroup("colours").nothingSelected)
      fails("missing file upload", fileUpload("nope"))
      fails("wrong hidden value", hiddenInput("csrfToken", "wrong"))
      fails("missing hidden input", hiddenInput("nope", "x"))
      fails("wrong submit text", submitButton("kitchenSink.save"))
      fails("wrong button text", button("save", "site.continue"))
      fails("wrong form action", formPostsTo("/elsewhere"))
      fails("wrong form method", formGetsFrom("/kitchen-sink"))
      fails("errorTitlePrefix on a clean page", errorTitlePrefix)
      fails("errorSummary on a clean page", errorSummary("email" -> "kitchenSink.error.email.required"))
      fails("errorSummaryTitle on a clean page", errorSummaryTitle())
      fails("fieldError on a clean page", fieldError("email", "kitchenSink.error.email.required"))
    }
  }

  "content expectations" should {
    "pass on a page that satisfies them" in {
      passes("content", content("kitchenSink.p1"))
      passes("contentText", contentText("A paragraph of guidance."))
      passes("noContent", noContent("kitchenSink.absent"))
      passes("paragraph", paragraph("kitchenSink.p1"))
      passes("warning", warning("kitchenSink.warning"))
      passes("insetText", insetText("kitchenSink.inset"))
      passes("notificationBanner", notificationBanner("kitchenSink.banner"))
      passes("panelTitle", panelTitle("kitchenSink.panelTitle"))
      passes("panelBody", panelBody("kitchenSink.panelBody"))
      passes("detailsSummary", detailsSummary("kitchenSink.details"))
      passes("bullets", bullets("kitchenSink.b1", "kitchenSink.b2"))
      passes("numberedItems", numberedItems("kitchenSink.n1", "kitchenSink.n2"))
      passes("link", link("kitchenSink.link").to("/guidance"))
      passes("linkText", linkText("Read the guidance"))
      passes("linkWithId", linkWithId("guidance-link").to("/guidance"))
      passes("link saying", linkWithId("guidance-link").saying("kitchenSink.link"))
      passes("summaryList", summaryList("checkAnswers.name" -> "Ada Lovelace", "checkAnswers.dob" -> "27 March 1993"))
      passes(
        "summaryRow",
        summaryRow("checkAnswers.name")
          .withValue("Ada Lovelace")
          .withChangeLinkTo("/change/name")
          .withActions("checkAnswers.change")
      )
      passes("tableHeaders", tableHeaders("kitchenSink.col1", "kitchenSink.col2"))
      passes("tableRow", tableRow("Ada", "1815"))
      passes("element", element("submit"))
      passes("noElement", noElement("nope"))
      passes("elementWithText", elementWithText("submit", "site.continue"))
      passes("elementHasClass", elementHasClass("submit", "govuk-button"))
      passes("cssSelector", cssSelector(".govuk-summary-list"))
      passes("noCssSelector", noCssSelector(".govuk-error-summary"))
      passes("elementCount", elementCount(".govuk-summary-list__row", 2))
    }

    "fail on a page that does not" in {
      fails("missing content", content("kitchenSink.absent"))
      fails("noContent that is present", noContent("kitchenSink.p1"))
      fails("wrong paragraph", paragraph("kitchenSink.absent"))
      fails("wrong warning", warning("kitchenSink.p1"))
      fails("wrong inset", insetText("kitchenSink.p1"))
      fails("wrong banner", notificationBanner("kitchenSink.p1"))
      fails("wrong panel title", panelTitle("kitchenSink.p1"))
      fails("wrong details summary", detailsSummary("kitchenSink.p1"))
      fails("wrong bullets", bullets("kitchenSink.b1"))
      fails("bullets with undefined key", bullets("kitchenSink.notAKey"))
      fails("wrong numbered items", numberedItems("kitchenSink.b1", "kitchenSink.b2"))
      fails("missing link", link("kitchenSink.absent"))
      fails("wrong link href", link("kitchenSink.link").to("/elsewhere"))
      fails("wrong summary list", summaryList("checkAnswers.name" -> "Someone Else"))
      fails("missing summary row", summaryRow("kitchenSink.absent"))
      fails("wrong summary row value", summaryRow("checkAnswers.name").withValue("Someone Else"))
      fails("wrong change link", summaryRow("checkAnswers.name").withChangeLinkTo("/elsewhere"))
      fails("wrong row actions", summaryRow("checkAnswers.name").withActions("kitchenSink.absent"))
      fails("wrong table headers", tableHeaders("kitchenSink.col2", "kitchenSink.col1"))
      fails("missing table row", tableRow("Grace", "1906"))
      fails("missing element", element("nope"))
      fails("noElement that exists", noElement("submit"))
      fails("wrong element text", elementWithText("submit", "kitchenSink.save"))
      fails("wrong class", elementHasClass("submit", "govuk-warning-text"))
      fails("class on missing element", elementHasClass("nope", "govuk-button"))
      fails("missing selector", cssSelector(".govuk-error-summary"))
      fails("noCssSelector that exists", noCssSelector(".govuk-summary-list"))
      fails("wrong element count", elementCount(".govuk-summary-list__row", 9))
    }
  }

  "expectation combinators" should {

    "combine with and" in {
      passes("and", title("kitchenSink.title").and(heading("kitchenSink.heading")))
      fails("and with one failure", title("kitchenSink.title").and(heading("kitchenSink.absent")))
    }

    "downgrade to a warning" in {
      val violations = heading("kitchenSink.absent").asWarning.check(page)
      violations.map(_.severity) mustBe Seq(io.github.frikit.twirlspec.expect.Severity.Warning)
    }

    "apply conditionally" in {
      passes("when false", heading("kitchenSink.absent").when(_ => false))
      fails("when true", heading("kitchenSink.absent").when(_ => true))
    }

    "describe themselves" in {
      Expectation.satisfied.check(page)                                                  mustBe empty
      expectations(title("kitchenSink.title"), heading("kitchenSink.heading")).description must include("title")
    }
  }

  "the page model" should {

    "expose selections that describe themselves" in {
      page.css(".govuk-summary-list__row").toString                                must include("x2")
      page.css(".nothing-here").toString                                           must include("no match")
      page.byId("submit").text                                                   mustBe "Continue"
      page.input("email").attr("type")                                           mustBe Some("email")
      page.input("email").ids                                                    mustBe List("email")
      page.css("main").select("h1").text                                         mustBe "Everything at once"
      page.checkboxes("colours").texts                                             must have size 2
      page.links.containing("guidance").size                                     mustBe 1
      page.css(".govuk-summary-list__row").filter(_.text().contains("Ada")).size mustBe 1
      page.byId("submit").classes                                                  must contain("govuk-button")
      page.byId("submit").hasClass("govuk-button")                               mustBe true
      page.byId("guidance-link").html                                              must include("Read the guidance")
      page.forms.attrs("method")                                                 mustBe List("post")
      page.text                                                                    must include("Everything at once")
      page.contains("A paragraph of guidance.")                                  mustBe true
      page.summaryRows                                                             must have size 2
      page.tables.nonEmpty                                                       mustBe true
      page.bulletList.nonEmpty                                                   mustBe true
      page.numberedList.nonEmpty                                                 mustBe true
      page.tags.isEmpty                                                          mustBe true
      page.breadcrumbs.isEmpty                                                   mustBe true
      page.pagination.isEmpty                                                    mustBe true
      page.accordion.isEmpty                                                     mustBe true
      page.tabs.isEmpty                                                          mustBe true
      page.addToAList.isEmpty                                                    mustBe true
      page.images.isEmpty                                                        mustBe true
      page.legends.size                                                          mustBe 1
      page.fieldsets.size                                                        mustBe 1
      page.hints.size                                                            mustBe 2
      page.buttons.nonEmpty                                                      mustBe true
      page.formAction                                                            mustBe Some("/kitchen-sink")
      page.formMethod                                                            mustBe Some("POST")
      page.toString                                                                must include("lang=en")
    }

    "resolve messages and report undefined keys" in {
      page.message("kitchenSink.title")            mustBe Some("Everything at once")
      page.message("kitchenSink.notAKey")          mustBe None
      page.messageOrKey("kitchenSink.notAKey")     mustBe "kitchenSink.notAKey"
      page.withLang(welsh, messagesIn(welsh)).lang mustBe welsh
    }
  }

  private def messagesIn(lang: play.api.i18n.Lang) = messagesApi.preferred(Seq(lang))
}
