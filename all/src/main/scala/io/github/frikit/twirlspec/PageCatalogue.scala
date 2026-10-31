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
import org.scalatest.exceptions.{TestCanceledException, TestPendingException}
import org.scalatest.matchers.MatchResult
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Alerting, Suite}
import play.api.data.Form
import play.twirl.api.Html

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** One way a page can be rendered. */
final case class PageState(name: String, render: () => Html)

/** A page in a catalogue, and the rules it is excused from.
  *
  * @param label
  *   what its tests are called
  * @param states
  *   the ways it is rendered, at least one
  * @param standardsExcept
  *   the standards rules it is excused from
  * @param translationExcept
  *   the language comparison rules it is excused from
  */
final case class PageCase(
    label: String,
    states: List[PageState],
    standardsExcept: Seq[String] = Nil,
    translationExcept: Seq[String] = Nil
) {
  require(states.nonEmpty, s"$label has no states, so nothing would be checked")

  /** Excuse this page from these standards rules. */
  def exceptStandards(ruleIds: String*): PageCase =
    copy(standardsExcept = standardsExcept ++ ruleIds)

  /** Excuse this page from these language comparison rules. */
  def exceptTranslation(ruleIds: String*): PageCase =
    copy(translationExcept = translationExcept ++ ruleIds)

}

/** A service's pages as data, and the two checks every page needs, for any test
  * style: [[PageCatalogue]] is the same for `AnyWordSpec`, with its tests
  * written for it.
  *
  * Nothing is rendered until a check runs, so each state renders in the
  * language being checked. A check runs every state in every language, and its
  * failure reports each that went wrong, by state and language.
  */
trait PageCases extends TwirlSpec with I18nChecks {
  self: Suite with Alerting =>

  /** A page with one state. */
  def staticPage(label: String)(view: => Html): PageCase =
    PageCase(label, List(PageState("as it stands", () => view)))

  /** A page with a form: blank, with the errors of an empty submission, and
    * with those of each submission named here. A submission that gives no
    * errors fails its check, because that state would show nothing the blank
    * form does not.
    */
  def formPage[A](
      label: String,
      form: => Form[A],
      submissions: (String, Map[String, String])*
  )(view: Form[A] => Html): PageCase = {
    val submitted = ("with errors" -> Map.empty[String, String]) +: submissions
    PageCase(
      label,
      PageState("blank", () => view(form)) :: submitted.toList.map {
        case (state, data) =>
          PageState(
            state,
            () => {
              val bound = form.bind(data)
              require(
                bound.hasErrors,
                s"submitting $data gave no errors, so `$state` shows nothing the blank form does not"
              )
              view(bound)
            }
          )
      }
    )
  }

  /** Each of this page's states meets the standards in every language. */
  def checkStandards(page: PageCase): Unit =
    report(
      page,
      for {
        state <- page.states
        lang <- languages.toList
        problem <- problemsIn(
          s"${state.name}, in ${lang.code}",
          // The rules are built here, as they may read this language's messages.
          attempt(
            inLanguage(lang)(
              standards(page)(renderPage(state.render()))
            )
          )
        )
      } yield problem
    )

  /** Each of this page's states says the same thing in every language. A state
    * that does not render in one of them has nothing to compare.
    */
  def checkTranslation(page: PageCase): Unit =
    report(
      page,
      page.states.flatMap { state =>
        val rendered = languages.toList.map(lang =>
          lang -> attempt(inLanguage(lang)(renderPage(state.render())))
        )
        val crashes = rendered.collect { case (lang, Failure(e)) =>
          crash(s"${state.name}, in ${lang.code}", e)
        }
        if (crashes.nonEmpty) crashes
        else
          problemsIn(
            state.name,
            attempt(alike(page)(rendered.collect { case (_, Success(p)) => p }))
          )
      }
    )

  private def standards(page: PageCase) =
    if (page.standardsExcept.isEmpty) meetStandards
    else meetStandardsExcept(page.standardsExcept: _*)

  private def alike(page: PageCase) =
    if (page.translationExcept.isEmpty) translateConsistently
    else translateConsistentlyExcept(page.translationExcept: _*)

  /** What a block did, unless the test was cancelled or is pending: those are
    * not failures of the page, and stop the test as they would anywhere else.
    */
  private def attempt[A](block: => A): Try[A] =
    try Success(block)
    catch {
      case e @ (_: TestCanceledException | _: TestPendingException) => throw e
      case NonFatal(e) => Failure(e)
    }

  private final case class Problem(text: String, cause: Option[Throwable])

  private def crash(where: String, e: Throwable) =
    Problem(s"$where: $e", Some(e))

  private def problemsIn(
      where: String,
      verdict: Try[MatchResult]
  ): List[Problem] =
    verdict match {
      case Failure(e)                         => List(crash(where, e))
      case Success(result) if !result.matches =>
        List(Problem(s"$where: ${result.failureMessage}", None))
      case _ => Nil
    }

  private def report(page: PageCase, problems: List[Problem]): Unit =
    if (problems.nonEmpty) {
      val text = s"${page.label} failed ${problems.size} check(s):\n" +
        problems.map(_.text).mkString("\n")
      problems.flatMap(_.cause).headOption.fold(fail(text))(fail(text, _))
    }

}

/** A table of a service's pages, with two tests per page:
  *
  * {{{
  * class EveryPageSpec extends ViewSpecBase with PageCatalogue {
  *   catalogue(
  *     staticPage("Info")(inject[InfoView](draftId)),
  *     formPage("Name", nameForm)(inject[NameView](_, draftId)),
  *     formPage("Date of birth", dobForm, "part of a date" -> Map("value.day" -> "1"))(
  *       inject[DateOfBirthView](_, draftId)
  *     ),
  *     staticPage("Not found")(inject[PageNotFoundView]())
  *       .exceptTranslation("i18n-same-links")
  *   )
  * }
  * }}}
  *
  * It writes its tests in the `AnyWordSpec` style. For another style, mix in
  * [[PageCases]] and call its checks from tests of your own.
  */
trait PageCatalogue extends AnyWordSpecLike with PageCases {

  /** Two tests per page: the standards in every language, and the comparison
    * across the languages, each over all of the page's states.
    */
  def catalogue(pages: PageCase*): Unit = {
    require(pages.nonEmpty, "a catalogue of no pages would check nothing")
    pages.foreach { page =>
      page.label must {
        "meet the standards in every language, in each of its states" in
          checkStandards(page)
        "say the same thing in every language, in each of its states" in
          checkTranslation(page)
      }
    }
  }

}
