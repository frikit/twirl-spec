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

import io.github.frikit.twirlspec.aria.AriaStandards
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.page.{Page, Tree}
import io.github.frikit.twirlspec.standards.Rule
import io.github.frikit.twirlspec.wcag.{SecurityStandards, WcagStandards}
import org.jsoup.nodes.Element
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Pages that jsoup's legacy and Web query modes would read apart without care.
  * CI runs every spec in both modes, so each verdict here holds in both.
  */
class QueryModesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def page(body: String, bodyAttributes: String = "") =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body$bodyAttributes><main><h1>A</h1>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def fired(rules: Seq[Rule], p: Page) =
    rules.flatMap(_.check(p)).map(_.rule)

  "A value a browser reads in any case" should {
    "count written in capitals" in {
      fired(
        WcagStandards.all,
        page("<button>Go</button>", " aria-hidden=TRUE")
      ) must
        contain("no-aria-hidden-focusable")
      fired(WcagStandards.all, page("""<div role="MAIN">x</div>""")) must
        contain("single-main")
      fired(
        SecurityStandards.all,
        page("""<a href="JAVASCRIPT:x">Go</a>""")
      ) must
        contain("no-javascript-href")
      // lower-cased as the selector did, so ſ stays ſ and this is no scheme
      fired(
        SecurityStandards.all,
        page("""<a href="javaſcript:x">Go</a>""")
      ) must
        not contain "no-javascript-href"
    }

    "not include an id, which compares exactly everywhere" in {
      // the id submit picks one of two submit buttons only when it is exactly that
      def chosen(id: String) = page(
        s"""<form><input type="submit" id="$id" value="Go"><input type="submit" value="Back"></form>"""
      ).submitButton.elements.size
      chosen("SUBMIT") mustBe 2
      chosen("submit") mustBe 1
    }
  }

  "The element a question starts from" should {
    "count itself" in {
      val copies = """<fieldset role="region" aria-label="Same"></fieldset>"""
      fired(AriaStandards.all, page(copies * 2)) must
        contain("landmarks-are-distinguishable")

      val hiddenError =
        """<p id="value-error" class="govuk-error-message govuk-visually-hidden">Error: Enter a name</p>"""
      fired(GovukStandards.all, page(hiddenError)) must not contain
        "error-hidden-prefix"
      page(hiddenError).fieldErrors.get("value") mustBe Some("")
    }
  }

  "A walk through the page" should {
    "reach any depth, which an older jsoup does not cap" in {
      val root = new Element("div")
      (1 to 20000).foldLeft(root)((parent, _) => parent.appendElement("div"))
      Tree.withDescendants(root).size mustBe 20001
    }
  }

}
