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

import io.github.frikit.twirlspec.expect.Expectation
import io.github.frikit.twirlspec.i18n.I18nChecks
import io.github.frikit.twirlspec.page.{CoverageRegistry, Page}
import io.github.frikit.twirlspec.render.{CsrfToken, SharedApplication}
import io.github.frikit.twirlspec.standards.ExclusionRegistry
import munit.{FunSuite, Location, Tag, TestOptions}
import org.junit.internal.AssumptionViolatedException
import org.scalatest.exceptions.{TestCanceledException, TestPendingException}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.Matcher
import play.api.Application
import play.api.data.Form
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.{AnyContentAsEmpty, Cookie, Request}
import play.api.test.FakeRequest
import play.twirl.api.Html

import scala.reflect.ClassTag
import scala.util.DynamicVariable

/** twirl-spec for MUnit: what `TwirlSpec` gives a ScalaTest spec, for an MUnit
  * suite. An application shared across the JVM, `inject[View]`, languages, the
  * expectation DSL, assertions over the same matchers, and the page catalogue
  * as a test for each check:
  *
  * {{{
  * class NameViewSuite extends TwirlSuite with HmrcServiceChecks {
  *   private val view = inject[NameView]
  *   private val form = Form(single("value" -> text))
  *
  *   cataloguePage(formPage("NameView", form)(view(_))) {
  *     test("NameView asks for the name") {
  *       assertDisplays(renderPage(view(form)), heading("name.heading"))
  *     }
  *   }
  * }
  * }}}
  *
  * Its rules are those its rule traits give, as in a ScalaTest spec, and its
  * failures are MUnit's, with the same messages. ScalaTest stays on the class
  * path, as the rules and matchers are built on it, but nothing here runs it.
  */
trait TwirlSuite extends FunSuite with TwirlSpecDsl with I18nChecks {

  /** Extra configuration for this suite's application. */
  def applicationConfig: Map[String, Any] = Map.empty

  /** The application this suite renders with, shared across the JVM with every
    * suite, of either kind, that uses the same [[applicationConfig]].
    */
  lazy val app: Application = SharedApplication(applicationConfig)

  /** An instance from the application's injector. */
  def inject[A](using tag: ClassTag[A]): A = app.injector.instanceOf[A]

  /** The application's `MessagesApi`. */
  lazy val messagesApiInstance: MessagesApi = inject[MessagesApi]

  /** The application's `MessagesApi`, implicitly. */
  implicit def messagesApi: MessagesApi = messagesApiInstance

  /** The English `Lang`, the base language. */
  val english: Lang = Lang("en")

  /** Every language the application is configured for, English first. */
  def languages: Seq[Lang] = {
    // An empty list is Play's default: its one default language, the base.
    val configured =
      app.configuration.get[Seq[String]]("play.i18n.langs").map(Lang(_))
    if (configured.isEmpty) Seq(baseLanguage)
    else configured.sortBy(l => if (l.language == "en") 0 else 1)
  }

  private val currentLanguage = new DynamicVariable[Option[Lang]](None)

  private lazy val baseLanguage: Lang =
    configuredMessages(english, messagesApiInstance).lang

  /** The language the current block runs in: English unless inside
    * [[inLanguage]].
    */
  def currentLang: Lang = currentLanguage.value.getOrElse(baseLanguage)

  /** `Messages` for the current language, implicitly. */
  implicit def messages: Messages =
    messagesApiInstance.preferred(Seq(currentLang))

  /** A GET request for the current language, implicitly, signed with a CSRF
    * token where Play's filters are on the class path, as `TwirlSpec`'s is.
    */
  implicit def request: Request[AnyContentAsEmpty.type] =
    CsrfToken.add(
      FakeRequest("GET", "/").withCookies(
        Cookie(messagesApiInstance.langCookieName, currentLang.code)
      ),
      CsrfToken.available
    )

  /** Run a block in a language the application is configured for. */
  def inLanguage[A](lang: Lang)(block: => A): A = {
    val chosen = configuredMessages(lang, messagesApiInstance).lang
    currentLanguage.withValue(Some(chosen))(block)
  }

  /** Run the same block once per configured language. */
  def inEachLanguage(block: Lang => Unit): Unit =
    languages.foreach(lang => inLanguage(lang)(block(lang)))

  /** Render a view in the current language. */
  def renderPage(html: => Html): Page = render(html, currentLang, messages)

  /** Render the same view in every configured language. */
  def renderInEachLanguage(html: => Html): Seq[Page] =
    languages.map(lang => inLanguage(lang)(render(html, lang, messages)))

  /** A template with injected dependencies, from the application, for the
    * entry-point checks, as `TwirlSpec` gives it.
    */
  override protected def templateInstance(template: Class[_]): AnyRef =
    app.injector.instanceOf(template).asInstanceOf[AnyRef]

  /** Any of this library's matchers as an MUnit assertion, failing with the
    * matcher's own message: `assertMatches(page, assertEverything)`.
    */
  def assertMatches[A](value: A, matcher: Matcher[A])(using
      loc: Location
  ): Unit = {
    val result = matcher(value)
    if (!result.matches) fail(result.failureMessage)
  }

  /** The page shows all of this, and holds to every rule the suite runs. */
  def assertDisplays(page: Page, expectations: Expectation*)(using
      loc: Location
  ): Unit = assertMatches(page, display(expectations: _*))

  /** The page holds to every rule the suite runs. */
  def assertMeetsStandards(page: Page)(using loc: Location): Unit =
    assertMatches(page, meetStandards)

  /** The pages, one per language, say the same thing. */
  def assertTranslatesConsistently(pages: Seq[Page])(using
      loc: Location
  ): Unit = assertMatches(pages, translateConsistently)

  /** What a rule's warning says on a pass, for the catalogue's host. */
  private[twirlspec] def warn(message: String): Unit = alertHook(message)

  // The catalogue's checks run on a ScalaTest PageCases that takes its
  // application, languages, rules and records from this suite.
  private var hosted = Option.empty[CatalogueHost]

  private[twirlspec] def host: CatalogueHost = hosted.getOrElse {
    val made = new CatalogueHost(this)
    hosted = Some(made)
    made
  }

  /** A page with one state, as `PageCases.staticPage` makes it. */
  def staticPage(label: String)(view: => Html): PageCase =
    host.staticPage(label)(skippable(view))

  /** A page with a form, blank and with the errors of its submissions, as
    * `PageCases.formPage` makes it.
    */
  def formPage[A](
      label: String,
      form: => Form[A],
      submissions: (String, Map[String, String])*
  )(view: Form[A] => Html): PageCase =
    host.formPage(label, skippable(form), submissions: _*)(f =>
      skippable(view(f))
    )

  // MUnit's own assume in a fixture skips its checks: the catalogue would take
  // JUnit's assumption for a crash, so it reaches it as a cancellation.
  private def skippable[A](block: => A): A =
    try block
    catch {
      case e: AssumptionViolatedException =>
        throw new TestCanceledException(e.getMessage, 0)
    }

  /** A test for each of each page's checks, as the ScalaTest catalogue writes
    * them: "NameView must meet one-h1 in cy: ...", tagged by kind, language and
    * rule.
    */
  def catalogue(pages: PageCase*): Unit = {
    require(pages.nonEmpty, "a catalogue of no pages would check nothing")
    pages.foreach(cataloguePage(_)(()))
  }

  /** A page's checks, then the suite's own tests of it. */
  def cataloguePage(page: PageCase)(tests: => Unit): Unit = {
    host.checksOf(page).foreach { check =>
      val name = new TestOptions(s"${page.label} must ${check.name}")
      val tagged = CheckCells
        .of(check)
        .toSeq
        .flatMap(_.tags)
        .foldLeft(name)((options, tag) => options.tag(new Tag(tag)))
      test(tagged)(run(check))
    }
    tests
  }

  // A check a fixture cancelled, or left pending, is skipped, as in ScalaTest.
  private def run(check: PageCheck)(using loc: Location): Unit =
    try check.run()
    catch {
      case e: TestCanceledException => assume(false, e.getMessage)
      case _: TestPendingException  => assume(false, "pending")
    }

  /** Each run starts without the coverage and exclusion records of the last,
    * and drops them once it has finished, as `SuiteLifecycle` does a ScalaTest
    * spec's; the catalogue's pages render again. A suite that overrides
    * `beforeAll` or `afterAll` calls `super`, as with ScalaTest's
    * `BeforeAndAfterAll`, or a second run of it in the same JVM reads the
    * records and pages of the first.
    */
  override def beforeAll(): Unit = {
    forgetRun()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    forgetRun()
  }

  private def forgetRun(): Unit = {
    CoverageRegistry.forget(getClass.getName)
    ExclusionRegistry.forget(getClass.getName)
    hosted.foreach(SuiteRuns.turn(_))
  }

}

/** The ScalaTest `PageCases` an MUnit suite's catalogue runs on: never run
  * itself, it takes everything a check reads from the suite, so a page renders
  * in the suite's language and an excused rule is recorded for the suite.
  */
private[twirlspec] final class CatalogueHost(suite: TwirlSuite)
    extends AnyFunSuite
    with PageCases {

  override lazy val app: Application = suite.app
  override lazy val messagesApiInstance: MessagesApi = suite.messagesApiInstance
  override def languages: Seq[Lang] = suite.languages
  override def currentLang: Lang = suite.currentLang
  override implicit def messages: Messages = suite.messages
  override implicit def request: Request[AnyContentAsEmpty.type] =
    suite.request
  override def inLanguage[A](lang: Lang)(block: => A): A =
    suite.inLanguage(lang)(block)
  override def renderPage(html: => Html): Page = suite.renderPage(html)
  override def standardsRules: Seq[standards.Rule] = suite.standardsRules
  override def failOnWarnings: Boolean = suite.failOnWarnings
  override def reportWarnings: Boolean = suite.reportWarnings
  override def translationConfig: i18n.TranslationConfig =
    suite.translationConfig
  override protected def alertHook(message: String): Unit = suite.warn(message)
  override private[twirlspec] def recordKey: String = suite.getClass.getName

}
