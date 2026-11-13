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

  import TwirlSpecReporter.{Line, Suite}

  private val results = mutable.ArrayBuffer.empty[Line]
  // By suite id, as two suites in two packages can share a name.
  private val suites = mutable.LinkedHashMap.empty[String, Suite]
  private val began = mutable.Map.empty[String, Long]
  private val warned = mutable.Set.empty[(String, String)]
  private var started = Option.empty[Long]
  private var ended = Option.empty[String]

  def apply(event: Event): Unit = synchronized {
    event match {
      case e: RunStarting   => started = Some(e.timeStamp)
      case e: SuiteStarting =>
        suite(e.suiteId, e.suiteName)
        began(e.suiteId) = e.timeStamp
      // A rule's warnings come as an alert while its test runs.
      case e: AlertProvided if e.message.startsWith("twirl-spec: ") =>
        e.nameInfo.foreach(n =>
          n.testName.foreach(t => warned += ((n.suiteId, t)))
        )
      case e: TestSucceeded =>
        val status =
          if (warned.remove((e.suiteId, e.testName))) "warned" else "passed"
        add(
          e.suiteId,
          e.suiteName,
          e.testName,
          e.testText,
          status,
          e.duration,
          ""
        )
      case e: TestFailed =>
        add(
          e.suiteId,
          e.suiteName,
          e.testName,
          e.testText,
          "failed",
          e.duration,
          e.message
        )
      case e: TestCanceled =>
        add(
          e.suiteId,
          e.suiteName,
          e.testName,
          e.testText,
          "cancelled",
          e.duration,
          e.message
        )
      case e: TestPending =>
        add(
          e.suiteId,
          e.suiteName,
          e.testName,
          e.testText,
          "pending",
          e.duration,
          ""
        )
      case e: TestIgnored =>
        add(e.suiteId, e.suiteName, e.testName, e.testText, "ignored", None, "")
      case e: SuiteCompleted =>
        finish(e.suiteId, e.suiteName, e.duration, e.timeStamp, None)
      case e: SuiteAborted =>
        finish(e.suiteId, e.suiteName, e.duration, e.timeStamp, Some(e.message))
      case e: RunCompleted => end(e.timeStamp, None)
      case e: RunStopped   => end(e.timeStamp, Some("✗ Run stopped"))
      case e: RunAborted   =>
        end(e.timeStamp, Some(s"✗ Run aborted: ${e.message}"))
      case _ => ()
    }
  }

  private def suite(id: String, name: String): Suite =
    suites.getOrElseUpdate(id, Suite(id, name, None, None))

  private def add(
      suiteId: String,
      suiteName: String,
      test: String,
      text: String,
      status: String,
      duration: Option[Long],
      message: String
  ): Unit = {
    suite(suiteId, suiteName)
    results += Line(
      suiteId,
      test,
      status,
      duration.getOrElse(0L),
      message,
      TwirlSpecReporter.cellOf(test, text)
    )
  }

  /** A suite's own time, from ScalaTest, else from its start, else none. */
  private def finish(
      id: String,
      name: String,
      duration: Option[Long],
      now: Long,
      aborted: Option[String]
  ): Unit = {
    val took = duration.orElse(began.get(id).map(now - _))
    suites(id) = suite(id, name).copy(millis = took, aborted = aborted)
    write(now)
  }

  private def end(now: Long, how: Option[String]): Unit = {
    ended = how
    write(now)
    println(
      s"twirl-spec report: ${directory.resolve("index.html").toAbsolutePath}"
    )
  }

  private def write(now: Long): Unit = {
    val took = started.fold(results.map(_.millis).sum)(now - _)
    val run = TwirlSpecReporter.Run(
      results.toList,
      suites.values.toList,
      started.getOrElse(now),
      took,
      ended
    )
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

  // (?s), as a state's name or a rule's description may run over lines.
  private val Render = "(?s)must render (.+), in (\\S+)".r
  private val Translation =
    "(?s)must (translate|be excused from translating) consistently by (\\S+), (.+?): (.*)".r
  private val Standard =
    "(?s)must (meet|be excused from) (\\S+) in (\\S+)(?:: (.*))?".r

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

  /** A suite: its id, the name shown, its own time where known, and why it
    * aborted where it did.
    */
  private[twirlspec] final case class Suite(
      id: String,
      name: String,
      millis: Option[Long],
      aborted: Option[String]
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
      all: List[Suite],
      started: Long,
      took: Long,
      ended: Option[String]
  ) {

    private def ofSuite(s: Suite) = lines.filter(_.suite == s.id)

    private def failed(s: Suite) =
      s.aborted.isDefined || ofSuite(s).exists(_.status == "failed")

    // Those that failed first, then by name.
    private val suites = all.sortBy(s => (!failed(s), s.name, s.id))

    private def tallyOf(s: Suite) =
      (s.aborted.map(_ => "aborted").toList :+ tally(counts(ofSuite(s))))
        .filter(_.nonEmpty)
        .mkString(" ")

    private def timeOf(s: Suite) = s.millis.fold(spent(ofSuite(s)))(time)

    private val total = counts(lines)

    private val bad = ended.isDefined || suites.exists(failed)

    private val headline = ended.getOrElse(
      if (bad) "✗ Tests failed" else "✓ Tests passed"
    )

    private val when = DateTimeFormatter
      .ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault())
      .format(Instant.ofEpochMilli(started))

    private val summary =
      s"${lines.size} tests in ${suites.size} suites · ${time(took)} · started $when"

    def markdown: String = {
      val rows = suites.map { s =>
        s"| ${s.name} | ${ofSuite(s).size} | ${tallyOf(s)} | ${timeOf(s)} |"
      }
      def block(title: String, message: String) =
        s"- $title\n\n  ```\n  ${message.replace("\n", "\n  ")}\n  ```"
      val failures =
        suites.flatMap(s =>
          s.aborted.map(m => block(s"**${s.name}** aborted", m))
        ) ++
          suites.flatMap(s =>
            ofSuite(s)
              .filter(_.status == "failed")
              .map(l => block(s"**${s.name}**: ${l.test}", l.message))
          )
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

    /** The page a test of no check of its own belongs to: the longest label it
      * starts with, so "Name with errors" keeps its tests from "Name".
      */
    private def owner(l: Line, pages: List[String]): Option[String] =
      pages.filter(p => l.test.startsWith(s"$p ")).maxByOption(_.length)

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
      val pages = in.flatMap(_.cell).map(_.page).distinct
      val own =
        in.filter(l => l.cell.isEmpty && owner(l, pages).contains(label))
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

    private def suite(s: Suite, index: Int): String = {
      val in = ofSuite(s)
      val pages = in.flatMap(_.cell).map(_.page).distinct
      val rest = in.filter(l => l.cell.isEmpty && owner(l, pages).isEmpty)
      val aborted = s.aborted.fold("")(m =>
        s"""<div class="message failed"><b>The suite aborted</b><pre>${esc(
            m
          )}</pre></div>"""
      )
      Seq(
        s"""<details id="s$index"${if (failed(s)) " open" else ""}>""",
        s"""<summary title="${esc(s.id)}"><span class="name">${esc(
            s.name
          )}</span><span class="tally">${tallyOf(
            s
          )}</span><span class="time">${timeOf(s)}</span></summary>""",
        aborted,
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
        s"""<tr class="${
            if (failed(s)) "failed" else "passed"
          }"><td><a href="#s$i" title="${esc(
            s.id
          )}">${esc(s.name)}</a></td><td class="num">${ofSuite(
            s
          ).size}</td><td>${tallyOf(s)}</td><td class="time">${timeOf(
            s
          )}</td></tr>"""
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
         |<h1 class="${if (bad) "failed" else "passed"}">${esc(headline)}</h1>
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
