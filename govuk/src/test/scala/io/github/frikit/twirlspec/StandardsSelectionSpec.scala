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
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.standards.Rule
import io.github.frikit.twirlspec.wcag.{TwirlStandards, WcagStandards}

/** Selecting and switching off rules, across the rule modules. */
class StandardsSelectionSpec extends AnyWordSpec with Matchers with TwirlSpec {

  override def standardsRules: Seq[Rule] =
    WcagStandards.all ++ TwirlStandards.all ++ GovukStandards.all

  "the matcher variants" should {

    "check expectations without the standards" in {
      // A page that breaks a standard but satisfies the expectation: displayOnly
      // passes where display would not.
      val broken = io.github.frikit.twirlspec.page.Page
        .fromString(
          """<html lang="en"><head><title>t</title></head><body><main>
                      |<h1>One</h1><h1>Two</h1></main></body></html>""".stripMargin,
          english,
          messages
        )
      broken must displayOnly(headingText("One Two"))
      standardsExpectation.check(broken).map(_.rule) must contain("one-h1")
    }

    "run the standards with named rules switched off" in {
      val noLabel = io.github.frikit.twirlspec.page.Page
        .fromString(
          """<html lang="en"><head><title>t</title></head><body><main>
                      |<h1>A</h1><input id="x" name="x"></main></body></html>""".stripMargin,
          english,
          messages
        )
      noLabel must meetStandardsExcept("labelled-controls")
      standardsExpectation.check(noLabel).map(_.rule) must contain(
        "labelled-controls"
      )
    }

    "surface warnings on a page that otherwise passes" in {
      val vague = io.github.frikit.twirlspec.page.Page
        .fromString(
          """<html lang="en"><head><title>t</title></head><body><main>
                      |<h1>A</h1><a href="/x">click here</a></main></body></html>""".stripMargin,
          english,
          messages
        )
      val report = checkPage(vague, Seq(standardsExpectation))
      report.passed mustBe true
      report.warnings.map(_.rule) must contain("link-text-is-meaningful")
      vague must meetStandards // passes, and routes the warning to the reporter
    }
  }

  "the standards rule set" should {

    "select a named subset" in {
      WcagStandards.only("one-h1").map(_.id) mustBe Seq("one-h1")
      WcagStandards.allExcept("one-h1").map(_.id) must not contain "one-h1"
      WcagStandards.all.head.toString must include("—")
    }

    "skip page-level rules for a fragment" in {
      val fragment = io.github.frikit.twirlspec.page.Page
        .fromString("<p>just a component</p>", english, messages)
      Rule.isFullPage(fragment) mustBe false
      standardsExpectation.check(fragment) mustBe empty
    }
  }

}
