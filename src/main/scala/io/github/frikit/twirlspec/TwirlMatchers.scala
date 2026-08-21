/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.{MatchResult, Matcher}
import io.github.frikit.twirlspec.expect._
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{GovukStandards, Rule, TwirlStandards, WcagStandards}

import scala.util.control.NonFatal

/** ScalaTest matchers over a rendered page.
  *
  * Deliberately style-agnostic: these are plain `Matcher[Page]` values, so they
  * work with `must` or `should`, inside `AnyWordSpec`, `AnyFreeSpec`,
  * `PlaySpec` or anything else. Nothing here emits test cases, so no spec has
  * to change shape to adopt it.
  */
trait TwirlMatchers { self: TwirlSpecDsl =>

  /** Which rules run alongside every `display(...)`.
    *
    * The default is design-system agnostic: accessibility plus the two Twirl
    * rendering rules. A project on the GOV.UK Design System adds
    * `GovukStandards.all` here once, in its own base spec, rather than per
    * spec — and any project can drop a rule the same way.
    */
  def standardsRules: Seq[Rule] = WcagStandards.all ++ TwirlStandards.all

  /** Whether warnings fail the test. Off by default so adoption is never
    * blocked by advisory rules; turn it on once a service is clean.
    */
  def failOnWarnings: Boolean = false

  /** Whether passing tests still surface their warnings in the test output. */
  def reportWarnings: Boolean = true

  /** The page shows all of this, and holds to the GOV.UK standards.
    *
    * {{{
    * page must display(
    *   title("whatIsYourName.title"),
    *   heading("whatIsYourName.heading"),
    *   textInput("firstName").labelled("whatIsYourName.firstName"),
    *   submitButton()
    * )
    * }}}
    */
  def display(expectations: Expectation*): Matcher[Page] =
    matcherFor(expectations.toSeq :+ WcagStandards.expectation(standardsRules), "page")

  /** As `display`, but without the standards — for the rare page that has to
    * break a rule, or while a legacy view is being brought up to standard.
    */
  def displayOnly(expectations: Expectation*): Matcher[Page] =
    matcherFor(expectations.toSeq, "page")

  /** Accessibility and Twirl rendering rules only, for a spec that has its own
    * assertions already.
    */
  def meetWcagStandards: Matcher[Page] =
    matcherFor(Seq(WcagStandards.expectation(WcagStandards.all ++ TwirlStandards.all)), "page")

  /** Everything, including the GOV.UK Design System conventions. */
  def meetGovukStandards: Matcher[Page] =
    matcherFor(Seq(WcagStandards.expectation(allStandards)), "page")

  def meetStandardsExcept(ruleIds: String*): Matcher[Page] =
    matcherFor(Seq(WcagStandards.expectation(allStandards.filterNot(r => ruleIds.toSet.contains(r.id)))), "page")

  /** Accessibility, Twirl rendering and GOV.UK rules together. */
  def allStandards: Seq[Rule] = WcagStandards.all ++ TwirlStandards.all ++ GovukStandards.all

  /** Every rule set as one expectation, for asserting on the result directly. */
  def allStandardsExpectation: Expectation = WcagStandards.expectation(allStandards)

  /** Assert against a page directly, outside a matcher. Returns the report so a
    * caller can inspect the violations rather than fail.
    */
  def checkPage(page: Page, expectations: Seq[Expectation]): CheckReport =
    CheckReport(page, Expectation.all(expectations).check(page), "page")

  private def matcherFor(expectations: Seq[Expectation], subject: String): Matcher[Page] =
    new Matcher[Page] {
      def apply(page: Page): MatchResult = {
        val report = CheckReport(page, Expectation.all(expectations).check(page), subject)
        val ok     = report.passed && (!failOnWarnings || report.warnings.isEmpty)

        if (ok && reportWarnings && report.warnings.nonEmpty) surface(report)

        MatchResult(
          ok,
          report.message,
          s"$subject satisfied every check, but was expected not to"
        )
      }
    }

  /** Warnings are worth seeing on a green run too — that is how a service
    * finds out it is one fix away from being able to turn `failOnWarnings` on.
    */
  private def surface(report: CheckReport): Unit =
    try {
      val lines = report.warnings.map(w => s"  ${w.rule}: ${w.message}").mkString("\n")
      alertHook(s"twirl-spec: ${report.warnings.size} warning(s)\n$lines")
    } catch { case NonFatal(_) => () }

}
