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

import io.github.frikit.twirlspec.i18n.{I18nChecks, TranslationStandards}
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{ExclusionRegistry, Rule}
import org.jsoup.nodes.Element
import org.scalatest.exceptions.{TestCanceledException, TestPendingException}
import org.scalatest.matchers.MatchResult
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Alerting, Suite, Tag}
import play.api.data.Form
import play.api.i18n.Lang
import play.twirl.api.Html

import scala.collection.mutable
import scala.jdk.CollectionConverters._
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

/** One of a page's checks on its own: its name, as a test's name reads after
  * the page's label and `must`, and the check.
  */
final case class PageCheck(name: String, run: () => Unit)

/** What one of a page's checks checks: its kind (`render`, `standards` or
  * `translation`), the state or rule, the language it ran in (`cy`, or
  * `cy against en` for a comparison), what the rule asks, and whether the page
  * is excused from it.
  */
private[twirlspec] final case class CheckCell(
    page: String,
    kind: String,
    check: String,
    language: String,
    about: String,
    excused: Boolean
) {

  /** The tags its test carries, to run a part of a catalogue with `-n`. */
  def tags: Seq[String] =
    Seq(
      "twirlspec.catalogue",
      s"twirlspec.$kind",
      s"twirlspec.lang.${language.takeWhile(_ != ' ')}"
    ) ++ Option.when(kind != "render")(s"twirlspec.rule.$check") ++
      Option.when(excused)("twirlspec.excused")

}

/** A service's pages as data, and the checks every page needs, for any test
  * style: all at once, by [[checkStandards]] and [[checkTranslation]], or one
  * by one, by [[checksOf]]. [[PageCatalogue]] is the same for `AnyWordSpec`,
  * with a test written for each check.
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
    * with those of each submission named here. The empty submission is the
    * blank form sent untouched, as a browser sends it: each control with the
    * value it renders with, so a field left empty arrives empty rather than
    * missing and meets the form's own message rather than Play's generic
    * `error.required`, and an unchecked or disabled control is left out. Where
    * it cannot tell which of several forms holds the fields, as when more than
    * one could send them or none is named after them, it sends nothing. A
    * submission that gives no errors fails its check, because that state would
    * show nothing the blank form does not.
    */
  def formPage[A](
      label: String,
      form: => Form[A],
      submissions: (String, Map[String, String])*
  )(view: Form[A] => Html): PageCase = {
    // Read when the state renders, in the language being checked, from a page
    // of its own, which no coverage record shares; bound as Play binds a
    // request, so a repeated name gives its first value, or a list for `[]`.
    val untouched = () => {
      val blank = form
      sentUntouched(Page(view(blank), currentLang, messages), blank) match {
        case Some(sent) =>
          val query =
            sent.toList.flatMap { case (k, vs) => vs.map(v => s"$k=$v") }
          (
            s"the blank form untouched (${query.mkString("&")})",
            blank.bindFromRequest(sent)
          )
        case None =>
          (
            "nothing, as which of the page's forms holds the fields cannot be told,",
            blank.bind(Map.empty[String, String])
          )
      }
    }
    val submitted: Seq[(String, () => (String, Form[A]))] =
      ("with errors" -> untouched) +: submissions.map { case (state, data) =>
        state -> (() => (data.toString, form.bind(data)))
      }
    PageCase(
      label,
      PageState("blank", () => view(form)) :: submitted.toList.map {
        case (state, submit) =>
          PageState(
            state,
            () => {
              val (sent, bound) = submit()
              require(
                bound.hasErrors,
                s"submitting $sent gave no errors, so `$state` shows nothing the blank form does not"
              )
              view(bound)
            }
          )
      }
    )
  }

  /** What a browser sends when the blank form is submitted untouched, each name
    * with every value it sends, in order: the controls of the page's one form,
    * or of the one form among several that holds this form's fields. A page
    * with no form sends all it has. None where which form holds the fields
    * cannot be told: more than one could send them, as which is sent is the
    * user's choice, or among several none is named after a mapping key, as a
    * list field's is not.
    */
  private def sentUntouched(
      page: Page,
      form: Form[_]
  ): Option[Map[String, Seq[String]]] = {
    // Sent or not: an unchecked radio sends nothing, but still names its form.
    val named = page.document
      .select("input[name], select[name], textarea[name]")
      .asScala
      .toList
    val forms = named.flatMap(page.formOwner).distinct
    val keys = form.mapping.mappings.map(_.key).filter(_.nonEmpty)
    def carries(e: Element) = {
      val name = e.attr("name")
      keys.exists(k =>
        name == k || name.startsWith(s"$k.") || name.startsWith(s"$k[")
      )
    }
    val holders =
      if (forms.size == 1) forms
      else named.filter(carries).flatMap(page.formOwner).distinct
    def sentBy(owned: Element => Boolean) =
      page.submittedControls
        .filter { case (e, _, _) => owned(e) }
        .groupMap(_._2)(_._3)
    holders match {
      case List(holder) => Some(sentBy(page.formOwner(_).contains(holder)))
      case _ if forms.isEmpty => Some(sentBy(_ => true))
      case _                  => None
    }
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

  /** Each of this page's checks on its own, for a test of its own, named for
    * the language it ran in: in each language the application is configured
    * for, that each state renders and that the page meets each standards rule,
    * and then, for each other language, that it translates consistently with
    * the first, English, by each comparison rule. A rule's check runs it over
    * every state, so together they check what [[checkStandards]] and
    * [[checkTranslation]] check, and a failure names the rule and the language.
    *
    * Each state renders once in each language, when a check first reads it, and
    * again in each run of the suite. Where it does not render, its own check
    * fails, and a rule's check that found nothing in the states that did is
    * cancelled, as it could not check them all. The check of a standards rule
    * the page is excused from runs the rule silently, as `meetStandardsExcept`
    * does, for [[unusedExclusions]], and fails where the spec runs no rule of
    * that id.
    *
    * The checks are named after `standardsRules` and `languages` when this is
    * called, so the rules and `applicationConfig` must be defined by then: by a
    * `def`, a `lazy val`, or a `val` declared above the call. As they run, the
    * checks build the rules again in each language: a state's check fails where
    * the spec runs a rule no check was named after, and a rule's check where
    * the spec does not run it. A page's states need names of their own, as
    * their checks are named after them.
    */
  def checksOf(page: PageCase): Seq[PageCheck] =
    describedChecks(page).map(_._1)

  /** [[checksOf]], each with what it checks, for its tags and the report. */
  private[twirlspec] def describedChecks(
      page: PageCase
  ): Seq[(PageCheck, CheckCell)] = {
    val rules = Try(standardsRules) match {
      case Success(read) if read != null && !read.contains(null) => read
      case read                                                  =>
        throw new IllegalStateException(
          "a page's checks are named after standardsRules, which could not be read when they were: " +
            "define the rules with a def or a lazy val, or declare them above the checks",
          read.failed.toOption.orNull
        )
    }
    val langs = Try(languages.toList) match {
      case Success(read) => read
      case Failure(e)    =>
        throw new IllegalStateException(
          "a page's checks are named after the languages the application is configured for, which could not be read when they were: " +
            "define applicationConfig with a def or a lazy val, or declare it above the checks",
          e
        )
    }
    val states = page.states.map(_.name)
    states.diff(states.distinct).headOption.foreach { name =>
      throw new IllegalArgumentException(
        s"${page.label} has two states called $name, which their checks are both named after"
      )
    }
    val excused = page.standardsExcept.distinct
    val named = (rules.map(_.id) ++ excused).distinct
    val renders = new Renders(page)
    val inEach = langs.flatMap { lang =>
      val rendering = page.states.indices.map { i =>
        PageCheck(
          s"render ${page.states(i).name}, in ${lang.code}",
          () => report(page, renders.failure(i, lang) ++ unnamed(named, lang))
        ) -> CheckCell(
          page.label,
          "render",
          page.states(i).name,
          lang.code,
          "",
          excused = false
        )
      }
      val standards = named.map { id =>
        val about = rules.filter(_.id == id).map(_.description).distinct
        val described =
          if (about.isEmpty) s"$id in ${lang.code}"
          else s"$id in ${lang.code}: ${about.mkString("; ")}"
        val check =
          if (excused.contains(id))
            PageCheck(
              s"be excused from $described",
              () => standardsCheck(page, id, lang, renders)(excuse)
            )
          else
            PageCheck(
              s"meet $described",
              () => standardsCheck(page, id, lang, renders)(meet)
            )
        check -> CheckCell(
          page.label,
          "standards",
          id,
          lang.code,
          about.mkString("; "),
          excused.contains(id)
        )
      }
      rendering ++ standards
    }
    val comparisons = TranslationStandards.all()
    val base = langs.head
    // With one language there is nothing to compare, but the page must still
    // declare its own, as checkTranslation asks.
    val pairs =
      if (langs.size > 1) langs.drop(1).map(_ -> comparisons)
      else List(base -> comparisons.filter(_.id == "i18n-lang-attribute"))
    val translation = for {
      (other, rules) <- pairs
      rule <- rules
    } yield {
      val against =
        if (other == base) s"${base.code} alone"
        else s"${other.code} against ${base.code}"
      val described = s"${rule.id}, $against: ${rule.description}"
      val excusedHere = page.translationExcept.contains(rule.id)
      val check =
        if (excusedHere)
          PageCheck(
            s"be excused from translating consistently by $described",
            () => ()
          )
        else
          PageCheck(
            s"translate consistently by $described",
            () =>
              translationCheck(
                page,
                rule.id,
                comparisons.map(_.id),
                langs.head,
                other,
                renders
              )
          )
      check -> CheckCell(
        page.label,
        "translation",
        rule.id,
        against,
        rule.description,
        excusedHere
      )
    }
    inEach ++ translation
  }

  /** A page's states, each rendered in a language when a check first reads it
    * in a run of the suite, which a run started since forgets.
    */
  private final class Renders(page: PageCase) {

    private val rendered = mutable.Map.empty[(Int, String), Try[Page]]
    private var run = SuiteRuns.current(PageCases.this)

    def apply(state: Int, lang: Lang): Try[Page] = {
      val now = SuiteRuns.current(PageCases.this)
      if (now != run) {
        rendered.clear()
        run = now
      }
      rendered.getOrElseUpdate(
        (state, lang.code),
        attempt(inLanguage(lang)(renderPage(page.states(state).render())))
      )
    }

    /** Why a state did not render in a language, if it did not. */
    def failure(state: Int, lang: Lang): List[Problem] =
      apply(state, lang).failed.toOption.toList.map(e =>
        crash(s"${page.states(state).name}, in ${lang.code}", e)
      )

  }

  /** Where, in this language, the spec runs a rule no check was named after, as
    * the rules were others when the checks were named.
    */
  private def unnamed(named: Seq[String], lang: Lang): List[Problem] =
    problemsIn(
      s"the rules, in ${lang.code}",
      attempt(inLanguage(lang) {
        val others = standardsRules.map(_.id).distinct.diff(named)
        MatchResult(
          others.isEmpty,
          s"the spec runs ${others.mkString(", ")} in ${lang.code}, which no check was named after, as the rules were others when the checks were named",
          "the spec ran only rules its checks were named after"
        )
      })
    )

  /** A standards rule's check, over each state in one language: `check` gets
    * the spec's rules of this id, built in the language, and the state's page.
    */
  private def standardsCheck(
      page: PageCase,
      id: String,
      lang: Lang,
      renders: Renders
  )(check: (Seq[Rule], Page) => MatchResult): Unit = {
    val unchecked = List.newBuilder[String]
    val problems = for {
      (state, i) <- page.states.zipWithIndex
      where = s"${state.name}, in ${lang.code}"
      problem <- renders(i, lang) match {
        case Failure(_) =>
          unchecked += where
          Nil
        case Success(rendered) =>
          problemsIn(
            where,
            attempt(inLanguage(lang) {
              val active = standardsRules
              if (active.exists(_.id == id))
                check(active.filter(_.id == id), rendered)
              else
                MatchResult(
                  false,
                  s"the spec runs no rule called $id in ${lang.code}, only ${active.map(_.id).distinct.sorted.mkString(", ")}",
                  s"the spec ran $id"
                )
            })
          )
      }
    } yield problem
    settle(page, id, problems, unchecked.result())
  }

  private def meet(rules: Seq[Rule], page: Page): MatchResult =
    verdict(
      checkPage(page, Seq(Rule.expectation(rules))),
      "page satisfied every check, but was expected not to"
    )

  /** Run the rules the page is excused from silently, recording for
    * [[unusedExclusions]] whether they found anything, as `meetStandardsExcept`
    * does.
    */
  private def excuse(rules: Seq[Rule], page: Page): MatchResult = {
    rules.foreach(r =>
      ExclusionRegistry.record(
        getClass.getName,
        r.id,
        page.withRecordingPaused(r.check(page)).nonEmpty
      )
    )
    MatchResult(true, "", "the page was excused from the rule")
  }

  /** A comparison rule's check, over each state, of one language against the
    * base.
    */
  private def translationCheck(
      page: PageCase,
      id: String,
      ids: Seq[String],
      base: Lang,
      other: Lang,
      renders: Renders
  ): Unit = {
    val unchecked = List.newBuilder[String]
    val problems = page.states.zipWithIndex.flatMap { case (state, i) =>
      val rendered = List(base, other).map(lang => renders(i, lang))
      val where =
        if (other == base) s"${state.name}, ${base.code} alone"
        else s"${state.name}, ${other.code} against ${base.code}"
      if (rendered.exists(_.isFailure)) {
        unchecked += where
        Nil
      } else
        problemsIn(
          where,
          attempt(
            translateConsistentlyExcept(ids.filterNot(_ == id): _*)(
              rendered.map(_.get)
            )
          )
        )
    }
    settle(page, id, problems, unchecked.result())
  }

  /** Fail on the problems; failing none, cancel where the check could not run,
    * which the state's own check reports.
    */
  private def settle(
      page: PageCase,
      id: String,
      problems: List[Problem],
      unchecked: List[String]
  ): Unit = {
    report(page, problems)
    if (unchecked.nonEmpty)
      cancel(
        s"${page.label} was not checked against $id where it did not render: ${unchecked.mkString("; ")}"
      )
  }

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

/** A table of a service's pages, with a test for each check on each page, so
  * the report lists what was checked:
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
  * {{{
  * Name
  * - must render blank, in en
  * - must render with errors, in en
  * - must meet one-h1 in en: a page has exactly one <h1>
  * - must render blank, in cy
  * - must meet one-h1 in cy: a page has exactly one <h1>
  * - must translate consistently by i18n-same-links, cy against en: every language links to the same places
  * }}}
  *
  * It writes its tests in the `AnyWordSpec` style. For another style, mix in
  * [[PageCases]] and call its checks from tests of your own.
  */
trait PageCatalogue extends AnyWordSpecLike with PageCases {

  /** A test for each of a page's checks, under its label, as [[checksOf]] lists
    * them: in each language, that each state renders and each rule over every
    * state, then each comparison of a language with English.
    */
  def catalogue(pages: PageCase*): Unit = {
    require(pages.nonEmpty, "a catalogue of no pages would check nothing")
    pages.foreach(cataloguePage(_)(()))
  }

  /** A page's tests under its one label: a test for each of its checks, as
    * [[catalogue]] writes them, then the spec's own, so the report does not
    * name the page twice:
    *
    * {{{
    * cataloguePage(formPage("Name", nameForm)(inject[NameView](_, draftId))) {
    *   "ask for the name" in { ... }
    * }
    * }}}
    */
  def cataloguePage(page: PageCase)(tests: => Unit): Unit = {
    // Outside the scope, where ScalaTest would hide a refusal behind its own.
    // Through checksOf, as a spec may override it; the tags by each check's name.
    val checks = checksOf(page)
    val tagged = describedChecks(page).map { case (c, cell) =>
      c.name -> cell.tags
    }.toMap
    page.label must {
      checks.foreach { check =>
        val tags = tagged.getOrElse(check.name, Seq("twirlspec.catalogue"))
        check.name.taggedAs(Tag(tags.head), tags.tail.map(Tag(_)): _*) in
          check.run()
      }
      tests
    }
  }

}
