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

import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What an HMRC service would otherwise write on its spec base. */
class HmrcServiceChecksSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with HmrcServiceChecks {

  private def pageOf(main: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head><body><main>$main</main></body></html>""",
      english,
      messages
    )

  "HmrcServiceChecks" should {

    "leave the report-a-problem link out of coverage, whatever it says" in {
      val link =
        """<a class="govuk-link hmrc-report-technical-issue" href="/report">Is this page not working properly?</a>"""
      pageOf(link) must assertEverything
      unassertedContent(pageOf(link)) mustBe empty
    }

    "still hold a spec to everything else on the page" in {
      val page = pageOf(
        """<a class="hmrc-report-technical-issue" href="/report">Report</a><a href="/mine">Mine</a>"""
      )
      unassertedContent(page).map(_.name) mustBe List("""link "Mine"""")
    }

    "let HMRC read the same in every language, and what the default lets too" in {
      translationConfig.sameTextIsFine must contain allOf ("HMRC", "GOV.UK")
    }

    "keep the rules on the link all the same" in {
      standardsRules.map(_.id) must contain("new-tab-is-announced")
    }
  }

}
