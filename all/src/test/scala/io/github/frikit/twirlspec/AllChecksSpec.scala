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
import io.github.frikit.twirlspec.aria.AriaStandards
import io.github.frikit.twirlspec.html.HtmlStandards
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.wcag.{
  SecurityStandards,
  TwirlStandards,
  WcagStandards
}
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.quality.{
  MetadataStandards,
  PerformanceStandards,
  SemanticStandards
}

/** One mixin has to be the same as four. */
class AllChecksSpec
    extends AnyWordSpec
    with Matchers
    with Bilingual
    with AllChecks {

  "AllChecks" should {

    "carry every rule from every module, and nothing twice" in {
      val ids = standardsRules.map(_.id)
      ids must contain allElementsOf WcagStandards.all.map(_.id)
      ids must contain allElementsOf TwirlStandards.all.map(_.id)
      ids must contain allElementsOf SecurityStandards.all.map(_.id)
      ids must contain allElementsOf GovukStandards.all.map(_.id)
      ids must contain allElementsOf SemanticStandards.all.map(_.id)
      ids must contain allElementsOf PerformanceStandards.all.map(_.id)
      ids must contain allElementsOf MetadataStandards.all.map(_.id)
      ids must contain allElementsOf AriaStandards.all.map(_.id)
      ids must contain allElementsOf HtmlStandards.all.map(_.id)
      ids.distinct.size mustBe ids.size
      ids.size mustBe (WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all ++
        GovukStandards.all ++ SemanticStandards.all ++ PerformanceStandards.all ++ MetadataStandards.all ++
        AriaStandards.all ++ HtmlStandards.all).size
    }

    "bring the message-file matchers with it" in {
      messagesApi must beConsistentAcrossLanguages()
    }

    "check a page against all of them at once" in {
      val broken = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1><h1>B</h1><img src="/x.png"><p>Some(leak)</p>
          |<a href="javascript:go()">go</a><div role="widget">x</div><flurble>y</flurble></main></body></html>""".stripMargin,
        english,
        messages
      )
      val fired = standardsExpectation.check(broken).map(_.rule)
      fired must contain("one-h1") // WcagStandards
      fired must contain("no-scala-leakage") // TwirlStandards
      fired must contain("no-javascript-href") // SecurityStandards
      fired must contain("has-charset") // MetadataStandards
      fired must contain("aria-role-is-real") // AriaStandards
      fired must contain("known-elements") // HtmlStandards
    }
  }

}
