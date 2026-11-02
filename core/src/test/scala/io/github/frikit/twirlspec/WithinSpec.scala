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

import io.github.frikit.twirlspec.expect.{Expectation, Violation}
import io.github.frikit.twirlspec.page.{Page, Roles}
import io.github.frikit.twirlspec.standards.Rule
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{DefaultMessagesApi, Lang, MessagesImpl}

/** One part of a page at a time, for a page with two of something. */
class WithinSpec extends AnyWordSpec with Matchers with TwirlSpecDsl {

  private val lang = Lang("en")
  private val messages = MessagesImpl(
    lang,
    new DefaultMessagesApi(
      Map("en" -> Map("name" -> "Name", "born" -> "Born", "city" -> "City"))
    )
  )

  private def pageOf(body: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>The title</title></head><body>$body</body></html>""",
      lang,
      messages
    )

  private def passes(body: String, e: Expectation) =
    withClue(e.description)(e.check(pageOf(body)) mustBe empty)

  private def rules(body: String, e: Expectation) =
    e.check(pageOf(body)).map(_.rule)

  private val twoForms =
    """<form id="search" action="/search" method="get"><input name="q" value="s" aria-label="Q"></form>
      |<form id="save" action="/save" method="post"><input name="q" value="m" aria-label="Q"></form>""".stripMargin

  private val twoTables =
    """<table id="people"><tr><th scope="col">Name</th><th scope="col">Born</th></tr>
      |<tr><th scope="row">Ada</th><td>1815</td></tr></table>
      |<table id="places"><tr><th>City</th></tr><tr><td>Leeds</td></tr></table>""".stripMargin

  "within" should {

    "ask of the form it names, where the page asks of the first" in {
      rules(twoForms, formPostsTo("/save")) must not be empty
      passes(twoForms, within("#save")(formPostsTo("/save")))
      passes(twoForms, within("form[action='/save']")(formPostsTo("/save")))
      rules(twoForms, within("#search")(formPostsTo("/save"))) must
        contain("within(#search) form action")
    }

    "read a form's values from that form alone" in {
      passes(twoForms, within("#save")(formValues("q" -> "m")))
      passes(twoForms, within("#search")(formValues("q" -> "s")))
      rules(twoForms, within("#search")(formValues("q" -> "m"))) must
        not be empty
    }

    "fail unless what it names is one element" in {
      val none = within("#gone")().check(pageOf(twoForms))
      none.map(_.rule) mustBe Seq("within(#gone)")
      val two = within("form")().check(pageOf(twoForms))
      two.map(_.message) mustBe Seq(
        "2 elements matched, so this assertion is ambiguous"
      )
    }

    "look only inside for what it is asked" in {
      val body = """<div id="a"><p>Inside</p><h2>Sub</h2></div><p>Outside</p>"""
      passes(body, within("#a")(contentText("Inside")))
      rules(body, within("#a")(contentText("Outside"))) must not be empty
      val page = pageOf(body)
      val inside = page.scopedTo(page.document.getElementById("a"))
      inside.text mustBe "Inside Sub"
      inside.contains("Outside") mustBe false
      inside.headings mustBe List((2, "Sub"))
      page.text mustBe "Inside Sub Outside"
    }

    "count the roles it can see, by the name each is given" in {
      val body =
        """<span id="l">Go</span><div id="a"><button aria-labelledby="l"></button></div><button>Go</button>"""
      passes(body, within("#a")(role("button").namedText("Go").occurring(1)))
      passes(body, within("#a")(role("button").occurring(1)))
      val page = pageOf(body)
      Roles.matching(page.document, "button") must have size 2
    }

    "read a selector against the whole page, and keep what is inside" in {
      passes(
        """<table><tbody id="rows"><tr><td>Ada</td></tr></tbody></table>""",
        within("#rows")(tableRow("Ada"))
      )
      passes(
        """<main><div id="a"><p>x</p></div></main>""",
        within("#a")(cssSelector("main p"))
      )
      rules(
        """<main><div id="a"></div><p>x</p></main>""",
        within("#a")(cssSelector("main p"))
      ) must not be empty
    }

    "take a landmark's role from where it really sits" in {
      val body =
        """<article><div id="a"><header>x</header><aside>y</aside></div></article>"""
      passes(body, within("#a")(role("banner").occurring(0)))
      passes(body, within("#a")(role("complementary").occurring(0)))
    }

    "follow an error summary's link out of the part it was asked about" in {
      val body =
        """<div id="summary" class="govuk-error-summary"><ul class="govuk-error-summary__list">
          |<li><a href="#x">Enter x</a></li></ul></div>
          |<p id="x-error" class="govuk-error-message">Enter x</p><input id="x" name="x">""".stripMargin
      val page = pageOf(body)
      val inside = page.scopedTo(page.document.getElementById("summary"))
      inside.errorSummaryDanglingLinks mustBe empty
      inside.landsOn("x", "x") mustBe true
      inside.errorAt("x").map(_.id) mustBe Some("x-error")
    }

    "count where an error summary's link leads, as the page would" in {
      val page = pageOf(
        """<div id="summary" class="govuk-error-summary"><ul class="govuk-error-summary__list">
          |<li><a href="#x">Name</a></li></ul></div><input id="x" name="x">""".stripMargin
      )
      within("#summary")(errorSummary("x" -> "name")).check(page) mustBe empty
      page.touchedAnchors must contain("#x")
    }

    "reach outside for what an element says of another by id" in {
      val body =
        """<label for="x">Label</label><p id="x-hint" class="govuk-hint">Hint</p>
          |<div id="a"><input id="x" name="x" aria-describedby="x-hint"></div>""".stripMargin
      passes(
        body,
        within("#a")(textInput("x").labelledText("Label").hintedText("Hint"))
      )
    }

    "still read the page's own title" in {
      passes(twoForms, within("#save")(titleText("The title")))
    }

    "go by what it was given, when it holds another" in {
      val body = """<div id="a"><p id="b">One</p></div><p id="c">Two</p>"""
      passes(body, within("#a")(within("#b")(contentText("One"))))
      rules(body, within("#a")(within("#c")())) mustBe
        Seq("within(#a) within(#c)")
    }

    "keep to the same part when read in another language" in {
      val page = pageOf("""<p id="a">One</p><p>Two</p>""")
      val inside = page.scopedTo(page.document.getElementById("a"))
      inside.withLang(lang, messages).text mustBe "One"
    }

    "count for the page's coverage what it asserts, and only that" in {
      val page =
        pageOf(
          """<div id="a"><p id="b">x</p><p id="c">y</p></div><p id="d">z</p>"""
        )
      within("#a")(element("b")).check(page)
      page.touchedAnchors must contain allOf ("#a", "#b")
      page.touchedAnchors must not contain "#c"
      page.touchedAnchors must not contain "#d"
    }

    "count nothing that fails to find one element" in {
      val page =
        pageOf("""<p id="a" class="x">1</p><p id="b" class="x">2</p>""")
      within(".x")().check(page)
      page.touchedAnchors mustBe empty
    }

    "count nothing where the page is not counting" in {
      val page = pageOf("""<div id="a"><p id="b">x</p></div>""")
      page.withRecordingPaused(within("#a")(element("b")).check(page))
      page.touchedAnchors mustBe empty
    }

    "leave the standards looking at the whole page" in {
      val spec = new TwirlSpecDsl {
        override def standardsRules: Seq[Rule] = Seq(
          Rule("no-outside", "x")(page =>
            if (page.document.select("#outside").isEmpty) Nil
            else Seq(Violation("no-outside", "it is there"))
          )
        )
      }
      val found =
        spec
          .display(spec.within("#a")())
          .apply(pageOf("""<p id="a"></p><p id="outside"></p>"""))
      found.matches mustBe false
      found.failureMessage must include("no-outside")
    }
  }

  "columnHeaders" should {

    "read a table's column headers, less each row's own" in {
      passes(twoTables, within("#people")(columnHeaders("name", "born")))
      passes(twoTables, within("#places")(columnHeaders("city")))
      rules(twoTables, within("#people")(tableHeaders("name", "born"))) must
        not be empty
    }

    "fail on a page with two tables until told which" in {
      columnHeaders("name").check(pageOf(twoTables)).map(_.message) mustBe Seq(
        "2 elements matched, so this assertion is ambiguous"
      )
    }

    "find the one table on a page without being told" in {
      passes(
        """<table><tr><th scope="col">Name</th></tr></table>""",
        columnHeaders("name")
      )
    }

    "say what it found instead" in {
      val found =
        columnHeaders("name").check(
          pageOf("<table><tr><td>x</td></tr></table>")
        )
      found.flatMap(_.actual) mustBe Seq("(no table headers)")
      columnHeaders("born")
        .check(pageOf("<table><tr><th>City</th></tr></table>"))
        .flatMap(_.actual) mustBe Seq("City")
    }

    "fail on no table, and on a key it cannot resolve" in {
      rules("<p>x</p>", columnHeaders("name")) mustBe Seq("columnHeaders")
      columnHeaders("missing")
        .check(pageOf("<table></table>"))
        .map(_.rule) mustBe Seq("columnHeaders")
    }
  }

}
