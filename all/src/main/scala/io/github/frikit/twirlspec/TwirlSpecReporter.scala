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

import org.scalatest.Reporter
import org.scalatest.events._

import java.nio.file.{Files, Path, Paths}
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import scala.collection.mutable

/** A report of a test run, as a page to read and as Markdown for a CI summary:
  * how many tests passed, failed, warned and were cancelled, how long they
  * took, and each suite's tests, with a catalogue's checks laid out as a table
  * of rules by language and every failure's message beside it.
  *
  * Add it to a run, in `build.sbt`:
  *
  * {{{
  * Test / testOptions += Tests.Argument(
  *   TestFrameworks.ScalaTest, "-C", "io.github.frikit.twirlspec.TwirlSpecReporter"
  * )
  * }}}
  *
  * It writes `index.html` and `report.md` to `target/twirl-spec-report`, or to
  * the directory the `twirlspec.report.dir` system property or the
  * `TWIRLSPEC_REPORT_DIR` environment variable names, as each suite finishes
  * and when the run does, and then says where.
  */
class TwirlSpecReporter(directory: Path) extends Reporter {

  /** The reporter ScalaTest's `-C` makes, writing where it is told to. */
  def this() =
    this(TwirlSpecReporter.configuredDirectory(sys.props.get, sys.env.get))

  private val results = mutable.ArrayBuffer.empty[TwirlSpecReporter.Line]
  private var started = Option.empty[Long]
  private val warned = mutable.Set.empty[(Option[String], Option[String])]

  def apply(event: Event): Unit = synchronized {
    event match {
      case e: RunStarting => started = Some(e.timeStamp)
      // A rule's warnings come as an alert while its test runs.
      case e: AlertProvided if e.message.startsWith("twirl-spec: ") =>
        e.nameInfo.foreach(n => warned += ((n.suiteClassName, n.testName)))
      case e: TestSucceeded =>
        val key = (e.suiteClassName, Some(e.testName))
        val status = if (warned(key)) "warned" else "passed"
        add(e.suiteName, e.testName, e.testText, status, e.duration, "")
      case e: TestFailed =>
        add(
          e.suiteName,
          e.testName,
          e.testText,
          "failed",
          e.duration,
          e.message
        )
      case e: TestCanceled =>
        add(
          e.suiteName,
          e.testName,
          e.testText,
          "cancelled",
          e.duration,
          e.message
        )
      case e: TestPending =>
        add(e.suiteName, e.testName, e.testText, "pending", e.duration, "")
      case e: TestIgnored =>
        add(e.suiteName, e.testName, e.testText, "ignored", None, "")
      case e: SuiteAborted =>
        val test = "the suite itself"
        add(e.suiteName, test, test, "failed", e.duration, e.message)
        write(e.timeStamp)
      case e: SuiteCompleted                               => write(e.timeStamp)
      case _: RunCompleted | _: RunStopped | _: RunAborted =>
        write(event.timeStamp)
        println(
          s"twirl-spec report: ${directory.resolve("index.html").toAbsolutePath}"
        )
      case _ => ()
    }
  }

  private def add(
      suite: String,
      test: String,
      text: String,
      status: String,
      duration: Option[Long],
      message: String
  ): Unit =
    results += TwirlSpecReporter.Line(
      suite,
      test,
      status,
      duration.getOrElse(0L),
      message,
      TwirlSpecReporter.cellOf(test, text)
    )

  private def write(now: Long): Unit = {
    val took = started.fold(results.map(_.millis).sum)(now - _)
    val run =
      TwirlSpecReporter.Run(results.toList, started.getOrElse(now), took)
    Files.createDirectories(directory)
    Files.writeString(directory.resolve("index.html"), run.html)
    Files.writeString(directory.resolve("report.md"), run.markdown)
  }

}

/** How [[TwirlSpecReporter]] lays a run out. */
object TwirlSpecReporter {

  /** Where the report goes: the system property, the environment variable, or
    * `target/twirl-spec-report`.
    */
  private[twirlspec] def configuredDirectory(
      property: String => Option[String],
      environment: String => Option[String]
  ): Path =
    Paths.get(
      property("twirlspec.report.dir")
        .orElse(environment("TWIRLSPEC_REPORT_DIR"))
        .getOrElse("target/twirl-spec-report")
    )

  private val Render = "must render (.+), in (\\S+)".r
  private val Translation =
    "must (translate|be excused from translating) consistently by (\\S+), (.+?): (.*)".r
  private val Standard =
    "must (meet|be excused from) (\\S+) in (\\S+)(?:: (.*))?".r

  /** What a catalogue test checks, read from its name as [[PageCatalogue]]
    * writes it: its page is the scope its text follows, and its text says the
    * check, the rule and the language. Read from the event rather than shared
    * with the catalogue, as the reporter can be loaded apart from the suites.
    */
  private[twirlspec] def cellOf(
      test: String,
      text: String
  ): Option[CheckCell] = {
    // WordSpec's text leaves out the scope's verb, which the name has.
    val page = test.stripSuffix(s" $text").stripSuffix(" must")
    if (page == test) None
    else
      s"must ${text.stripPrefix("must ")}" match {
        case Render(state, language) =>
          Some(CheckCell(page, "render", state, language, "", excused = false))
        case Translation(verb, id, against, about) =>
          Some(
            CheckCell(
              page,
              "translation",
              id,
              against,
              about,
              verb != "translate"
            )
          )
        case Standard(verb, id, language, about) =>
          Some(
            CheckCell(
              page,
              "standards",
              id,
              language,
              Option(about).getOrElse(""),
              verb != "meet"
            )
          )
        case _ => None
      }
  }

  private[twirlspec] final case class Line(
      suite: String,
      test: String,
      status: String,
      millis: Long,
      message: String,
      cell: Option[CheckCell]
  )

  private val order =
    List("failed", "warned", "cancelled", "pending", "ignored", "passed")

  private val symbol = Map(
    "passed" -> "✓",
    "warned" -> "!",
    "failed" -> "✗",
    "cancelled" -> "–",
    "pending" -> "…",
    "ignored" -> "○"
  )

  private[twirlspec] def time(millis: Long): String =
    if (millis < 1000) s"$millis ms" else f"${millis / 1000.0}%.1f s"

  private def esc(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  private def counts(lines: Seq[Line]): Map[String, Int] =
    lines.groupBy(_.status).view.mapValues(_.size).toMap

  private def tally(c: Map[String, Int]): String =
    order.filter(c.contains).map(s => s"${c(s)} ${symbol(s)}").mkString(" ")

  private def spent(lines: Seq[Line]): String = time(lines.map(_.millis).sum)

  private[twirlspec] final case class Run(
      lines: List[Line],
      started: Long,
      took: Long
  ) {

    private def ofSuite(suite: String) = lines.filter(_.suite == suite)

    // Those that failed first, then by name.
    private val suites = lines
      .map(_.suite)
      .distinct
      .sortBy(s => (!ofSuite(s).exists(_.status == "failed"), s))

    private val total = counts(lines)

    private val headline =
      if (total.contains("failed")) "✗ Tests failed" else "✓ Tests passed"

    private val when = DateTimeFormatter
      .ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault())
      .format(Instant.ofEpochMilli(started))

    private val summary =
      s"${lines.size} tests in ${suites.size} suites · ${time(took)} · started $when"

    def markdown: String = {
      val rows = suites.map { s =>
        val in = ofSuite(s)
        s"| $s | ${in.size} | ${tally(counts(in))} | ${spent(in)} |"
      }
      val failures = lines.filter(_.status == "failed").map { l =>
        s"- **${l.suite}**: ${l.test}\n\n  ```\n  ${l.message.replace("\n", "\n  ")}\n  ```"
      }
      // Joined, not stripMargin'd, as a row's own `|` would be stripped.
      (Seq(
        s"# $headline",
        "",
        s"$summary: ${tally(total)}",
        "",
        "| Suite | Tests | Result | Time |",
        "|---|---:|---|---:|"
      ) ++ rows).mkString("", "\n", "\n") +
        (if (failures.isEmpty) ""
         else s"\n## Failures\n\n${failures.mkString("\n")}\n")
    }

    /** The tests run, each with its status, how long it took and its message.
      */
    private def tests(in: List[Line], prefix: String): String =
      if (in.isEmpty) ""
      else
        in.map { l =>
          val message =
            if (l.message.isEmpty) "" else s"<pre>${esc(l.message)}</pre>"
          s"""<tr class="${l.status}"><td class="status">${symbol(
              l.status
            )}</td><td>${esc(
              l.test.stripPrefix(prefix)
            )}$message</td><td class="time">${time(
              l.millis
            )}</td></tr>"""
        }.mkString("<table class=\"tests\">\n", "\n", "</table>")

    /** A page's catalogue checks as rules by language, then its own tests. */
    private def page(label: String, in: List[Line]): String = {
      val checks = in.filter(_.cell.exists(_.page == label))
      val cells = checks.flatMap(l => l.cell.map(_ -> l))
      val languages = cells.map(_._1.language).distinct
      val rows = cells.map { case (c, _) =>
        (c.kind, c.check, c.about)
      }.distinct
      val body = rows.map { case (kind, check, about) =>
        val name =
          if (kind == "render") s"renders <b>${esc(check)}</b>"
          else s"<code>${esc(check)}</code>"
        val row = languages.map { language =>
          cells
            .find { case (c, _) =>
              c.kind == kind && c.check == check && c.language == language
            }
            .fold("<td></td>") { case (c, l) =>
              // An excuse that failed, for a rule the spec does not run, fails.
              val text =
                if (c.excused && l.status == "passed") "excused"
                else symbol(l.status)
              val note =
                if (l.message.isEmpty) "" else s" — ${l.message.take(300)}"
              s"""<td class="${l.status}${
                  if (c.excused) " excused"
                  else ""
                }" title="${esc(time(l.millis) + note)}">$text</td>"""
            }
        }
        s"""<tr><th scope="row">$name<span class="about">${esc(
            about
          )}</span></th>${row.mkString}</tr>"""
      }
      val own = in.filter(l => l.cell.isEmpty && l.test.startsWith(s"$label "))
      val failing = checks
        .filter(l => l.status == "failed" || l.status == "cancelled")
        .map(l =>
          s"""<div class="message ${l.status}"><b>${esc(
              l.test
            )}</b><pre>${esc(l.message)}</pre></div>"""
        )
      val heads = languages.map(l => s"<th>${esc(l)}</th>").mkString
      Seq(
        s"""<h3>${esc(label)} <span class="tally">${tally(
            counts(checks)
          )} · ${spent(checks)}</span></h3>""",
        s"""<div class="scroll"><table class="matrix"><thead><tr><th>Check</th>$heads</tr></thead>""",
        s"<tbody>${body.mkString("\n")}</tbody></table></div>",
        failing.mkString("\n"),
        tests(own, s"$label ")
      ).mkString("\n")
    }

    private def suite(name: String, index: Int): String = {
      val in = ofSuite(name)
      val c = counts(in)
      val pages = in.flatMap(_.cell).map(_.page).distinct
      val rest = in.filter(l =>
        l.cell.isEmpty && !pages.exists(p => l.test.startsWith(s"$p "))
      )
      Seq(
        s"""<details id="s$index"${
            if (c.contains("failed")) " open" else ""
          }>""",
        s"""<summary><span class="name">${esc(
            name
          )}</span><span class="tally">${tally(
            c
          )}</span><span class="time">${spent(in)}</span></summary>""",
        pages.map(page(_, in)).mkString("\n"),
        tests(rest, ""),
        "</details>"
      ).mkString("\n")
    }

    def html: String = {
      val pills = order
        .filter(total.contains)
        .map(s => s"""<span class="pill $s">${total(s)} $s</span>""")
        .mkString(" ")
      val index = suites.zipWithIndex.map { case (s, i) =>
        val in = ofSuite(s)
        val c = counts(in)
        s"""<tr class="${
            if (c.contains("failed")) "failed"
            else "passed"
          }"><td><a href="#s$i">${esc(
            s
          )}</a></td><td class="num">${in.size}</td><td>${tally(
            c
          )}</td><td class="time">${spent(in)}</td></tr>"""
      }
      s"""<!DOCTYPE html>
         |<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
         |<title>Test report</title>
         |<style>
         |:root{--bg:#fff;--fg:#1f2328;--muted:#59636e;--line:#d1d9e0;--card:#f6f8fa;--ok:#1a7f37;--fail:#d1242f;--warn:#9a6700;--skip:#59636e}
         |@media (prefers-color-scheme:dark){:root{--bg:#0d1117;--fg:#e6edf3;--muted:#9198a1;--line:#3d444d;--card:#151b23;--ok:#3fb950;--fail:#f85149;--warn:#d29922;--skip:#9198a1}}
         |body{margin:0 auto;max-width:1200px;padding:24px 16px;background:var(--bg);color:var(--fg);font:14px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}
         |h1{margin:0 0 4px;font-size:24px}h1.passed{color:var(--ok)}h1.failed{color:var(--fail)}
         |.meta{color:var(--muted);margin-bottom:12px}
         |.pill{display:inline-block;padding:2px 10px;border-radius:999px;border:1px solid var(--line);margin:0 4px 4px 0}
         |.pill.passed{color:var(--ok)}.pill.failed{color:var(--fail)}.pill.warned{color:var(--warn)}
         |table{border-collapse:collapse;width:100%}th,td{padding:4px 8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
         |.num,.time{text-align:right;white-space:nowrap;color:var(--muted)}
         |details{border:1px solid var(--line);border-radius:8px;margin:12px 0;background:var(--card)}
         |summary{display:flex;gap:12px;padding:10px 14px;cursor:pointer;font-weight:600}summary .name{flex:1}
         |summary .tally,summary .time{font-weight:400;color:var(--muted)}
         |details>*:not(summary){margin:0 14px 14px}h3{font-size:15px;margin:16px 0 6px}h3 .tally{font-weight:400;color:var(--muted)}
         |.scroll{overflow-x:auto}.matrix td{text-align:center;width:110px}.matrix th[scope=row]{font-weight:400}
         |.about{display:block;color:var(--muted);font-size:12px}
         |td.passed,tr.passed .status{color:var(--ok)}td.failed,tr.failed .status{color:var(--fail);font-weight:700}
         |td.warned,tr.warned .status{color:var(--warn);font-weight:700}
         |td.cancelled,td.pending,td.ignored,td.excused,tr.cancelled .status{color:var(--skip)}
         |pre{white-space:pre-wrap;margin:4px 0;padding:8px;background:var(--bg);border:1px solid var(--line);border-radius:6px;font-size:12px}
         |.message{margin:8px 0}.message.failed b{color:var(--fail)}
         |.tests td.status{width:24px;text-align:center}.tests td.time{width:80px}
         |</style></head><body>
         |<h1 class="${
          if (total.contains("failed")) "failed"
          else "passed"
        }">$headline</h1>
         |<div class="meta">$summary</div>
         |<div>$pills</div>
         |<h2>Suites</h2>
         |<table><thead><tr><th>Suite</th><th class="num">Tests</th><th>Result</th><th class="time">Time</th></tr></thead>
         |<tbody>${index.mkString("\n")}</tbody></table>
         |""".stripMargin +
        // After stripMargin, which would strip a message's own `|`.
        suites.zipWithIndex.map { case (s, i) => suite(s, i) }.mkString("\n") +
        """
          |<p class="meta">Written by twirl-spec's TwirlSpecReporter. ✓ passed · ! warned · ✗ failed · – cancelled · … pending · ○ ignored</p>
          |</body></html>
          |""".stripMargin
    }

  }

}
