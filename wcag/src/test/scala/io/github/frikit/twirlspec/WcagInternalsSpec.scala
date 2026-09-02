package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.wcag.{TwirlStandards, WcagStandards}

/** Edge cases of the accessibility and rendering rules. */
class WcagInternalsSpec extends AnyWordSpec with Matchers with TwirlSpec {

  override def standardsRules = WcagStandards.all ++ TwirlStandards.all

  private def pageOf(body: String, lang: play.api.i18n.Lang = english): Page =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="${lang.code}"><head><title>t</title></head>
         |<body><main id="main-content">$body</main></body></html>""".stripMargin,
      lang,
      messagesApi.preferred(Seq(lang))
    )

  "the standards" should {

    "flag an empty title, a missing main landmark and a nameless submit control" in {
      val noTitle = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title></title></head>
          |<body><h1>A</h1><button type="submit"></button></body></html>""".stripMargin,
        english,
        messages
      )
      val fired   = standardsExpectation.check(noTitle).map(_.rule)
      fired must contain("title-present")
      fired must contain("main-landmark")
      fired must contain("submit-has-name")
    }

    "quote the surrounding text when a Scala value leaks in" in {
      val leak = pageOf("<h1>A</h1><p>Your name is Some(Ada) according to our records</p>")
      val v    = standardsExpectation.check(leak).find(_.rule == "no-scala-leakage")
      v.flatMap(_.actual).getOrElse("") must include("Some(Ada)")
    }
  }

}
