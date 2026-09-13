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

package io.github.frikit.twirlspec.i18n

import io.github.frikit.twirlspec.expect.{Severity, Violation}
import io.github.frikit.twirlspec.page.{Page, Text}
import play.api.i18n.Lang

import scala.jdk.CollectionConverters._

/** One check comparing a translated page against the language it was translated
  * from.
  */
final case class TranslationRule(
    id: String,
    description: String,
    severity: Severity = Severity.Error
)(run: (Page, Page) => Seq[Violation]) {

  /** The violations this rule finds comparing a translated page with the base
    * one, each a warning when the rule is.
    */
  def check(base: Page, other: Page): Seq[Violation] =
    run(base, other).map(v => if (severity == Severity.Warning) v.warn else v)

}

/** What a page is allowed to differ by between languages.
  *
  * @param sameTextIsFine
  *   text that legitimately reads the same in every language: product names,
  *   units, anything not worth translating
  */
final case class TranslationConfig(
    sameTextIsFine: Set[String] = TranslationConfig.commonlyUntranslated
)

/** The default configuration, and what it leaves untranslated. */
object TranslationConfig {

  /** Left alone by most services: the platform's own name, and the phase banner
    * wording.
    */
  val commonlyUntranslated: Set[String] = Set("GOV.UK", "BETA", "ALPHA")

  /** The default: only what almost every service leaves untranslated. */
  val default: TranslationConfig = TranslationConfig()
}

/** Whether a view says the same thing in every language it is offered in.
  *
  * The message-file checks in `twirl-spec-messages` compare the files. These
  * compare the pages those files produce, which is where a translation that
  * parses but renders differently shows up: a control that vanished, a link
  * that kept its English href, a heading level that moved.
  *
  * Nothing here is specific to any language pair. Nominate a base and every
  * other language is measured against it.
  */
object TranslationStandards {

  /** Every language-parity rule, honouring what the configuration allows to
    * stay the same.
    */
  def all(
      config: TranslationConfig = TranslationConfig.default
  ): Seq[TranslationRule] = Seq(
    TranslationRule(
      "i18n-lang-attribute",
      "the page declares the language it was rendered in"
    ) { (_, other) =>
      other.htmlLang match {
        case Some(declared)
            if declared.toLowerCase.startsWith(other.lang.code.toLowerCase) =>
          Nil
        case declared =>
          Seq(
            Violation(
              "i18n-lang-attribute",
              s"rendered in ${other.lang.code} but the page declares ${declared.getOrElse("no language")}",
              expected = Some(other.lang.code),
              actual = declared
            ).withHint(
              "WCAG 3.1.1 — a screen reader picks its voice from this attribute"
            )
          )
      }
    },
    TranslationRule(
      "i18n-same-ids",
      "every language renders the same elements"
    ) { (base, other) =>
      missingAndExtra(
        "i18n-same-ids",
        "element",
        idsOf(base),
        idsOf(other),
        other.lang
      )
    },
    TranslationRule(
      "i18n-same-links",
      "every language links to the same places"
    ) { (base, other) =>
      missingAndExtra(
        "i18n-same-links",
        "link target",
        hrefsOf(base),
        hrefsOf(other),
        other.lang
      )
    },
    TranslationRule(
      "i18n-same-controls",
      "every language collects the same fields"
    ) { (base, other) =>
      missingAndExtra(
        "i18n-same-controls",
        "form control",
        controlsOf(base),
        controlsOf(other),
        other.lang
      )
    },
    TranslationRule(
      "i18n-same-headings",
      "every language has the same heading structure"
    ) { (base, other) =>
      val baseLevels = base.headings.map(_._1)
      val otherLevels = other.headings.map(_._1)
      if (baseLevels == otherLevels) Nil
      else
        Seq(
          Violation(
            "i18n-same-headings",
            s"the heading levels in ${other.lang.code} do not follow the base language",
            expected = Some(baseLevels.mkString("h", ", h", "")),
            actual = Some(otherLevels.mkString("h", ", h", ""))
          ).withHint(
            "a translation that drops or adds a heading changes how the page is navigated"
          )
        )
    },
    TranslationRule(
      "i18n-nothing-lost",
      "no text present in the base language goes missing"
    ) { (base, other) =>
      val otherText = textByIdOf(other)
      textByIdOf(base).toSeq.sortBy(_._1).flatMap { case (id, baseText) =>
        otherText.get(id) match {
          case Some(text) if text.trim.nonEmpty => Nil
          case _ if baseText.trim.isEmpty       => Nil
          case _                                =>
            Seq(
              Violation(
                "i18n-nothing-lost",
                s"#$id says something in the base language but is empty in ${other.lang.code}",
                expected = Some(baseText),
                actual = Some("")
              )
            )
        }
      }
    },
    TranslationRule(
      "i18n-actually-translated",
      "the page is translated, not copied",
      Severity.Warning
    ) { (base, other) =>
      val baseText = textByIdOf(base)
      val otherText = textByIdOf(other)
      val copied = baseText.toSeq.sortBy(_._1).collect {
        case (id, text)
            if otherText
              .get(id)
              .contains(text) && worthTranslating(text, config) =>
          id -> text
      }
      copied.map { case (id, text) =>
        Violation(
          "i18n-actually-translated",
          s"#$id reads the same in ${other.lang.code} as in the base language",
          actual = Some(text)
        ).withHint("either it is untranslated, or add it to sameTextIsFine")
      }
    }
  )

  private def worthTranslating(
      text: String,
      config: TranslationConfig
  ): Boolean = {
    val trimmed = text.trim
    trimmed.length > 3 &&
    trimmed.exists(_.isLetter) &&
    !config.sameTextIsFine.contains(trimmed) &&
    !config.sameTextIsFine.exists(allowed => Text.same(allowed, trimmed))
  }

  private def idsOf(page: Page): Set[String] =
    page.document.getAllElements.asScala.map(_.id()).filter(_.nonEmpty).toSet

  private def hrefsOf(page: Page): Set[String] =
    page.links.elements.map(_.attr("href")).filter(_.nonEmpty).toSet

  private def controlsOf(page: Page): Set[String] =
    page.formControls.map(_.attr("name")).filter(_.nonEmpty).toSet

  private def textByIdOf(page: Page): Map[String, String] =
    page.document.getAllElements.asScala.collect {
      case e if e.id().nonEmpty => e.id() -> Text.normalise(e.ownText())
    }.toMap

  private def missingAndExtra(
      rule: String,
      what: String,
      base: Set[String],
      other: Set[String],
      lang: Lang
  ): Seq[Violation] = {
    val missing = (base -- other).toList.sorted
    val extra = (other -- base).toList.sorted
    val gone =
      if (missing.isEmpty) Nil
      else
        Seq(
          Violation(
            rule,
            s"${lang.code} is missing ${plural(what, missing.size)} the base language has",
            expected = Some(missing.mkString(", ")),
            actual = Some("absent")
          )
        )
    val added =
      if (extra.isEmpty) Nil
      else
        Seq(
          Violation(
            rule,
            s"${lang.code} has ${plural(what, extra.size)} the base language does not",
            expected = Some("absent"),
            actual = Some(extra.mkString(", "))
          )
        )
    gone ++ added
  }

  private def plural(what: String, n: Int): String =
    if (n == 1) s"1 $what" else s"$n ${what}s"

}
