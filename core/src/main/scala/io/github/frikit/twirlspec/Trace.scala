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

import play.api.Logger

import io.github.frikit.twirlspec.expect.{CheckReport, Violation}
import io.github.frikit.twirlspec.page.Page

import scala.util.control.NonFatal

/** What the library did, at `DEBUG`, through Play's logger: the application it
  * built, each page it rendered, each check it made and what it found. The
  * logback configuration a service's tests use governs it, under the name of
  * this package:
  *
  * {{{
  * <logger name="io.github.frikit.twirlspec" level="DEBUG"/>
  * }}}
  *
  * Nothing is written at any other level.
  */
private[twirlspec] object Trace {

  /** The logger every trace goes through, named after the library. */
  val logger: Logger = Logger("io.github.frikit.twirlspec")

  /** A trace says what happened and must not change it, so a message that
    * cannot be built is dropped rather than failing the step it describes.
    */
  def debug(message: => String): Unit =
    try logger.debug(message)
    catch { case NonFatal(_) => () }

  /** The page, having noted that it was rendered. */
  def rendered(page: Page): Page = {
    debug(s"rendered '${page.title}' (${page.lang.code})")
    page
  }

  /** The report, having noted what the check found. It says which rules rather
    * than whether the check failed: under `failOnWarnings` a warning fails it,
    * and only the matcher knows that.
    */
  def checked(report: CheckReport): CheckReport = {
    debug {
      def rules(violations: Seq[Violation]) =
        violations.map(_.rule).distinct.mkString(", ")
      s"checked '${report.page.title}' (${report.page.lang.code}): " +
        s"errors [${rules(report.errors)}], warnings [${rules(report.warnings)}]"
    }
    report
  }

}
