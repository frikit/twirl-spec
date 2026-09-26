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

import io.github.frikit.twirlspec.quality.MetadataStandards
import io.github.frikit.twirlspec.standards.Rule

/** [[AllChecks]] for a GOV.UK transactional service: every rule, less the ones
  * about search results.
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with GovukServiceChecks
  * }}}
  *
  * A service is reached from GOV.UK rather than from a search engine, often
  * keeps its pages out of search on purpose, and titles every page in the
  * Design System's "Page - Service - GOV.UK" pattern, so
  * [[io.github.frikit.twirlspec.quality.MetadataStandards.searchRules]] would
  * warn on every page it has. Everything else [[AllChecks]] runs, this runs.
  */
trait GovukServiceChecks extends AllChecks {

  override def standardsRules: Seq[Rule] = {
    val search = MetadataStandards.searchRules.map(_.id).toSet
    super.standardsRules.filterNot(r => search.contains(r.id))
  }

}
