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
import io.github.frikit.twirlspec.wcag.{SecurityStandards, TwirlStandards, WcagStandards}

class SafetyAndAriaSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val allRules = Rule.expectation(WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all)

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
      fired("""<h1>A</h1><label for="e">E</label><input id="e" aria-describedby="ghost">""") must
        contain("aria-references-resolve")
      violation(
        """<h1>A</h1><label for="e">E</label><input id="e" aria-labelledby="ghost">""",
        "aria-references-resolve"
      ).map(_.message).getOrElse("")                                                         must include("aria-labelledby")
    }

    "be accepted when they resolve" in {
      fired("""<h1>A</h1><p id="hint">Hint</p><label for="e">E</label><input id="e" aria-describedby="hint">""") must
        not contain "aria-references-resolve"
    }
  }

  "an element hidden from assistive technology" should {

    "be flagged when it can still take focus" in {
      fired("""<h1>A</h1><div aria-hidden="true"><a href="/x">Still reachable</a></div>""") must
        contain("no-aria-hidden-focusable")
      fired("""<h1>A</h1><a href="/x" aria-hidden="true">Itself</a>""")                     must contain("no-aria-hidden-focusable")
    }

    "be accepted when nothing inside it is focusable" in {
      fired("""<h1>A</h1><span aria-hidden="true">&times;</span>""") must not contain "no-aria-hidden-focusable"
    }
  }

  "focus order" should {

    "reject a positive tabindex but allow 0 and -1" in {
      fired("""<h1>A</h1><div tabindex="3">Jumped</div>""") must contain("no-positive-tabindex")
      val ok = fired("""<h1>A</h1><div tabindex="0">Fine</div><div tabindex="-1">Also fine</div>""")
      ok must not contain "no-positive-tabindex"
    }

    "ignore a tabindex that is not a number" in {
      fired("""<h1>A</h1><div tabindex="nonsense">x</div>""") must not contain "no-positive-tabindex"
    }
  }

  "zoom" should {

    "be flagged when the viewport blocks it" in {
      fired("<h1>A</h1>", head = """<meta name="viewport" content="width=device-width, user-scalable=no">""")  must
        contain("zoom-not-blocked")
      fired("<h1>A</h1>", head = """<meta name="viewport" content="width=device-width, maximum-scale=1.0">""") must
        contain("zoom-not-blocked")
    }

    "be accepted for an ordinary viewport" in {
      fired("<h1>A</h1>", head = """<meta name="viewport" content="width=device-width, initial-scale=1">""") must
        not contain "zoom-not-blocked"
    }
  }

  "a label" should {
    "be flagged when it points at nothing" in {
      fired("""<h1>A</h1><label for="ghost">Name</label>""") must contain("label-for-resolves")
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
      fired("""<h1>A</h1><label for="a">A</label><input id="a" name="dup">
              |<label for="b">B</label><input id="b" name="dup">""".stripMargin) must
        contain("unambiguous-field-names")
    }

    "order its findings when more than one name is duplicated" in {
      val names = allRules
        .check(page("""<h1>A</h1>
                      |<label for="z1">Z</label><input id="z1" name="zebra">
                      |<label for="z2">Z</label><input id="z2" name="zebra">
                      |<label for="a1">A</label><input id="a1" name="apple">
                      |<label for="a2">A</label><input id="a2" name="apple">""".stripMargin))
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
      fired("""<h1>A</h1><form method="get" action="/login">
              |<label for="p">Password</label><input type="password" id="p" name="p"></form>""".stripMargin) must
        contain("no-password-in-get")
    }

    "accept a password submitted by POST" in {
      fired("""<h1>A</h1><form method="post" action="/login">
              |<label for="p">Password</label><input type="password" id="p" name="p"></form>""".stripMargin) must
        not contain "no-password-in-get"
    }

    "reject a javascript: link" in {
      fired("""<h1>A</h1><a href="javascript:doThing()">Do the thing</a>""") must contain("no-javascript-href")
    }

    "warn about a new tab that can reach back, and accept rel=noopener" in {
      val v =
        violation("""<h1>A</h1><a href="/x" target="_blank">Guidance (opens in new tab)</a>""", "target-blank-is-safe")
      v.map(_.severity)                                                                                mustBe Some(Severity.Warning)
      fired("""<h1>A</h1><a href="/x" target="_blank" rel="noopener">Guidance (opens in new tab)</a>""") must
        not contain "target-blank-is-safe"
    }
  }

  "the rule sets" should {
    "stay disjoint and uniquely identified" in {
      val ids = (WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all).map(_.id)
      ids.distinct.size                                         mustBe ids.size
      SecurityStandards.all.map(_.id)                             must contain allOf ("no-password-in-get", "no-javascript-href")
      SecurityStandards.allExcept("no-javascript-href").map(_.id) must not contain "no-javascript-href"
      SecurityStandards.only("no-javascript-href").map(_.id)    mustBe Seq("no-javascript-href")
    }
  }

}
