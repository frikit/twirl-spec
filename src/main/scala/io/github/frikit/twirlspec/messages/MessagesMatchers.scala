/*
 * Copyright 2026 HM Revenue & Customs
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
import io.github.frikit.twirlspec.expect.{Severity, Violation}

import java.io.File

/** Matchers over a service's message files.
  *
  * {{{
  * "conf/messages" should {
  *   "be consistent across languages" in {
  *     messagesApi must beConsistentAcrossLanguages()
  *   }
  *   "define every key once" in {
  *     Seq(new File("conf/messages"), new File("conf/messages.cy")) must haveNoDuplicateKeys
  *   }
  * }
  * }}}
  */
trait MessagesMatchers {

  def messagesIntegrityConfig: MessagesIntegrity.Config = MessagesIntegrity.Config.default

  def beConsistentAcrossLanguages(
    config: MessagesIntegrity.Config = messagesIntegrityConfig
  ): Matcher[MessagesApi] =
    new Matcher[MessagesApi] {
      def apply(api: MessagesApi): MatchResult = {
        val violations = MessagesIntegrity.check(api, config)
        report(violations, "message files")
      }
    }

  val haveNoDuplicateKeys: Matcher[Seq[File]] = new Matcher[Seq[File]] {
    def apply(files: Seq[File]): MatchResult =
      report(MessagesIntegrity.duplicateKeys(files), "message files")
  }

  private def report(violations: Seq[Violation], subject: String): MatchResult = {
    val errors   = violations.filter(_.severity == Severity.Error)
    val warnings = violations.filter(_.severity == Severity.Warning)

    val body   = errors.map(_.render()).mkString("\n\n")
    val warned =
      if (warnings.isEmpty) "" else "\n\n  warnings:\n" + warnings.map(_.render("  ")).mkString("\n")

    MatchResult(
      errors.isEmpty,
      s"\n$subject failed ${errors.size} check(s):\n\n$body$warned\n",
      s"$subject passed every check, but were expected not to"
    )
  }

}
