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

import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The halves of the GOV.UK rules' failure messages that the main specs do not
  * reach.
  */
class GovukEdgeCasesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def violations(body: String) =
    GovukStandards.all.flatMap(
      _.check(
        Page.fromString(
          s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
             |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
          english,
          messages
        )
      )
    )

  "error-aria-describedby" should {

    "name what the field's aria-describedby does point at, or say that it has none" in {
      val error =
        """<p id="e-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Boom</p>"""

      violations(
        s"""<label for="e">E</label>$error<input id="e" name="e" aria-describedby="e-hint">"""
      )
        .find(_.rule == "error-aria-describedby")
        .flatMap(_.actual) mustBe Some("e-hint")

      violations(s"""<label for="e">E</label>$error<input id="e" name="e">""")
        .find(_.rule == "error-aria-describedby")
        .flatMap(_.actual) mustBe Some("(no aria-describedby)")
    }
  }

}
