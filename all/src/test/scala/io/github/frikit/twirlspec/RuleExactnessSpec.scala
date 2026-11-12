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

    "ask a row for its cells, and a hidden control for no name" in {
      fired(
        AriaStandards.all,
        """<div role="table" aria-label="Results"><div role="row"></div></div>"""
      ) must contain("aria-required-children")
      fired(
        AriaStandards.all,
        """<div role="table" aria-label="Results"><div role="row"><div role="cell">1</div></div></div>"""
      ) must not contain "aria-required-children"
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
