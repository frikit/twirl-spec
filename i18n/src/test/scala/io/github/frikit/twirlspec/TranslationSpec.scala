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
