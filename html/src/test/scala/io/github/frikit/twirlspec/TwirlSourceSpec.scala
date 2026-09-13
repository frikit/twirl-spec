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

import io.github.frikit.twirlspec.html.{TagBalance, TwirlSource}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A template is two languages at once, and only one of them writes tags. */
class TwirlSourceSpec extends AnyWordSpec with Matchers {

  /** Blanking leaves the spaces where the Scala was, so compare structure
    * rather than spacing.
    */
  private def html(template: String) =
    TwirlSource.htmlOnly(template).replaceAll("\\s+", "")

  "htmlOnly" should {

    "keep the markup and drop the expression" in {
      html("""<p>@messages("hello")</p>""") mustBe "<p></p>"
    }

    "drop a comparison so it is not read as a tag" in {
      html("@if(page < total) { <div>x</div> }") mustBe "{<div>x</div>}"
    }

    "drop the arrow in a for comprehension" in {
      html("@for(item <- items) { <li>x</li> }") mustBe "{<li>x</li>}"
    }

    "drop a scala block" in {
      html("""<p>@{ if (a<b) "x" else "y" }</p>""") mustBe "<p></p>"
    }

    "drop a parameter declaration" in {
      html("@(form: Form[String], mode: Mode)\n<p>x</p>") mustBe "<p>x</p>"
    }

    "drop a dotted chain and its arguments" in {
      html(
        """<a href="@routes.Foo.bar(1)">x</a>"""
      ) mustBe """<ahref="">x</a>"""
    }

    "drop type parameters" in {
      html("""<p>@render[Widget](x)</p>""") mustBe "<p></p>"
    }

    "drop an import line but keep what follows it" in {
      html("@import config.AppConfig\n<p>x</p>") mustBe "<p>x</p>"
    }

    "cope with an import at the end of the file" in {
      html("<p>x</p>\n@import config.AppConfig") mustBe "<p>x</p>"
    }

    "drop a twirl comment" in {
      html("<p>@* a <div> that is only talk *@x</p>") mustBe "<p>x</p>"
    }

    "cope with a comment that is never closed" in {
      html("<p>x</p>@* trailing") mustBe "<p>x</p>"
    }

    "keep an escaped at-sign out of the way" in {
      html("<p>a@@b</p>") mustBe "<p>ab</p>"
    }

    "leave an at-sign that starts nothing" in {
      html("<p>@</p>") mustBe "<p>@</p>"
    }

    "cope with an at-sign at the very end" in {
      html("<p>x</p>@") mustBe "<p>x</p>"
    }

    "not be fooled by a bracket inside a string" in {
      html("""<p>@messages("a ) b")</p>""") mustBe "<p></p>"
    }

    "not be fooled by an escaped quote inside a string" in {
      html("""<p>@messages("a \" ) b")</p>""") mustBe "<p></p>"
    }

    "cope with an argument list that is never closed" in {
      html("""<p>@messages("x"""") mustBe "<p>"
    }

    "drop a bare else-if continuation, which twirl allows without an at-sign" in {
      html(
        "@if(a) { <p>1</p> } else if(b < c) { <p>2</p> }"
      ) mustBe "{<p>1</p>}{<p>2</p>}"
    }

    "drop a bare else" in {
      html(
        "@if(a) { <p>1</p> } else { <p>2</p> }"
      ) mustBe "{<p>1</p>}{<p>2</p>}"
    }

    "drop a bare else-if whose condition is not bracketed" in {
      html("} else if x { <p>1</p> }") must include("<p>1</p>")
    }

    "leave a closing brace that continues nothing alone" in {
      html("<p>1</p>}<p>2</p>") mustBe "<p>1</p>}<p>2</p>"
    }

    "not treat a word merely beginning with else as a continuation" in {
      html("}elsewhere<p>x</p>") mustBe "}elsewhere<p>x</p>"
    }

    "not treat a word merely beginning with if as the condition" in {
      html("} else iffy <p>x</p>") mustBe "}iffy<p>x</p>"
    }

    "drop a fragment definition, which is half a tag pair by design" in {
      html("@link = {<a href=\"/x\">}\n<p>y</p>") mustBe "<p>y</p>"
    }

    "still read a value that is assigned a scala block" in {
      html("@title = @{ messages(\"x\") }\n<p>y</p>") mustBe "=<p>y</p>"
    }

    "leave an assignment that opens no block alone" in {
      html("@count = 3\n<p>y</p>") mustBe "=3<p>y</p>"
    }

    "keep line numbers where they were" in {
      TwirlSource.htmlOnly("@if(a < b) {\n\n<div>\n}").count(_ == '\n') mustBe 3
    }

    "keep the newlines inside an expression that spans lines" in {
      TwirlSource
        .htmlOnly("@if(a <\n b) {\n<div>x</div>\n}")
        .count(_ == '\n') mustBe 3
    }

    "match the right bracket when they are nested" in {
      html("""<p>@messages(concat(a, b), "c")</p>""") mustBe "<p></p>"
    }

    "cope with an else at the very end of the template" in {
      html("<p>x</p> } else") mustBe "<p>x</p>}"
    }
  }

  "checkTemplate" should {

    "stay quiet on a template whose scala contains comparisons" in {
      TagBalance.checkTemplate(
        "@if(pageIndex < pageCount) {\n  <li><a href=\"/next\">Next</a></li>\n}"
      ) mustBe empty
    }

    "still find genuinely unbalanced markup" in {
      TagBalance
        .checkTemplate("@if(a) {\n  <div><span>text</div>\n}")
        .map(_.message) mustBe
        List(
          "</div> on line 2 closes <span> from line 2 as well, so the two overlap"
        )
    }

    "still find a stray end tag" in {
      TagBalance
        .checkTemplate("<ul><li>x</li></ul>\n</a>")
        .map(_.message) mustBe
        List("</a> on line 2 closes nothing that was open")
    }
  }

}
