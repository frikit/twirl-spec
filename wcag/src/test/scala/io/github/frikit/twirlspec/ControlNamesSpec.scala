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

import io.github.frikit.twirlspec.expect.Severity
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.wcag.WcagStandards
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Every button and frame needs a name, and nothing else says so. */
class ControlNamesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val rules = WcagStandards.all.filter(r =>
    r.id == "button-has-name" || r.id == "iframe-has-name"
  )

  private def found(body: String) =
    rules.flatMap(
      _.check(Page.fromString(s"<main>$body</main>", english, messages))
    )

  private def on(body: String) = found(body).map(_.rule)

  private val hidden = Seq(
    """<x hidden></x>""",
    """<x inert></x>""",
    """<x aria-hidden="true"></x>""",
    """<x style="display:none"></x>""",
    """<x style="width:0; DISPLAY : NONE;"></x>""",
    """<x style="visibility: hidden"></x>""",
    """<div hidden><x></x></div>""",
    """<noscript><x></x></noscript>""",
    """<template><x></x></template>"""
  )

  "button-has-name" should {

    "warn of each kind of button with no name" in {
      Seq(
        """<button type="button"></button>""",
        """<button type="reset"></button>""",
        """<input type="button">""",
        """<input type="image" src="x.png">""",
        """<div role="button" tabindex="0"></div>""",
        """<div role="BUTTON" tabindex="0"></div>""",
        """<div role="button link" tabindex="0"></div>""",
        """<div role=" button" tabindex="0"></div>""",
        """<span role="button"><span aria-hidden="true">x</span></span>""",
        """<button type="button" style="display:none; display:block"></button>"""
      ).foreach { markup =>
        withClue(markup) {
          on(markup) mustBe Seq("button-has-name")
          found(markup).map(_.severity) mustBe Seq(Severity.Warning)
        }
      }
    }

    "leave alone a button named any way at all" in {
      Seq(
        """<button type="button">Go</button>""",
        """<button type="button" aria-label="Go"></button>""",
        """<button type="button"><img src="x.png" alt="Go"></button>""",
        """<input type="button" value="Go">""",
        """<input type="image" src="x.png" alt="Go">""",
        """<div role="button">Go</div>""",
        """<div role="button link">Go</div>""",
        """<div role="buttons"></div>""",
        """<div role="presentation button"></div>""",
        """<div role="link button"></div>""",
        """<span id="l">Go</span><div role="button" aria-labelledby="l"></div>""",
        """<div role="button" title="Go"></div>"""
      ).foreach(markup => withClue(markup)(on(markup) mustBe empty))
    }

    "leave alone a reset input, which the browser names" in {
      on("""<input type="reset">""") mustBe empty
    }

    "leave to other rules what they report already" in {
      Seq(
        """<button></button>""",
        """<button role="button"></button>""",
        """<button type="submit" role="button"></button>""",
        """<input type="submit" role="button">""",
        """<a href="/x" role="button"></a>"""
      ).foreach(markup => withClue(markup)(on(markup) mustBe empty))
    }

    "leave alone what assistive technology is not told about" in {
      hidden
        .map(
          _.replace("<x", """<button type="button"""")
            .replace("</x>", "</button>")
        )
        .foreach(markup => withClue(markup)(on(markup) mustBe empty))
    }
  }

  "iframe-has-name" should {

    "warn of a frame with no name, whatever it holds" in {
      Seq(
        """<iframe src="/x"></iframe>""",
        """<iframe src="/x">Your browser cannot show this</iframe>""",
        """<iframe src="/x" title=" "></iframe>"""
      ).foreach { markup =>
        withClue(markup) {
          on(markup) mustBe Seq("iframe-has-name")
          found(markup).map(_.severity) mustBe Seq(Severity.Warning)
        }
      }
    }

    "leave alone a frame named any way at all" in {
      Seq(
        """<iframe src="/x" title="Map"></iframe>""",
        """<iframe src="/x" aria-label="Map"></iframe>""",
        """<span id="l">Map</span><iframe src="/x" aria-labelledby="l"></iframe>"""
      ).foreach(markup => withClue(markup)(on(markup) mustBe empty))
    }

    "leave alone what assistive technology is not told about" in {
      hidden
        .map(_.replace("<x", "<iframe").replace("</x>", "</iframe>"))
        .foreach(markup => withClue(markup)(on(markup) mustBe empty))
    }
  }

}
