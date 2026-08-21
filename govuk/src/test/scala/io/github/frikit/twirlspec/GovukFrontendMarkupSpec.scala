package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{GovukChecks, WcagChecks}

import scala.io.Source
import scala.util.Using

/** twirl-spec's selectors and rules, checked against genuine govuk-frontend
  * markup.
  *
  * The fixtures in `test/resources/captured` are the real output of a GOV.UK
  * Design System implementation — govukInput, govukRadios, govukDateInput,
  * govukSummaryList, govukErrorSummary and govukButton — captured verbatim.
  * They are checked in rather than generated at test time, so this library
  * depends on no component package in order to verify itself against one.
  *
  * Recapture with a newer play-frontend-hmrc when the Design System moves; a
  * diff of these files is then exactly the markup change to react to.
  */
class GovukFrontendMarkupSpec extends AnyWordSpec with Matchers with TwirlSpec with WcagChecks with GovukChecks {

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
