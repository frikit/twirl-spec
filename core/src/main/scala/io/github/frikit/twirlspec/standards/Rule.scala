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

package io.github.frikit.twirlspec.standards

import io.github.frikit.twirlspec.expect.{Expectation, Severity, Violation}
import io.github.frikit.twirlspec.page.Page

/** The WCAG conformance level a success criterion belongs to. */
sealed abstract class Level(val name: String, val order: Int)

/** The three conformance levels. */
object Level {

  /** Level A, the minimum. */
  case object A extends Level("A", 0)

  /** Level AA, what public sector bodies are held to. */
  case object AA extends Level("AA", 1)

  /** Level AAA. */
  case object AAA extends Level("AAA", 2)

  /** Every level, lowest first. */
  val all: Seq[Level] = Seq(A, AA, AAA)

  /** Every level up to and including this one, which is what conformance means:
    * claiming AA requires satisfying A as well.
    */
  def upTo(level: Level): Seq[Level] = all.filter(_.order <= level.order)
}

/** The WCAG version a success criterion first appeared in. */
sealed abstract class WcagVersion(val name: String, val order: Int)

/** The WCAG versions a criterion can date from. */
object WcagVersion {

  /** WCAG 2.0. */
  case object V2_0 extends WcagVersion("2.0", 0)

  /** WCAG 2.1. */
  case object V2_1 extends WcagVersion("2.1", 1)

  /** WCAG 2.2. */
  case object V2_2 extends WcagVersion("2.2", 2)

  /** Every version, oldest first. */
  val all: Seq[WcagVersion] = Seq(V2_0, V2_1, V2_2)

  /** Every version up to and including this one. */
  def upTo(version: WcagVersion): Seq[WcagVersion] =
    all.filter(_.order <= version.order)

}

/** The success criterion a rule enforces, so a project can select the rules
  * that match the conformance it is actually claiming.
  */
final case class Criterion(
    number: String,
    title: String,
    level: Level,
    since: WcagVersion
) {

  override def toString: String =
    s"WCAG $number $title (Level ${level.name}, since ${since.name})"

}

/** One named rule.
  *
  * @param id
  *   the stable id a spec excludes or selects the rule by
  * @param description
  *   what the rule checks, as a phrase
  * @param pageLevel
  *   whether the rule applies only to a whole page, so a fragment rendered
  *   without a layout is not judged on what only a layout provides
  * @param severity
  *   whether a finding fails the page or is only reported
  * @param criterion
  *   the WCAG success criterion the rule enforces, if it enforces one
  * @param run
  *   the check itself; an empty result means the page passed
  */
final class Rule(
    val id: String,
    val description: String,
    val pageLevel: Boolean,
    val severity: Severity,
    val criterion: Option[Criterion],
    run: Page => Seq[Violation]
) {

  /** The violations this rule finds on a page: none for a page-level rule on a
    * fragment, and each one a warning when the rule is.
    */
  def check(page: Page): Seq[Violation] =
    if (pageLevel && !Rule.isFullPage(page)) Nil
    else {
      val found = run(page)
      if (severity == Severity.Warning) found.map(_.warn) else found
    }

  /** The conformance level of the criterion this rule enforces, if it enforces
    * one.
    */
  def level: Option[Level] = criterion.map(_.level)

  /** The WCAG version the criterion this rule enforces dates from, if it
    * enforces one.
    */
  def wcagVersion: Option[WcagVersion] = criterion.map(_.since)

  override def toString: String =
    criterion.fold(s"$id — $description")(c => s"$id — $description [$c]")

}

/** Ways to build and combine rules. */
object Rule {

  /** A set of rules as one expectation. */
  def expectation(rules: Seq[Rule]): Expectation = new Expectation {
    def description: String = s"standards (${rules.size} rules)"
    def check(page: Page): Seq[Violation] =
      page.withRecordingPaused(rules.flatMap(_.check(page)))
  }

  /** The severity of a rule that reports without failing the page. */
  val Warning: Severity = Severity.Warning

  /** The severity of a rule that fails the page. */
  val Blocking: Severity = Severity.Error

  /** A rule from its id, description and check; blocking, applying to fragments
    * too, and tied to no criterion unless said otherwise.
    */
  def apply(
      id: String,
      description: String,
      pageLevel: Boolean = false,
      severity: Severity = Severity.Error,
      criterion: Option[Criterion] = None
  )(run: Page => Seq[Violation]): Rule =
    new Rule(id, description, pageLevel, severity, criterion, run)

  /** Jsoup wraps every fragment in `<html><head><body>`, so the parsed tree
    * cannot tell us whether the template included the layout.
    */
  private[twirlspec] def isFullPage(page: Page): Boolean = {
    val head = page.source.take(2000).toLowerCase
    head.contains("<!doctype html") || head.contains("<html")
  }

}
