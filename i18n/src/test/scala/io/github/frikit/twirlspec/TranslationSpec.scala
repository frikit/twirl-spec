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

import io.github.frikit.twirlspec.i18n.{
  I18nChecks,
  TranslationConfig,
  TranslationStandards
}
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.Lang

/** Whether a view says the same thing in every language it is offered in. */
class TranslationSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with I18nChecks {

  private val fr = Lang("fr")

  private def pageIn(lang: Lang, body: String, declared: String = "") = {
    val langAttr = if (declared.nonEmpty) declared else lang.code
    // the heading has to differ per language too, or it trips the copied-text rule in every comparison
    val heading = if (lang.code == "en") "The heading" else "Le titre"
    Page.fromString(
      s"""<!DOCTYPE html><html lang="$langAttr"><head><title>t</title></head>
         |<body><main><h1 id="h">$heading</h1>$body</main></body></html>""".stripMargin,
      lang,
      messagesApiInstance.preferred(Seq(lang))
    )
  }

  private val base = pageIn(
    english,
    """<p id="intro">Hello there</p><a id="go" href="/next">Continue</a>"""
  )

  private def rulesFor(
      other: Page,
      config: TranslationConfig = TranslationConfig.default
  ) =
    TranslationStandards
      .all(config)
      .flatMap(_.check(base, other))
      .map(_.rule)
      .distinct

  private def rulesBetween(from: Page, to: Page) =
    TranslationStandards.all().flatMap(_.check(from, to)).map(_.rule).distinct

  "i18n-lang-attribute" should {
    "notice a page that declares the wrong language" in {
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="go" href="/next">Continuer</a>""",
        declared = "en"
      )
      rulesFor(other) must contain("i18n-lang-attribute")
    }
    "accept a regional variant of the right one" in {
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="go" href="/next">Continuer</a>""",
        declared = "fr-CA"
      )
      rulesFor(other) must not contain "i18n-lang-attribute"
    }
    "notice a page that declares nothing" in {
      val other = Page.fromString(
        """<!DOCTYPE html><html><head><title>t</title></head><body><main><h1 id="h">Le titre</h1></main></body></html>""",
        fr,
        messagesApiInstance.preferred(Seq(fr))
      )
      rulesFor(other) must contain("i18n-lang-attribute")
    }
  }

  "i18n-lang-attribute, on how a language is written" should {
    "accept the bare language on a page rendered in a region of it" in {
      rulesFor(
        pageIn(
          Lang("fr-CA"),
          """<p id="intro">Bonjour</p><a id="go" href="/next">Continuer</a>""",
          declared = "fr"
        )
      ) must not contain "i18n-lang-attribute"
    }
    "accept it in any case, and not take another word for it" in {
      rulesFor(
        pageIn(
          fr,
          """<p id="intro">Bonjour</p><a id="go" href="/next">Continuer</a>""",
          declared = "FR"
        )
      ) must not contain "i18n-lang-attribute"
      rulesFor(
        pageIn(
          fr,
          """<p id="intro">Bonjour</p><a id="go" href="/next">Continuer</a>""",
          declared = "french"
        )
      ) must contain("i18n-lang-attribute")
    }
  }

  "i18n-same-ids" should {
    "notice an element the translation dropped" in {
      rulesFor(
        pageIn(fr, """<a id="go" href="/next">Continuer</a>""")
      ) must contain("i18n-same-ids")
    }
    "notice an element the translation added" in {
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="go" href="/next">C</a><p id="extra">x</p>"""
      )
      rulesFor(other) must contain("i18n-same-ids")
    }
  }

  "i18n-same-links" should {
    "notice a translation that kept a different href" in {
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="go" href="/suivant">Continuer</a>"""
      )
      rulesFor(other) must contain("i18n-same-links")
    }

    "accept the language switch, which points at the other language on each page" in {
      val en = pageIn(
        english,
        """<p id="intro">Hello there</p><a id="switch" href="/hmrc-frontend/language/fr">Français</a>"""
      )
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="switch" href="/hmrc-frontend/language/en">English</a>"""
      )
      rulesBetween(en, other) must not contain "i18n-same-links"
    }

    "accept a link that carries the page's own language, wherever it carries it" in {
      val en = pageIn(
        english,
        """<p id="intro">Hello there</p><a id="help" href="/en/help?lang=en&print#top">Help</a>"""
      )
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="help" href="/fr/help?lang=fr&print#top">Aide</a>"""
      )
      rulesBetween(en, other) must not contain "i18n-same-links"
    }

    "read the bare language where the page is configured with a region" in {
      val en = pageIn(
        Lang("en-GB"),
        """<p id="intro">Hello there</p><a id="switch" href="/language/cy">Cymraeg</a>"""
      )
      val other = pageIn(
        Lang("cy-GB"),
        """<p id="intro">Helo</p><a id="switch" href="/language/en">English</a>"""
      )
      rulesBetween(en, other) must not contain "i18n-same-links"
    }

    "leave a language name inside a longer segment alone" in {
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="go" href="/next-fr">Continuer</a>"""
      )
      rulesFor(other) must contain("i18n-same-links")
    }

    "report a different place as each page wrote it" in {
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour</p><a id="go" href="/suivant?lang=fr">Continuer</a>"""
      )
      val found = TranslationStandards
        .all()
        .flatMap(_.check(base, other))
        .filter(_.rule == "i18n-same-links")
      found.flatMap(_.expected) must contain("/next")
      found.flatMap(_.actual) must contain("/suivant?lang=fr")
    }
  }

  "i18n-same-controls" should {
    "notice a field missing from the translation" in {
      val withField = pageIn(
        english,
        """<p id="intro">Hello</p><input name="nino"><a id="go" href="/next">C</a>"""
      )
      val translated =
        pageIn(fr, """<p id="intro">Bonjour</p><a id="go" href="/next">C</a>""")
      TranslationStandards
        .all()
        .flatMap(_.check(withField, translated))
        .map(_.rule) must contain("i18n-same-controls")
    }
  }

  "i18n-same-headings" should {
    "notice a heading level that moved" in {
      val other = Page.fromString(
        """<!DOCTYPE html><html lang="fr"><head><title>t</title></head>
          |<body><main><h2 id="h">Le titre</h2><p id="intro">Bonjour</p>
          |<a id="go" href="/next">Continuer</a></main></body></html>""".stripMargin,
        fr,
        messagesApiInstance.preferred(Seq(fr))
      )
      rulesFor(other) must contain("i18n-same-headings")
    }
  }

  "i18n-nothing-lost" should {
    "notice text that went empty" in {
      val other = pageIn(
        fr,
        """<p id="intro"></p><a id="go" href="/next">Continuer</a>"""
      )
      rulesFor(other) must contain("i18n-nothing-lost")
    }
    "not complain when the base was empty too" in {
      val emptyBase = pageIn(
        english,
        """<p id="intro"></p><a id="go" href="/next">Continue</a>"""
      )
      val other = pageIn(
        fr,
        """<p id="intro"></p><a id="go" href="/next">Continuer</a>"""
      )
      TranslationStandards
        .all()
        .flatMap(_.check(emptyBase, other))
        .map(_.rule) must not contain "i18n-nothing-lost"
    }
  }

  "i18n-nothing-lost, on what has no id" should {
    "notice a paragraph that went empty" in {
      rulesBetween(
        pageIn(english, "<p>Important instruction</p>"),
        pageIn(fr, "<p></p>")
      ) must contain("i18n-nothing-lost")
    }
    "notice an answer that went empty" in {
      rulesBetween(
        pageIn(english, "<dl><dd>Ada</dd></dl>"),
        pageIn(fr, "<dl><dd></dd></dl>")
      ) must contain("i18n-nothing-lost")
    }
    "not complain when the base was empty too" in {
      rulesBetween(
        pageIn(english, "<p></p>"),
        pageIn(fr, "<p></p>")
      ) must not contain "i18n-nothing-lost"
    }
    "not pair blocks when one language has more, as a start page offering Welsh does" in {
      rulesBetween(
        pageIn(english, "<p>Also in Welsh</p><p>Start now</p>"),
        pageIn(fr, "<p></p>")
      ) must not contain "i18n-nothing-lost"
    }
    "not pair a block with a kind of block the other language does not have" in {
      rulesBetween(
        pageIn(english, "<p>Words here</p>"),
        pageIn(fr, "<div></div>")
      ) must not contain "i18n-nothing-lost"
    }
    "not mind a table sorting differently once translated" in {
      def table(first: String, second: String) =
        s"<table><tbody><tr><td>$first</td></tr><tr><td>$second</td></tr></tbody></table>"
      rulesBetween(
        pageIn(english, table("Berlin", "")),
        pageIn(fr, table("", "Berlin"))
      ) must not contain "i18n-nothing-lost"
    }
    "say how many went empty, and which words were there" in {
      val lost = TranslationStandards
        .all()
        .flatMap(
          _.check(
            pageIn(english, "<p>First words</p><p>Second words</p>"),
            pageIn(fr, "<p>Premiers mots</p><p></p>")
          )
        )
        .filter(_.rule == "i18n-nothing-lost")
      lost.map(_.message) mustBe Seq(
        "1 <p> say something in the base language but are empty in fr"
      )
      lost.flatMap(_.expected) mustBe Seq("First words | Second words")
      lost.flatMap(_.actual) mustBe Seq("Premiers mots")
    }
    "notice an answer with an id going empty, inline elements and all" in {
      rulesBetween(
        pageIn(english, """<dl><dd id="a"><strong>Ada</strong></dd></dl>"""),
        pageIn(fr, """<dl><dd id="a"><strong></strong></dd></dl>""")
      ) must contain("i18n-nothing-lost")
    }
    "notice a component that writes its words directly going empty" in {
      rulesBetween(
        pageIn(english, """<div class="govuk-inset-text">Important</div>"""),
        pageIn(fr, """<div class="govuk-inset-text"></div>""")
      ) must contain("i18n-nothing-lost")
    }
    "read the whole of an element with an id, not only what is directly in it" in {
      rulesBetween(
        pageIn(english, """<p id="p"><strong>Important</strong></p>"""),
        pageIn(fr, """<p id="p"><strong></strong></p>""")
      ) must contain("i18n-nothing-lost")
    }
  }

  "i18n-actually-translated, on what has no id" should {
    def warnings(from: Page, to: Page) =
      TranslationStandards
        .all()
        .flatMap(_.check(from, to))
        .filter(_.rule == "i18n-actually-translated")
        .map(_.message)

    "notice a page left in English, in any order" in {
      warnings(
        pageIn(english, "<p>First words</p><label>Your name</label>"),
        pageIn(fr, "<label>Your name</label><p>First words</p>")
      ) mustBe Seq(
        "a <label> reads the same in fr as in the base language",
        "a <p> reads the same in fr as in the base language"
      )
    }
    "leave answers, names and table data alone" in {
      val data =
        "<dl><dt>Ada Lovelace</dt><dd>Ada Lovelace</dd></dl><table><tbody><tr><td>Ada Lovelace</td></tr></tbody></table>"
      warnings(pageIn(english, data), pageIn(fr, data)) mustBe empty
    }
    "leave what an answer holds alone too" in {
      val answer =
        """<dl><dd class="govuk-summary-list__value"><p>Ada Lovelace</p></dd></dl>"""
      warnings(pageIn(english, answer), pageIn(fr, answer)) mustBe empty
    }
    "leave an answer with an id alone" in {
      val answer =
        """<dl><dt id="who">Ada Lovelace</dt><dd id="answer"><p>Ada Lovelace</p></dd></dl>"""
      warnings(pageIn(english, answer), pageIn(fr, answer)) mustBe empty
    }
    "not read a section with an id as one block of words" in {
      val brands =
        """<section id="brands"><p>GOV.UK</p><p>BETA</p></section>"""
      warnings(pageIn(english, brands), pageIn(fr, brands)) mustBe empty
    }
    "read a summary list's actions, with an id or without" in {
      val actions =
        """<dl><dd class="govuk-summary-list__actions"><a id="change" href="/change">Change this</a></dd></dl>"""
      warnings(pageIn(english, actions), pageIn(fr, actions)) mustBe Seq(
        "#change reads the same in fr as in the base language",
        "a <dd> reads the same in fr as in the base language"
      )
    }
    "read a summary list's keys, which are labels" in {
      val key =
        """<dl><dt class="govuk-summary-list__key">National Insurance number</dt></dl>"""
      warnings(pageIn(english, key), pageIn(fr, key)) mustBe Seq(
        "a <dt> reads the same in fr as in the base language"
      )
    }
    "read a component that writes its words directly" in {
      val inset = """<div class="govuk-inset-text">Important words</div>"""
      warnings(pageIn(english, inset), pageIn(fr, inset)) mustBe Seq(
        "a <div> reads the same in fr as in the base language"
      )
    }
    "read the whole of an element with an id, not only what is directly in it" in {
      val strong = """<p id="p"><strong>Important words</strong></p>"""
      warnings(pageIn(english, strong), pageIn(fr, strong)) mustBe Seq(
        "#p reads the same in fr as in the base language"
      )
    }
    "leave text on the allowed list alone" in {
      TranslationStandards
        .all(TranslationConfig(sameTextIsFine = Set("Example Ltd")))
        .flatMap(
          _.check(
            pageIn(english, "<p>Example Ltd</p>"),
            pageIn(fr, "<p>Example Ltd</p>")
          )
        )
        .map(_.rule) must not contain "i18n-actually-translated"
    }
    "read a page with no main content from its body" in {
      def bare(lang: Lang) = Page.fromString(
        "<html><head><title>t</title></head><body><p>Same words</p></body></html>",
        lang,
        messagesApiInstance.preferred(Seq(lang))
      )
      warnings(bare(english), bare(fr)) mustBe Seq(
        "a <p> reads the same in fr as in the base language"
      )
    }
    "notice a title left in English" in {
      def titled(lang: Lang) = Page.fromString(
        s"""<html lang="${lang.code}"><head><title>Your name - GOV.UK</title></head><body></body></html>""",
        lang,
        messagesApiInstance.preferred(Seq(lang))
      )
      warnings(titled(english), titled(fr)) mustBe Seq(
        "the title reads the same in fr as in the base language"
      )
    }
  }

  "i18n-same-submissions" should {
    def submissions(from: Page, to: Page) =
      TranslationStandards
        .all()
        .flatMap(_.check(from, to))
        .filter(_.rule == "i18n-same-submissions")

    def select(options: String) =
      s"""<label for="c">C</label><select id="c" name="c">$options</select>"""

    "notice an option the translation offers differently" in {
      val found = submissions(
        pageIn(english, select("""<option value="GB">UK</option>""")),
        pageIn(fr, select("""<option value="FR">RU</option>"""))
      )
      found.map(_.message) mustBe Seq(
        "fr is missing 1 choice the base language has",
        "fr has 1 choice the base language does not"
      )
      found.map(_.severity.label).distinct mustBe Seq("warning")
    }
    "not mind the labels or the order of the options" in {
      submissions(
        pageIn(
          english,
          select(
            """<option value="GB">Britain</option><option value="FR">France</option>"""
          )
        ),
        pageIn(
          fr,
          select(
            """<option value="FR">Ffrainc</option><option value="GB">Prydain</option>"""
          )
        )
      ) mustBe empty
    }
    "read an option with no value by what it submits, its words" in {
      submissions(
        pageIn(english, select("<option>Britain</option>")),
        pageIn(fr, select("<option>Prydain</option>"))
      ) must not be empty
    }
    "notice a choice offered twice" in {
      submissions(
        pageIn(english, select("""<option value="GB">UK</option>""")),
        pageIn(
          fr,
          select(
            """<option value="GB">UK</option><option value="GB">UK</option>"""
          )
        )
      ).map(_.actual) mustBe Seq(Some("c=GB (x2)"))
    }
    "notice a radio or a checkbox submitting something else" in {
      def radio(value: String) =
        s"""<input type="radio" id="r" name="r" value="$value"><label for="r">R</label>"""
      submissions(
        pageIn(english, radio("true")),
        pageIn(fr, radio("yes"))
      ) must not be empty
    }
    "read a box with no value by what it submits, which is on" in {
      def box(value: String) =
        s"""<input type="checkbox" id="b" name="b"$value><label for="b">B</label>"""
      submissions(
        pageIn(english, box("")),
        pageIn(fr, box(""" value="on""""))
      ) mustBe empty
      submissions(
        pageIn(english, box("")),
        pageIn(fr, box(""" value="""""))
      ) must not be empty
    }
    "notice two forms with ids swapping their destinations" in {
      def forms(first: String, second: String) =
        s"""<form id="save" action="$first" method="post"></form><form id="drop" action="$second" method="post"></form>"""
      submissions(
        pageIn(english, forms("/save", "/drop")),
        pageIn(fr, forms("/drop", "/save"))
      ).flatMap(_.expected) must contain("#drop POST /drop, #save POST /save")
    }
    "notice a form appearing twice" in {
      val form = """<form action="/a" method="post"></form>"""
      submissions(
        pageIn(english, form),
        pageIn(fr, form + form)
      ).flatMap(_.actual) mustBe Seq("POST /a (x2)")
    }
    "notice a form going elsewhere, as each page wrote it" in {
      val found = submissions(
        pageIn(english, """<form action="/a" method="post"></form>"""),
        pageIn(fr, """<form action="/b" method="post"></form>""")
      )
      found.flatMap(_.expected) must contain("POST /a")
      found.flatMap(_.actual) must contain("POST /b")
    }
    "read a form with no method as the GET it is" in {
      submissions(
        pageIn(english, """<form action="/a" method="get"></form>"""),
        pageIn(fr, """<form action="/a"></form>""")
      ) mustBe empty
    }
    "accept a form going to the page's own language, or carrying a token" in {
      submissions(
        pageIn(
          english,
          """<form action="/en/a?csrfToken=1" method="post"></form>"""
        ),
        pageIn(fr, """<form action="/fr/a?csrfToken=2" method="post"></form>""")
      ) mustBe empty
    }
  }

  "i18n-same-links, on links with ids" should {
    "notice two links that swapped their destinations" in {
      val found = TranslationStandards
        .all()
        .flatMap(
          _.check(
            pageIn(
              english,
              """<a id="tax" href="/tax">Tax</a><a id="help" href="/help">Help</a>"""
            ),
            pageIn(
              fr,
              """<a id="tax" href="/help">Impot</a><a id="help" href="/tax">Aide</a>"""
            )
          )
        )
        .filter(_.rule == "i18n-same-links")
      found.map(_.message) mustBe Seq(
        "#help goes somewhere else in fr",
        "#tax goes somewhere else in fr"
      )
      found.flatMap(_.expected) mustBe Seq("/help", "/tax")
      found.flatMap(_.actual) mustBe Seq("/tax", "/help")
    }
    "accept a link that differs only by the language it names" in {
      rulesBetween(
        pageIn(english, """<a id="switch" href="/language/en">Switch</a>"""),
        pageIn(fr, """<a id="switch" href="/language/fr">Changer</a>""")
      ) must not contain "i18n-same-links"
    }
    "leave a link the translation dropped to the other rules" in {
      rulesBetween(
        pageIn(english, """<a id="go" href="/next">Continue</a>"""),
        pageIn(fr, "")
      ) must contain("i18n-same-links")
    }
  }

  "i18n-actually-translated" should {
    "notice text copied straight from the base language" in {
      val other = pageIn(
        fr,
        """<p id="intro">Hello there</p><a id="go" href="/next">Continuer</a>"""
      )
      rulesFor(other) must contain("i18n-actually-translated")
    }
    "report it only as a warning" in {
      val other = pageIn(
        fr,
        """<p id="intro">Hello there</p><a id="go" href="/next">Continuer</a>"""
      )
      TranslationStandards
        .all()
        .flatMap(_.check(base, other))
        .filter(_.rule == "i18n-actually-translated")
        .map(_.severity.label)
        .distinct mustBe List("warning")
    }
    "leave short text alone" in {
      val shortBase = pageIn(
        english,
        """<p id="intro">OK</p><a id="go" href="/next">Continue</a>"""
      )
      val translated = pageIn(
        fr,
        """<p id="intro">OK</p><a id="go" href="/next">Continuer</a>"""
      )
      TranslationStandards
        .all()
        .flatMap(_.check(shortBase, translated))
        .map(_.rule) must not contain "i18n-actually-translated"
    }
    "leave text on the allowed list alone" in {
      val other = pageIn(
        fr,
        """<p id="intro">Hello there</p><a id="go" href="/next">Continuer</a>"""
      )
      TranslationStandards
        .all(TranslationConfig(sameTextIsFine = Set("Hello there")))
        .flatMap(_.check(base, other))
        .map(_.rule) must not contain "i18n-actually-translated"
    }
    "leave text with no letters alone" in {
      val numeric = pageIn(
        english,
        """<p id="intro">1234</p><a id="go" href="/next">Continue</a>"""
      )
      val translated = pageIn(
        fr,
        """<p id="intro">1234</p><a id="go" href="/next">Continuer</a>"""
      )
      TranslationStandards
        .all()
        .flatMap(_.check(numeric, translated))
        .map(_.rule) must not contain "i18n-actually-translated"
    }
  }

  "the translateConsistently matcher" should {

    val good = pageIn(
      fr,
      """<p id="intro">Bonjour tout le monde</p><a id="go" href="/next">Continuer</a>"""
    )
    val bad = pageIn(fr, """<a id="go" href="/next">Continuer</a>""")

    "pass when every language matches the base" in {
      Seq(base, good) must translateConsistently
    }

    "touch nothing on the pages it compares, so coverage is not affected" in {
      val en = pageIn(
        english,
        """<p id="intro">Hello there</p><a id="go" href="/next">Continue</a>"""
      )
      val other = pageIn(
        fr,
        """<p id="intro">Bonjour tout le monde</p><a id="go" href="/next">Continuer</a>"""
      )
      Seq(en, other) must translateConsistently
      translationDifferences(Seq(en, other)) mustBe empty
      en.touchedAnchors mustBe empty
      other.touchedAnchors mustBe empty
    }

    "fail when one does not, naming what differs" in {
      val thrown =
        the[org.scalatest.exceptions.TestFailedException] thrownBy (Seq(
          base,
          bad
        ) must translateConsistently)
      thrown.getMessage must include("i18n-same-ids")
    }

    "accept a rule the project has decided to live without" in {
      Seq(base, bad) must translateConsistentlyExcept(
        "i18n-same-ids",
        "i18n-nothing-lost"
      )
    }

    "measure against a nominated base rather than the first page" in {
      Seq(good, base) must basedOn(english)
    }

    "fall back to the first page when the nominated language is absent" in {
      Seq(base, good) must basedOn(Lang("de"))
    }

    "pass when there is only one language to compare" in {
      Seq(base) must translateConsistently
    }

    "fail when given no pages at all" in {
      val thrown =
        the[org.scalatest.exceptions.TestFailedException] thrownBy (Seq
          .empty[Page] must translateConsistently)
      thrown.getMessage must include("no pages")
    }

    "report the differences without failing" in {
      translationDifferences(Seq(base, bad)).map(_.rule) must contain(
        "i18n-same-ids"
      )
    }

    "report nothing for an empty comparison" in {
      translationDifferences(Seq.empty) mustBe empty
    }
  }

}
