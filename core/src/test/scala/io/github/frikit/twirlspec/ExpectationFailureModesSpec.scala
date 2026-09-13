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

/** The failure branch of every expectation. */
class ExpectationFailureModesSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec {

  /** Nothing on it. */
  private lazy val bare: Page =
    Page.fromString(
      """<!DOCTYPE html><html><head><title></title></head><body></body></html>""",
      english,
      messages
    )

  /** Present but wrong, so the mismatch branches fire rather than the missing
    * ones.
    */
  private lazy val wrong: Page = Page.fromString(
    """<!DOCTYPE html>
      |<html lang="cy">
      |  <head><title>Something else - Example Service - GOV.UK</title></head>
      |  <body>
      |    <span class="govuk-service-navigation__service-name">A different service</span>
      |    <a href="/elsewhere" class="govuk-back-link">Back</a>
      |    <main id="main-content">
      |      <span class="govuk-caption-l">Wrong caption</span>
      |      <h1>Wrong heading</h1>
      |      <form method="get" action="/elsewhere">
      |        <input id="email" name="email" type="text">
      |        <input type="radio" id="colours" name="colours" value="red">
      |        <input type="radio" id="colours-2" name="colours" value="blue">
      |        <fieldset><input id="sizes" name="sizes" type="radio" value="s">
      |          <input id="sizes-2" name="sizes" type="radio" value="m"></fieldset>
      |        <div class="govuk-date-input" id="dob">
      |          <input id="dob.day" name="dob.day">
      |        </div>
      |      </form>
      |    </main>
      |  </body>
      |</html>""".stripMargin,
    english,
    messages
  )

  /** An error state whose summary points at nothing that exists. */
  private lazy val errored: Page = Page.fromString(
    """<!DOCTYPE html>
      |<html lang="en"><head><title>Error: Everything at once - Example Service - GOV.UK</title></head>
      |<body><main id="main-content"><h1>Everything at once</h1>
      |  <div class="govuk-error-summary" data-module="govuk-error-summary">
      |    <h2 class="govuk-error-summary__title">Something went wrong</h2>
      |    <ul><li><a href="#ghost">Enter an email address</a></li>
      |        <li><a href="#other">Another problem</a></li></ul>
      |  </div>
      |  <label for="email">Email address</label>
      |  <p id="email-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> A different message</p>
      |  <input id="email" name="email" aria-describedby="email-error">
      |</main></body></html>""".stripMargin,
    english,
    messages
  )

  private def failsOn(page: Page, name: String, e: Expectation): Unit =
    withClue(s"$name should have failed — ")(e.check(page) must not be empty)

  "expectations looking for something that is not there" should {
    "each report a missing element" in {
      failsOn(bare, "heading", heading("kitchenSink.heading"))
      failsOn(bare, "headingAtLevel", subheading("kitchenSink.section"))
      failsOn(bare, "caption", caption("kitchenSink.caption"))
      failsOn(bare, "serviceName", serviceName())
      failsOn(bare, "backLink", backLink)
      failsOn(bare, "backLink.to", backLink.to("/back"))
      failsOn(bare, "languageToggle", languageToggle)
      failsOn(bare, "timeoutDialog", timeoutDialog)
      failsOn(bare, "signOutLink", signOutLink)
      failsOn(bare, "phaseBanner", phaseBanner)
      failsOn(bare, "formPostsTo", formPostsTo("/x"))
      failsOn(
        bare,
        "textInput",
        textInput("email").labelled("kitchenSink.email")
      )
      failsOn(bare, "textArea", textArea("notes"))
      failsOn(
        bare,
        "dropdown",
        dropdown("country").labelled("kitchenSink.country")
      )
      failsOn(bare, "radioGroup", radioGroup("colours"))
      failsOn(bare, "checkboxGroup", checkboxGroup("colours"))
      failsOn(bare, "dateInput", dateInput("dob"))
      failsOn(bare, "fileUpload", fileUpload("evidence"))
      failsOn(bare, "submitButton", submitButton())
      failsOn(bare, "hasSubmitButton", hasSubmitButton)
      failsOn(bare, "button", button("save", "kitchenSink.save"))
      failsOn(bare, "warning", warning("kitchenSink.warning"))
      failsOn(bare, "insetText", insetText("kitchenSink.inset"))
      failsOn(
        bare,
        "notificationBanner",
        notificationBanner("kitchenSink.banner")
      )
      failsOn(bare, "panelTitle", panelTitle("kitchenSink.panelTitle"))
      failsOn(bare, "panelBody", panelBody("kitchenSink.panelBody"))
      failsOn(bare, "detailsSummary", detailsSummary("kitchenSink.details"))
      failsOn(bare, "bullets", bullets("kitchenSink.b1"))
      failsOn(bare, "numberedItems", numberedItems("kitchenSink.n1"))
      failsOn(bare, "link", link("kitchenSink.link"))
      failsOn(bare, "linkWithId", linkWithId("nope"))
      failsOn(bare, "summaryList", summaryList("checkAnswers.name" -> "Ada"))
      failsOn(bare, "summaryRow", summaryRow("checkAnswers.name"))
      failsOn(bare, "tableHeaders", tableHeaders("kitchenSink.col1"))
      failsOn(bare, "tableRow", tableRow("Ada"))
      failsOn(bare, "paragraph", paragraph("kitchenSink.p1"))
      failsOn(bare, "content", content("kitchenSink.p1"))
      failsOn(bare, "errorSummaryTitle", errorSummaryTitle())
      failsOn(
        bare,
        "errorSummaryContaining",
        errorSummaryContaining("email", "kitchenSink.error.email.required")
      )
      failsOn(
        bare,
        "fieldError",
        fieldError("email", "kitchenSink.error.email.required")
      )
    }
  }

  "expectations finding the wrong thing" should {
    "each report a mismatch" in {
      failsOn(wrong, "title", title("kitchenSink.title"))
      failsOn(wrong, "heading", heading("kitchenSink.heading"))
      failsOn(wrong, "caption", caption("kitchenSink.caption"))
      failsOn(wrong, "serviceName", serviceName())
      failsOn(wrong, "backLink.to", backLink.to("/back"))
      failsOn(wrong, "formPostsTo", formPostsTo("/kitchen-sink"))
      failsOn(
        wrong,
        "unlabelled input",
        textInput("email").labelled("kitchenSink.email")
      )
      failsOn(
        wrong,
        "missing hint",
        textInput("email").hinted("kitchenSink.email.hint")
      )
      failsOn(
        wrong,
        "radios with no fieldset",
        radioGroup("colours").legendIs("kitchenSink.colours")
      )
      failsOn(
        wrong,
        "fieldset with no legend",
        radioGroup("sizes").legendIs("kitchenSink.colours")
      )
      failsOn(wrong, "date input missing parts", dateInput("dob"))
      failsOn(
        wrong,
        "date input hint",
        dateInput("dob").withParts("day").hinted("dateOfBirth.hint")
      )
    }
  }

  "expectations about an error state" should {
    "report a wrong summary title, wrong entries and dangling links" in {
      failsOn(errored, "errorSummaryTitle", errorSummaryTitle())
      failsOn(
        errored,
        "wrong summary entry",
        errorSummary("email" -> "kitchenSink.error.email.required")
      )
      failsOn(
        errored,
        "unexpected summary entries",
        errorSummaryContaining("ghost", "kitchenSink.error.email.required")
      )
      failsOn(
        errored,
        "wrong field error",
        fieldError("email", "kitchenSink.error.email.required")
      )
      failsOn(errored, "noErrors", noErrors)
      failsOn(
        errored,
        "undefined key in fieldError",
        fieldError("email", "kitchenSink.notAKey")
      )
      failsOn(
        errored,
        "undefined key in errorSummary",
        errorSummary("ghost" -> "kitchenSink.notAKey")
      )
      failsOn(
        errored,
        "undefined key in summaryList",
        summaryList("kitchenSink.notAKey" -> "x")
      )
      failsOn(
        errored,
        "undefined key in tableHeaders",
        tableHeaders("kitchenSink.notAKey")
      )
      failsOn(
        errored,
        "undefined key in noContent",
        noContent("kitchenSink.notAKey")
      )
      failsOn(errored, "undefined key in link", link("kitchenSink.notAKey"))
      failsOn(
        errored,
        "undefined key in summaryRow",
        summaryRow("kitchenSink.notAKey")
      )
      failsOn(
        errored,
        "undefined key in subheading",
        subheading("kitchenSink.notAKey")
      )
      failsOn(
        errored,
        "undefined key in row actions",
        summaryRow("checkAnswers.name").withActions("kitchenSink.notAKey")
      )
    }

    "still see the error summary links that do exist" in {
      errored.errorSummaryLinks.map(_._1) mustBe List("ghost", "other")
      errored.fieldErrors("email") mustBe "A different message"
    }
  }

  "a heading whose caption is inside the h1" should {
    "compare against the heading alone" in {
      val page = Page.fromString(
        """<html lang="en"><head><title>t</title></head><body><main>
          |<h1><span class="govuk-caption-l">A caption</span> Everything at once</h1>
          |</main></body></html>""".stripMargin,
        english,
        messages
      )
      heading("kitchenSink.heading").check(page) mustBe empty
      caption("kitchenSink.caption").check(page) mustBe empty
    }
  }

  "every expectation" should {
    "describe itself" in {
      textInput("email").description mustBe "input(email)"
      textArea("notes").description mustBe "textarea(notes)"
      radioGroup("colours").description mustBe "radioGroup(colours)"
      checkboxGroup("colours").description mustBe "checkboxGroup(colours)"
      dropdown("country").description mustBe "dropdown(country)"
      dateInput("dob").description mustBe "dateInput(dob)"
      backLink.description mustBe "backLink"
      backLink.to("/back").description mustBe "backLink -> /back"
      link("kitchenSink.link").description must include(
        "messages(kitchenSink.link)"
      )
      linkWithId("guidance-link")
        .withId("other")
        .description mustBe "link(#other)"
      summaryRow("checkAnswers.name").description must include("summaryRow")
      errorSummary(
        "email" -> "kitchenSink.error.email.required"
      ).description must include("errorSummary")
      literal("x").describe mustBe "\"x\""
      anyText.describe must include("anything")
    }
  }

}
