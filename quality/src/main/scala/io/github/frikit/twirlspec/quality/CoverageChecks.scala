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

package io.github.frikit.twirlspec.quality

import io.github.frikit.twirlspec.TwirlSpecDsl
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.{MatchResult, Matcher}
import play.twirl.api.Html

/** Two checks about the spec rather than the page.
  *
  * `assertEverything` fails when the page shows something no assertion ever
  * looked at. `entryPointsAgree` exercises the `render`, `f` and `ref` that
  * Twirl generates and nothing else calls.
  */
trait CoverageChecks extends TwirlSpecDsl {

  /** Attributes that mark an element as worth asserting, beyond ids and links.
    */
  def trackedAttributes: Set[String] = ContentCoverage.defaultTrackedAttributes

  /** Which part of the page a spec answers for. Defaults to the main content,
    * so the layout's header and footer are not the view spec's problem.
    */
  def coverageScope: String = ContentCoverage.defaultScope

  /** Ids, links or tracking values every page in this project inherits from its
    * layout.
    *
    * Declare them once on the project's spec base rather than in each view
    * spec.
    */
  def coverageIgnored: Set[String] = Set.empty

  /** Every id, link and tracked element on the page was asserted by some test
    * in this spec.
    *
    * Put it in the last test of the spec: it can only see the assertions that
    * have already run.
    */
  def assertEverything: Matcher[Page] = assertEverythingExcept()

  /** As `assertEverything`, but these ids, links or tracking values are
    * deliberately not asserted.
    */
  def assertEverythingExcept(ignored: String*): Matcher[Page] =
    new Matcher[Page] {
      def apply(page: Page): MatchResult = {
        val missing =
          ContentCoverage.unasserted(
            page,
            ignored.toSet ++ coverageIgnored,
            trackedAttributes,
            coverageScope
          )
        MatchResult(
          missing.isEmpty,
          if (missing.isEmpty) ""
          else ContentCoverage.report(missing) + "\n\n" + page.outline,
          "every part of the page was asserted, but was expected not to be"
        )
      }
    }

  /** What this spec has asserted so far, for a spec that wants to report rather
    * than fail.
    */
  def unassertedContent(page: Page): List[ContentCoverage.Anchor] =
    ContentCoverage.unasserted(
      page,
      coverageIgnored,
      trackedAttributes,
      coverageScope
    )

  /** Twirl's generated `render`, `f` and `ref` all agree with `apply`.
    *
    * {{{
    * memberNameView must entryPointsAgree(
    *   memberNameView(form, edit = false)(request, messages, appConfig),
    *   form, false, request, messages, appConfig
    * )
    * }}}
    *
    * The arguments are the ones `render` takes: every parameter including the
    * implicit ones, flattened, in order.
    */
  def entryPointsAgree(expected: Html, args: Any*): Matcher[AnyRef] =
    new Matcher[AnyRef] {
      def apply(view: AnyRef): MatchResult = {
        val problems = EntryPoints.problems(view, expected, args.toSeq)
        MatchResult(
          problems.isEmpty,
          s"${view.getClass.getSimpleName.stripSuffix("$")}: ${problems.mkString("; ")}",
          "every entry point agreed with apply, but was expected not to"
        )
      }
    }

}
