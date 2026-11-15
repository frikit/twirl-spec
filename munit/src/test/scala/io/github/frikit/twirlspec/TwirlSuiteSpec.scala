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

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.i18n.TranslationStandards
import io.github.frikit.twirlspec.standards.Rule
import munit.MUnitRunner
import org.junit.runner.Description
import org.junit.runner.notification.{Failure, RunListener, RunNotifier}
import org.scalatest.exceptions.{TestCanceledException, TestPendingException}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Lang
import play.twirl.api.Html

import scala.collection.mutable

/** A bilingual MUnit suite with one rule of its own, which fails a page that
  * says "bad", and one that only warns of a page that says "soft".
  */
abstract class Bilingual extends TwirlSuite {

  override def applicationConfig: Map[String, Any] =
    Map("play.i18n.langs" -> Seq("en", "cy"))

  override def standardsRules: Seq[Rule] = Seq(
    Rule("no-bad", "a page does not say bad")(page =>
      if (page.contains("bad")) Seq(Violation("no-bad", "it says bad")) else Nil
    ),
    Rule("soft", "a page says nothing soft", severity = Rule.Warning)(page =>
      if (page.contains("soft")) Seq(Violation("soft", "it says soft")) else Nil
    )
  )

  /** A page in the language being rendered. */
  def pageOf(body: String): Html = {
    val code = messages.lang.code
    Html(
      s"""<!DOCTYPE html><html lang="$code"><head><title>$code</title></head><body><main><h1>Heading $code</h1>$body</main></body></html>"""
    )
  }

  val nameForm: Form[String] = Form(single("name" -> nonEmptyText))

}

/** A suite asking for an injected instance, as the entry-point checks do. */
final class Injecting extends Bilingual {
  def instance: AnyRef = templateInstance(classOf[play.api.i18n.MessagesApi])
}

/** MUnit suites, run as MUnit runs them, read by what they report. */
class TwirlSuiteSpec extends AnyWordSpec with Matchers {

  private final case class Outcome(
      passed: Seq[String],
      failed: Seq[String],
      skipped: Seq[String]
  )

  private def run(suite: munit.Suite): Outcome = {
    val started = mutable.ListBuffer.empty[String]
    val failed = mutable.ListBuffer.empty[String]
    val skipped = mutable.ListBuffer.empty[String]
    val notifier = new RunNotifier
    notifier.addListener(new RunListener {
      override def testStarted(d: Description): Unit =
        started += d.getMethodName
      override def testFailure(f: Failure): Unit =
        failed += s"${f.getDescription.getMethodName}: ${f.getMessage}"
      override def testAssumptionFailure(f: Failure): Unit =
        skipped += s"${f.getDescription.getMethodName}: ${f.getMessage}"
    })
    new MUnitRunner(suite.getClass.asInstanceOf[Class[munit.Suite]], suite)
      .run(notifier)
    def name(s: String) = s.takeWhile(_ != ':')
    val done = (failed ++ skipped).map(name).toSet
    Outcome(
      started.filterNot(s => done(name(s))).toSeq,
      failed.toSeq,
      skipped.toSeq
    )
  }

  private val comparisons = TranslationStandards.all()

  "An MUnit suite's catalogue" should {

    "name and tag a test for each check, as the ScalaTest catalogue does" in {
      val suite = new Bilingual {
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      }
      val names = Seq("en", "cy").flatMap(lang =>
        Seq(
          s"Info must render as it stands, in $lang",
          s"Info must meet no-bad in $lang: a page does not say bad",
          s"Info must meet soft in $lang: a page says nothing soft"
        )
      ) ++ comparisons.map(r =>
        s"Info must translate consistently by ${r.id}, cy against en: ${r.description}"
      )
      suite.munitTests().map(_.name) mustBe names
      suite
        .munitTests()
        .find(_.name.startsWith("Info must meet no-bad in cy"))
        .map(_.tags.map(_.value)) mustBe Some(
        Set(
          "twirlspec.catalogue",
          "twirlspec.standards",
          "twirlspec.lang.cy",
          "twirlspec.rule.no-bad"
        )
      )
      run(suite).passed mustBe names
    }

    "render each check in the suite's language, so a page's Welsh is judged in Welsh" in {
      val suite = new Bilingual {
        catalogue(staticPage("Welsh only")(pageOf(messages.lang.code match {
          case "cy" => "<p>bad</p>"
          case _    => "<p>Fine</p>"
        })))
      }
      val failed = run(suite).failed
      failed.map(_.takeWhile(_ != ':')) mustBe Seq(
        "Welsh only must meet no-bad in cy"
      )
      failed.head must include("as it stands, in cy: ")
    }

    "render a form blank and with errors, and run the suite's own tests after its checks" in {
      var seen = List.empty[Map[String, String]]
      val suite = new Bilingual {
        cataloguePage(formPage("Name", nameForm) { form =>
          seen = form.data :: seen
          // a field the untouched submission sends empty
          pageOf(
            """<form method="post"><input name="name" aria-label="Name"></form>"""
          )
        }) {
          test("Name shows a heading") {
            assertDisplays(renderPage(pageOf("<p>Fine</p>")))
          }
        }
      }
      val outcome = run(suite)
      outcome.failed mustBe empty
      outcome.passed.last mustBe "Name shows a heading"
      outcome.passed must contain("Name must render with errors, in cy")
      seen must contain(Map("name" -> ""))
    }

    "skip a check whose fixture cancels it, leaves it pending or assumes as MUnit does" in {
      val suite = new Bilingual {
        catalogue(
          staticPage("Cancelled")(
            throw new TestCanceledException("no fixture", 0)
          ),
          staticPage("Pending")(throw new TestPendingException),
          staticPage("Assumed")({ assume(false, "no data"); Html("") }),
          formPage("Assumed form", { assume(false, "no form"); nameForm })(_ =>
            Html("")
          )
        )
      }
      val outcome = run(suite)
      outcome.failed mustBe empty
      val perPage = 2 + 2 * 2 + comparisons.size
      outcome.skipped.size must be >= 4 * perPage
      Seq("no fixture", "pending", "no data", "no form").foreach(reason =>
        outcome.skipped.exists(_.contains(reason)) mustBe true
      )
    }

    "record an excused rule for the suite, not for the catalogue's host" in {
      var fired, needless = Seq.empty[String]
      val firing = new Bilingual {
        catalogue(
          staticPage("Bad")(pageOf("<p>bad</p>")).exceptStandards("no-bad")
        )
        test("zz record") { fired = unusedExclusions }
      }
      val unneeded = new Bilingual {
        catalogue(
          staticPage("Fine")(pageOf("<p>Fine</p>")).exceptStandards("no-bad")
        )
        test("zz record") { needless = unusedExclusions }
      }
      run(firing).failed mustBe empty
      run(unneeded).failed mustBe empty
      fired mustBe empty
      needless mustBe Seq("no-bad")
    }

    "render its pages again when the same suite runs again" in {
      var body = "<p>bad</p>"
      val suite = new Bilingual {
        catalogue(staticPage("Again")(pageOf(body)))
      }
      run(suite).failed must have size 2
      body = "<p>Fine</p>"
      run(suite).failed mustBe empty
    }

    "refuse a catalogue of no pages" in {
      the[IllegalArgumentException] thrownBy new Bilingual {
        catalogue()
      } must have message
        "requirement failed: a catalogue of no pages would check nothing"
    }
  }

  "An MUnit suite" should {

    "assert with the library's matchers, failing with their messages" in {
      val suite = new Bilingual {
        test("standards") {
          assertMeetsStandards(renderPage(pageOf("<p>bad, soft</p>")))
        }
        test("translation") {
          assertTranslatesConsistently(
            renderInEachLanguage(
              pageOf(
                s"""<a href="/help-${messages.lang.code}">Help</a>"""
              )
            )
          )
        }
        test("passes") {
          assertDisplays(renderPage(pageOf("<p>Fine</p>")))
          assertTranslatesConsistently(
            renderInEachLanguage(pageOf("<p>Fine</p>"))
          )
        }
      }
      val outcome = run(suite)
      outcome.passed mustBe Seq("passes")
      outcome.failed.mkString must include("it says bad")
      outcome.failed.mkString must include("i18n-same-links")
    }

    "render in each language, and share its application with the catalogue's host" in {
      val suite = new Bilingual {}
      val welsh = Lang("cy")
      suite.languages.map(_.code) mustBe Seq("en", "cy")
      var seen = List.empty[String]
      suite.inEachLanguage(lang => seen = suite.messages.lang.code :: seen)
      seen.reverse mustBe Seq("en", "cy")
      suite.inject[play.api.i18n.MessagesApi] mustBe suite.messagesApi
      val host = suite.host
      host.app mustBe theSameInstanceAs(suite.app)
      host.messagesApiInstance mustBe theSameInstanceAs(
        suite.messagesApiInstance
      )
      suite.inLanguage(welsh) {
        host.currentLang.code mustBe "cy"
        host.messages.lang.code mustBe "cy"
        host.request.cookies
          .get(suite.messagesApi.langCookieName)
          .map(_.value) mustBe Some("cy")
      }
      host.renderPage(suite.pageOf("")).lang.code mustBe "en"
      suite.host mustBe theSameInstanceAs(host)
      // a suite that asks for nothing gets Play's defaults
      new TwirlSuite {}.applicationConfig mustBe empty
      // a template with injected dependencies comes from the application
      new Injecting().instance mustBe theSameInstanceAs(
        suite.messagesApiInstance
      )
    }
  }

}
