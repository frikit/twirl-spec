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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.twirl.api.Html

/** A component, rendered without a layout, catalogued in a service that
  * declares no languages: the catalogue runs in Play's one default language,
  * and the component is not asked to declare a language it has no `<html>` to
  * declare on.
  */
class UndeclaredLanguagesCatalogueSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with AllChecks
    with PageCatalogue {

  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq.empty[String])

  cataloguePage(staticPage("Fragment")(Html("""<p id="hello">Hello</p>"""))) {
    "check in the one language" in {
      languages mustBe Seq(currentLang)
    }
  }
}
