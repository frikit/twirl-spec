/*
 * Copyright 2026 Victor Osipov
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

import io.github.frikit.twirlspec.aria.AriaChecks
import io.github.frikit.twirlspec.html.HtmlChecks
import io.github.frikit.twirlspec.i18n.I18nChecks
import io.github.frikit.twirlspec.messages.MessagesMatchers
import io.github.frikit.twirlspec.quality.{CoverageChecks, QualityChecks}
import io.github.frikit.twirlspec.govuk.GovukChecks
import io.github.frikit.twirlspec.wcag.WcagChecks

/** Every rule module and the message-file matchers, in one mixin.
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with AllChecks
  * }}}
  *
  * Mix the modules in individually instead when a project wants some and not
  * others — a project not using the GOV.UK Design System has no use for
  * [[io.github.frikit.twirlspec.govuk.GovukChecks]].
  */
trait AllChecks
    extends WcagChecks
    with GovukChecks
    with QualityChecks
    with AriaChecks
    with HtmlChecks
    with CoverageChecks
    with I18nChecks
    with MessagesMatchers
