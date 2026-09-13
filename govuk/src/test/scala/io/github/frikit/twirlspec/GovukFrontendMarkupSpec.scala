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
import io.github.frikit.twirlspec.govuk.GovukChecks
import io.github.frikit.twirlspec.wcag.WcagChecks

import scala.io.Source
import scala.util.Using

/** twirl-spec's selectors and rules, checked against genuine govuk-frontend markup. */
class GovukFrontendMarkupSpec extends AnyWordSpec with Matchers with Bilingual with WcagChecks with GovukChecks {

  private def captured(name: String): String =
    Using.resource(Source.fromResource(s"captured/$name.html"))(_.mkString)

  private def pageOf(name: String, lang: play.api.i18n.Lang = english): Page =
    Page.fromString(captured(name), lang, messagesApi.preferred(Seq(lang)))

  "real govuk-frontend markup, with no errors" should {

    "raise nothing under the GOV.UK standards" in {
      pageOf("govuk-components-clean") must meetStandards
    }

    "be understood by the expectation DSL" in {
      pageOf("govuk-components-clean") must display(
        title("whatIsYourName.title"),
        heading("whatIsYourName.heading"),
        serviceName(),
        backLink.to("/back"),
        textInput("firstName").labelled("whatIsYourName.firstName").withAutocomplete("given-name"),
        textInput("lastName").labelled("whatIsYourName.lastName").hinted("whatIsYourName.lastName.hint"),
        radioGroup("livesInUk")
          .legendIs("livesInUk.legend", "Ada")
          .hinted("livesInUk.hint")
          .withOptions("true" -> "site.yes", "false" -> "site.no")
          .nothingSelected,
        dateInput("dateOfBirth").legendIs("dateOfBirth.legend").hinted("dateOfBirth.hint"),
        summaryRow("checkAnswers.name").withValue("Ada Lovelace").withChangeLinkTo("/change/name"),
        submitButton("site.continue"),
        noErrors
      )
    }
  }

  "real govuk-frontend markup, in an error state" should {

    "be understood by the expectation DSL" in {
      pageOf("govuk-components-errors") must display(
        errorTitlePrefix,
        errorSummaryTitle(),
        errorSummaryContaining("firstName", "whatIsYourName.error.firstName.required"),
        fieldError("firstName", "whatIsYourName.error.firstName.required")
      )
    }

    "wire the error to its field the way govuk-frontend does" in {
      val page = pageOf("govuk-components-errors")
      page.fieldErrors.keys                                          must contain("firstName")
      page.input("firstName").attr("aria-describedby").getOrElse("") must include("firstName-error")
      page.errorSummaryLinks.map(_._1)                               must contain("firstName")
    }

    "hold in Welsh too" in {
      pageOf("govuk-components-errors-cy", welsh) must display(
        errorTitlePrefix,
        heading("whatIsYourName.heading"),
        errorSummaryContaining("firstName", "whatIsYourName.error.firstName.required")
      )
    }
  }

}
