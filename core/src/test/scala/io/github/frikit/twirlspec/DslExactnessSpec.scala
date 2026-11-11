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

/** What an expectation finds and what it asks of it, read as a browser reads
  * the page.
  */
class DslExactnessSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def page(body: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  "An expectation" should {

    "read a submit input's words from its value" in {
      submitButtonText("Continue").check(
        page("""<form><input type="submit" value="Continue"></form>""")
      ) mustBe empty
    }

    "take only a link for a link" in {
      linkWithId("help").check(page("""<div id="help">Help</div>""")) must
        not be empty
      linkWithId("help").check(page("""<a id="help">Help</a>""")) must
        not be empty
      linkWithId("help").check(
        page("""<a id="help" href="/help">Help</a>""")
      ) mustBe empty
    }

    "find a label by the field's id, never by its name" in {
      val expectation = textInput("x").labelledText("Name")
      expectation.check(
        page("""<label for="x">Name</label><input name="x">""")
      ) must not be empty
      expectation.check(
        page("""<label for="x">Name</label><input id="x" name="x">""")
      ) mustBe empty
    }

    "count each option, and read the label of every one" in {
      val twice =
        """<input type="radio" id="a" name="x" value="yes"><label for="a">Yes</label>
          |<input type="radio" id="b" name="x" value="yes"><label for="b">No</label>""".stripMargin
      radioGroup("x").withOptionValues("yes").check(page(twice)) must
        not be empty
      radioGroup("x")
        .containingOption("yes", "site.yes")
        .check(
          page(twice)
        ) must not be empty
    }
  }

}
