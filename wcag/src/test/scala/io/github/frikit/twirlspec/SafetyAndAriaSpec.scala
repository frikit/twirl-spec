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
import io.github.frikit.twirlspec.expect.Severity
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Rule
import io.github.frikit.twirlspec.wcag.{
  SecurityStandards,
  TwirlStandards,
  WcagStandards
}

class SafetyAndAriaSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val allRules = Rule.expectation(
    WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all
  )

  private def page(body: String, head: String = "", lang: String = "en"): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="$lang"><head><title>t</title>$head</head>
         |<body><main id="main-content">$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def fired(body: String, head: String = ""): Seq[String] =
    allRules.check(page(body, head)).map(_.rule)

  private def violation(body: String, rule: String, head: String = "") =
    allRules.check(page(body, head)).find(_.rule == rule)

  "aria references" should {

    "be flagged when they resolve to nothing" in {
      fired(
        """<h1>A</h1><label for="e">E</label><input id="e" aria-describedby="ghost">"""
      ) must
        contain("aria-references-resolve")
      violation(
        """<h1>A</h1><label for="e">E</label><input id="e" aria-labelledby="ghost">""",
        "aria-references-resolve"
      ).map(_.message).getOrElse("") must include("aria-labelledby")
    }

    "be accepted when they resolve" in {
      fired(
        """<h1>A</h1><p id="hint">Hint</p><label for="e">E</label><input id="e" aria-describedby="hint">"""
      ) must
        not contain "aria-references-resolve"
    }
  }

  "an element hidden from assistive technology" should {

    "be flagged when it can still take focus" in {
      fired(
        """<h1>A</h1><div aria-hidden="true"><a href="/x">Still reachable</a></div>"""
      ) must
        contain("no-aria-hidden-focusable")
      fired(
        """<h1>A</h1><a href="/x" aria-hidden="true">Itself</a>"""
      ) must contain("no-aria-hidden-focusable")
    }

    "be accepted when nothing inside it is focusable" in {
      fired(
        """<h1>A</h1><span aria-hidden="true">&times;</span>"""
      ) must not contain "no-aria-hidden-focusable"
    }

    // The hint tells a reader to add tabindex="-1". Taking that advice has to
    // satisfy the rule, so this case is the hint's own contract.
    "be accepted once the advice in the hint has been taken" in {
      fired(
        """<h1>A</h1><div aria-hidden="true"><a href="/x" tabindex="-1">Out of reach</a></div>"""
      ) must not contain "no-aria-hidden-focusable"
      fired(
        """<h1>A</h1><a href="/x" aria-hidden="true" tabindex="-1">Itself</a>"""
      ) must not contain "no-aria-hidden-focusable"
    }

    "still be flagged when the tabindex is not a number" in {
      fired(
        """<h1>A</h1><a href="/x" aria-hidden="true" tabindex="nonsense">Itself</a>"""
      ) must contain("no-aria-hidden-focusable")
    }

    // HTML allows the whitespace around an integer attribute that toInt does
    // not, and a tabindex is an integer attribute.
    "be accepted when the tabindex is padded with spaces" in {
      fired(
        """<h1>A</h1><a href="/x" aria-hidden="true" tabindex=" -1 ">Itself</a>"""
      ) must not contain "no-aria-hidden-focusable"
    }
  }

  "focus order" should {

    "reject a positive tabindex but allow 0 and -1" in {
      fired("""<h1>A</h1><div tabindex="3">Jumped</div>""") must contain(
        "no-positive-tabindex"
      )
      val ok = fired(
        """<h1>A</h1><div tabindex="0">Fine</div><div tabindex="-1">Also fine</div>"""
      )
      ok must not contain "no-positive-tabindex"
    }

    "ignore a tabindex that is not a number" in {
      fired(
        """<h1>A</h1><div tabindex="nonsense">x</div>"""
      ) must not contain "no-positive-tabindex"
    }

    "read a tabindex that is padded with spaces" in {
      fired("""<h1>A</h1><div tabindex=" 3 ">Jumped</div>""") must contain(
        "no-positive-tabindex"
      )
    }
  }

  "zoom" should {

    "be flagged when the viewport blocks it" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width, user-scalable=no">"""
      ) must
        contain("zoom-not-blocked")
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width, maximum-scale=1.0">"""
      ) must
        contain("zoom-not-blocked")
    }

    "be flagged when the cap is below the 200% WCAG 1.4.4 asks for" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width, maximum-scale=1.5">"""
      ) must
        contain("zoom-not-blocked")
    }

    "be accepted for an ordinary viewport" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width, initial-scale=1">"""
      ) must
        not contain "zoom-not-blocked"
    }

    // maximum-scale=10 contains the text "maximum-scale=1" and allows ten
    // times the size: the cap has to be read as a number, not matched.
    "be accepted for a cap that allows zoom" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width, maximum-scale=10">"""
      ) must
        not contain "zoom-not-blocked"
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width, maximum-scale=5">"""
      ) must
        not contain "zoom-not-blocked"
    }

    // Where a directive is given twice the last one applies, so reading the
    // first would miss a cap added after a permissive one.
    "be read from the last declaration when there are two" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="maximum-scale=10, maximum-scale=1">"""
      ) must
        contain("zoom-not-blocked")
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="maximum-scale=1, maximum-scale=10">"""
      ) must
        not contain "zoom-not-blocked"
    }

    // The cap is read the way a browser reads it: the leading number if there
    // is one, then the words it knows, and a word it does not know — or no
    // value at all — is a cap of nothing, which is a page that will not zoom.
    // Only a negative number caps nothing, because it translates to auto.
    "be read the way the viewport algorithm reads it" in {
      def capped(value: String) =
        fired(
          "<h1>A</h1>",
          head = s"""<meta name="viewport" content="maximum-scale=$value">"""
        )
      capped("1e-1") must contain("zoom-not-blocked")
      capped("+1") must contain("zoom-not-blocked")
      capped("yes") must contain("zoom-not-blocked")
      capped("1junk") must contain("zoom-not-blocked")
      capped("10.e-1") must contain("zoom-not-blocked")
      capped("nonsense") must contain("zoom-not-blocked")
      capped("") must contain("zoom-not-blocked")
      capped("1e5") must not contain "zoom-not-blocked"
      capped("device-width") must not contain "zoom-not-blocked"
      capped("10junk") must not contain "zoom-not-blocked"
      capped("-1") must not contain "zoom-not-blocked"
    }

    // Whitespace ends a value rather than joining what surrounds it, so this
    // declares a cap of 1 and must not be read as 10.
    "not join a value back together across a space" in {
      fired(
        "<h1>A</h1>",
        head = """<meta name="viewport" content="maximum-scale=1 0">"""
      ) must
        contain("zoom-not-blocked")
    }

    // Whitespace separates one directive from the next as a comma does.
    "find a directive that only a space separates" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="width=device-width maximum-scale=1">"""
      ) must
        contain("zoom-not-blocked")
    }

    // A browser takes the property name up to the first separator and then
    // scans on for the =, so the cap here belongs to maximum-scale. Looking
    // for well-formed pairs instead would find "ignored=1" and miss it.
    "keep the property name while scanning on for its value" in {
      fired(
        "<h1>A</h1>",
        head = """<meta name="viewport" content="maximum-scale ignored=1">"""
      ) must
        contain("zoom-not-blocked")
      fired(
        "<h1>A</h1>",
        head = """<meta name="viewport" content="maximum-scale = 1">"""
      ) must
        contain("zoom-not-blocked")
    }

    // An empty value ends at the comma. Reading past it would take the next
    // property name as this one's value and lose the directive it named.
    "not let an empty value swallow the directive after it" in {
      fired(
        "<h1>A</h1>",
        head = """<meta name="viewport" content="width=, maximum-scale=1">"""
      ) must
        contain("zoom-not-blocked")
    }

    "ignore a directive that never reaches a value" in {
      fired(
        "<h1>A</h1>",
        head = """<meta name="viewport" content="maximum-scale=1, trailing">"""
      ) must
        contain("zoom-not-blocked")
      fired(
        "<h1>A</h1>",
        head = """<meta name="viewport" content="width, maximum-scale=1">"""
      ) must
        contain("zoom-not-blocked")
    }

    // The same translation again: a word it does not know turns scaling off,
    // and so does any number between -1 and 1.
    "read user-scalable the way the viewport algorithm reads it" in {
      def scalable(value: String) =
        fired(
          "<h1>A</h1>",
          head = s"""<meta name="viewport" content="user-scalable=$value">"""
        )
      scalable("no") must contain("zoom-not-blocked")
      scalable("nope") must contain("zoom-not-blocked")
      scalable("") must contain("zoom-not-blocked")
      scalable("0") must contain("zoom-not-blocked")
      scalable("yes") must not contain "zoom-not-blocked"
      scalable("1") must not contain "zoom-not-blocked"
      scalable("device-width") must not contain "zoom-not-blocked"
    }

    // The last declaration is the one that applies, whatever it says: a
    // negative value there lifts the cap an earlier one set rather than
    // being skipped over.
    "let the last declaration lift a cap the first one set" in {
      fired(
        "<h1>A</h1>",
        head =
          """<meta name="viewport" content="maximum-scale=1, maximum-scale=-1">"""
      ) must
        not contain "zoom-not-blocked"
    }
  }

  "a label" should {
    "be flagged when it points at nothing" in {
      fired("""<h1>A</h1><label for="ghost">Name</label>""") must contain(
        "label-for-resolves"
      )
    }
  }

  "landmarks" should {
    "be flagged when a page has two mains" in {
      val two = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1></main><main>second</main></body></html>""".stripMargin,
        english,
        messages
      )
      allRules.check(two).map(_.rule) must contain("single-main")
    }
  }

  "field names" should {

    "be flagged when two controls submit under the same name" in {
      fired(
        """<h1>A</h1><label for="a">A</label><input id="a" name="dup">
              |<label for="b">B</label><input id="b" name="dup">""".stripMargin
      ) must
        contain("unambiguous-field-names")
    }

    "order its findings when more than one name is duplicated" in {
      val names = allRules
        .check(
          page(
            """<h1>A</h1>
                      |<label for="z1">Z</label><input id="z1" name="zebra">
                      |<label for="z2">Z</label><input id="z2" name="zebra">
                      |<label for="a1">A</label><input id="a1" name="apple">
                      |<label for="a2">A</label><input id="a2" name="apple">""".stripMargin
          )
        )
        .filter(_.rule == "unambiguous-field-names")
        .map(_.message.split("\"")(1))
      names mustBe Seq("apple", "zebra")
    }

    "not object to radios or checkboxes sharing a name, which is how they work" in {
      fired(
        """<h1>A</h1><fieldset><legend>C</legend>
              |<input type="radio" id="r1" name="c" value="1"><label for="r1">One</label>
              |<input type="radio" id="r2" name="c" value="2"><label for="r2">Two</label></fieldset>""".stripMargin
      ) must
        not contain "unambiguous-field-names"
    }
  }

  "safety" should {

    "reject a password submitted by GET" in {
      fired(
        """<h1>A</h1><form method="get" action="/login">
              |<label for="p">Password</label><input type="password" id="p" name="p"></form>""".stripMargin
      ) must
        contain("no-password-in-get")
    }

    "accept a password submitted by POST" in {
      fired(
        """<h1>A</h1><form method="post" action="/login">
              |<label for="p">Password</label><input type="password" id="p" name="p"></form>""".stripMargin
      ) must
        not contain "no-password-in-get"
    }

    // A form that names no method submits by GET. That is the shape the
    // mistake actually ships in, so it has to be the one that is caught.
    "reject a password in a form that names no method" in {
      fired(
        """<h1>A</h1><form action="/login">
              |<label for="p">Password</label><input type="password" id="p" name="p"></form>""".stripMargin
      ) must
        contain("no-password-in-get")
    }

    "reject a javascript: link" in {
      fired(
        """<h1>A</h1><a href="javascript:doThing()">Do the thing</a>"""
      ) must contain("no-javascript-href")
    }

    "warn about a new tab that can reach back, and accept rel=noopener" in {
      val v =
        violation(
          """<h1>A</h1><a href="/x" target="_blank">Guidance (opens in new tab)</a>""",
          "target-blank-is-safe"
        )
      v.map(_.severity) mustBe Some(Severity.Warning)
      fired(
        """<h1>A</h1><a href="/x" target="_blank" rel="noopener">Guidance (opens in new tab)</a>"""
      ) must
        not contain "target-blank-is-safe"
    }

    // noreferrer severs window.opener as well, so it is already the fix.
    "accept a new tab held off by rel=noreferrer" in {
      fired(
        """<h1>A</h1><a href="/x" target="_blank" rel="noreferrer">Guidance (opens in new tab)</a>"""
      ) must
        not contain "target-blank-is-safe"
    }
  }

  "an accessible name" should {

    "be read the same way for a link, a submit control and a field" in {
      val namedElsewhere = fired(
        """<h1>A</h1><span id="n">Download the form</span>
          |<a href="/x" aria-labelledby="n"></a>
          |<button type="submit" aria-labelledby="n"></button>
          |<input id="f" name="f" aria-labelledby="n">""".stripMargin
      )
      namedElsewhere must not contain "link-has-name"
      namedElsewhere must not contain "submit-has-name"
      namedElsewhere must not contain "labelled-controls"
    }

    "not be granted by a reference that points at nothing" in {
      fired(
        """<h1>A</h1><input id="f" name="f" aria-labelledby="ghost">"""
      ) must contain("labelled-controls")
    }

    "not be granted to a link by a decorative image" in {
      fired(
        """<h1>A</h1><a href="/x"><img src="i.png" alt=""></a>"""
      ) must contain("link-has-name")
      fired(
        """<h1>A</h1><a href="/x"><img src="i.png" alt="Download the form"></a>"""
      ) must not contain "link-has-name"
    }
  }

  "the rule sets" should {
    "stay disjoint and uniquely identified" in {
      val ids =
        (WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all)
          .map(_.id)
      ids.distinct.size mustBe ids.size
      SecurityStandards.all.map(_.id) must contain allOf (
        "no-password-in-get",
        "no-javascript-href"
      )
      SecurityStandards
        .allExcept("no-javascript-href")
        .map(_.id) must not contain "no-javascript-href"
      SecurityStandards.only("no-javascript-href").map(_.id) mustBe Seq(
        "no-javascript-href"
      )
    }
  }

}
