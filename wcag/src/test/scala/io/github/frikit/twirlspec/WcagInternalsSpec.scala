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
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.wcag.{TwirlStandards, WcagStandards}

/** Edge cases of the accessibility and rendering rules. */
class WcagInternalsSpec extends AnyWordSpec with Matchers with TwirlSpec {

  override def standardsRules = WcagStandards.all ++ TwirlStandards.all

  private def pageOf(body: String, lang: play.api.i18n.Lang = english): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="${lang.code}"><head><title>t</title></head>
         |<body><main id="main-content">$body</main></body></html>""".stripMargin,
      lang,
      messagesApi.preferred(Seq(lang))
    )

  "the standards" should {

    "flag an empty title, a missing main landmark and a nameless submit control" in {
      val noTitle = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title></title></head>
          |<body><h1>A</h1><button type="submit"></button></body></html>""".stripMargin,
        english,
        messages
      )
      val fired = standardsExpectation.check(noTitle).map(_.rule)
      fired must contain("title-present")
      fired must contain("main-landmark")
      fired must contain("submit-has-name")
    }

    // `main-landmark` and `single-main` have to count the same thing, or a
    // page can be told at once that it has no main landmark and too many.
    "accept a main landmark declared by role, as single-main does" in {
      val byRole = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><div role="main"><h1>A</h1></div></body></html>""".stripMargin,
        english,
        messages
      )
      val firedByRole = standardsExpectation.check(byRole).map(_.rule)
      firedByRole must not contain "main-landmark"
      firedByRole must not contain "single-main"
    }

    "quote the surrounding text when a Scala value leaks in" in {
      val leak = pageOf(
        "<h1>A</h1><p>Your name is Some(Ada) according to our records</p>"
      )
      val v =
        standardsExpectation.check(leak).find(_.rule == "no-scala-leakage")
      v.flatMap(_.actual).getOrElse("") must include("Some(Ada)")
    }
  }

}
