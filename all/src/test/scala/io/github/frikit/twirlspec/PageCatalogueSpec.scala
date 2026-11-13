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
import org.scalatest.events.{
  Event,
  ScopeOpened,
  TestCanceled,
  TestFailed,
  TestSucceeded
}
import org.scalatest.exceptions.{
  TestCanceledException,
  TestFailedException,
  TestPendingException
}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Args, Reporter, Suite}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.twirl.api.Html

import scala.collection.mutable

private object CatalogueFixture {

  /** A page in the current language, with a heading that differs by language.
    */
  def pageOf(body: String)(implicit messages: Messages): Html = {
    val code = messages.lang.code
    Html(
      s"""<!DOCTYPE html><html lang="$code"><head><title>$code</title></head><body><main><h1>Heading $code</h1>$body</main></body></html>"""
    )
  }

}

/** A catalogue, measured by running one and reading what it reports. */
class PageCatalogueSpec extends AnyWordSpec with Matchers {

  import CatalogueFixture.pageOf

  /** A spec with one rule of its own: a page must not say "bad". */
  private abstract class Spec
      extends AnyWordSpec
      with Bilingual
      with PageCatalogue {

    override def standardsRules: Seq[Rule] = Seq(
      Rule("no-bad", "a page does not say bad")(page =>
        if (page.contains("bad")) Seq(Violation("no-bad", "it says bad"))
        else Nil
      )
    )

    val nameForm: Form[String] = Form(single("name" -> nonEmptyText))

  }

  private final case class Outcome(
      passed: Seq[String],
      failed: Seq[String],
      cancelled: Seq[String],
      scopes: Seq[String]
  )

  private def run(suite: Suite): Outcome = {
    val passed = mutable.ListBuffer.empty[String]
    val failed = mutable.ListBuffer.empty[String]
    val cancelled = mutable.ListBuffer.empty[String]
    val scopes = mutable.ListBuffer.empty[String]
    val reporter = new Reporter {
      def apply(event: Event): Unit = event match {
        case e: TestSucceeded => passed += e.testName
        case e: TestFailed    => failed += s"${e.testName}: ${e.message}"
        case e: TestCanceled  => cancelled += s"${e.testName}: ${e.message}"
        case e: ScopeOpened   => scopes += e.message
        case _                => ()
      }
    }
    suite.run(None, Args(reporter)).waitUntilCompleted()
    Outcome(passed.toSeq, failed.toSeq, cancelled.toSeq, scopes.toSeq)
  }

  private val comparisons = TranslationStandards.all()

  "A catalogue" should {

    "give each page a test for each state and each rule, named after them" in {
      val spec = new Spec {
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      }
      run(spec).passed mustBe Seq(
        "Info must render as it stands, in every language",
        "Info must meet no-bad: a page does not say bad"
      ) ++ comparisons.map(r =>
        s"Info must translate consistently by ${r.id}: ${r.description}"
      )
    }

    "put a page's own tests under its one label, after its checks" in {
      val spec = new Spec {
        cataloguePage(staticPage("Info")(pageOf("<p>Fine</p>"))) {
          "say it is fine" in succeed
        }
      }
      val outcome = run(spec)
      outcome.scopes mustBe Seq("Info")
      outcome.passed.head mustBe "Info must render as it stands, in every language"
      outcome.passed.last mustBe "Info must say it is fine"
    }

    "render nothing until a test runs, then each state once in each language" in {
      var renders = 0
      val spec = new Spec {
        catalogue(staticPage("Info") {
          renders += 1
          pageOf("<p>Fine</p>")
        })
      }
      renders mustBe 0
      run(spec)
      // However many tests read them.
      renders mustBe 2
    }

    "render its pages again when the same suite runs again" in {
      var body = "<p>bad</p>"
      val spec = new Spec {
        catalogue(staticPage("Again")(pageOf(body)))
      }
      run(spec).failed must have size 1
      body = "<p>Fine</p>"
      run(spec).failed mustBe empty
    }

    "render a run's pages once, whatever another instance of its spec does" in {
      var renders = 0
      var meanwhile = Option.empty[Suite]
      def spec(): Spec = new Spec {
        catalogue(staticPage("Overlap") {
          renders += 1
          // Another instance of the same class runs in the middle of this run.
          meanwhile.foreach { other =>
            meanwhile = None
            PageCatalogueSpec.this.run(other)
          }
          pageOf("<p>Fine</p>")
        })
      }
      val first = spec()
      meanwhile = Some(spec())
      run(first).failed mustBe empty
      // Each instance renders its page once in each language.
      renders mustBe 4
    }

    "render a page in the language under test, and say which that was" in {
      val spec = new Spec {
        catalogue(staticPage("Welsh only")(pageOf(messages.lang.code match {
          case "cy" => "<p>bad</p>"
          case _    => "<p>Fine</p>"
        })))
      }
      val failures = run(spec).failed.mkString
      failures must include("Welsh only must meet no-bad")
      failures must include("as it stands, in cy: ")
      failures must not include "as it stands, in en: "
      failures must include("it says bad")
    }

    "render a form blank, with the errors of an empty submission, and with each named" in {
      var seen = List.empty[Map[String, String]]
      val spec = new Spec {
        catalogue(
          formPage("Name", nameForm, "too long" -> Map("other" -> "x"))(
            form => {
              seen = form.data :: seen
              pageOf(if (form.hasErrors) "<p>Problem</p>" else "<p>Fine</p>")
            }
          )
        )
      }
      run(spec).failed mustBe empty
      seen.distinct must contain allOf (
        Map.empty[String, String],
        Map("other" -> "x")
      )
    }

    "submit the blank form as a browser does, with every field empty" in {
      val named: Form[String] =
        Form(single("name" -> text.verifying("name.missing", _.nonEmpty)))
      def errorsShown(
          body: String,
          of: Form[_] = named,
          fragment: Boolean = false
      ): List[String] = {
        var errors = List.empty[String]
        val spec = new Spec {
          catalogue(formPage("Name", of) { form =>
            errors = form.errors.map(_.message).toList ++ errors
            if (fragment) Html(body) else pageOf(body)
          })
        }
        run(spec)
        errors.distinct
      }
      val other =
        """<form action="/other"><input name="name" value="Ada" aria-label="Other"></form>"""
      val field = """<input name="name" aria-label="Name">"""
      // Play answers a field that is missing with its generic error.required;
      // a browser sends the field empty, which meets the form's own message.
      errorsShown(
        s"""<form method="post"><input type="hidden" name="csrfToken" value="t">$field</form>"""
      ) mustBe List("name.missing")
      // a name sent twice binds its first value, as Play reads a request
      errorsShown(
        s"""<form method="post">$field<input name="name" value="Ada" aria-label="Again"></form>"""
      ) mustBe List("name.missing")
      // a page's one form sends its list's one blank item, whatever its key
      errorsShown(
        """<form method="post"><input name="names[]" aria-label="Name"></form>""",
        Form(single("names" -> list(nonEmptyText)))
      ) mustBe List("error.required")
      // a fragment, a component with no form of its own, sends all it has
      errorsShown(field, fragment = true) mustBe List("name.missing")
      // of several forms, the one that holds the fields, wherever it stands
      errorsShown(
        s"""<form action="/search"><input name="q" value="x" aria-label="Search"></form><form method="post">$field</form>"""
      ) mustBe List("name.missing")
      // two that could both send them leave it to the user, so none is sent,
      // an unchecked radio counting, as it still names its form
      errorsShown(s"""$other<form method="post">$field</form>""") mustBe
        List("error.required")
      errorsShown(
        s"""<form method="post"><input type="radio" name="name" value="a" aria-label="A"></form>$other"""
      ) mustBe List("error.required")
    }

    "report every state and language that failed, not just the first" in {
      val spec = new Spec {
        catalogue(formPage("Name", nameForm)(_ => pageOf("<p>bad</p>")))
      }
      val failures = run(spec).failed.mkString
      failures must include("Name failed 4 check(s)")
      Seq(
        "blank, in en: ",
        "blank, in cy: ",
        "with errors, in en: ",
        "with errors, in cy: "
      ).foreach(failures must include(_))
    }

    "compare every state, not just those before the first that differs" in {
      val spec = new Spec {
        catalogue(
          formPage("Name", nameForm)(_ =>
            pageOf(
              if (messages.lang.code == "cy")
                """<a href="/only-welsh">Welsh</a>"""
              else "<p>Fine</p>"
            )
          )
        )
      }
      val failures = run(spec).failed.mkString
      failures must include("translate consistently by i18n-same-links")
      failures must include("blank: ")
      failures must include("with errors: ")
    }

    "name the state and language of a crash, and keep it as the cause" in {
      val boom = new IllegalStateException("boom")
      val spec = new Spec {}
      val page = spec.staticPage("Crash")(
        if (spec.messages.lang.code == "cy") throw boom
        else pageOf("<p>Fine</p>")(spec.messages)
      )
      Seq[PageCase => Unit](spec.checkStandards, spec.checkTranslation)
        .foreach { check =>
          val failure = intercept[TestFailedException](check(page))
          failure.getMessage must include(
            "as it stands, in cy: java.lang.IllegalStateException: boom"
          )
          failure.getMessage must not include "in en"
          failure.getCause mustBe boom
        }
    }

    "fail a state's render where it crashes, and cancel what it left unchecked" in {
      def crashing(body: String) = run(new Spec {
        catalogue(staticPage("Crash") {
          if (messages.lang.code == "cy")
            throw new IllegalStateException("boom")
          else pageOf(body)
        })
      })
      val rendered = "Crash must render as it stands, in every language"
      val fine = crashing("<p>Fine</p>")
      fine.failed.map(_.takeWhile(_ != ':')) mustBe Seq(rendered)
      fine.failed.head must include(
        "as it stands, in cy: java.lang.IllegalStateException: boom"
      )
      fine.cancelled must have size (1L + comparisons.size)
      fine.cancelled.head must include(
        "Crash was not checked against no-bad where it did not render: as it stands, in cy"
      )
      fine.cancelled(1) must include(
        "Crash was not checked against i18n-lang-attribute where it did not render: as it stands"
      )
      // A rule that failed where the page rendered fails, rather than cancels.
      crashing("<p>bad</p>").failed.map(_.takeWhile(_ != ':')) mustBe Seq(
        rendered,
        "Crash must meet no-bad"
      )
    }

    "let a cancelled or a pending test stay so, rather than fail it" in {
      val spec = new Spec {}
      val cancelled = spec.staticPage("Info")(cancel("fixture unavailable"))
      val unfinished = spec.staticPage("Info") { pending; Html("") }
      intercept[TestCanceledException](spec.checkStandards(cancelled))
      intercept[TestCanceledException](spec.checkTranslation(cancelled))
      intercept[TestPendingException](spec.checkStandards(unfinished))
      intercept[TestPendingException](spec.checkTranslation(unfinished))
      spec
        .checksOf(cancelled)
        .foreach(check => intercept[TestCanceledException](check.run()))
      spec
        .checksOf(unfinished)
        .foreach(check => intercept[TestPendingException](check.run()))
    }

    "check each rule alone, and rules that share an id together" in {
      def says(id: String, word: String) =
        Rule(id, s"nor does it say $word")(page =>
          if (page.contains(word)) Seq(Violation(id, s"it says $word"))
          else Nil
        )
      val spec = new Spec {
        override def standardsRules: Seq[Rule] =
          super.standardsRules ++ Seq(
            says("no-worse", "worse"),
            says("no-bad", "awful")
          )
        catalogue(staticPage("Awful")(pageOf("<p>awful</p>")))
      }
      val outcome = run(spec)
      outcome.passed must contain(
        "Awful must meet no-worse: nor does it say worse"
      )
      outcome.failed.map(_.takeWhile(_ != ':')) mustBe Seq(
        "Awful must meet no-bad"
      )
      outcome.failed.head must include(
        "Awful must meet no-bad: a page does not say bad; nor does it say awful"
      )
    }

    "fail one rule at a time on what a page's checks fail all at once" in {
      val spec = new AnyWordSpec
        with Bilingual
        with PageCatalogue
        with GovukServiceChecks {
        // Then a rule that only warns fails both ways.
        override def failOnWarnings: Boolean = true
      }
      val page = spec.staticPage("Rough")(
        pageOf(
          if (spec.messages.lang.code == "cy")
            """<img src="a.png"><a href="/only-welsh">Cymraeg</a>"""
          else """<img src="a.png"><h1>Again</h1>"""
        )(spec.messages)
      )
      val violated = """(?m)^\s*x (\S+) — """.r
      def atOnce(check: PageCase => Unit) = violated
        .findAllMatchIn(intercept[TestFailedException](check(page)).getMessage)
        .map(_.group(1))
        .toSet
      def oneByOne(prefix: String) = spec
        .checksOf(page)
        .filter(c => c.name.startsWith(prefix))
        .filter(c => scala.util.Try(c.run()).isFailure)
        .map(_.name.stripPrefix(prefix).takeWhile(_ != ':'))
        .toSet
      val standards = atOnce(spec.checkStandards)
      standards.size must be > 3
      oneByOne("meet ") mustBe standards
      val translation = atOnce(spec.checkTranslation)
      translation must not be empty
      oneByOne("translate consistently by ") mustBe translation
    }

    "fail where a language's rules are not those its tests were named after" in {
      val spec = new Spec {
        override def standardsRules: Seq[Rule] = {
          val only =
            if (messages.lang.code == "cy") "welsh-only" else "english-only"
          super.standardsRules :+ Rule(only, s"a rule $only")(_ => Nil)
        }
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      }
      val failures = run(spec).failed.mkString
      failures must include(
        "Info must render as it stands, in every language: "
      )
      failures must include(
        "the rules, in cy: the spec runs welsh-only in cy, which no check was named after"
      )
      failures must include("Info must meet english-only: ")
      failures must include("the spec runs no rule called english-only in cy")
      // Even where no rule had a test of its own.
      val welshOnly = new Spec {
        override def standardsRules: Seq[Rule] =
          if (messages.lang.code == "cy") super.standardsRules else Nil
        catalogue(staticPage("Info")(pageOf("<p>bad</p>")))
      }
      run(welshOnly).failed.mkString must include(
        "the spec runs no-bad in cy, which no check was named after"
      )
    }

    "refuse rules it cannot read as it names the tests" in {
      val unread =
        "a page's checks are named after standardsRules, which could not be read when they were: " +
          "define the rules with a def or a lazy val, or declare them above the checks"
      the[IllegalStateException] thrownBy new Spec {
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
        override val standardsRules: Seq[Rule] = Nil
      } must have message unread
      // As a val declared below, among the rules, leaves.
      the[IllegalStateException] thrownBy new Spec {
        override def standardsRules: Seq[Rule] = Seq(null)
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      } must have message unread
      val boom = new IllegalStateException("boom")
      val failing = the[IllegalStateException] thrownBy new Spec {
        override def standardsRules: Seq[Rule] = throw boom
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      }
      failing.getCause mustBe boom
    }

    "refuse two states of a page with one name, as their tests would share it" in {
      the[IllegalArgumentException] thrownBy new Spec {
        catalogue(
          formPage("Name", nameForm, "with errors" -> Map("name" -> ""))(_ =>
            pageOf("<p>Fine</p>")
          )
        )
      } must have message
        "Name has two states called with errors, which their checks are both named after"
    }

    "build a spec's rules in the language under test" in {
      val spec = new Spec {
        // As a rule built from the service's name in messages would.
        override def standardsRules: Seq[Rule] = {
          val heading = s"Heading ${messages.lang.code}"
          Seq(
            Rule("says-its-language", "the heading is in the page's language")(
              page =>
                if (page.contains(heading)) Nil
                else Seq(Violation("says-its-language", s"no '$heading'"))
            )
          )
        }
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      }
      run(spec).failed mustBe empty
    }

    "fail a submission that gives no errors, rather than test nothing new" in {
      val spec = new Spec {
        catalogue(
          formPage("Name", nameForm, "valid" -> Map("name" -> "Ada"))(_ =>
            pageOf("<p>Fine</p>")
          )
        )
      }
      run(spec).failed.mkString must include(
        "shows nothing the blank form does not"
      )
    }

    "fail a form whose empty submission gives no errors, naming it" in {
      val spec = new Spec {
        val anything: Form[Option[String]] =
          Form(single("name" -> optional(text)))
        catalogue(formPage("Optional", anything)(_ => pageOf("<p>Fine</p>")))
      }
      run(spec).failed.mkString must include("`with errors`")
    }

    "excuse a page from the standards it names, and no others" in {
      val bad = new Spec {
        catalogue(staticPage("Bad")(pageOf("<p>bad</p>")))
      }
      run(bad).failed must have size 1
      val excused = new Spec {
        catalogue(
          staticPage("Bad")(pageOf("<p>bad</p>")).exceptStandards("no-bad")
        )
      }
      val outcome = run(excused)
      outcome.failed mustBe empty
      outcome.passed must contain(
        "Bad must be excused from no-bad: a page does not say bad"
      )
      // The rule still runs, silently, so a needless excuse is known; read
      // outside a run, as a suite forgets its exclusions once it has run.
      def unused(spec: Spec, body: String) = {
        val page = spec
          .staticPage("Excused")(pageOf(body)(spec.messages))
          .exceptStandards("no-bad")
        spec.checksOf(page).foreach(_.run())
        spec.unusedExclusions
      }
      unused(new Spec {}, "<p>bad</p>") mustBe empty
      unused(new Spec {}, "<p>Fine</p>") mustBe Seq("no-bad")
      // And all at once.
      val spec = new Spec {}
      spec.checkStandards(
        spec
          .staticPage("Bad")(pageOf("<p>bad</p>")(spec.messages))
          .exceptStandards("no-bad")
      )
      val misspelt = new Spec {
        catalogue(
          staticPage("Bad")(pageOf("<p>Fine</p>")).exceptStandards("no-bda")
        )
      }
      val failures = run(misspelt).failed.mkString
      failures must include("Bad must be excused from no-bda: ")
      failures must include(
        "the spec runs no rule called no-bda in en, only no-bad"
      )
    }

    "excuse a page from the language comparison rules it names" in {
      def linked(code: String) =
        s"""<a href="/help-$code-page">Help $code</a>"""
      val differing = new Spec {
        catalogue(staticPage("Help")(pageOf(linked(messages.lang.code))))
      }
      run(differing).failed.mkString must include("i18n-same-links")
      val excused = new Spec {
        catalogue(
          staticPage("Help")(pageOf(linked(messages.lang.code)))
            .exceptTranslation("i18n-same-links")
        )
      }
      val outcome = run(excused)
      outcome.failed mustBe empty
      outcome.passed must contain(
        "Help must be excused from translating consistently by i18n-same-links: every language links to the same places"
      )
      // And all at once.
      val spec = new Spec {}
      spec.checkTranslation(
        spec
          .staticPage("Help")(
            pageOf(linked(spec.messages.lang.code))(spec.messages)
          )
          .exceptTranslation("i18n-same-links")
      )
    }

    "refuse a page with no states, and a catalogue with no pages" in {
      the[IllegalArgumentException] thrownBy PageCase(
        "Info",
        Nil
      ) must have message
        "requirement failed: Info has no states, so nothing would be checked"
      the[IllegalArgumentException] thrownBy new Spec {
        catalogue()
      } must have message
        "requirement failed: a catalogue of no pages would check nothing"
    }
  }

}

/** A spec base on should matchers, which the catalogue must not fight with. */
class PageCatalogueOnShouldMatchersSpec
    extends AnyWordSpec
    with org.scalatest.matchers.should.Matchers
    with Bilingual
    with PageCatalogue {

  catalogue(staticPage("A page")(CatalogueFixture.pageOf("<p>Fine</p>")))

  "A test of its own" should {
    "keep its own matchers" in { languages should have size 2 }
  }

}

/** The same checks from another test style, with the tests written by hand. */
class PageCasesInAnotherStyleSpec
    extends AnyFunSuite
    with Matchers
    with Bilingual
    with PageCases {

  private val page =
    staticPage("A page")(CatalogueFixture.pageOf("<p>Fine</p>"))

  test("the standards")(checkStandards(page))
  test("the comparison")(checkTranslation(page))
  checksOf(page).foreach(check =>
    test(s"A page must ${check.name}")(check.run())
  )

}
