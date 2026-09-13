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

import io.github.frikit.twirlspec.html.{
  HtmlChecks,
  HtmlStandards,
  HtmlVocabulary,
  TagBalance
}
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Whether the markup a template wrote is the markup it meant. */
class HtmlStandardsSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with HtmlChecks {

  private def pageOf(body: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  private def messagesOf(body: String) =
    HtmlStandards.all.flatMap(_.check(pageOf(body))).map(_.message)

  private def rulesOf(body: String) =
    HtmlStandards.all.flatMap(_.check(pageOf(body))).map(_.rule).distinct

  "TagBalance" should {

    "name an element that was never closed, and the line it opened on" in {
      TagBalance.check("<div>text").map(_.message) mustBe List(
        "<div> opened on line 1 was never closed"
      )
    }

    "count the line properly" in {
      TagBalance.check("\n\n<div>text").map(_.message) mustBe List(
        "<div> opened on line 3 was never closed"
      )
    }

    "report each unclosed element separately" in {
      TagBalance.check("<div><section>x").map(_.message).size mustBe 2
    }

    "report a close tag that closes nothing" in {
      TagBalance.check("<p>x</p></p>").map(_.message) mustBe List(
        "</p> on line 1 closes nothing that was open"
      )
    }

    "report a close tag when nothing at all is open" in {
      TagBalance.check("</div>").map(_.message) mustBe List(
        "</div> on line 1 closes nothing that was open"
      )
    }

    "report a close tag for something that is not open, while other things are" in {
      TagBalance.check("<div></span></div>").map(_.message) mustBe
        List("</span> on line 1 closes nothing that was open")
    }

    "keep the line count right across a multi-line comment" in {
      TagBalance.check("<div>\n<!-- a\nspan\n-->\n</div>\n<p>x") mustBe empty
      TagBalance
        .check("<div>\n<!-- a\nspan\n-->\n<section>x")
        .map(_.message) mustBe
        List(
          "<div> opened on line 1 was never closed",
          "<section> opened on line 5 was never closed"
        )
    }

    "explain overlapping elements" in {
      TagBalance.check("<div><span>text</div>").map(_.message) mustBe
        List(
          "</div> on line 1 closes <span> from line 1 as well, so the two overlap"
        )
    }

    "accept an end tag the specification lets you leave out" in {
      TagBalance.check("<div><p>text</div>") mustBe empty
      TagBalance.check("<ul><li>a<li>b</ul>") mustBe empty
    }

    "accept a void element" in {
      TagBalance.check("""<div><img src="/x"><br>text</div>""") mustBe empty
    }

    "accept a self-closing element" in {
      TagBalance.check("""<div><path d="M0"/></div>""") mustBe empty
    }

    "ignore what is inside a comment" in {
      TagBalance.check("<div><!-- <span> --></div>") mustBe empty
    }

    "cope with a comment that is never closed" in {
      TagBalance.check("<div>x</div><!-- <span>") mustBe empty
    }

    "ignore a less-than sign inside a script" in {
      TagBalance.check("<div><script>if (a<b) {}</script></div>") mustBe empty
    }

    "cope with a raw text element that is never closed" in {
      TagBalance.check("<div><script>if (a<b) {}").map(_.message) must contain(
        "<div> opened on line 1 was never closed"
      )
    }

    "ignore a doctype" in {
      TagBalance.check("<!DOCTYPE html><div>x</div>") mustBe empty
    }

    "leave clean markup alone" in {
      TagBalance.check(
        "<div><p>hello</p><ul><li>a</li></ul></div>"
      ) mustBe empty
    }
  }

  "the rules" should {

    "carry the balance problems onto the page, with the line" in {
      val v = HtmlStandards.all
        .flatMap(_.check(pageOf("<div><span>x</div>")))
        .find(_.rule == "tags-are-balanced")
      v.map(_.message) mustBe Some(
        "</div> on line 2 closes <span> from line 2 as well, so the two overlap"
      )
      v.flatMap(_.where) mustBe Some("line 2")
    }

    "stop after ten balance problems" in {
      val many = (1 to 20).map(_ => "<div>").mkString
      HtmlStandards.all
        .flatMap(_.check(pageOf(many)))
        .count(_.rule == "tags-are-balanced") mustBe 10
    }

    "notice an element that is not in the specification" in {
      messagesOf("<flurble>x</flurble>") must contain(
        "<flurble> is not an HTML element"
      )
    }

    "accept the elements that are" in {
      rulesOf(
        "<section><figure><figcaption>x</figcaption></figure></section>"
      ) must not contain "known-elements"
    }

    "notice an attribute that is not in the specification" in {
      messagesOf("""<div wibble="1">x</div>""") must contain(
        "wibble is not an HTML attribute"
      )
    }

    "say which element carries it" in {
      HtmlStandards.all
        .flatMap(_.check(pageOf("""<div wibble="1">x</div>""")))
        .find(_.rule == "known-attributes")
        .flatMap(_.where) mustBe Some("<div>")
    }

    "accept data and aria attributes" in {
      rulesOf(
        """<div data-module="x" aria-label="y">z</div>"""
      ) must not contain "known-attributes"
    }

    "accept a prefix the project has declared" in {
      new HtmlStandards(Set("acme-")).all
        .flatMap(_.check(pageOf("""<div acme-thing="1">x</div>""")))
        .map(_.rule) must not contain "known-attributes"
    }

    "still object to that prefix when it has not been declared" in {
      rulesOf("""<div acme-thing="1">x</div>""") must contain(
        "known-attributes"
      )
    }

    "leave sound markup alone" in {
      rulesOf("""<p class="a"><a href="/x">link</a></p>""") mustBe empty
    }
  }

  "the vocabulary" should {
    "know an element by any case" in {
      HtmlVocabulary.isKnownElement("DIV") mustBe true
      HtmlVocabulary.isKnownElement("nope") mustBe false
    }
  }

  "mixing in HtmlChecks" should {
    "add the rules to the active set" in {
      standardsRules.map(_.id) must contain allElementsOf HtmlStandards.all.map(
        _.id
      )
    }
  }

}
