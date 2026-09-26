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

package views

import io.github.frikit.twirlspec.{GovukServiceChecks, TwirlSpec}
import io.github.frikit.twirlspec.i18n.TranslationConfig
import io.github.frikit.twirlspec.messages.MessagesIntegrity
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

// every rule, less the search-result ones a GOV.UK service has no use for
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with GovukServiceChecks {

  // the languages come from play.i18n.langs in conf/application.conf

  // what every page inherits from the layout, so coverage does not ask each spec for it
  override def coverageIgnored: Set[String] = Set("#report-technical-issue", "#hmrc-timeout")

  // text that is the same in every language by design
  override def translationConfig = TranslationConfig(sameTextIsFine = Set("GOV.UK", "HMRC"))

  // the service is bilingual, so the message files must be too
  override def messagesIntegrityConfig = MessagesIntegrity.Config(requireTranslations = true)

  // the furniture every page must carry
  val furniture = expectations(serviceName(), languageToggle, signOutLink, timeoutDialog)
}
