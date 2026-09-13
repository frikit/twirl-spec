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

package io.github.frikit.twirlspec.html

import io.github.frikit.twirlspec.TwirlSpecDsl
import io.github.frikit.twirlspec.standards.Rule

/** Adds the HTML validity rules to every `display(...)`. */
trait HtmlChecks extends TwirlSpecDsl {

  /** Prefixes this project uses for its own attributes, beyond `data-` and
    * `aria-`.
    */
  def attributePrefixes: Set[String] = Set.empty

  private lazy val htmlStandards: HtmlStandards = new HtmlStandards(
    attributePrefixes
  )

  override def standardsRules: Seq[Rule] =
    super.standardsRules ++ htmlStandards.all

}
