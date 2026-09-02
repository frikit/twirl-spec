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
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import testviews.html.{checkAnswersView, dateView, nameView, radioView}

/** The library used exactly as a service would use it. */
class TwirlSpecEndToEndSpec extends AnyWordSpec with Matchers with Bilingual {

  private val nameForm: Form[(String, String)] =
    Form(mapping("firstName" -> text, "lastName" -> text)(Tuple2.apply)(t => Some((t._1, t._2))))

  private val name         = inject[nameView]
  private val radio        = inject[radioView]
  private val date         = inject[dateView]
  private val checkAnswers = inject[checkAnswersView]

  "a question page" should {

    "satisfy every expectation and the GOV.UK standards" in {
      render(name(nameForm)) must display(
        title("whatIsYourName.title"),
        heading("whatIsYourName.heading"),
        caption("whatIsYourName.caption"),
        serviceName(),
        backLink.to("/back"),
        languageToggle,
        paragraph("whatIsYourName.p1"),
        formPostsTo("/register/name"),
        textInput("firstName").labelled("whatIsYourName.firstName").withAutocomplete("given-name"),
        textInput("lastName")
          .labelled("whatIsYourName.lastName")
          .hinted("whatIsYourName.lastName.hint")
          .withAutocomplete("family-name"),
        submitButton("site.continue"),
        link("guidance.link").to("/guidance"),
        noErrors
      )
    }

    "report the value a form was filled with" in {
      val filled = nameForm.fill(("Ada", "Lovelace"))
      render(name(filled)) must display(
        textInput("firstName").withValue("Ada"),
        textInput("lastName").withValue("Lovelace")
      )
    }

    "check the error state end to end" in {
      render(name(nameForm, showErrors = true)) must display(
        errorTitlePrefix,
        errorSummaryTitle(),
        errorSummary(
          "firstName" -> "whatIsYourName.error.firstName.required",
          "lastName"  -> "whatIsYourName.error.lastName.required"
        ),
        fieldError("firstName", "whatIsYourName.error.firstName.required"),
        fieldError("lastName", "whatIsYourName.error.lastName.required")
      )
    }
  }

  "a radio page" should {

    "check its legend, options and hint" in {
      render(radio("Ada")) must display(
        title("livesInUk.title", "Ada"),
        radioGroup("value")
          .legendIs("livesInUk.heading", "Ada")
          .hinted("livesInUk.hint")
          .withOptions("true" -> "site.yes", "false" -> "site.no")
          .nothingSelected,
        submitButton()
      )
    }

    "notice which option is selected" in {
      render(radio("Ada", selected = Some("false"))) must display(
        radioGroup("value").selectedIs("false")
      )
    }
  }

  "a date page" should {
    "check all three fields, the legend and the hint at once" in {
      render(date()) must display(
        dateInput("value").legendIs("dateOfBirth.legend").hinted("dateOfBirth.hint"),
        submitButton()
      )
    }
  }

  "a check your answers page" should {
    "check the rows and their change links" in {
      render(checkAnswers("Ada Lovelace", "27 March 1993")) must display(
        summaryList(
          "checkAnswers.name" -> "Ada Lovelace",
          "checkAnswers.dob"  -> "27 March 1993"
        ),
        summaryRow("checkAnswers.name").withValue("Ada Lovelace").withChangeLinkTo("/change/name"),
        summaryRow("checkAnswers.dob").withValue("27 March 1993").withChangeLinkTo("/change/dob")
      )
    }
  }

  "welsh rendering" should {

    "check the same page in both languages with one block" in
      inEachLanguage { _ =>
        render(name(nameForm)) must display(
          title("whatIsYourName.title"),
          heading("whatIsYourName.heading"),
          textInput("firstName").labelled("whatIsYourName.firstName"),
          submitButton("site.continue")
        )
      }

    "resolve welsh content, not english" in
      inLanguage(welsh) {
        val page = render(name(nameForm))
        page.title               must startWith("Beth yw eu henw?")
        page.h1.text           mustBe "Beth yw eu henw?"
        page.htmlLang          mustBe Some("cy")
        page.submitButton.text mustBe "Yn eich blaen"
      }
  }

  "the shared application" should {
    "be built once for a whole run, not once per view" in {
      SharedApplicationCount.value must be <= 2
    }
  }

}

object SharedApplicationCount {
  def value: Int = io.github.frikit.twirlspec.render.SharedApplication.instanceCount
}
