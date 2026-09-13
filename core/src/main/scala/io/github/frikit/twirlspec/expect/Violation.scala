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

/** Severity of a single failed check. */
sealed abstract class Severity(val label: String, val order: Int)

/** The two severities a violation can have. */
object Severity {

  /** Fails the page. */
  case object Error extends Severity("error", 0)

  /** Reported, but fails the page only when `failOnWarnings` is on. */
  case object Warning extends Severity("warning", 1)
}

/** A single thing that was wrong with a rendered page. */
final case class Violation(
    rule: String,
    message: String,
    expected: Option[String] = None,
    actual: Option[String] = None,
    where: Option[String] = None,
    severity: Severity = Severity.Error,
    hint: Option[String] = None
) {

  /** The same violation, located at this element or line. */
  def at(location: String): Violation = copy(where = Some(location))

  /** The same violation as a warning. */
  def warn: Violation = copy(severity = Severity.Warning)

  /** The same violation with advice on the fix. */
  def withHint(h: String): Violation = copy(hint = Some(h))

  /** Multi-line rendering used inside ScalaTest failure messages. */
  def render(indent: String = "  "): String = {
    val head = s"$indent${Violation.Cross} $rule — $message"
    val lines = Seq(
      where.map(w => s"$indent      at        $w"),
      expected.map(e => s"$indent      expected  ${Violation.quote(e)}"),
      actual.map(a => s"$indent      actual    ${Violation.quote(a)}"),
      hint.map(h => s"$indent      hint      $h")
    ).flatten
    (head +: lines).mkString("\n")
  }

}

/** Ways to build the common violations. */
object Violation {
  private val Cross = "x"

  private[twirlspec] def quote(s: String): String = {
    val truncated = if (s.length > 300) s.take(300) + "..." else s
    "\"" + truncated.replace("\n", "\\n") + "\""
  }

  /** A violation with only a rule and a message. */
  def apply(rule: String, message: String): Violation =
    new Violation(rule, message, None, None, None, Severity.Error, None)

  /** The standard "expected X but got Y" violation. */
  def mismatch(
      rule: String,
      expected: String,
      actual: String,
      message: String = "did not match"
  ): Violation =
    new Violation(
      rule,
      message,
      Some(expected),
      Some(actual),
      None,
      Severity.Error,
      None
    )

  /** Nothing matched a selector. */
  def missing(
      rule: String,
      selector: String,
      message: String = "no element matched"
  ): Violation =
    new Violation(
      rule,
      message,
      Some(selector),
      None,
      None,
      Severity.Error,
      None
    )

}
