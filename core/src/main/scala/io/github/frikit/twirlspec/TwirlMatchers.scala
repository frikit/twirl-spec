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

import org.scalatest.matchers.{MatchResult, Matcher}
import io.github.frikit.twirlspec.expect._
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{ExclusionRegistry, Rule}

import scala.util.control.NonFatal

/** ScalaTest matchers over a rendered page. */
trait TwirlMatchers { self: TwirlSpecDsl =>

  /** Which rules run alongside every `display(...)`. Empty here; rule modules
    * add theirs via `super.standardsRules`.
    */
  def standardsRules: Seq[Rule] = Nil

  /** Whether warnings fail the test. */
  def failOnWarnings: Boolean = false

  /** Whether passing tests still surface their warnings in the test output. */
  def reportWarnings: Boolean = true

  /** The page shows all of this, and holds to every rule `standardsRules`
    * resolves to: none in the core, and each check trait's own once mixed in.
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
  def meetStandards: Matcher[Page] =
    matcherFor(Seq(Rule.expectation(standardsRules)), "page")

  /** The standards, minus the named rules. Prefer fixing the page: an exclusion
    * here hides the rule on every page it is applied to.
    *
    * Every id must name an active rule. A misspelt id, or one for a rule this
    * spec does not run, would exclude nothing, so it fails the match instead.
    * The excluded rules still run, silently, so [[unusedExclusions]] can tell
    * an exclusion that is still needed from one that has outlived its reason.
    */
  def meetStandardsExcept(ruleIds: String*): Matcher[Page] =
    exclusionMatcher(ruleIds, None)

  /** As `meetStandardsExcept`, with the reason for the exclusion, which a
    * failure then reports alongside the ids:
    *
    * {{{
    * page must meetStandardsExcept(Seq("one-h1"), because = "legacy page, see JIRA-123")
    * }}}
    */
  def meetStandardsExcept(
      ruleIds: Seq[String],
      because: String
  ): Matcher[Page] =
    exclusionMatcher(ruleIds, Some(because))

  /** The rules this spec has excluded with `meetStandardsExcept` that found
    * nothing on any page they were excluded from, and so exclude nothing.
    *
    * Ask it in the spec's last test, once every exclusion has run:
    *
    * {{{
    * "need every exclusion it makes" in { unusedExclusions mustBe empty }
    * }}}
    *
    * It knows only the tests that have run, so a run of one test can report an
    * exclusion that another test needs.
    */
  def unusedExclusions: Seq[String] = ExclusionRegistry.unused(getClass.getName)

  private def exclusionMatcher(
      ruleIds: Seq[String],
      because: Option[String]
  ): Matcher[Page] = {
    // The spec's name, taken here: inside the matcher, getClass is the matcher.
    val spec = getClass.getName
    new Matcher[Page] {
      def apply(page: Page): MatchResult = {
        val active = standardsRules
        val unknown =
          ruleIds.filterNot(id => active.exists(_.id == id)).distinct
        if (unknown.nonEmpty)
          MatchResult(
            false,
            s"meetStandardsExcept names ${unknown.mkString(", ")}, which is not " +
              "among the rules this spec runs, so excluding it would do nothing. " +
              s"The rules it runs: ${active.map(_.id).distinct.sorted.mkString(", ")}",
            "every excluded rule was an active one"
          )
        else {
          val (excluded, kept) = active.partition(r => ruleIds.contains(r.id))
          excluded.foreach(r =>
            ExclusionRegistry.record(
              spec,
              r.id,
              page.withRecordingPaused(r.check(page)).nonEmpty
            )
          )
          val result =
            matcherFor(Seq(Rule.expectation(kept)), "page").apply(page)
          because.fold(result)(reason =>
            MatchResult(
              result.matches,
              result.failureMessage +
                s"\n\n  excluded: ${ruleIds.mkString(", ")} — $reason",
              result.negatedFailureMessage
            )
          )
        }
      }
    }
  }

  /** The active rule set as one expectation, for asserting on the result. */
  def standardsExpectation: Expectation = Rule.expectation(standardsRules)

  /** Assert against a page directly, outside a matcher. */
  def checkPage(page: Page, expectations: Seq[Expectation]): CheckReport =
    CheckReport(page, Expectation.all(expectations).check(page), "page")

  private def matcherFor(
      expectations: Seq[Expectation],
      subject: String
  ): Matcher[Page] =
    new Matcher[Page] {
      def apply(page: Page): MatchResult = {
        val report =
          CheckReport(page, Expectation.all(expectations).check(page), subject)
        val ok = report.passed && (!failOnWarnings || report.warnings.isEmpty)

        if (ok && reportWarnings && report.warnings.nonEmpty) surface(report)

        // The message walks the whole page to build the outline, so it is only built for a failure.
        MatchResult(
          ok,
          if (ok) "" else report.message,
          s"$subject satisfied every check, but was expected not to"
        )
      }
    }

  /** Warnings are worth seeing on a green run too — that is how a service finds
    * out it is one fix away from being able to turn `failOnWarnings` on.
    */
  private def surface(report: CheckReport): Unit =
    try {
      val lines =
        report.warnings.map(w => s"  ${w.rule}: ${w.message}").mkString("\n")
      alertHook(s"twirl-spec: ${report.warnings.size} warning(s)\n$lines")
    } catch { case NonFatal(_) => () }

}
