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

import io.github.frikit.twirlspec.i18n.I18nChecks
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.Lang

/** The language comparison follows the spec's policy on warnings, as `display`
  * does.
  */
class TranslationWarningsSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with I18nChecks {

  private var surfaced = List.empty[String]
  private var strict = false
  private var quiet = false

  override def failOnWarnings: Boolean = strict
  override def reportWarnings: Boolean = !quiet
  override protected def alertHook(message: String): Unit =
    surfaced = message :: surfaced

  private val fr = Lang("fr")

  private def pageIn(lang: Lang, heading: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="${lang.code}"><head><title>t</title></head>
         |<body><main><h1>$heading</h1><p>Copied words here</p></main></body></html>""".stripMargin,
      lang,
      messagesApiInstance.preferred(Seq(lang))
    )

  private val pages =
    Seq(pageIn(english, "The heading"), pageIn(fr, "Le titre"))

  "translateConsistently" should {

    "surface what it warns about on a passing run" in {
      surfaced = Nil
      translateConsistently(pages).matches mustBe true
      surfaced.mkString must include("i18n-actually-translated")
    }

    "keep quiet when warning reporting is off" in {
      surfaced = Nil
      quiet = true
      try translateConsistently(pages).matches mustBe true
      finally quiet = false
      surfaced mustBe empty
    }

    "fail on a warning under failOnWarnings, and say so" in {
      strict = true
      val result =
        try translateConsistently(pages)
        finally strict = false
      result.matches mustBe false
      result.failureMessage must include(
        "translation failed on 1 warning(s), as failOnWarnings asks:"
      )
    }
  }

}
