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

package io.github.frikit.twirlspec.expect

import io.github.frikit.twirlspec.page.Page

/** One question asked of a rendered page. */
trait Expectation { self =>

  /** Human description, used when listing what a page was checked against. */
  def description: String

  /** Empty means satisfied. */
  def check(page: Page): Seq[Violation]

  def and(other: Expectation): Expectation = Expectation.all(Seq(self, other))

  /** Downgrade every violation this expectation produces to a warning. */
  def asWarning: Expectation = new Expectation {
    def description: String               = s"${self.description} (warning only)"
    def check(page: Page): Seq[Violation] = self.check(page).map(_.warn)
  }

  /** Only apply this expectation when the page satisfies a predicate. */
  def when(p: Page => Boolean): Expectation = new Expectation {
    def description: String               = s"${self.description} (conditional)"
    def check(page: Page): Seq[Violation] = if (p(page)) self.check(page) else Nil
  }

}

object Expectation {

  def apply(name: String)(f: Page => Seq[Violation]): Expectation = new Expectation {
    def description: String               = name
    def check(page: Page): Seq[Violation] = f(page)
  }

  val satisfied: Expectation = apply("(nothing)")(_ => Nil)

  def all(expectations: Seq[Expectation]): Expectation = new Expectation {
    def description: String               = expectations.map(_.description).mkString(", ")
    def check(page: Page): Seq[Violation] = expectations.flatMap(_.check(page))
  }

}

/** The result of evaluating a set of expectations against a page. */
final case class CheckReport(page: Page, violations: Seq[Violation], subject: String) {

  def errors: Seq[Violation]   = violations.filter(_.severity == Severity.Error)
  def warnings: Seq[Violation] = violations.filter(_.severity == Severity.Warning)

  def passed: Boolean = errors.isEmpty

  /** The message a developer reads when their view test goes red. */
  def message: String = {
    val header =
      if (errors.size == 1) s"$subject failed 1 check:"
      else s"$subject failed ${errors.size} checks:"

    val body = errors.map(_.render()).mkString("\n\n")

    val warned =
      if (warnings.isEmpty) ""
      else "\n\n  warnings:\n" + warnings.map(_.render("  ")).mkString("\n")

    s"""|
        |$header
        |
        |$body$warned
        |
        |  --- page outline (${page.lang.code}) ---
        |${page.outline}
        |""".stripMargin
  }

}
