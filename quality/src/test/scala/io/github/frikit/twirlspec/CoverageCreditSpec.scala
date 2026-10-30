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

import io.github.frikit.twirlspec.expect.Expectation
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.quality.{ContentCoverage, CoverageChecks}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** An assertion counts for what it found, not for what it looked through on the
  * way, and everything it checks counts.
  */
class CoverageCreditSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with CoverageChecks {

  private def pageOf(main: String): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head><body><main>$main</main></body></html>""",
      english,
      messages
    )

  /** What is left to assert on this page once these have passed. */
  private def leftAfter(main: String)(checks: Expectation*): List[String] = {
    val page = pageOf(main)
    checkPage(page, checks).violations mustBe empty
    unassertedContent(page).map(_.path)
  }

  private val twoChangeLinks =
    """<a href="/one">Change</a><a href="/two">Change</a>"""

  private val buttons =
    """<button id="s" type="submit">Save</button><button id="o" type="button">Other</button>"""

  private val row =
    """<dl class="govuk-summary-list"><div id="r" class="govuk-summary-list__row">
      |<dt id="k" class="govuk-summary-list__key">Name</dt>
      |<dd id="v" class="govuk-summary-list__value">Ada</dd>
      |<dd class="govuk-summary-list__actions"><a href="/change">Change</a><a href="/remove">Remove</a></dd>
      |</div></dl>""".stripMargin

  private val summary =
    """<div class="govuk-error-summary"><ul class="govuk-error-summary__list">
      |<li><a href="#a">Yes</a></li><li><a href="#b">No</a></li>
      |</ul></div><input id="a" name="a"><input id="b" name="b">""".stripMargin

  "Finding a link" should {

    "count that link, not every link on the page" in {
      leftAfter("""<a href="/a">Alpha</a><a href="/b">Beta</a>""")(
        linkText("Alpha")
      ) mustBe List("""link "Beta" -> /b""")
    }

    "count every link with the words, when no destination is named" in {
      leftAfter(twoChangeLinks)(linkText("Change")) mustBe empty
    }

    "count only the link going where it was told to" in {
      leftAfter(twoChangeLinks)(linkText("Change").to("/one")) mustBe
        List("""link "Change" -> /two""")
    }

    "count nothing when the link goes elsewhere" in {
      val page = pageOf("""<a id="x" href="/a">Alpha</a>""")
      checkPage(
        page,
        Seq(linkWithId("x").to("/b"))
      ).violations must not be empty
      unassertedContent(page).map(_.name) must contain("#x")
    }

    "tell an empty destination from none at all" in {
      leftAfter("""<a id="x" href="">Reload</a>""")(
        linkWithId("x").to("")
      ) mustBe empty
      checkPage(
        pageOf("""<a id="x">Reload</a>"""),
        Seq(linkWithId("x").to(""))
      ).violations must not be empty
    }

    "not count the links it lists when it finds none" in {
      val page = pageOf("""<a href="/a">Alpha</a>""")
      checkPage(page, Seq(linkText("Beta"))).violations must not be empty
      unassertedContent(page).map(_.path) mustBe List("""link "Alpha" -> /a""")
    }
  }

  "Two links with the same words" should {

    "be two things to assert, however one of them is found" in {
      leftAfter(twoChangeLinks)(cssSelector("a[href='/one']")) mustBe
        List("""link "Change" -> /two""")
    }

    "be one thing when they go to the same place" in {
      leftAfter("""<a href="/x">Change</a><a href="/x">Change</a>""")(
        linkText("Change").to("/x")
      ) mustBe empty
    }

    "both be named in the report, with where they go" in {
      ContentCoverage.report(
        ContentCoverage.unasserted(pageOf(twoChangeLinks))
      ) must include("""link "Change" -> /one, link "Change" -> /two""")
    }

    "both be ignored by their words, or one by where it goes" in {
      val page = pageOf(twoChangeLinks)
      ContentCoverage.unasserted(
        page,
        ignored = Set("""link "Change"""")
      ) mustBe empty
      ContentCoverage
        .unasserted(page, ignored = Set("""link "Change" -> /one"""))
        .map(_.path) mustBe List("""link "Change" -> /two""")
    }

    "be listed apart among the page's anchors" in {
      ContentCoverage.anchors(pageOf(twoChangeLinks)).map(_.path) mustBe List(
        """link "Change" -> /one""",
        """link "Change" -> /two"""
      )
    }
  }

  "A role" should {

    "count only the element announcing the name" in {
      leftAfter(buttons)(role("button").namedText("Save")) mustBe List("#o")
      leftAfter(buttons)(role("button").namedMatching("Sa.*".r)) mustBe
        List("#o")
    }

    "count all of them when no name is asked for" in {
      leftAfter(buttons)(role("button")) mustBe empty
    }
  }

  "An expectation that picks one of several" should {

    "count the paragraph saying it" in {
      leftAfter("""<p id="a">Continue</p><p id="b">Other</p>""")(
        paragraph("site.continue")
      ) mustBe List("#b")
    }

    "count the component saying it" in {
      leftAfter(
        """<div id="a" class="govuk-inset-text">Continue</div><div id="b" class="govuk-inset-text">Other</div>"""
      )(insetText("site.continue")) mustBe List("#b")
    }

    "count the row reading it" in {
      leftAfter(
        """<table><tbody><tr id="a"><td>A</td></tr><tr id="b"><td>B</td></tr></tbody></table>"""
      )(tableRow("A")) mustBe List("#b")
    }

    "count the form it read, which is the first" in {
      leftAfter(
        """<form id="f" action="/a" method="post"></form><form id="g" action="/b" method="post"></form>"""
      )(formPostsTo("/a")) mustBe List("#g")
    }

    "count the first match of each selector in a reading order" in {
      leftAfter(
        """<p id="a" class="x">1</p><p id="b" class="y">2</p><p id="c" class="y">3</p>"""
      )(appearsBefore(".x", ".y")) mustBe List("#c")
    }

    "leave a caption to an assertion of its own" in {
      val page =
        """<span id="cap" class="govuk-caption-l">Section</span><h1>Title</h1>"""
      leftAfter(page)(headingText("Title")) mustBe List("#cap")
      leftAfter(page)(headingText("Title"), captionText("Section")) mustBe empty
    }

    "count the heading saying it, as words, a pattern or anything at all" in {
      val page = """<h2 id="a">Continue</h2><h2 id="b">Other</h2>"""
      leftAfter(page)(subheading("site.continue")) mustBe List("#b")
      leftAfter(page)(headingAtLevel(2, matching("Cont.*".r))) mustBe List("#b")
      leftAfter(page)(headingAtLevel(2, anyText)) mustBe empty
    }
  }

  "Words spread over two elements" should {

    "not pass as one element saying them" in {
      val spread = "error.summary.title"
      def fails(main: String, check: Expectation) =
        checkPage(pageOf(main), Seq(check)).violations must not be empty
      fails("<p>There is</p><p>a problem</p>", paragraph(spread))
      fails(
        """<div class="govuk-inset-text">There is</div><div class="govuk-inset-text">a problem</div>""",
        insetText(spread)
      )
      fails(
        """<span class="govuk-caption-l">There is</span><span class="govuk-caption-l">a problem</span>""",
        caption(spread)
      )
    }

    "report an unknown key rather than a mismatch" in {
      checkPage(
        pageOf("""<span class="govuk-caption-l">Section</span>"""),
        Seq(caption("no.such.key"))
      ).violations.map(v => (v.rule, v.message)) mustBe Seq(
        ("caption", "message key `no.such.key` is not defined for lang `en`")
      )
    }

    "show each element's words on their own in the failure" in {
      val failure = checkPage(
        pageOf("<p>There is</p><p>a problem</p>"),
        Seq(paragraph("error.summary.title"))
      ).violations.head
      failure.message must include("in any one element")
      failure.actual mustBe Some("There is | a problem")
    }

    "say the page has none of them when it has none" in {
      checkPage(
        pageOf("<div>There is a problem</div>"),
        Seq(paragraph("error.summary.title"))
      ).violations.head.actual mustBe Some("(empty)")
    }
  }

  "A failed expectation" should {

    def uncovered(main: String, check: Expectation) = {
      val page = pageOf(main)
      checkPage(page, Seq(check)).violations must not be empty
      unassertedContent(page).map(_.name)
    }

    "count nothing for a link saying something else" in {
      uncovered(
        """<a id="x" href="/a">Wrong</a>""",
        linkWithId("x").to("/a").saying("site.yes")
      ) must contain("#x")
    }

    "count nothing for a form going elsewhere" in {
      uncovered(
        """<form id="f" action="/a" method="post"></form>""",
        formPostsTo("/b")
      ) must contain("#f")
    }

    "count nothing for a reading order the wrong way round" in {
      uncovered(
        """<p id="a" class="y">1</p><p id="b" class="x">2</p>""",
        appearsBefore(".x", ".y")
      ) mustBe List("#a", "#b")
    }

    "count nothing for a hidden input holding something else" in {
      uncovered(
        """<input id="h" type="hidden" name="t" value="bad">""",
        hiddenInput("t", "good")
      ) must contain("#h")
    }

    "count nothing for a legend or a description saying something else" in {
      uncovered(
        """<fieldset><legend id="l">Colours</legend><input type="radio" id="r" name="c"><label for="r">Red</label></fieldset>""",
        radioGroup("c").legendIs("site.yes")
      ) must contain("#l")
      uncovered(
        """<input id="q" name="q" aria-describedby="h"><p id="h">Help</p>""",
        describedAs("q", "site.yes")
      ) must contain("#h")
    }
  }

  "What an expectation checks" should {

    "count, for a date input, its container and its fields" in {
      leftAfter(
        """<div id="value"><label for="value.day">Day</label><input id="value.day" name="value.day">
          |<label for="value.month">Month</label><input id="value.month" name="value.month">
          |<label for="value.year">Year</label><input id="value.year" name="value.year"></div>""".stripMargin
      )(dateInput()) mustBe empty
    }

    "count, for a summary row, the row and its key" in {
      leftAfter(row)(summaryRow("checkAnswers.name")) mustBe List(
        "#v",
        """link "Change" -> /change""",
        """link "Remove" -> /remove"""
      )
    }

    "count a summary row's value and change link once they are named" in {
      leftAfter(row)(
        summaryRow("checkAnswers.name")
          .withValue("Ada")
          .withChangeLinkTo("/change")
      ) mustBe List("""link "Remove" -> /remove""")
    }

    "count a summary row's action once it is named" in {
      leftAfter(row)(
        summaryRow("checkAnswers.name").withActions("checkAnswers.change")
      ) mustBe List("#v", """link "Remove" -> /remove""")
    }

    "count, for an inline error, the error" in {
      leftAfter(
        """<p id="value-error" class="govuk-error-message"><span class="govuk-visually-hidden">Error:</span> Yes</p>"""
      )(fieldError("value", "site.yes")) mustBe empty
    }

    "count, for the error summary, the links it names and where they go" in {
      leftAfter(summary)(
        errorSummary("a" -> "site.yes", "b" -> "site.no")
      ) mustBe
        empty
      leftAfter(summary)(errorSummaryContaining("a", "site.yes")) mustBe
        List("""link "No" -> #b""", "#b")
    }

    "count, for the error summary, only the entry saying what it checked" in {
      leftAfter(
        """<div class="govuk-error-summary"><a href="#a">Yes</a><a href="#a">Wrong</a></div><input id="a" name="a">"""
      )(errorSummaryContaining("a", "site.yes")) mustBe
        List("""link "Wrong" -> #a""")
    }

    "count, for a summary list, the rows, keys and values but not the actions" in {
      leftAfter(row)(summaryList("checkAnswers.name" -> "Ada")) mustBe List(
        """link "Change" -> /change""",
        """link "Remove" -> /remove"""
      )
    }

    "count, for a summary list, nothing outside the rows it read" in {
      leftAfter(
        row + """<p id="extra" class="govuk-summary-list__value">Unread</p>"""
      )(summaryList("checkAnswers.name" -> "Ada")) must contain("#extra")
    }

    "count, for a submitted value, the control it comes from and no other" in {
      leftAfter("""<input id="email" name="email" value="a@b.c">""")(
        formValues("email" -> "a@b.c")
      ) mustBe empty
      leftAfter(
        """<input type="radio" id="y" name="c" value="y" checked><input type="radio" id="n" name="c" value="n">"""
      )(formValues("c" -> "y")) mustBe List("#n")
    }

    "count, for a table row, its cells as well" in {
      leftAfter(
        """<table><tbody><tr id="r"><td id="c">A</td></tr></tbody></table>"""
      )(tableRow("A")) mustBe empty
    }

    "count, for a group's legend, the legend" in {
      leftAfter(
        """<fieldset><legend id="l">Yes</legend><input type="radio" id="r" name="c"><label for="r">Red</label></fieldset>"""
      )(radioGroup("c").legendIs("site.yes")) mustBe empty
    }

    "count, for what describes a control, the elements describing it" in {
      leftAfter(
        """<input id="q" name="q" aria-describedby="h"><p id="h">Yes</p>"""
      )(describedAs("q", "site.yes")) mustBe empty
    }

    "count, of two hidden inputs with one name, the first, which it read" in {
      leftAfter(
        """<input id="a" type="hidden" name="t" value="good"><input id="b" type="hidden" name="t" value="bad">"""
      )(hiddenInput("t", "good")) mustBe List("#b")
    }

    "count, of two captions, the one saying it" in {
      leftAfter(
        """<span id="a" class="govuk-caption-l">Section</span><span id="b" class="govuk-caption-l">Other</span>"""
      )(captionText("Section")) mustBe List("#b")
    }
  }

  "A block left out by a selector" should {

    "leave out what it holds, and nothing else" in {
      val page = pageOf(
        """<div class="chrome"><a href="/help">Help</a></div><p id="mine">x</p>"""
      )
      ContentCoverage
        .unassertedOutside(page, Set(".chrome"))
        .map(_.name) mustBe List("#mine")
    }

    "leave out the element it matches, as well as what is inside it" in {
      ContentCoverage.unassertedOutside(
        pageOf("""<a class="chrome" href="/help">Help</a>"""),
        Set(".chrome")
      ) mustBe empty
    }

    "be read from the spec's own setting" in {
      val spec = new TwirlSpecDsl with CoverageChecks {
        override def coverageIgnoredSelectors = Set(".chrome")
      }
      val page = pageOf(
        """<div class="chrome"><a href="/help">Help</a></div><p id="mine">x</p>"""
      )
      spec.unassertedContent(page).map(_.name) mustBe List("#mine")
      spec.assertEverythingExcept("#mine")(page).matches mustBe true
    }
  }

  "A tracked attribute beyond the default" should {

    "be covered by asserting the element carrying it" in {
      val tracked = Set("data-event")
      val page =
        pageOf(
          """<button id="save" type="button" data-event="save">Save</button>"""
        )
      ContentCoverage
        .unasserted(page, trackedAttributes = tracked)
        .map(_.path) must contain("data-event=save")
      page.byId("save")
      ContentCoverage.unasserted(page, trackedAttributes = tracked) mustBe empty
    }

    "be covered even when it is the id or the href" in {
      val page = pageOf("""<a id="x" href="/next">Next</a>""")
      page.byId("x")
      ContentCoverage.unasserted(
        page,
        trackedAttributes = Set("id", "href")
      ) mustBe empty
    }
  }

}
