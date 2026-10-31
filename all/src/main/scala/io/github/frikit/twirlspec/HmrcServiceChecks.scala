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

import io.github.frikit.twirlspec.i18n.TranslationConfig

/** [[GovukServiceChecks]] for an HMRC service built on play-frontend-hmrc, with
  * what every such service would otherwise write on its spec base:
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with HmrcServiceChecks
  * }}}
  *
  * The report-a-problem link every page inherits is left out of coverage, in
  * whichever language it reads, and "HMRC" may read the same in every language.
  * The link's own layout deserves a test of its own, and the rules still check
  * it.
  */
trait HmrcServiceChecks extends GovukServiceChecks {

  override def coverageIgnoredSelectors: Set[String] =
    super.coverageIgnoredSelectors + HmrcServiceChecks.reportATechnicalProblem

  override def translationConfig: TranslationConfig =
    TranslationConfig(super.translationConfig.sameTextIsFine + "HMRC")

}

/** What [[HmrcServiceChecks]] knows about play-frontend-hmrc's markup. */
object HmrcServiceChecks {

  /** The link `hmrcReportTechnicalIssueHelper` renders, which has a class but
    * no id, and words that change with the language.
    */
  val reportATechnicalProblem: String = ".hmrc-report-technical-issue"

}
