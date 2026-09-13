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

package io.github.frikit.twirlspec.quality

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.TwirlSpecDsl

/** Adds the semantic, performance and metadata rules to every `display(...)`.
  *
  * Composes with the other rule modules, so `with WcagChecks with QualityChecks`
  * runs both.
  */
trait QualityChecks extends TwirlSpecDsl {

  override def standardsRules: Seq[Rule] =
    super.standardsRules ++ SemanticStandards.all ++ PerformanceStandards.all ++ MetadataStandards.all

}
