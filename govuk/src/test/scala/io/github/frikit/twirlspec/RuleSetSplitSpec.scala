package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{GovukStandards, Rule, TwirlStandards, WcagStandards}

/** The rule sets have to be genuinely separable, not separate in name only.
  *
  * A Twirl page built on Bootstrap, Tailwind or nothing at all should pass the
  * accessibility rules without being told it is broken for not using the GOV.UK
  * Design System.
  */
class RuleSetSplitSpec extends AnyWordSpec with Matchers with TwirlSpec {

  /** The default a project gets from `with WcagChecks`. */
  override def standardsRules: Seq[Rule] = WcagStandards.all ++ TwirlStandards.all

  private def allStandards: Seq[Rule] = standardsRules ++ GovukStandards.all

  /** An accessible page with an error state, using no design system at all. */
  private val plainHtml =
    """<!DOCTYPE html>
      |<html lang="en">
      |  <head><title>Sign up</title></head>
      |  <body>
      |    <main>
      |      <h1>Sign up</h1>
      |      <div class="alert" role="alert">
      |        <h2>There is a problem</h2>
      |        <ul><li><a href="#email">Enter an email address</a></li></ul>
      |      </div>
      |      <form method="post" action="/sign-up">
      |        <label for="email">Email address</label>
      |        <p id="email-error" class="error">Enter an email address</p>
      |        <input id="email" name="email" type="email" aria-describedby="email-error">
      |        <fieldset>
      |          <legend>Contact preference</legend>
      |          <input type="radio" id="c-email" name="contact" value="email">
      |          <label for="c-email">Email</label>
      |          <input type="radio" id="c-post" name="contact" value="post">
      |          <label for="c-post">Post</label>
      |        </fieldset>
      |        <button type="submit">Continue</button>
      |      </form>
      |    </main>
      |  </body>
      |</html>""".stripMargin

  private lazy val plain: Page = Page.fromString(plainHtml, english, messages)

  "a page using no design system" should {

    "satisfy the accessibility and twirl rules" in {
      plain must meetStandards
    }

    "not be judged against GOV.UK Design System conventions" in {
      // The GOV.UK rules key off Design System markup — govuk-error-summary,
      // govuk-error-message — so on a page that uses none of it they are silent
      // rather than wrong. That makes them safe to add, and it is why the split
      // is about naming and intent as much as behaviour.
      GovukStandards.expectation().check(plain) mustBe empty

      // They are still excluded from the default set, so nothing reports itself
      // as a GOV.UK standard to a project that is not using the Design System.
      standardsRules.map(_.id) must contain noElementsOf GovukStandards.all.map(_.id)
    }

    "still have GOV.UK rules that bite on Design System markup" in {
      // The other half: on a page that does use the Design System, an error
      // state with an unprefixed title is caught.
      val govukPage = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>Sign up</title></head>
          |<body><main><h1>Sign up</h1>
          |  <div class="govuk-error-summary" data-module="govuk-error-summary">
          |    <h2 class="govuk-error-summary__title">There is a problem</h2>
          |    <ul><li><a href="#email">Enter an email address</a></li></ul>
          |  </div>
          |  <label for="email">Email address</label>
          |  <p id="email-error" class="govuk-error-message">
          |    <span class="govuk-visually-hidden">Error:</span> Enter an email address</p>
          |  <input id="email" name="email" aria-describedby="email-error">
          |</main></body></html>""".stripMargin,
        english,
        messages
      )
      GovukStandards.expectation().check(govukPage).map(_.rule)                   must contain("error-title-prefix")
      // Warnings are advisory; what matters is that nothing blocking fires.
      WcagStandards
        .expectation(standardsRules)
        .check(govukPage)
        .filter(_.severity == io.github.frikit.twirlspec.expect.Severity.Error) mustBe empty
    }
  }

  "the rule sets" should {

    "be disjoint" in {
      val wcag  = WcagStandards.all.map(_.id).toSet
      val twirl = TwirlStandards.all.map(_.id).toSet
      val govuk = GovukStandards.all.map(_.id).toSet
      wcag intersect twirl  mustBe empty
      wcag intersect govuk  mustBe empty
      twirl intersect govuk mustBe empty
    }

    "together account for every rule, each with a unique id" in {
      val ids = allStandards.map(_.id)
      ids.distinct.size mustBe ids.size
      ids.size          mustBe (WcagStandards.all.size + TwirlStandards.all.size + GovukStandards.all.size)
    }

    "default to the design-system agnostic set" in {
      standardsRules.map(_.id) must contain allOf ("one-h1", "labelled-controls", "no-raw-message-keys")
      standardsRules.size    mustBe (WcagStandards.all.size + TwirlStandards.all.size)
    }

    "add the GOV.UK rules only when asked" in {
      allStandards.map(_.id) must contain("error-summary-targets")
      allStandards.size    mustBe (standardsRules.size + GovukStandards.all.size)
    }
  }

}
