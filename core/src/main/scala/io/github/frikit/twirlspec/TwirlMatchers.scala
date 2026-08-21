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

import org.scalatest.matchers.{MatchResult, Matcher}
import io.github.frikit.twirlspec.expect._
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Rule

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
    * Empty in the core module, which carries no rules of its own. Mixing in a
    * rule module's trait adds its set, and the traits compose, so
    * `with WcagChecks with GovukChecks` runs both:
    *
    * {{{
    * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with WcagChecks
    * }}}
    *
    * Override it directly to pin an exact set, or to drop a rule by id.
    */
  def standardsRules: Seq[Rule] = Nil

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
    matcherFor(expectations.toSeq :+ Rule.expectation(standardsRules), "page")

  /** As `display`, but without the standards — for the rare page that has to
    * break a rule, or while a legacy view is being brought up to standard.
    */
  def displayOnly(expectations: Expectation*): Matcher[Page] =
    matcherFor(expectations.toSeq, "page")

  /** Whatever `standardsRules` resolves to, for a spec that has its own
    * assertions already.
    */
  def meetStandards: Matcher[Page] = matcherFor(Seq(Rule.expectation(standardsRules)), "page")

  def meetStandardsExcept(ruleIds: String*): Matcher[Page] =
    matcherFor(Seq(Rule.expectation(standardsRules.filterNot(r => ruleIds.toSet.contains(r.id)))), "page")

  /** The active rule set as one expectation, for asserting on the result. */
  def standardsExpectation: Expectation = Rule.expectation(standardsRules)

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
