/*
 * Copyright 2026 Victor Osipov
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

import io.github.frikit.twirlspec.TwirlSpecDsl
import io.github.frikit.twirlspec.expect.{CheckReport, Violation}
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.i18n.Lang

/** Compares the same view rendered in several languages.
  *
  * {{{
  * renderInEachLanguage(memberNameView(form, edit = false)) must translateConsistently
  * }}}
  *
  * The first page is the base by default, or nominate one with `basedOn`.
  * Nothing here knows about any particular language.
  */
trait I18nChecks { self: TwirlSpecDsl =>

  /** What a page may legitimately keep the same between languages. */
  def translationConfig: TranslationConfig = TranslationConfig.default

  /** Every language renders the same page as the base language, differing only in words. */
  def translateConsistently: Matcher[Seq[Page]] = matcher(None, Set.empty)

  /** As `translateConsistently`, without the named rules. */
  def translateConsistentlyExcept(ruleIds: String*): Matcher[Seq[Page]] = matcher(None, ruleIds.toSet)

  /** Measure the other languages against this one rather than against the first page given. */
  def basedOn(lang: Lang): Matcher[Seq[Page]] = matcher(Some(lang), Set.empty)

  /** The differences, for a spec that would rather report than fail. */
  def translationDifferences(pages: Seq[Page], base: Option[Lang] = None): Seq[Violation] =
    compare(pages, base, Set.empty)

  private def compare(pages: Seq[Page], base: Option[Lang], excluded: Set[String]): Seq[Violation] = {
    val rules = TranslationStandards.all(translationConfig).filterNot(r => excluded.contains(r.id))
    basePage(pages, base) match {
      case None       => Nil
      case Some(from) => pages.filterNot(_ eq from).flatMap(to => rules.flatMap(_.check(from, to)))
    }
  }

  private def basePage(pages: Seq[Page], base: Option[Lang]): Option[Page] =
    base.flatMap(l => pages.find(_.lang.code == l.code)).orElse(pages.headOption)

  private def matcher(base: Option[Lang], excluded: Set[String]): Matcher[Seq[Page]] =
    new Matcher[Seq[Page]] {
      def apply(pages: Seq[Page]): MatchResult = pages.headOption match {
        case None        =>
          MatchResult(false, "no pages were given to compare", "no pages were given to compare")
        case Some(first) =>
          val report = CheckReport(first, compare(pages, base, excluded), "translation")
          MatchResult(
            report.passed,
            report.message,
            "every language matched the base, but was expected not to"
          )
      }
    }

}
