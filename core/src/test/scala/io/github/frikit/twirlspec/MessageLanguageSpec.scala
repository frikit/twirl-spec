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

import io.github.frikit.twirlspec.expect.Expected
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Lang, MessagesImpl}

/** A key resolves in a language only when that language defines it, so a Welsh
  * assertion cannot pass on the English Play would render in its place.
  */
class MessageLanguageSpec extends AnyWordSpec with Matchers {

  private val api = new DefaultMessagesApi(
    Map(
      "default" -> Map("english.only" -> "In English"),
      "default.play" -> Map("error.required" -> "This field is required"),
      "cy" -> Map("both" -> "Yn Gymraeg"),
      "fr-CA" -> Map("regional" -> "Au Canada")
    ),
    new DefaultLangs(Seq(Lang("en"), Lang("cy"), Lang("fr-CA")))
  )

  private def pageIn(code: String) = {
    val lang = Lang(code)
    Page.fromString("<title>t</title>", lang, MessagesImpl(lang, api))
  }

  "A message key" should {

    "come from the default files for English, in any region" in {
      pageIn("en").message("english.only") mustBe Some("In English")
      pageIn("en-GB").message("english.only") mustBe Some("In English")
      pageIn("en").message("error.required") mustBe
        Some("This field is required")
    }

    "not come from the default files for another language" in {
      pageIn("cy").message("english.only") mustBe None
      pageIn("cy").message("error.required") mustBe None
    }

    "come from the language's own file" in {
      pageIn("cy").message("both") mustBe Some("Yn Gymraeg")
    }

    "come from a region's own file, or the bare language's, as Play reads them" in {
      pageIn("fr-CA").message("regional") mustBe Some("Au Canada")
      pageIn("cy-GB").message("both") mustBe Some("Yn Gymraeg")
    }

    "come from the default files for the first language configured, whatever it is" in {
      val german = new DefaultMessagesApi(
        Map("default" -> Map("greeting" -> "Guten Tag")),
        new DefaultLangs(Seq(Lang("de"), Lang("cy")))
      )
      def in(code: String) = Page.fromString(
        "<title>t</title>",
        Lang(code),
        MessagesImpl(Lang(code), german)
      )
      in("de").message("greeting") mustBe Some("Guten Tag")
      in("de-AT").message("greeting") mustBe Some("Guten Tag")
      in("cy").message("greeting") mustBe None
    }

    "be missing when nothing defines it" in {
      pageIn("en").message("nowhere") mustBe None
      pageIn("cy").message("nowhere") mustBe None
    }

    "fail an expectation on a Welsh page, naming the language" in {
      Expected.Key("english.only").resolve(pageIn("cy")) match {
        case Left(v) =>
          v.message mustBe "message key `english.only` is not defined for lang `cy`"
          v.hint mustBe Some("add `english.only` to conf/messages.cy")
        case Right(words) => fail(s"resolved to $words")
      }
    }

    "fall back to the key where asked to" in {
      pageIn("cy").messageOrKey("english.only") mustBe "english.only"
    }
  }

}
