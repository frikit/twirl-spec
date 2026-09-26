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

import io.github.frikit.twirlspec.quality.CoverageChecks
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.twirl.api.Html

/** A spec's assertions count for the page they were made on: every render of
  * it, but not a different view that happens to share its ids.
  */
class CoverageKeyingSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with CoverageChecks {

  private val first =
    """<main id="main-content"><h1>First</h1><a id="back" href="/back">Back</a><p id="amount">£10</p></main>"""
  private val second =
    """<main id="main-content"><h1>Second</h1><a id="back" href="/back">Back</a><p id="amount">£99</p></main>"""

  "coverage" should {

    "count every render of the same page together" in {
      val page = render(Html(first))
      page.css("#back")
      page.css("#amount")
      render(Html(first)) must assertEverything
    }

    "not let one view's assertions cover another view in the same spec" in {
      unassertedContent(render(Html(second))).map(_.name) must contain allOf (
        "#back",
        "#amount"
      )
    }

    "count the states a spec names as one group together" in {
      val withErrors = render(Html(first)).coveredAs("amount-page")
      withErrors.css("#back")
      withErrors.css("#amount")
      render(Html(second)).coveredAs("amount-page") must assertEverything
    }

    "refuse a group without a name" in {
      an[IllegalArgumentException] must be thrownBy
        render(Html(first)).coveredAs("")
    }
  }
}
