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

package io.github.frikit.twirlspec.messages

import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.i18n.MessagesApi
import io.github.frikit.twirlspec.TwirlSpecDsl
import io.github.frikit.twirlspec.expect.{Outcome, Severity, Violation}

import java.io.File
import scala.util.control.NonFatal

/** Matchers over a service's message files. */
trait MessagesMatchers {

  /** The configuration the matchers use; override it once on a spec base. */
  def messagesIntegrityConfig: MessagesIntegrity.Config =
    MessagesIntegrity.Config.default

  /** Every configured language is measured against the base: key parity both
    * ways, empty values, placeholders, quoting and how much is translated.
    */
  def beConsistentAcrossLanguages(
      config: MessagesIntegrity.Config = messagesIntegrityConfig
  ): Matcher[MessagesApi] =
    new Matcher[MessagesApi] {
      def apply(api: MessagesApi): MatchResult = {
        val violations = MessagesIntegrity.check(api, config)
        report(violations, "message files")
      }
    }

  /** No key is defined twice within any one of these files. */
  val haveNoDuplicateKeys: Matcher[Seq[File]] = new Matcher[Seq[File]] {
    def apply(files: Seq[File]): MatchResult =
      report(MessagesIntegrity.duplicateKeys(files), "message files")
  }

  private def report(
      violations: Seq[Violation],
      subject: String
  ): MatchResult = {
    val errors = violations.filter(_.severity == Severity.Error)
    val warnings = violations.filter(_.severity == Severity.Warning)

    // A spec on the DSL decides what its warnings do. Without one they fail
    // nothing, and standard output is the only place left to say them.
    val spec = Option(this).collect { case dsl: TwirlSpecDsl => dsl }
    val ok =
      errors.isEmpty && (warnings.isEmpty || !spec.exists(_.failOnWarnings))
    if (ok)
      try
        spec.fold(if (warnings.nonEmpty) println(Outcome.surfaced(warnings)))(
          _.surfaceWarnings(warnings)
        )
      catch { case NonFatal(_) => () }

    val body = errors.map(_.render()).mkString("\n\n")
    val warned =
      if (warnings.isEmpty) ""
      else "\n\n  warnings:\n" + warnings.map(_.render("  ")).mkString("\n")
    val headline = Outcome.headline(subject, errors.size, warnings.size)

    MatchResult(
      ok,
      s"\n$headline\n\n$body$warned\n",
      s"$subject passed every check, but were expected not to"
    )
  }

}
