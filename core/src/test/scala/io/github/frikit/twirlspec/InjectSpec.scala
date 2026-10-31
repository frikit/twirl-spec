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
import testviews.html.checkAnswersView

/** A view can be applied straight after it is injected. */
class InjectSpec extends AnyWordSpec with Matchers with Bilingual {

  "inject" should {

    "apply what it injects to arguments given straight after it" in {
      val html: Html = inject[checkAnswersView]("Ada", "1815")
      html.body must include("Ada")
    }

    "leave room for a placeholder, as a table of views wants" in {
      val view: String => Html = inject[checkAnswersView](_, "1815")
      view("Ada").body must include("Ada")
    }

    "still take .apply, and still give the view itself" in {
      inject[checkAnswersView].apply("Ada", "1815").body must include("Ada")
      inject[checkAnswersView] mustBe a[checkAnswersView]
    }
  }

}
