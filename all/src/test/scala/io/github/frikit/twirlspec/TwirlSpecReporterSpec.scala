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
import org.scalatest.{Args, ConfigMap, Filter, Reporter, Suite}

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

  /** The report of a run, with the events `run` sends after its start. */
  private def report(first: Option[Long])(
      run: Reporter => Unit
  ): (String, String) = {
    val dir = Files.createTempDirectory("twirl-spec-report")
    val reporter = new TwirlSpecReporter(dir)
    first.foreach(t =>
      reporter(RunStarting(ordinal, 0, ConfigMap.empty, timeStamp = t))
    )
    run(reporter)
    (
      Files.readString(dir.resolve("index.html")),
      Files.readString(dir.resolve("report.md"))
    )
  }

  /** A suite run as ScalaTest's runner runs it, with the time it reports. */
  private def runSuite(
      reporter: Reporter,
      s: Suite,
      starting: Boolean,
      duration: Option[Long]
  ): Unit = {
    val className = Some(s.getClass.getName)
    if (starting)
      reporter(SuiteStarting(ordinal, s.suiteName, s.suiteId, className))
    s.run(None, Args(reporter)).waitUntilCompleted()
    reporter(
      SuiteCompleted(ordinal, s.suiteName, s.suiteId, className, duration)
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
      val (html, md) = report(Some(System.currentTimeMillis() - 1500)) { r =>
        runSuite(r, spec, starting = true, duration = Some(2500))
        runSuite(r, funSuite, starting = true, duration = None)
        r(SuiteStarting(ordinal, "Broken", "broken", None))
        r(SuiteAborted(ordinal, "it would not start", "Broken", "broken", None))
        r(RunCompleted(ordinal))
      }
      html must include("<code>no-such</code>")
      html must include("a test of another style")
      html must include("✗ Tests failed")
      // the run's time, and a suite's as ScalaTest reports it
      html must include("1.5 s")
      html must include("2.5 s")
      // an aborted suite is no test, but fails the run
      html must include("The suite aborted")
      html must not include "the suite itself"
      md must include("| Broken | 0 | aborted |")
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
        "Another test should fail"
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
      def ran(end: Event) = report(None) { r =>
        runSuite(r, spec, starting = false, duration = None)
        r(end)
      }
      val (html, md) = ran(RunCompleted(ordinal))
      html must include("✓ Tests passed")
      html must not include "<details id=\"s0\" open"
      md must include("# ✓ Tests passed")
      md must not include "## Failures"
      // A run that did not finish did not pass, whatever its tests did.
      ran(RunStopped(ordinal))._1 must include("✗ Run stopped")
      ran(RunAborted(ordinal, "out of memory", None))._2 must include(
        "# ✗ Run aborted: out of memory"
      )
    }

    "keep apart suites that share a name, and a warning to its own test" in {
      def alert(suite: String, test: Option[String]) = AlertProvided(
        ordinal,
        "twirl-spec: 1 warning(s)",
        Some(NameInfo("IndexSpec", suite, None, test))
      )
      def passed(suite: String) =
        TestSucceeded(ordinal, "IndexSpec", suite, None, "t", "t", Vector.empty)
      val (_, md) = report(None) { r =>
        r(alert("a.IndexSpec", Some("t")))
        r(alert("a.IndexSpec", None))
        r(AlertProvided(ordinal, "someone else's", None))
        r(passed("b.IndexSpec"))
        r(passed("a.IndexSpec"))
        r(passed("a.IndexSpec"))
        r(RunCompleted(ordinal))
      }
      md must include("| IndexSpec | 1 | 1 ✓ |")
      md must include("| IndexSpec | 2 | 1 ! 1 ✓ |")
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
