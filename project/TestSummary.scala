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

import sbt.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardOpenOption}
import scala.util.Try
import scala.xml.XML

/** A CI run's summary of its tests: each module's results, from the JUnit XML
  * sbt writes to `<module>/target/test-reports`, with the failures beneath,
  * then the report TwirlSpecReporter wrote of the all module's tests. Written
  * to the file `GITHUB_STEP_SUMMARY` names, or to the log.
  */
object TestSummary {

  private def cell(text: String) = text.replace("|", "\\|").replace("\n", " ")

  private def seconds(time: Double) = f"$time%.1f s"

  def apply(base: File, report: File, log: Logger): Unit = {
    val modules = Option(base.listFiles).toList.flatten
      .filter(m => (m / "target" / "test-reports").isDirectory)
      .sortBy(_.getName)
    val failures = List.newBuilder[String]
    val rows = modules.map { module =>
      val suites =
        Option((module / "target" / "test-reports").listFiles).toList.flatten
          .filter(_.getName.endsWith(".xml"))
          .sortBy(_.getName)
          .map(XML.loadFile)
      // The build compiles with Scala 2.12, which has no toIntOption.
      def sum(key: String) =
        suites.map(s => Try((s \@ key).toInt).getOrElse(0)).sum
      val time = suites.map(s => Try((s \@ "time").toDouble).getOrElse(0.0)).sum
      for {
        suite <- suites
        test <- suite \ "testcase"
        bad <- (test \ "failure") ++ (test \ "error")
      } failures += s"- **${module.getName}** `${cell(suite \@ "name")}`: ${cell(test \@ "name")}: ${cell(
          (bad \@ "message").trim.split("\n").headOption.getOrElse("").take(200)
        )}"
      (
        module.getName,
        sum("tests"),
        sum("failures"),
        sum("errors"),
        sum("skipped"),
        time
      )
    }
    val tests = rows.map(_._2).sum
    val failed = rows.map(_._3).sum
    val errors = rows.map(_._4).sum
    val skipped = rows.map(_._5).sum
    val bad = failed + errors
    val head =
      if (bad > 0) s"✗ $bad of $tests tests failed"
      else s"✓ ${tests - skipped} tests passed"
    val lines =
      List(
        s"## $head",
        "",
        "| Module | Tests | Failed | Errors | Skipped | Time |",
        "|---|---:|---:|---:|---:|---:|"
      ) ++
        rows.map { case (m, t, f, e, s, time) =>
          s"| $m | $t | $f | $e | $s | ${seconds(time)} |"
        } ++
        List(
          s"| **all modules** | **$tests** | **$failed** | **$errors** | **$skipped** | **${seconds(rows.map(_._6).sum)}** |"
        ) ++ {
          val all = failures.result()
          if (all.isEmpty) Nil
          else
            ("" :: "### Failures" :: "" :: all.take(50)) ++
              (if (all.size > 50) List(s"- and ${all.size - 50} more") else Nil)
        } ++ {
          if (!report.isFile) Nil
          else
            List(
              "",
              "<details><summary>The report of the all module's tests, by suite and page</summary>",
              "",
              IO.read(report),
              "</details>"
            )
        }
    val text = lines.mkString("", "\n", "\n")
    sys.env.get("GITHUB_STEP_SUMMARY") match {
      case Some(path) =>
        Files.writeString(
          file(path).toPath,
          text,
          UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND
        )
      case None => log.info(text)
    }
  }

}
