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
import io.github.frikit.twirlspec.wcag.{TwirlStandards, WcagStandards}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.Lang

/** Markup a real page can have, where a rule used to say the wrong thing about
  * it, or could not say anything at all.
  */
class RealPageVerdictsSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val rules = WcagStandards.all ++ TwirlStandards.all

  private def document(body: String, title: String = "T", lang: String = "en") =
    s"""<!doctype html><html lang="$lang"><head><meta charset="utf-8"><title>$title</title></head>
       |<body><main id="main-content"><h1>H</h1>$body</main></body></html>""".stripMargin

  private def found(html: String, lang: Lang = english): Seq[String] =
    rules
      .flatMap(_.check(Page.fromString(html, lang, messages)))
      .map(_.rule)
      .distinct

  private def on(body: String) = found(document(body))

  "grouped-choices" should {
    val rule = "grouped-choices"
    def radio(id: String) =
      s"""<label for="$id">$id</label><input type="radio" id="$id" name="value">"""
    def fieldset(body: String, legend: String = "<legend>Pick</legend>") =
      s"<fieldset>$legend$body</fieldset>"

    "pass radios in one fieldset with a legend" in {
      on(fieldset(radio("a") + radio("b"))) must not contain rule
    }

    "notice a group a fieldset holds only part of, either way round" in {
      on(fieldset(radio("a")) + radio("b")) must contain(rule)
      on(radio("a") + fieldset(radio("b"))) must contain(rule)
    }

    "notice a group split across two fieldsets" in {
      on(fieldset(radio("a")) + fieldset(radio("b"))) must contain(rule)
    }

    "notice a group in no fieldset at all" in {
      on(radio("a") + radio("b")) must contain(rule)
    }

    "notice a fieldset with no legend, or one that says nothing" in {
      on(fieldset(radio("a") + radio("b"), "")) must contain(rule)
      on(fieldset(radio("a") + radio("b"), "<legend></legend>")) must
        contain(rule)
      on(
        fieldset(
          radio("a") + radio("b"),
          """<legend><span aria-hidden="true">Pick</span></legend>"""
        )
      ) must contain(rule)
    }

    "take a name from ARIA where the legend says nothing" in {
      on(
        """<h2 id="q">Pick</h2><fieldset aria-labelledby="q"><legend></legend>""" +
          radio("a") + radio("b") + "</fieldset>"
      ) must not contain rule
    }

    "not let an outer fieldset borrow the legend of one nested in it" in {
      on(
        "<fieldset>" + radio("a") + radio("b") + fieldset("<p>x</p>") +
          "</fieldset>"
      ) must contain(rule)
    }

    "pass a group with subgroups inside one named fieldset" in {
      on(
        fieldset(radio("a") + fieldset(radio("b"), "<legend>Sub</legend>"))
      ) must not contain rule
      on(
        fieldset(
          fieldset(radio("a"), "<legend>One</legend>") +
            fieldset(radio("b"), "<legend>Two</legend>")
        )
      ) must not contain rule
    }

    "keep apart two forms with a group of one name each" in {
      def form(action: String, ids: String*) =
        s"""<form method="post" action="$action">${fieldset(
            ids.map(radio).mkString
          )}</form>"""
      on(form("/a", "a", "b") + form("/b", "c", "d")) must not contain rule
    }
  }

  "label-for-resolves" should {
    val rule = "label-for-resolves"
    def labelled(target: String) = s"""<label for="x">X</label>$target"""

    "notice a label pointing at nothing, or at what no label can name" in {
      on(labelled("")) must contain(rule)
      on(labelled("""<div id="x"></div>""")) must contain(rule)
      on(labelled("""<input type="hidden" id="x">""")) must contain(rule)
    }

    "pass each element a label can name" in {
      Seq(
        """<input id="x">""",
        """<input type="radio" id="x" name="x">""",
        """<select id="x"></select>""",
        """<textarea id="x"></textarea>""",
        """<button id="x"></button>""",
        """<meter id="x"></meter>""",
        """<output id="x"></output>""",
        """<progress id="x"></progress>""",
        """<my-checkbox id="x"></my-checkbox>"""
      ).foreach(target =>
        withClue(target)(on(labelled(target)) must not contain rule)
      )
    }
  }

  "no-scala-leakage" should {
    val rule = "no-scala-leakage"

    "notice an object with no toString, a lambda, and a case class holding None" in {
      Seq(
        "models.Name@6f2b958e",
        "Name@6f2b95",
        "views.html.page$$Lambda$123",
        "FullName(John,None,Smith)",
        "models.FullName(None,Smith,1)"
      ).foreach(leak => withClue(leak)(on(s"<p>$leak</p>") must contain(rule)))
    }

    "notice a value in the title as well as the body" in {
      found(document("", title = "Some(Ada)")) must contain(rule)
    }

    "leave alone an email address, and prose that looks a little like one" in {
      Seq(
        "Bob@decade.com",
        "Bob@decade-mail.com",
        "bob@decade",
        "None, thanks",
        "None of these",
        "John,None,Smith",
        "Yes (None of these apply)"
      ).foreach(words =>
        withClue(words)(on(s"<p>$words</p>") must not contain rule)
      )
    }
  }

  "unambiguous-field-names" should {
    val rule = "unambiguous-field-names"
    val token = """<input type="hidden" name="csrfToken" value="t">"""

    "leave two forms alone, each with a token of its own" in {
      on(
        s"""<form method="post" action="/a">$token</form><form method="post" action="/b">$token</form>"""
      ) must not contain rule
    }

    "notice two controls with one name in one form" in {
      on(s"""<form method="post" action="/a">$token$token</form>""") must
        contain(rule)
    }

    "leave alone controls in no form, which submit nothing" in {
      on(
        """<input name="q" aria-label="q"><input name="q" aria-label="q">"""
      ) must
        not contain rule
      on(
        """<input name="q" aria-label="q" form="nowhere"><input name="q" aria-label="q" form="nowhere">"""
      ) must not contain rule
    }

    "read a control into the form it names" in {
      on(
        """<input type="hidden" name="x" form="f"><form id="f"><input type="hidden" name="x"></form>"""
      ) must contain(rule)
    }

    "leave out what submits nothing, and a name asking for a list" in {
      on(
        """<form><input type="hidden" name="x"><input type="hidden" name="x" disabled></form>"""
      ) must not contain rule
      on(
        """<form><input type="hidden" name="x"><input type="reset" name="x"><input type="image" name="x" alt="Go"></form>"""
      ) must not contain rule
      on(
        """<form><input type="hidden" name="x[]"><input type="hidden" name="x[]"></form>"""
      ) must not contain rule
    }
  }

  "A reference to an id" should {

    "give a verdict on an id with a quote in it, rather than throwing" in {
      on("""<label for='a"b'>L</label><input id='a"b' name="q">""") must
        not contain "label-for-resolves"
      on("""<div aria-describedby='a"b'>x</div>""") must
        contain("aria-references-resolve")
    }

    "be held to the id's case, which a browser is too" in {
      on("""<label for="Q">L</label><input id="q" name="q">""") must
        contain("label-for-resolves")
      on(
        """<input aria-label="q" name="q" aria-describedby="Hint"><p id="hint">h</p>"""
      ) must contain("aria-references-resolve")
    }
  }

  "no-raw-message-keys" should {
    val rule = "no-raw-message-keys"

    "notice a key wherever a citizen can be shown it" in {
      Seq(
        """<button type="submit">site.continue</button>""",
        """<div class="govuk-hint">x.y.hint</div>""",
        """<div class="govuk-inset-text">x.y.inset</div>""",
        """<label for="c">C</label><select id="c" name="c"><option value="1">x.y.option</option></select>""",
        """<input type="submit" value="site.continue">""",
        """<a href="/x" aria-label="x.y.label">Go</a>""",
        """<img src="/a.png" alt="x.y.alt">""",
        """<label for="q">Q</label><input id="q" name="q" placeholder="x.y.place">"""
      ).foreach(body => withClue(body)(on(body) must contain(rule)))
    }

    "notice a key in the title, whatever the layout puts round it" in {
      found(document("", title = "x.y.title")) must contain(rule)
      found(document("", title = "x.y.title - Service - GOV.UK")) must
        contain(rule)
      found(document("", title = "Error: x.y.title - Service - GOV.UK")) must
        contain(rule)
    }

    "notice a key with a hyphen or a capital, which an answer is unlikely to have" in {
      on("<p>addOther.add-them-later</p>") must contain(rule)
      on("<p>some-page.title.text</p>") must contain(rule)
      on("<p>otherIndividual.name</p>") must contain(rule)
    }

    "notice a plain pair of words when the service has keys beginning that way" in {
      on("<p>site.maybe</p>") must contain(rule)
    }

    "leave alone a plain pair of words that could be someone's answer" in {
      on("<p>john.smith</p>") must not contain rule
      on("<dl><dd>john.smith</dd></dl>") must not contain rule
      on("<p>anne-marie.smith</p>") must not contain rule
    }

    "leave alone a version, an address and a file" in {
      on("<p>v1.2</p>") must not contain rule
      on("<p>1.2.3</p>") must not contain rule
      on("<p>www.gov.uk</p>") must not contain rule
      on("<p>www.gov.wales</p>") must not contain rule
      on("<p>report.pdf</p>") must not contain rule
    }

    "leave alone what is there to be read literally, or is someone's own text" in {
      on("<code>x.y.hint</code>") must not contain rule
      on("<pre>x.y.hint</pre>") must not contain rule
      on("<pre><code><span>x.y.hint</span></code></pre>") must not contain rule
      on(
        """<label for="t">T</label><textarea id="t" name="t">x.y.hint</textarea>"""
      ) must not contain rule
    }
  }

  "labelled-controls" should {

    "read an image button's alt as its name" in {
      on("""<input type="image" src="/go.png" alt="Go" name="go">""") must
        not contain "labelled-controls"
      on("""<input type="image" src="/go.png" name="go">""") must
        contain("labelled-controls")
    }
  }

  "input-purpose-autocomplete" should {
    val rule = "input-purpose-autocomplete"

    def field(name: String) =
      s"""<label for="$name">L</label><input type="text" id="$name" name="$name">"""

    "not ask it of a control it does not apply to" in {
      on(
        """<fieldset><legend>L</legend><input type="radio" id="y" name="addressYesNo" value="true"><label for="y">Y</label>
          |<input type="radio" id="n" name="addressYesNo" value="false"><label for="n">N</label></fieldset>""".stripMargin
      ) must not contain rule
      on(
        """<label for="e">L</label><input type="checkbox" id="e" name="emailConsent">"""
      ) must not contain rule
    }

    "not read a short token inside another word" in {
      on(field("hotelName")) must not contain rule
      on(field("microphone")) must not contain rule
    }

    "read the short tokens as whole words, in the ways a name is written" in {
      Seq("tel", "telNumber", "phone_number", "mobilePhone", "telephone")
        .foreach(name => withClue(name)(on(field(name)) must contain(rule)))
    }

    "still read the longer tokens anywhere in a name" in {
      on(field("contactEmail")) must contain(rule)
    }
  }

  "no-aria-hidden-focusable" should {
    val rule = "no-aria-hidden-focusable"

    "leave alone what takes no focus" in {
      Seq(
        """<input disabled aria-hidden="true" aria-label="x" name="x">""",
        """<div aria-hidden="true"><input type="hidden" name="x" value="1"></div>""",
        """<input type="hidden" tabindex="0" aria-hidden="true" name="x">""",
        """<div aria-hidden="true"><button type="button" disabled>Off</button></div>""",
        """<div aria-hidden="true"><fieldset disabled><input aria-label="y" name="y"></fieldset></div>""",
        """<div aria-hidden="true" hidden><button type="button">Off</button></div>""",
        """<div aria-hidden="true" inert><button type="button">Off</button></div>"""
      ).foreach(body => withClue(body)(on(body) must not contain rule))
    }

    "still notice what does" in {
      on(
        """<div aria-hidden="true"><button type="button">x</button></div>"""
      ) must
        contain(rule)
      on("""<a href="/x" aria-hidden="true">x</a>""") must contain(rule)
    }
  }

  "new-tab-is-announced" should {
    val rule = "new-tab-is-announced"

    def link(words: String, aria: String = "") =
      s"""<a href="/x" target="_blank" rel="noopener" aria-label="$aria">$words</a>"""

    "know how a Welsh link says it" in {
      Seq(
        "Cymorth (yn agor tab newydd)",
        "Cymorth (yn agor tab neu ffenestr newydd)",
        "Cymorth (yn agor ffenestr neu dab newydd)"
      ).foreach(words => withClue(words)(on(link(words)) must not contain rule))
    }

    "read what the link is announced as, in either language" in {
      on(link("Cymorth", aria = "Cymorth, yn agor tab newydd")) must
        not contain rule
      on(link("Help", aria = "Help, opens in a new window")) must
        not contain rule
    }

    "still notice a link that does not say so" in {
      on(link("Help")) must contain(rule)
      on(link("Cymorth")) must contain(rule)
    }
  }

  "html-lang" should {
    val rule = "html-lang"

    "accept any region of the page's language, in any case" in {
      found(document(""), Lang("en-GB")) must not contain rule
      found(document("", lang = "EN")) must not contain rule
      found(document("", lang = "en-GB")) must not contain rule
    }

    "not take another word for the language" in {
      found(document("", lang = "english")) must contain(rule)
      found(document("", lang = "cy")) must contain(rule)
    }
  }

}
