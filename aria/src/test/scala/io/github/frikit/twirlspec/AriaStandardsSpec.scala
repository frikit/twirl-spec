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

import io.github.frikit.twirlspec.aria.{AriaChecks, AriaStandards}
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The static half of what an automated accessibility tool reports, with no browser involved. */
class AriaStandardsSpec extends AnyWordSpec with Matchers with TwirlSpec with AriaChecks {

  private def pageOf(body: String, bodyAttrs: String = "") =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body$bodyAttrs><main><h1>A</h1>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def violations(body: String, bodyAttrs: String = "") =
    AriaStandards.all.flatMap(_.check(pageOf(body, bodyAttrs)))

  private def ids(body: String, bodyAttrs: String = "") = violations(body, bodyAttrs).map(_.rule).distinct

  "the rule set" should {
    "be silent on a page that does nothing wrong" in {
      ids("""<p>Ordinary words</p>""") mustBe empty
    }

    "expose every rule through the mixin" in {
      standardsRules.map(_.id) must contain allElementsOf AriaStandards.all.map(_.id)
    }
  }

  "aria-attr-is-real" should {
    "reject an attribute the specification does not define" in {
      ids("""<div aria-lable="x">y</div>""") must contain("aria-attr-is-real")
    }
    "accept one it does" in {
      ids("""<div aria-label="x">y</div>""") must not contain "aria-attr-is-real"
    }
  }

  "aria-attr-value-is-allowed" should {
    "reject a token outside the allowed set" in {
      violations("""<button aria-expanded="yes">x</button>""")
        .find(_.rule == "aria-attr-value-is-allowed")
        .map(_.actual) mustBe Some(Some("yes"))
    }
    "accept an allowed token whatever its case" in {
      ids("""<button aria-expanded="TRUE">x</button>""") must not contain "aria-attr-value-is-allowed"
    }
  }

  "aria-role-is-real" should {
    "reject a role that is not in the specification" in {
      ids("""<div role="widget">x</div>""") must contain("aria-role-is-real")
    }
    "reject an abstract role" in {
      ids("""<div role="widget">x</div>""") must contain("aria-role-is-real")
    }
    "accept a real one" in {
      ids("""<div role="note">x</div>""") must not contain "aria-role-is-real"
    }
  }

  "aria-required-attr" should {
    "notice a checkbox with no checked state" in {
      ids("""<div role="checkbox">x</div>""") must contain("aria-required-attr")
    }
    "accept one that declares it" in {
      ids("""<div role="checkbox" aria-checked="false">x</div>""") must not contain "aria-required-attr"
    }
  }

  "aria-required-parent" should {
    "notice a tab outside a tablist" in {
      ids("""<div role="tab">x</div>""") must contain("aria-required-parent")
    }
    "accept one inside" in {
      ids("""<div role="tablist"><div role="tab">x</div></div>""") must not contain "aria-required-parent"
    }
  }

  "aria-required-children" should {
    "notice a tablist with no tabs" in {
      ids("""<div role="tablist"><p>nothing</p></div>""") must contain("aria-required-children")
    }
    "say what it found instead" in {
      violations("""<div role="tablist"><div role="note">x</div></div>""")
        .find(_.rule == "aria-required-children")
        .flatMap(_.actual) mustBe Some("note")
    }
    "accept one with a tab" in {
      ids("""<div role="tablist"><div role="tab">x</div></div>""") must not contain "aria-required-children"
    }
  }

  "aria-hidden-not-on-body" should {
    "notice a page hidden from assistive technology" in {
      ids("""<p>x</p>""", bodyAttrs = """ aria-hidden="true"""") must contain("aria-hidden-not-on-body")
    }
  }

  "no-role-conflict" should {
    "notice a presentational element that is still named" in {
      ids("""<div role="presentation" aria-label="x">y</div>""") must contain("no-role-conflict")
    }
    "notice a presentational element that can still take focus" in {
      ids("""<div role="none" tabindex="0">y</div>""") must contain("no-role-conflict")
    }
    "accept one that is neither" in {
      ids("""<div role="presentation" tabindex="-1">y</div>""") must not contain "no-role-conflict"
    }
  }

  "accesskey-unique" should {
    "notice two elements sharing a key" in {
      ids("""<a href="/a" accesskey="s">a</a><a href="/b" accesskey="S">b</a>""") must contain("accesskey-unique")
    }
    "accept distinct keys" in {
      ids("""<a href="/a" accesskey="s">a</a><a href="/b" accesskey="t">b</a>""") must not contain "accesskey-unique"
    }
  }

  "autocomplete-is-valid" should {
    "reject a token the specification does not define" in {
      ids("""<input autocomplete="fullname">""") must contain("autocomplete-is-valid")
    }
    "accept a plain token" in {
      ids("""<input autocomplete="given-name">""") must not contain "autocomplete-is-valid"
    }
    "accept a token behind a modifier" in {
      ids("""<input autocomplete="shipping postal-code">""") must not contain "autocomplete-is-valid"
    }
    "reject a modifier on its own" in {
      ids("""<input autocomplete="shipping">""") must contain("autocomplete-is-valid")
    }
    "leave an empty attribute alone" in {
      ids("""<input autocomplete="">""") must not contain "autocomplete-is-valid"
    }
  }

  "no-meta-refresh" should {
    "notice a page that refreshes itself" in {
      val page = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title>
          |<meta http-equiv="refresh" content="5"></head><body><main><h1>A</h1></main></body></html>""".stripMargin,
        english,
        messages
      )
      AriaStandards.all.flatMap(_.check(page)).map(_.rule) must contain("no-meta-refresh")
    }
  }

  "no-deprecated-effects" should {
    "notice a blink" in {
      ids("<blink>x</blink>") must contain("no-deprecated-effects")
    }
    "notice a marquee" in {
      ids("<marquee>x</marquee>") must contain("no-deprecated-effects")
    }
  }

  "embedded-content-has-name" should {
    "notice an unnamed object" in {
      ids("""<object data="/x"></object>""") must contain("embedded-content-has-name")
    }
    "accept one named by aria-label" in {
      ids("""<object data="/x" aria-label="A chart"></object>""") must not contain "embedded-content-has-name"
    }
    "accept an image button with alt text" in {
      ids("""<input type="image" alt="Search">""") must not contain "embedded-content-has-name"
    }
    "accept an svg named by a title child" in {
      ids("""<svg role="img"><title>A chart</title></svg>""") must not contain "embedded-content-has-name"
    }
    "report only as a warning" in {
      violations("""<object data="/x"></object>""")
        .filter(_.rule == "embedded-content-has-name")
        .map(_.severity.label) mustBe List("warning")
    }
  }

  "table-headers-resolve" should {
    "notice a headers attribute naming nothing" in {
      ids("""<table><tr><th id="a">A</th></tr><tr><td headers="b">1</td></tr></table>""") must
        contain("table-headers-resolve")
    }
    "accept one that resolves" in {
      ids("""<table><tr><th id="a">A</th></tr><tr><td headers="a">1</td></tr></table>""") must
        not contain "table-headers-resolve"
    }
    "say what the table does offer" in {
      violations("""<table><tr><th id="a">A</th></tr><tr><td headers="b">1</td></tr></table>""")
        .find(_.rule == "table-headers-resolve")
        .flatMap(_.expected) mustBe Some("a")
    }
  }

  "definition-list-structure" should {
    "notice a stray element" in {
      ids("<dl><dt>a</dt><p>stray</p></dl>") must contain("definition-list-structure")
    }
    "accept terms, descriptions and wrappers" in {
      ids("<dl><div><dt>a</dt><dd>b</dd></div></dl>") must not contain "definition-list-structure"
    }
  }

  "landmarks-are-distinguishable" should {
    "notice two navigations with no names" in {
      ids("""<nav><a href="/a">a</a></nav><nav><a href="/b">b</a></nav>""") must
        contain("landmarks-are-distinguishable")
    }
    "accept two that are named apart" in {
      ids(
        """<nav aria-label="Primary"><a href="/a">a</a></nav><nav aria-label="Footer"><a href="/b">b</a></nav>"""
      ) must
        not contain "landmarks-are-distinguishable"
    }
  }

  /** Content placed directly in the body, so header and footer are page-level landmarks. */
  private def bodyPageOf(body: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head><body>$body</body></html>""",
      english,
      messages
    )

  private def bodyIds(body: String) = AriaStandards.all.flatMap(_.check(bodyPageOf(body))).map(_.rule).distinct

  "the roles a tag implies" should {

    "treat a list as needing list items" in {
      ids("""<ul></ul>""") must contain("aria-required-children")
    }

    "treat a list item outside a list as misplaced" in {
      ids("""<li>orphan</li>""") must contain("aria-required-parent")
    }

    "treat two unnamed asides as indistinguishable" in {
      ids("""<aside>one</aside><aside>two</aside>""") must contain("landmarks-are-distinguishable")
    }

    "treat two unnamed forms as indistinguishable" in {
      ids("""<form action="/a"></form><form action="/b"></form>""") must contain("landmarks-are-distinguishable")
    }

    "treat a header and footer in the body as page-level landmarks" in {
      bodyIds("""<header>one</header><header>two</header><main><h1>A</h1></main>""") must
        contain("landmarks-are-distinguishable")
      bodyIds("""<footer>one</footer><footer>two</footer><main><h1>A</h1></main>""") must
        contain("landmarks-are-distinguishable")
    }

    "not treat a header inside main as the page banner" in {
      ids("""<header>one</header><header>two</header>""") must not contain "landmarks-are-distinguishable"
    }
  }

  "naming and locating things" should {

    "name a landmark by the element aria-labelledby points at" in {
      ids("""<p id="t1">Primary</p><nav aria-labelledby="t1"><a href="/a">a</a></nav>
            |<p id="t2">Footer</p><nav aria-labelledby="t2"><a href="/b">b</a></nav>""".stripMargin) must
        not contain "landmarks-are-distinguishable"
    }

    "locate a faulty element by its id where it has one" in {
      violations("""<div id="thing" role="widget">x</div>""")
        .find(_.rule == "aria-role-is-real")
        .flatMap(_.where) mustBe Some("#thing")
    }

    "locate one by tag and class where it has no id" in {
      violations("""<div class="a b" role="widget">x</div>""")
        .find(_.rule == "aria-role-is-real")
        .flatMap(_.where) mustBe Some("<div>.a")
    }
  }

  "table-headers-resolve" should {

    "say so when the table offers no headers at all" in {
      violations("""<table><tr><td headers="b">1</td></tr></table>""")
        .find(_.rule == "table-headers-resolve")
        .flatMap(_.expected) mustBe Some("a th with an id")
    }
  }

}
