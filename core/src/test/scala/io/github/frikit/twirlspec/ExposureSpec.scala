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
import play.api.i18n.{DefaultMessagesApi, Lang, MessagesImpl}

/** What the markup keeps from assistive technology, and which error a link in
  * the summary leads to.
  */
class ExposureSpec extends AnyWordSpec with Matchers {

  private val lang = Lang("en")
  private val messages = MessagesImpl(lang, new DefaultMessagesApi())

  private def pageOf(body: String) = Page.fromString(body, lang, messages)

  private def exposed(body: String) = {
    val page = pageOf(body)
    page.isExposed(page.document.getElementById("x"))
  }

  "isExposed" should {

    "see what the markup hides, on the element or above it" in {
      Seq(
        """<p id="x" hidden></p>""",
        """<p id="x" inert></p>""",
        """<p id="x" aria-hidden="true"></p>""",
        """<div hidden><p id="x"></p></div>""",
        """<noscript><p id="x"></p></noscript>""",
        """<template><p id="x"></p></template>""",
        """<p id="x" style="display:none"></p>""",
        """<p id="x" style="width:0; DISPLAY : NONE;"></p>""",
        """<div style="display:none"><p id="x"></p></div>""",
        """<p id="x" style="visibility:hidden"></p>""",
        """<div style="visibility:hidden"><p id="x"></p></div>"""
      ).foreach(markup => withClue(markup)(exposed(markup)) mustBe false)
    }

    "go by the declaration that wins" in {
      Seq(
        """<p id="x" style="display:block; display:none"></p>""",
        """<p id="x" style="display:none !important; display:block"></p>""",
        """<p id="x" style="display: none ! important; display:block"></p>"""
      ).foreach(markup => withClue(markup)(exposed(markup)) mustBe false)
      Seq(
        """<p id="x" style="display:none; display:block"></p>""",
        """<p id="x" style="display:none !important; display:block !important"></p>"""
      ).foreach(markup => withClue(markup)(exposed(markup)) mustBe true)
    }

    "let an element show itself inside something invisible, unless it defers" in {
      exposed(
        """<div style="visibility:hidden"><p id="x" style="visibility:visible"></p></div>"""
      ) mustBe true
      Seq("inherit", "unset").foreach(value =>
        withClue(value) {
          exposed(
            s"""<div style="visibility:hidden"><p id="x" style="visibility:$value"></p></div>"""
          ) mustBe false
        }
      )
    }

    "not end a declaration at a semicolon in a comment, a string or brackets" in {
      Seq(
        """<p id="x" style="display:none /* waiting for JS */"></p>""",
        """<p id="x" style="/* a;b */ display:none"></p>"""
      ).foreach(markup => withClue(markup)(exposed(markup)) mustBe false)
      Seq(
        """<p id="x" style="/* display:none */"></p>""",
        """<p id="x" style="background-image:url('https://example.test/a;display:none;z')"></p>""",
        """<p id="x" style="background-image:url(https://example.test/a;display:none;z)"></p>""",
        """<p id="x" style='font-family:"a;display:none"'></p>"""
      ).foreach(markup => withClue(markup)(exposed(markup)) mustBe true)
    }

    "not take anything else for hiding" in {
      Seq(
        """<p id="x"></p>""",
        """<p id="x" aria-hidden="false"></p>""",
        """<p id="x" style="display:block"></p>""",
        """<p id="x" style="--display:none"></p>""",
        """<p id="x" style="color"></p>"""
      ).foreach(markup => withClue(markup)(exposed(markup)) mustBe true)
    }
  }

  "A name" should {
    def nameOf(body: String) = {
      val page = pageOf(body)
      page.accessibleName(page.document.getElementById("x"))
    }

    "not come from what the markup hides inside the element" in {
      nameOf(
        """<a id="x" href="/next"><span hidden>Continue</span></a>"""
      ) mustBe ""
      nameOf(
        """<a id="x" href="/next">Next <span hidden>page</span></a>"""
      ) mustBe "Next"
    }

    "come to a fieldset from ARIA, then its own legend, and not its content" in {
      nameOf(
        """<span id="l">Asked</span><fieldset id="x" aria-labelledby="l"><legend>Pick</legend></fieldset>"""
      ) mustBe "Asked"
      nameOf(
        """<fieldset id="x" aria-label="Asked"><legend>Pick</legend></fieldset>"""
      ) mustBe "Asked"
      nameOf(
        """<fieldset id="x"><legend>Pick</legend><p>More</p></fieldset>"""
      ) mustBe "Pick"
      nameOf(
        """<fieldset id="x"><p>Words</p><fieldset><legend>Inner</legend></fieldset></fieldset>"""
      ) mustBe ""
    }

    "still come from a hidden element named directly" in {
      nameOf(
        """<span id="l" hidden>Go</span><button id="x" aria-labelledby="l"></button>"""
      ) mustBe "Go"
    }
  }

  "errorAt" should {
    def error(id: String) =
      s"""<p id="$id" class="govuk-error-message">$id</p>"""

    "go by what the target says describes it, before a conventional id" in {
      val page = pageOf(
        error("a-error") + error("b-error") +
          """<input id="a" name="b" aria-describedby="b-error">"""
      )
      page.errorAt("a").map(_.id) mustBe Some("b-error")
      page.errorOf("a").map(_.id) mustBe Some("a-error")
    }

    "go through the fieldset around the target" in {
      pageOf(
        s"""<fieldset aria-describedby="other">${error(
            "other"
          )}<input id="a"></fieldset>"""
      ).errorAt("a").map(_.id) mustBe Some("other")
    }

    "fall back on the field's error, and find none for no target" in {
      val page = pageOf(error("a-error") + """<input id="a" name="a">""")
      page.errorAt("a").map(_.id) mustBe Some("a-error")
      page.errorAt("") mustBe None
    }

    "not record what it looks at as asserted" in {
      val page = pageOf(error("a-error") + """<input id="a" name="a">""")
      page.errorAt("a")
      page.touchedAnchors mustBe empty
    }
  }

}
