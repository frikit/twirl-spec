package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Level
import io.github.frikit.twirlspec.wcag.{TwirlStandards, WcagStandards}

class WcagEdgeCasesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private def fired(body: String): Seq[String] =
    io.github.frikit.twirlspec.standards.Rule
      .expectation(WcagStandards.all ++ TwirlStandards.all)
      .check(
        Page.fromString(
          s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
             |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
          english,
          messages
        )
      )
      .map(_.rule)

  "selecting rules at exactly one level" should {
    "return that level alone, not the levels beneath it" in {
      val aaa = WcagStandards.atLevel(Level.AAA).map(_.id)
      aaa                                       must contain("link-text-is-meaningful")
      aaa                                       must not contain "labelled-controls" // Level A
      WcagStandards.atLevel(Level.AA).map(_.id) must contain("no-empty-headings")
    }
  }

  "findings" should {

    "be ordered when a page has more than one duplicated id" in {
      val page = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head><body><main><h1>A</h1>
          |<p id="zebra">1</p><p id="zebra">2</p><p id="apple">3</p><p id="apple">4</p>
          |</main></body></html>""".stripMargin,
        english,
        messages
      )
      val ids  = WcagStandards
        .expectation(WcagStandards.only("unique-ids"))
        .check(page)
        .map(_.message.split("\"")(1))
      ids mustBe Seq("apple", "zebra")
    }

    "be ordered when a page has more than one ungrouped choice set" in {
      val body  =
        """<label for="z1">Z1</label><input type="radio" id="z1" name="zebra" value="1">
          |<label for="z2">Z2</label><input type="radio" id="z2" name="zebra" value="2">
          |<label for="a1">A1</label><input type="radio" id="a1" name="apple" value="1">
          |<label for="a2">A2</label><input type="radio" id="a2" name="apple" value="2">""".stripMargin
      val page  = Page.fromString(
        s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
           |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
        english,
        messages
      )
      val names = WcagStandards
        .expectation(WcagStandards.only("grouped-choices"))
        .check(page)
        .map(_.message.split("\"")(1))
      names mustBe Seq("apple", "zebra")
    }

    "quote the text around a leaked value that starts the page" in {
      // snippetAround has to cope with a marker at index 0, where there is no
      // preceding context to show.
      fired("<p>Some(Ada) is the first thing on the page</p>") must contain("no-scala-leakage")
    }
  }

}
