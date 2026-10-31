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

import io.github.frikit.twirlspec.page.{Page, Selection}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{DefaultMessagesApi, Lang, Messages, MessagesImpl}

/** An id and a name are what they are: jsoup's `[a="x"]` ignores case, and does
  * not parse a value with a quote in it.
  */
class ExactLookupSpec extends AnyWordSpec with Matchers {

  private val lang = Lang("en")
  private val messages = MessagesImpl(lang, new DefaultMessagesApi())

  private def pageOf(body: String) = Page.fromString(body, lang, messages)

  /** Each lookup, by the right name, the wrong case, and with a quote in it. */
  private def exact(body: String, name: String)(
      lookup: (Page, String) => Selection
  ) = {
    val page = pageOf(body)
    lookup(page, name).size mustBe 1
    lookup(page, name.toUpperCase).isEmpty mustBe true
    lookup(page, "a\"b").isEmpty mustBe true
  }

  "A lookup by id or name" should {

    "take the id exactly" in {
      exact("""<p id="name">x</p>""", "name")(_.byId(_))
    }

    "find every element with the id, as the ambiguity check needs" in {
      pageOf("""<p id="a">1</p><p id="a">2</p>""").byId("a").size mustBe 2
    }

    "take an input's name or id exactly" in {
      exact("""<input name="email">""", "email")(_.input(_))
      exact("""<input id="email">""", "email")(_.input(_))
    }

    "take any control's name or id exactly" in {
      exact("""<input name="a">""", "a")(_.formControl(_))
      exact("""<select name="a"></select>""", "a")(_.formControl(_))
      exact("""<textarea id="a"></textarea>""", "a")(_.formControl(_))
    }

    "take a textarea's and a select's name or id exactly" in {
      exact("""<textarea name="a"></textarea>""", "a")(_.textarea(_))
      exact("""<select id="a"></select>""", "a")(_.selectBox(_))
    }

    "take a radio's and a checkbox's name exactly" in {
      exact("""<input type="radio" name="a">""", "a")(_.radios(_))
      exact("""<input type="checkbox" name="a">""", "a")(_.checkboxes(_))
    }

    "take a file input's name or id exactly" in {
      exact("""<input type="file" name="a">""", "a")(_.fileUpload(_))
      exact("""<input type="file" id="a">""", "a")(_.fileUpload(_))
    }

    "take a label's target exactly" in {
      exact("""<label for="a">A</label>""", "a")(_.labelFor(_))
    }

    "take a hint's field exactly, by the convention or inside its wrapper" in {
      exact("""<div id="a-hint">H</div>""", "a")(_.hint(_))
      exact("""<div id="a"><div class="govuk-hint">H</div></div>""", "a")(
        _.hint(_)
      )
      pageOf("""<div id="b"><div class="govuk-hint">H</div></div>""")
        .hint("a")
        .isEmpty mustBe true
    }

    "take a date input's field exactly, under either convention" in {
      val fields =
        """<input id="a.day"><input id="a-month"><input id="a.year"><input id="a.other">"""
      pageOf(fields).dateInput("a").size mustBe 3
      pageOf(fields).dateInput("A").isEmpty mustBe true
    }

    "find nothing called nothing, though an absent attribute reads as empty" in {
      val page = pageOf(
        """<input name="a"><input id="b"><input type="radio"><input type="checkbox"><label>L</label><p id="">x</p><div><div class="govuk-hint">H</div></div>"""
      )
      page.byId("").isEmpty mustBe true
      page.input("").isEmpty mustBe true
      page.radios("").isEmpty mustBe true
      page.checkboxes("").isEmpty mustBe true
      page.labelFor("").isEmpty mustBe true
      page.hint("").isEmpty mustBe true
    }

    "hold the DSL to the same" in {
      val dsl = new TwirlSpecDsl {}
      val page = pageOf(
        """<p id="Name">x</p><input type="hidden" name="Token" value="v">"""
      )
      dsl.element("name").check(page) must not be empty
      dsl.element("Name").check(page) mustBe empty
      dsl.hiddenInput("token", "v").check(page) must not be empty
      dsl.hiddenInput("Token", "v").check(page) mustBe empty
      dsl
        .hiddenInput("", "v")
        .check(pageOf("""<input id="t" type="hidden" value="v">""")) must
        not be empty
    }
  }

  "A name and a role" should {

    "read an image button as a button, named by its alt" in {
      val dsl = new TwirlSpecDsl {}
      val page = pageOf("""<input type="image" src="/go.png" alt="Go">""")
      dsl.role("button").namedText("Go").check(page) mustBe empty
    }
  }

  "A Messages other than the one Play hands out" should {

    /** One that can answer only what `Messages` itself offers. */
    val other = new Messages {
      def lang: Lang = Lang("cy")
      def apply(key: String, args: Any*): String = s"[$key]"
      def apply(keys: Seq[String], args: Any*): String = keys.mkString
      def translate(key: String, args: Seq[Any]): Option[String] = None
      def isDefinedAt(key: String): Boolean = key == "known"
      def asJava: play.i18n.Messages = null
    }

    "only be asked isDefinedAt, and offer no families of keys" in {
      val page = Page.fromString("<p>x</p>", Lang("cy"), other)
      page.message("known") mustBe Some("[known]")
      page.message("unknown") mustBe None
      page.messageKeyFamilies mustBe empty
    }
  }

}
