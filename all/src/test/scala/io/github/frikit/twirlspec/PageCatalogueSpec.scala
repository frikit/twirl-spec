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
import io.github.frikit.twirlspec.standards.Rule
import org.scalatest.events.{Event, TestFailed, TestSucceeded}
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

  private final case class Outcome(passed: Seq[String], failed: Seq[String])

  private def run(suite: Suite): Outcome = {
    val passed = mutable.ListBuffer.empty[String]
    val failed = mutable.ListBuffer.empty[String]
    val reporter = new Reporter {
      def apply(event: Event): Unit = event match {
        case e: TestSucceeded => passed += e.testName
        case e: TestFailed    => failed += s"${e.testName}: ${e.message}"
        case _                => ()
      }
    }
    suite.run(None, Args(reporter)).waitUntilCompleted()
    Outcome(passed.toSeq, failed.toSeq)
  }

  private val standardsTest =
    "meet the standards in every language, in each of its states"
  private val translationTest =
    "say the same thing in every language, in each of its states"

  "A catalogue" should {

    "give each page two tests, named after it" in {
      val spec = new Spec {
        catalogue(staticPage("Info")(pageOf("<p>Fine</p>")))
      }
      run(spec).passed must contain theSameElementsAs Seq(
        s"Info must $standardsTest",
        s"Info must $translationTest"
      )
    }

    "render nothing until a test runs, then each state in each language" in {
      var renders = 0
      val spec = new Spec {
        catalogue(staticPage("Info") {
          renders += 1
          pageOf("<p>Fine</p>")
        })
      }
      renders mustBe 0
      run(spec)
      // Two languages for the standards, and two for the comparison.
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
      failures must include(standardsTest)
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
      failures must include(translationTest)
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

    "let a cancelled or a pending test stay so, rather than fail it" in {
      val spec = new Spec {}
      val cancelled = spec.staticPage("Info")(cancel("fixture unavailable"))
      val unfinished = spec.staticPage("Info") { pending; Html("") }
      intercept[TestCanceledException](spec.checkStandards(cancelled))
      intercept[TestCanceledException](spec.checkTranslation(cancelled))
      intercept[TestPendingException](spec.checkStandards(unfinished))
      intercept[TestPendingException](spec.checkTranslation(unfinished))
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
      run(excused).failed mustBe empty
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
      run(excused).failed mustBe empty
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

}
