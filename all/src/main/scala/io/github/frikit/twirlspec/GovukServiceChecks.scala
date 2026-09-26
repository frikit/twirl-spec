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

import io.github.frikit.twirlspec.quality.{
  MetadataStandards,
  PerformanceStandards
}
import io.github.frikit.twirlspec.standards.Rule

/** [[AllChecks]] for a GOV.UK transactional service: every rule, less the ones
  * about search results, and with the platform's own head script left alone.
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with GovukServiceChecks
  * }}}
  *
  * A service is reached from GOV.UK rather than from a search engine, often
  * keeps its pages out of search on purpose, and titles every page in the
  * Design System's "Page - Service - GOV.UK" pattern, so
  * [[io.github.frikit.twirlspec.quality.MetadataStandards.searchRules]] would
  * warn on every page it has. And `scripts-are-deferred` would warn on every
  * page that carries HMRC's tracking-consent script, which the platform puts in
  * the head on purpose, so here it leaves
  * [[GovukServiceChecks.platformScripts]] alone and still reports the service's
  * own. Everything else [[AllChecks]] runs, this runs.
  *
  * Mix it in after any rule trait of your own: a trait mixed in later adds its
  * rules after this one has filtered.
  */
trait GovukServiceChecks extends AllChecks {

  override def standardsRules: Seq[Rule] = {
    val search = MetadataStandards.searchRules.map(_.id).toSet
    val deferred = GovukServiceChecks.scriptsAreDeferred
    super.standardsRules
      .filterNot(r => search.contains(r.id))
      .map(r => if (r.id == deferred.id) deferred else r)
  }

}

/** What [[GovukServiceChecks]] knows about a GOV.UK service's platform. */
object GovukServiceChecks {

  /** Head scripts a service's platform places there on purpose: the script
    * play-frontend-hmrc's tracking-consent snippet adds wherever
    * `tracking-consent-frontend` is configured, which has to run before
    * anything it gives consent for.
    */
  val platformScripts: Seq[String] = Seq("script#tracking-consent-script-tag")

  /** `scripts-are-deferred` for a GOV.UK service: the platform's scripts are
    * left alone, and the service's own are still reported.
    */
  lazy val scriptsAreDeferred: Rule =
    PerformanceStandards.scriptsAreDeferredExcept(platformScripts: _*)

}
