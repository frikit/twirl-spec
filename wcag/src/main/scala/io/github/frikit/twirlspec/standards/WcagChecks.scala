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
package io.github.frikit.twirlspec.standards

import io.github.frikit.twirlspec.TwirlSpecDsl

/** Adds the design-system agnostic rules to every `display(...)` in a spec.
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with WcagChecks
  * }}}
  *
  * Calls `super.standardsRules`, so mixing in more than one rule module
  * accumulates rather than replaces: `with WcagChecks with GovukChecks` runs
  * both sets.
  */
trait WcagChecks extends TwirlSpecDsl {
  override def standardsRules: Seq[Rule] = super.standardsRules ++ WcagStandards.all ++ TwirlStandards.all
}
