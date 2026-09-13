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
import io.github.frikit.twirlspec.standards._
import io.github.frikit.twirlspec.quality.{
  MetadataStandards,
  PerformanceStandards,
  SemanticStandards
}

class QualityStandardsSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val allRules =
    Rule.expectation(
      SemanticStandards.all ++ PerformanceStandards.all ++ MetadataStandards.all
    )

  private def page(
      body: String,
      head: String = """<meta charset="utf-8">"""
  ): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title>$head</head>
         |<body><main>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def fired(
      body: String,
      head: String = """<meta charset="utf-8">"""
  ): Seq[String] =
    allRules.check(page(body, head)).map(_.rule)

  private def find(
      body: String,
      rule: String,
      head: String = """<meta charset="utf-8">"""
  ) =
    allRules.check(page(body, head)).find(_.rule == rule)

  "semantics" should {

    "reject a control inside another control" in {
      fired("""<a href="/x">Read <button>more</button></a>""") must contain(
        "no-nested-interactive"
      )
      fired("""<button>Save <a href="/x">now</a></button>""") must contain(
        "no-nested-interactive"
      )
      fired(
        """<a href="/x">Read more</a><button>Save</button>"""
      ) must not contain "no-nested-interactive"
    }

    "reject a non-list-item inside a list" in {
      fired("<ul><li>One</li><div>Two</div></ul>") must contain(
        "lists-contain-list-items"
      )
      fired(
        "<ul><li>One</li><li>Two</li></ul>"
      ) must not contain "lists-contain-list-items"
      fired(
        "<ul><li>One</li><script>var x = 1</script></ul>"
      ) must not contain "lists-contain-list-items"
    }

    "warn about presentational markup" in {
      val v = find("<p><b>Important</b></p>", "no-presentational-markup")
      v.map(_.severity) mustBe Some(Severity.Warning)
      fired("<p><strong>Important</strong> and <em>emphasised</em></p>") must
        not contain "no-presentational-markup"
    }

    "warn about consecutive line breaks" in {
      fired("<p>One<br><br>Two</p>") must contain("no-br-for-layout")
      fired("<p>Line one<br>Line two</p>") must not contain "no-br-for-layout"
    }
  }

  "page weight" should {

    "warn about an image with no dimensions" in {
      fired("""<img src="/logo.png" alt="Logo">""") must contain(
        "images-have-dimensions"
      )
      fired("""<img src="/logo.png" alt="Logo" width="80" height="40">""") must
        not contain "images-have-dimensions"
    }

    "warn about a render-blocking script, and accept defer, async or module" in {
      fired(
        "<p>x</p>",
        head = """<meta charset="utf-8"><script src="/a.js"></script>"""
      ) must
        contain("scripts-are-deferred")
      Seq("defer", "async", """type="module"""").foreach { attr =>
        fired(
          "<p>x</p>",
          head =
            s"""<meta charset="utf-8"><script src="/a.js" $attr></script>"""
        ) must
          not contain "scripts-are-deferred"
      }
    }

    "warn about an oversized data uri and an oversized style block" in {
      val big = "A" * 11000
      fired(
        s"""<img src="data:image/png;base64,$big" alt="Big">"""
      ) must contain("no-oversized-data-uri")
      fired(
        "<p>x</p>",
        head = s"""<meta charset="utf-8"><style>$big</style>"""
      ) must
        contain("no-large-inline-style")
      fired(
        """<img src="data:image/png;base64,AAAA" alt="Small">"""
      ) must not contain "no-oversized-data-uri"
    }
  }

  "metadata" should {

    "require a character encoding" in {
      fired("<p>x</p>", head = "") must contain("has-charset")
      fired(
        "<p>x</p>",
        head =
          """<meta http-equiv="Content-Type" content="text/html; charset=utf-8">"""
      ) must
        not contain "has-charset"
    }

    "warn about noindex, a missing description and an overlong title" in {
      fired(
        "<p>x</p>",
        head =
          """<meta charset="utf-8"><meta name="robots" content="noindex, nofollow">"""
      ) must
        contain("not-noindex")
      fired("<p>x</p>") must contain("has-meta-description")
      fired(
        "<p>x</p>",
        head =
          """<meta charset="utf-8"><meta name="description" content="A page">"""
      ) must
        not contain "has-meta-description"
      fired(
        "<p>x</p>",
        head =
          """<meta charset="utf-8"><meta name="description" content="   ">"""
      ) must
        contain("has-meta-description")

      val long = Page.fromString(
        s"""<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
           |<title>${"A very long title indeed " * 4}</title></head><body><main><p>x</p></main></body></html>""".stripMargin,
        english,
        messages
      )
      allRules.check(long).map(_.rule) must contain("title-is-concise")
    }

    "skip page level rules for a fragment" in {
      allRules
        .check(Page.fromString("<p>just a component</p>", english, messages))
        .map(_.rule) must
        not contain "has-charset"
    }
  }

  "the module" should {
    "expose its rules with unique ids, disjoint from the other sets" in {
      val ours =
        (SemanticStandards.all ++ PerformanceStandards.all ++ MetadataStandards.all)
          .map(_.id)
      ours.distinct.size mustBe ours.size
      ours must have size 12
      SemanticStandards.only("no-br-for-layout").map(_.id) mustBe Seq(
        "no-br-for-layout"
      )
      PerformanceStandards.allExcept("images-have-dimensions").map(_.id) must
        not contain "images-have-dimensions"
      MetadataStandards.expectation().description must include(
        "12".take(0) + "rules"
      )
    }
  }

}
