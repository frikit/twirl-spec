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

import org.scalatest.{Alerting, Suite}
import play.api.i18n.Lang

/** The test fixtures come in English and Welsh, so the suites that use both say
  * so.
  */
trait Bilingual extends TwirlSpec { self: Suite with Alerting =>

  val welsh: Lang = Lang("cy")

  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq("en", "cy"))

}
