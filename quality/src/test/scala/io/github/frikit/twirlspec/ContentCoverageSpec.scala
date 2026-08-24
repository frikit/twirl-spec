package io.github.frikit.twirlspec

import io.github.frikit.twirlspec.page.{Anchors, CoverageRegistry, Page}
import io.github.frikit.twirlspec.standards.{ContentCoverage, CoverageChecks, QualityChecks}
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Rendering a page is not the same as testing it: this is the check that says
  * what a spec never looked at.
  */
class ContentCoverageSpec
    extends AnyWordSpec with Matchers with TwirlSpec with QualityChecks with CoverageChecks with BeforeAndAfterEach {

  override def beforeEach(): Unit = CoverageRegistry.reset()

  private val body =
    """<p id="intro">Hello</p>
      |<a href="/somewhere" id="go" data-journey-click="nav:go">Go somewhere</a>
      |<a href="/bare"></a>
      |<span>untracked</span>""".stripMargin

  private def pageIn(group: String, content: String = body): Page =
    Page
      .fromString(
        s"""<!DOCTYPE html><html lang="en"><head><title>t</title><meta charset="utf-8">
           |<meta name="description" content="A page"></head>
           |<body><header><a href="/skip" id="skip">Skip</a></header>
           |<main><h1>A</h1>$content</main></body></html>""".stripMargin,
        english,
        messages
      )
      .belongingTo(group)

  "Anchors" should {

    "name an element by its id" in {
      Anchors.namesOf(Jsoup.parse("""<p id="a">x</p>""").select("p").first()) mustBe List("#a")
    }

    "name a link by its text" in {
      Anchors.namesOf(Jsoup.parse("""<a href="/x">Read more</a>""").select("a").first()) mustBe
        List("""link "Read more"""")
    }

    "fall back to the href when a link has no text" in {
      Anchors.namesOf(Jsoup.parse("""<a href="/x"></a>""").select("a").first()) mustBe List("link /x")
    }

    "not treat an anchor without an href as a link" in {
      Anchors.namesOf(Jsoup.parse("""<a>x</a>""").select("a").first()) mustBe empty
    }

    "name a tracked attribute" in {
      Anchors.namesOf(Jsoup.parse("""<div data-journey-click="go">x</div>""").select("div").first()) mustBe
        List("data-journey-click=go")
    }

    "ignore an attribute that is not tracked" in {
      Anchors.namesOf(Jsoup.parse("""<div data-other="go">x</div>""").select("div").first()) mustBe empty
    }
  }

  "ContentCoverage" should {

    "list the ids, links and tracked elements inside the main content" in {
      val names = ContentCoverage.anchors(pageIn("a")).map(_.name)
      names must contain allOf ("#intro", "#go", """link "Go somewhere"""", "link /bare", "data-journey-click=nav:go")
    }

    "leave the layout's header out of it" in {
      ContentCoverage.anchors(pageIn("b")).map(_.name) must not contain "#skip"
    }

    "fall back to the whole document when the scope matches nothing" in {
      val names = ContentCoverage.anchors(pageIn("c"), scope = "#nothing-here").map(_.name)
      names must contain("#skip")
    }

    "count everything as unasserted when nothing has been looked at" in {
      ContentCoverage.unasserted(pageIn("d")).map(_.name) must contain("#intro")
    }

    "stop reporting something once a spec has looked at it" in {
      val page = pageIn("e")
      page.byId("intro")
      ContentCoverage.unasserted(page).map(_.name) must not contain "#intro"
    }

    "accept an id to ignore, with or without its hash" in {
      ContentCoverage.unasserted(pageIn("f"), ignored = Set("#intro")).map(_.name) must not contain "#intro"
      ContentCoverage.unasserted(pageIn("g"), ignored = Set("intro")).map(_.name)  must not contain "#intro"
    }

    "group its report by kind" in {
      val report = ContentCoverage.report(ContentCoverage.unasserted(pageIn("h")))
      report must include("were never asserted")
      report must include("id (")
      report must include("link (")
      report must include("tracking (")
    }

    "report nothing but the count when there is nothing to report" in {
      ContentCoverage.report(Nil) mustBe "0 things on this page were never asserted:"
    }
  }

  "CoverageRegistry" should {

    "ignore an empty recording" in {
      CoverageRegistry.record("k", Nil)
      CoverageRegistry.touched("k") mustBe empty
    }

    "know nothing about a group it has never seen" in {
      CoverageRegistry.touched("never-seen") mustBe empty
    }

    "forget everything on reset" in {
      CoverageRegistry.record("k", List("#a"))
      CoverageRegistry.reset()
      CoverageRegistry.touched("k") mustBe empty
    }
  }

  "a page" should {

    "not record what the standards rules touch" in {
      val page = pageIn("i")
      page.withRecordingPaused(page.byId("intro"))
      CoverageRegistry.touched("i") mustBe empty
    }

    "keep its group when handed an empty one" in {
      val page = pageIn("j")
      page.belongingTo("")
      page.byId("intro")
      CoverageRegistry.touched("j") must contain("#intro")
    }

    "record what a role lookup touches" in {
      val page = pageIn("k")
      page.byRole("link")
      CoverageRegistry.touched("k") must contain("""link "Go somewhere"""")
    }
  }

  "the assertEverything matcher" should {

    "fail while part of the page has gone unasserted" in {
      val page   = pageIn("l")
      val thrown = the[org.scalatest.exceptions.TestFailedException] thrownBy (page must assertEverything)
      thrown.getMessage must include("were never asserted")
      thrown.getMessage must include("#intro")
    }

    "pass once every part has been asserted" in {
      val page = pageIn("m", """<p id="only">Hello</p>""")
      page.byId("only")
      page must assertEverything
    }

    "accept the parts a spec deliberately leaves alone" in {
      val page = pageIn("n", """<p id="only">Hello</p>""")
      page must assertEverythingExcept("#only")
    }

    "report what is outstanding without failing" in {
      unassertedContent(pageIn("o")).map(_.name) must contain("#intro")
    }
  }

  "building a failure message" should {

    "not mark anything as asserted" in {
      val page = pageIn("p")
      page.outline
      CoverageRegistry.touched("p") mustBe empty
    }
  }

  "ignoring a block" should {

    "ignore what the block contains" in {
      val page = pageIn("q", """<div id="chrome"><a href="/help" id="help">Help</a></div>""")
      ContentCoverage.unasserted(page, ignored = Set("#chrome")).map(_.name) mustBe empty
    }

    "leave the rest of the page alone" in {
      val page = pageIn("r", """<div id="chrome"><a href="/help">Help</a></div><p id="mine">x</p>""")
      ContentCoverage.unasserted(page, ignored = Set("#chrome")).map(_.name) mustBe List("#mine")
    }
  }

}
