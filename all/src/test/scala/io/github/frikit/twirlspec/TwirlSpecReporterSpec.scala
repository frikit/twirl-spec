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
import org.scalatest.events._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Args, ConfigMap, Filter, Suite}

import java.nio.file.{Files, Paths}

/** The report of a run, read from the files it writes. */
class TwirlSpecReporterSpec extends AnyWordSpec with Matchers {

  import CatalogueFixture.pageOf

  /** A spec with a rule that fails and one that only warns. */
  private abstract class Spec
      extends AnyWordSpec
      with Bilingual
      with PageCatalogue {
    override def standardsRules: Seq[Rule] = Seq(
      Rule("no-bad", "a page does not say bad")(page =>
        if (page.contains("bad"))
          Seq(Violation("no-bad", "it says <bad> & \"worse\""))
        else Nil
      ),
      Rule("soft", "a page says nothing soft", severity = Rule.Warning)(page =>
        if (page.contains("soft")) Seq(Violation("soft", "it says soft"))
        else Nil
      )
    )
  }

  private def ordinal = new Ordinal(1)

  private def report(suites: Seq[Suite], first: Option[Long])(
      end: Event
  ): (String, String) = {
    val dir = Files.createTempDirectory("twirl-spec-report")
    val reporter = new TwirlSpecReporter(dir)
    first.foreach(t =>
      reporter(RunStarting(ordinal, 0, ConfigMap.empty, timeStamp = t))
    )
    suites.foreach { s =>
      s.run(None, Args(reporter)).waitUntilCompleted()
      reporter(
        SuiteCompleted(
          ordinal,
          s.suiteName,
          s.suiteId,
          Some(s.getClass.getName)
        )
      )
    }
    reporter(end)
    (
      Files.readString(dir.resolve("index.html")),
      Files.readString(dir.resolve("report.md"))
    )
  }

  "The report" should {

    "say what ran, how it went and how long it took, by suite, page, rule and language" in {
      val spec = new Spec {
        catalogue(staticPage("Fine")(pageOf("<p>Fine</p>")))
        cataloguePage(staticPage("Bad")(pageOf("<p>bad, soft</p>"))) {
          "say something" in { info("a note"); succeed }
          "wait" in pending
          "give up" in cancel("no fixture")
          "be skipped" ignore {}
        }
        catalogue(
          staticPage("Excused")(pageOf("<p>bad</p>"))
            .exceptStandards("no-bad", "no-such")
            .exceptTranslation("i18n-same-links")
        )
        "Another test" should { "fail" in fail("on purpose") }
      }
      val funSuite = new org.scalatest.funsuite.AnyFunSuite {
        test("a test of another style")(succeed)
      }
      val aborted =
        SuiteAborted(ordinal, "it would not start", "Broken", "broken", None)
      val (html, md) =
        report(Seq(spec, funSuite), Some(System.currentTimeMillis() - 1500))(
          aborted
        )
      html must include("<code>no-such</code>")
      html must include("a test of another style")
      html must include("✗ Tests failed")
      html must include("1.5 s")
      // the catalogue's checks as rules by language
      Seq(
        "<th>en</th>",
        "<th>cy</th>",
        "<th>cy against en</th>",
        "renders <b>as it stands</b>",
        "<code>no-bad</code>"
      )
        .foreach(html must include(_))
      html must include("""class="warned"""")
      html must include("""class="passed excused"""")
      html must include("it says &lt;bad&gt; &amp; &quot;worse&quot;")
      // the page's own tests under it, and the rest under the suite
      Seq(
        "say something",
        "wait",
        "give up",
        "be skipped",
        "Another test should fail",
        "the suite itself"
      )
        .foreach(html must include(_))
      md must include("# ✗ Tests failed")
      md must include("## Failures")
      md must include("it would not start")
      // every row a table row, the failed suites first
      md.linesIterator.filter(_.contains(" | ")).foreach(_ must startWith("| "))
      md.linesIterator.find(_.startsWith("| Broken")) mustBe defined
      md.indexOf("| Broken") must be < md.indexOf("TwirlSpecReporterSpec")
    }

    "say a run passed, timed by its tests where the run's start was not seen" in {
      val spec = new Spec {
        catalogue(staticPage("Fine")(pageOf("<p>Fine</p>")))
      }
      val (html, md) = report(Seq(spec), None)(RunCompleted(ordinal))
      html must include("✓ Tests passed")
      html must not include "<details id=\"s0\" open"
      md must include("# ✓ Tests passed")
      md must not include "## Failures"
      Seq(RunStopped(ordinal), RunAborted(ordinal, "stopped", None)).foreach(
        end => report(Seq(spec), None)(end)._1 must include("✓ Tests passed")
      )
    }

    "read what a catalogue test checks from its name, and nothing from another's" in {
      def cell(page: String, text: String) =
        TwirlSpecReporter.cellOf(s"$page $text", text)
      cell("Name", "must render one begun, blank, in cy") mustBe
        Some(
          CheckCell(
            "Name",
            "render",
            "one begun, blank",
            "cy",
            "",
            excused = false
          )
        )
      cell("Name", "must meet one-h1 in en: a page has exactly one <h1>") mustBe
        Some(
          CheckCell(
            "Name",
            "standards",
            "one-h1",
            "en",
            "a page has exactly one <h1>",
            excused = false
          )
        )
      cell("Name", "must be excused from no-such in cy") mustBe
        Some(
          CheckCell("Name", "standards", "no-such", "cy", "", excused = true)
        )
      cell(
        "Name",
        "must translate consistently by i18n-same-ids, cy against en: a: b"
      ) mustBe
        Some(
          CheckCell(
            "Name",
            "translation",
            "i18n-same-ids",
            "cy against en",
            "a: b",
            excused = false
          )
        )
      cell(
        "Name",
        "must be excused from translating consistently by i18n-lang-attribute, en alone: x"
      ) mustBe
        Some(
          CheckCell(
            "Name",
            "translation",
            "i18n-lang-attribute",
            "en alone",
            "x",
            excused = true
          )
        )
      cell("Name", "must ask for a name, in English and Welsh") mustBe None
      TwirlSpecReporter.cellOf(
        "must meet one-h1 in en",
        "must meet one-h1 in en"
      ) mustBe None
    }

    "go where it is told, or to target/twirl-spec-report" in {
      val none: String => Option[String] = _ => None
      TwirlSpecReporter.configuredDirectory(
        Map("twirlspec.report.dir" -> "a").get,
        none
      ) mustBe Paths.get("a")
      TwirlSpecReporter.configuredDirectory(
        none,
        Map("TWIRLSPEC_REPORT_DIR" -> "b").get
      ) mustBe Paths.get("b")
      TwirlSpecReporter.configuredDirectory(none, none) mustBe Paths.get(
        "target/twirl-spec-report"
      )
      new TwirlSpecReporter() mustBe a[TwirlSpecReporter]
    }
  }

  "A catalogue's tests" should {

    "carry tags, to run a part of a catalogue" in {
      val spec = new Spec {
        catalogue(
          staticPage("Info")(pageOf("<p>Fine</p>")).exceptStandards("no-bad")
        )
      }
      spec.tags("Info must render as it stands, in en") mustBe
        Set("twirlspec.catalogue", "twirlspec.render", "twirlspec.lang.en")
      spec.tags(
        "Info must be excused from no-bad in cy: a page does not say bad"
      ) mustBe
        Set(
          "twirlspec.catalogue",
          "twirlspec.standards",
          "twirlspec.lang.cy",
          "twirlspec.rule.no-bad",
          "twirlspec.excused"
        )
      spec.tags.keys
        .filter(_.contains("i18n-same-ids"))
        .flatMap(spec.tags) mustBe
        Set(
          "twirlspec.catalogue",
          "twirlspec.translation",
          "twirlspec.lang.cy",
          "twirlspec.rule.i18n-same-ids"
        )
      var ran = List.empty[String]
      spec.run(
        None,
        Args(
          { case e: TestSucceeded => ran = e.testName :: ran; case _ => () },
          filter = Filter(tagsToInclude = Some(Set("twirlspec.lang.cy")))
        )
      )
      ran must not be empty
      all(ran) must (include(" in cy") or include("cy against en"))
    }
  }

}
