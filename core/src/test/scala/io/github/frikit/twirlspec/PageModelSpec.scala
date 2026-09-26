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

import io.github.frikit.twirlspec.standards.Rule
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.twirl.api.Html

/** The page model answers the way a browser would: what a control is called,
  * what a form submits, and whether markup is a whole page.
  */
class PageModelSpec extends AnyWordSpec with Matchers with TwirlSpec {

  "an accessible name taken from content" should {

    "read an image's alt text where the image stands, among the text" in {
      val p = render(
        Html(
          """<a id="a" href="/f.pdf"><img src="d.png" alt="Download"> PDF</a>
            |<a id="b" href="/"><img src="logo.png" alt="HMRC"></a>
            |<a id="c" href="/x">Report <img src="i.png" alt=""> now</a>
            |<a id="d" href="/y"><img src="h.png" alt="Home" role="presentation"> Start</a>
            |<a id="e" href="/z">Next<img src="n.png" alt="arrow" aria-hidden="true"></a>""".stripMargin
        )
      )
      p.accessibleName(p.byId("a")(0)) mustBe "Download PDF"
      p.accessibleName(p.byId("b")(0)) mustBe "HMRC"
      p.accessibleName(p.byId("c")(0)) mustBe "Report now"
      p.accessibleName(p.byId("d")(0)) mustBe "Start"
      p.accessibleName(p.byId("e")(0)) mustBe "Next"
    }
  }

  "a form" should {

    val form = render(
      Html(
        """<form>
          |<select name="country"><option value="uk">UK</option><option value="fr">France</option></select>
          |<select name="picked"><option value="a" selected>A</option><option value="b" selected>B</option></select>
          |<select name="many" multiple><option value="x" selected>X</option><option value="y">Y</option><option value="z" selected disabled>Z</option></select>
          |<select name="none" multiple><option value="x">X</option></select>
          |<select name="listbox" size="3"><option value="x">X</option></select>
          |<select name="skip"><option value="off" disabled>Off</option><optgroup disabled><option value="grp">G</option></optgroup><option>Plain  text</option></select>
          |<select name="empty"></select>
          |<input name="ref" value="stale" disabled>
          |<fieldset disabled><legend><input name="inLegend" value="kept"></legend><input name="inBody" value="dropped"></fieldset>
          |<input type="checkbox" name="tick" value="a" checked><input type="checkbox" name="tick" value="b" checked><input type="checkbox" name="tick" value="c">
          |<input type="checkbox" name="agree" checked>
          |<input type="radio" name="colour" value="red"><input type="radio" name="colour" value="blue" checked>
          |<input type="submit" name="go" value="Go"><input type="button" name="btn" value="B"><input type="reset" name="rst"><input type="image" name="img" src="x.png">
          |<input type="hidden" name="csrfToken" value="t0k3n">
          |<input name="noValue">
          |<textarea name="notes">  Some   notes </textarea>
          |<input value="nameless">
          |</form>""".stripMargin
      )
    )

    "submit what a browser would, in document order" in {
      form.formSubmission mustBe Seq(
        "country" -> "uk",
        "picked" -> "b",
        "many" -> "x",
        "skip" -> "Plain text",
        "inLegend" -> "kept",
        "tick" -> "a",
        "tick" -> "b",
        "agree" -> "on",
        "colour" -> "blue",
        "csrfToken" -> "t0k3n",
        "noValue" -> "",
        "notes" -> "Some notes"
      )
    }

    "keep the last value per name in formValues" in {
      form.formValues("tick") mustBe "b"
      form.formValues.keySet must not contain "ref"
    }

    "accept any of a repeated name's values, and list them when none matches" in {
      formValues("tick" -> "a", "tick" -> "b").check(form) mustBe empty
      formValues("tick" -> "c").check(form).flatMap(_.actual) mustBe Seq(
        "a, b"
      )
      formValues("country" -> "fr").check(form).flatMap(_.actual) mustBe Seq(
        "uk"
      )
    }
  }

  "a disabled fieldset" should {

    "leave its first legend's controls enabled, and only those" in {
      val p = render(
        Html(
          """<fieldset disabled><legend><input id="first" name="f"></legend>
            |<div><legend><input id="nested" name="n"></legend></div>
            |<input id="body" name="b"></fieldset>
            |<fieldset disabled><legend>Heading</legend><legend><input id="second" name="s"></legend></fieldset>""".stripMargin
        )
      )
      p.isDisabled(p.byId("first")(0)) mustBe false
      p.isDisabled(p.byId("body")(0)) mustBe true
      p.isDisabled(p.byId("nested")(0)) mustBe true
      p.isDisabled(p.byId("second")(0)) mustBe true
    }
  }

  "whether markup is a whole page" should {

    def isPage(markup: String) = Rule.isFullPage(render(Html(markup)))

    "look past a byte order mark, whitespace and comments, however long" in {
      isPage(
        "﻿\n" + (" " * 3000) + "<!-- built by x -->\n<!-- and y -->\n" +
          "<!DOCTYPE html><html><body></body></html>"
      ) mustBe true
      isPage("<HTML lang=\"en\"><body></body></HTML>") mustBe true
    }

    "not take a fragment that mentions <html further in for a page" in {
      isPage("<div><p>raw <html in text</p></div>") mustBe false
      isPage("<div>fragment</div><!DOCTYPE html>") mustBe false
      isPage("<!-- a comment that never ends <!DOCTYPE html>") mustBe false
      isPage("") mustBe false
    }
  }
}
