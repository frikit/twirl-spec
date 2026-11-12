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
import io.github.frikit.twirlspec.html.{
  HtmlStandards,
  HtmlVocabulary,
  TagBalance
}
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Rule
import io.github.frikit.twirlspec.wcag.{SecurityStandards, WcagStandards}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Rules reading what a browser does with the markup. */
class RuleExactnessSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def fired(rules: Seq[Rule], body: String) =
    rules
      .flatMap(
        _.check(
          Page.fromString(
            s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
               |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
            english,
            messages
          )
        )
      )
      .map(_.rule)

  "A rule" should {

    "see a password that a button sends by GET" in {
      def form(button: String) =
        s"""<form method="post" action="/in"><label for="p">P</label>
           |<input type="password" id="p" name="p">$button</form>""".stripMargin
      fired(
        SecurityStandards.all,
        form("""<button formmethod="get">Go</button>""")
      ) must
        contain("no-password-in-get")
      fired(SecurityStandards.all, form("<button>Go</button>")) must
        not contain "no-password-in-get"
      // a method HTML does not know is GET, the form's or a button's
      fired(
        SecurityStandards.all,
        form("<button>Go</button>").replace("method=\"post\"", "method=\"\"")
      ) must contain("no-password-in-get")
      fired(
        SecurityStandards.all,
        form("""<button formmethod="">Go</button>""")
      ) must contain("no-password-in-get")
      // a disabled button sends nothing
      fired(
        SecurityStandards.all,
        form("""<button formmethod="get" disabled>Go</button>""")
      ) must not contain "no-password-in-get"
      // a button belongs to the form it names, wherever it sits
      val login =
        """<form id="login" method="post" action="/in"><label for="p">P</label>
          |<input type="password" id="p" name="p"></form>""".stripMargin
      fired(
        SecurityStandards.all,
        login + """<button form="login" formmethod="get">Go</button>"""
      ) must contain("no-password-in-get")
      fired(
        SecurityStandards.all,
        login.replace(
          "</form>",
          """<button form="other" formmethod="get">Go</button></form><form id="other"></form>"""
        )
      ) must not contain "no-password-in-get"
    }

    "take an error summary link to # for one that lands nowhere" in {
      fired(
        GovukStandards.all,
        """<div class="govuk-error-summary"><ul class="govuk-error-summary__list">
          |<li><a href="#">Enter a name</a></li></ul></div>""".stripMargin
      ) must contain("error-summary-targets")
    }

    "name a submit or reset input as a browser does, and a label only its own control" in {
      // with no value a browser calls them Submit and Reset; an empty one says nothing
      val unvalued = fired(
        WcagStandards.all,
        """<form action="/a" method="post"><input type="reset"><input type="submit"></form>"""
      )
      unvalued must not contain "labelled-controls"
      unvalued must not contain "submit-has-name"
      fired(
        WcagStandards.all,
        """<form action="/a" method="post"><input type="submit" value=""></form>"""
      ) must contain("submit-has-name")
      // a label with a for names that control, not the one it wraps
      fired(
        WcagStandards.all,
        """<label for="other">Wrong<input id="x" name="x"></label><input id="other" name="other">"""
      ) must contain("labelled-controls")
    }

    "ask an open combobox and a scrollbar what they control, and a cell for its own table's headers" in {
      fired(
        AriaStandards.all,
        """<input role="combobox" aria-expanded="true" aria-label="Country">"""
      ) must contain("aria-required-attr")
      // a closed one's popup need not exist yet
      fired(
        AriaStandards.all,
        """<input role="combobox" aria-expanded="false" aria-label="Country">"""
      ) must not contain "aria-required-attr"
      fired(
        AriaStandards.all,
        """<div role="scrollbar" aria-valuenow="50" aria-label="Scroll"></div>"""
      ) must contain("aria-required-attr")
      // only a combobox is asked, whatever else is expanded
      fired(
        AriaStandards.all,
        """<div role="button" tabindex="0" aria-expanded="true">Menu</div>"""
      ) must not contain "aria-required-attr"
      fired(
        AriaStandards.all,
        """<table><tr><td headers="inner">Outer<table><tr><th id="inner">Inner</th></tr></table></td></tr></table>"""
      ) must contain("table-headers-resolve")
    }

    "know HTML's own attributes and custom elements, and read tags as HTML does" in {
      fired(
        HtmlStandards.all,
        """<button type="button" popovertarget="help" onclick="go()">Help</button><div id="help" popover>Help text</div>"""
      ) must not contain "known-attributes"
      val custom =
        fired(HtmlStandards.all, """<flag-icon country="nl"></flag-icon>""")
      custom must not contain "known-elements"
      custom must not contain "known-attributes"
      HtmlVocabulary.isCustomElementName("x-!") mustBe false
      HtmlVocabulary.isCustomElementName("font-face") mustBe false
      // a slash closes only a void or a foreign element
      TagBalance.check("<main><div/></main>") must not be empty
      TagBalance.check("<main><svg><path/></svg></main>") mustBe empty
      TagBalance.check(
        "<main><svg><foreignObject><div/></foreignObject></svg></main>"
      ) must not be empty
      TagBalance.check("<main><math><mspace/></math></main>") mustBe empty
      TagBalance.check(
        "<main><math><mtext><div/></mtext></math></main>"
      ) must not be empty
      // a doctype that never closes takes the rest with it, as a comment does
      TagBalance.check("<main></main><!DOCTYPE") mustBe empty
      // a paragraph's end tag is not left off inside a link
      TagBalance.check("""<main><a href="/x"><p>Link</a></main>""") must
        not be empty
      TagBalance.check("<main><div><p>Text</div></main>") mustBe empty
      // a comment marker inside an attribute is no comment
      TagBalance.check(
        """<main><div title="<!--">x</div></main>"""
      ) mustBe empty
    }

    "ask a row for its cells, and a hidden control for no name" in {
      fired(
        AriaStandards.all,
        """<div role="table" aria-label="Results"><div role="row"></div></div>"""
      ) must contain("aria-required-children")
      fired(
        AriaStandards.all,
        """<div role="table" aria-label="Results"><div role="row"><div role="cell">1</div></div></div>"""
      ) must not contain "aria-required-children"
      // HTML lets a native row be empty
      fired(
        AriaStandards.all,
        """<table aria-label="R"><tr></tr></table>"""
      ) must
        not contain "aria-required-children"
      // native rows and cells count, with or without a role written on them
      fired(
        AriaStandards.all,
        """<table role="table" aria-label="R"><tr role="row"><td>1</td></tr></table>"""
      ) must not contain "aria-required-children"
      fired(
        WcagStandards.all,
        """<div hidden><input name="website"></div>"""
      ) must
        not contain "labelled-controls"
    }

    "count native options and radios as the children their roles need" in {
      fired(
        AriaStandards.all,
        """<select role="listbox" multiple aria-label="Country"><option>UK</option></select>"""
      ) must not contain "aria-required-children"
      fired(
        AriaStandards.all,
        """<select role="listbox" multiple aria-label="Country"><optgroup label="Europe"><option>UK</option></optgroup></select>"""
      ) must not contain "aria-required-children"
      fired(
        AriaStandards.all,
        """<div role="radiogroup" aria-label="C"><input type="radio" name="c" value="a" aria-label="A"></div>"""
      ) must not contain "aria-required-children"
    }
  }

}
