package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{Rule, TwirlStandards}

/** Markup a browser has to repair. */
class WellFormedHtmlSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def fired(html: String): Seq[String] =
    Rule.expectation(TwirlStandards.all).check(Page.fromString(html, english, messages)).map(_.rule)

  "a template that does not close its tags" should {
    "be reported, with the position the parser gave up" in {
      val bad =
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1><div><p>text</p></main></div></body></html>""".stripMargin
      fired(bad) must contain("well-formed-html")

      val page = Page.fromString(bad, english, messages)
      page.parseErrors      must not be empty
      page.parseErrors.head must include("</main>")
    }
  }

  "well-formed markup" should {
    "raise nothing" in {
      val good =
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1><div><p>text</p></div></main></body></html>""".stripMargin
      fired(good)                                          mustBe empty
      Page.fromString(good, english, messages).parseErrors mustBe empty
    }
  }

  "a page carried through a language switch" should {
    "keep what the parser reported" in {
      val bad  = """<html lang="en"><head><title>t</title></head><body><div><p>x</div></p></body></html>"""
      val page = Page.fromString(bad, english, messages)
      page.withLang(welsh, messagesApi.preferred(Seq(welsh))).parseErrors mustBe page.parseErrors
    }
  }

}
