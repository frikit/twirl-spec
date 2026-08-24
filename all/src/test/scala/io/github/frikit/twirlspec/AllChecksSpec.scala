package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.aria.AriaStandards
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards._

/** One mixin has to be the same as four. */
class AllChecksSpec extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {

  "AllChecks" should {

    "carry every rule from every module, and nothing twice" in {
      val ids = standardsRules.map(_.id)
      ids                 must contain allElementsOf WcagStandards.all.map(_.id)
      ids                 must contain allElementsOf TwirlStandards.all.map(_.id)
      ids                 must contain allElementsOf SecurityStandards.all.map(_.id)
      ids                 must contain allElementsOf GovukStandards.all.map(_.id)
      ids                 must contain allElementsOf SemanticStandards.all.map(_.id)
      ids                 must contain allElementsOf PerformanceStandards.all.map(_.id)
      ids                 must contain allElementsOf MetadataStandards.all.map(_.id)
      ids                 must contain allElementsOf AriaStandards.all.map(_.id)
      ids.distinct.size mustBe ids.size
      ids.size          mustBe (WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all ++
        GovukStandards.all ++ SemanticStandards.all ++ PerformanceStandards.all ++ MetadataStandards.all ++
        AriaStandards.all).size
    }

    "bring the message-file matchers with it" in {
      messagesApi must beConsistentAcrossLanguages()
    }

    "check a page against all of them at once" in {
      val broken = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1><h1>B</h1><img src="/x.png"><p>Some(leak)</p>
          |<a href="javascript:go()">go</a><div role="widget">x</div></main></body></html>""".stripMargin,
        english,
        messages
      )
      val fired  = standardsExpectation.check(broken).map(_.rule)
      fired must contain("one-h1") // WcagStandards
      fired must contain("no-scala-leakage") // TwirlStandards
      fired must contain("no-javascript-href") // SecurityStandards
      fired must contain("has-charset") // MetadataStandards
      fired must contain("aria-role-is-real") // AriaStandards
    }
  }

}
