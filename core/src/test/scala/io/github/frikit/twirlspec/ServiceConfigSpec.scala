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

import io.github.frikit.twirlspec.render.{CsrfToken, SharedApplication}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.i18n.Lang
import play.api.test.FakeRequest
import play.twirl.api.Html
import testviews.html.{csrfView, nameView}

/** A suite that declares no languages of its own gets the service's: the ones
  * `conf/application.conf` declares, which for these fixtures are English and
  * Welsh.
  */
class ServiceLanguagesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val form = Form(
    mapping("firstName" -> text, "lastName" -> text)(Tuple2.apply)(t =>
      Some((t._1, t._2))
    )
  )

  "the shared application" should {

    "leave the languages to the service's own configuration" in {
      SharedApplication.viewTestDefaults.keySet must not contain "play.i18n.langs"
      languages.map(_.code) mustBe Seq("en", "cy")
    }

    "render Welsh without the spec declaring it again" in {
      val view = inject[nameView]
      inLanguage(Lang("cy"))(renderPage(view(form)).h1.text) mustBe
        "Beth yw eu henw?"
    }
  }

  "the implicit request" should {

    "carry a signed CSRF token" in {
      play.filters.csrf.CSRF.getToken(request).map(_.name) mustBe Some(
        "csrfToken"
      )
    }

    "let a view render Play's CSRF field instead of throwing" in {
      render(csrfView())
        .css("input[type=hidden][name=csrfToken]")
        .elements must have size 1
    }
  }

  "the check for Play's CSRF helper" should {

    "find the helper here, and not a class that does not exist" in {
      CsrfToken.available mustBe true
      CsrfToken.isOnClasspath(CsrfToken.helperClass) mustBe true
      CsrfToken.isOnClasspath("no.such.Helper") mustBe false
    }

    "leave the request as it is when the helper is missing" in {
      val bare = FakeRequest("GET", "/")
      CsrfToken.add(bare, helperPresent = false) must be theSameInstanceAs bare
    }
  }
}

/** A suite whose application offers English alone. */
class UnconfiguredLanguageSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec {

  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq("en"))

  "a language the application is not configured for" should {

    "fail inLanguage rather than render English in its place" in {
      val e = the[IllegalArgumentException] thrownBy inLanguage(Lang("cy"))(())
      e.getMessage must include(
        "cy is not a configured language (play.i18n.langs: en)"
      )
      e.getMessage must include("so Play would render en in its place")
    }

    "fail renderIn the same way" in {
      val e = the[IllegalArgumentException] thrownBy
        renderIn(Lang("cy"))(_ => Html("<p>x</p>"))
      e.getMessage must include("cy is not a configured language")
    }

    "leave a configured language alone" in {
      inEnglish(currentLang) mustBe english
    }
  }
}
