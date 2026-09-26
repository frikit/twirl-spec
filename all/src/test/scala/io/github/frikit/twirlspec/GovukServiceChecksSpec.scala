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
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.html.HtmlStandards
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.quality.{
  MetadataStandards,
  PerformanceStandards,
  SemanticStandards
}
import io.github.frikit.twirlspec.standards.Rule
import io.github.frikit.twirlspec.wcag.{
  SecurityStandards,
  TwirlStandards,
  WcagStandards
}

/** The GOV.UK service set is AllChecks without the search-result rules. */
class GovukServiceChecksSpec
    extends AnyWordSpec
    with Matchers
    with Bilingual
    with GovukServiceChecks {

  private val everyRule =
    WcagStandards.all ++ TwirlStandards.all ++ SecurityStandards.all ++
      GovukStandards.all ++ SemanticStandards.all ++ PerformanceStandards.all ++
      MetadataStandards.all ++ AriaStandards.all ++ HtmlStandards.all

  /** A service page as the GOV.UK template renders it: a charset and a
    * GOV.UK-pattern title, no description, and kept out of search.
    */
  private val servicePage = Page.fromString(
    """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
      |<meta name="robots" content="noindex, nofollow">
      |<title>What is your full name and date of birth? - Register a trust for a deceased person - GOV.UK</title></head>
      |<body><main id="main-content"><h1>What is your full name and date of birth?</h1></main></body></html>""".stripMargin,
    english,
    messages
  )

  "GovukServiceChecks" should {

    "run every rule AllChecks runs except the search-result ones" in {
      val search = MetadataStandards.searchRules.map(_.id)
      standardsRules.map(_.id) must contain theSameElementsAs
        everyRule.map(_.id).filterNot(search.contains)
      standardsRules.map(_.id) must contain("has-charset")
    }

    "stay quiet about search on a page the GOV.UK template renders" in {
      val fired = standardsExpectation.check(servicePage).map(_.rule)
      fired must contain noneOf (
        "not-noindex",
        "has-meta-description",
        "title-is-concise"
      )
    }

    "leave AllChecks as it was, warning about all three" in {
      val fired =
        Rule.expectation(MetadataStandards.all).check(servicePage).map(_.rule)
      fired must contain allOf (
        "not-noindex",
        "has-meta-description",
        "title-is-concise"
      )
    }

    "leave the platform's tracking-consent script alone, and report the service's own" in {
      val page = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><title>t</title>
          |<script src="/tracking-consent/tracking.js" id="tracking-consent-script-tag" data-gtm-container="b"></script>
          |<script src="/my-service/assets/app.js"></script></head>
          |<body><main id="main-content"><h1>Hello</h1></main></body></html>""".stripMargin,
        english,
        messages
      )
      val blocking = standardsExpectation
        .check(page)
        .filter(_.rule == "scripts-are-deferred")
        .flatMap(_.actual)
      blocking mustBe Seq("/my-service/assets/app.js")
      standardsRules.count(_.id == "scripts-are-deferred") mustBe 1
    }
  }
}

/** The built-in rule traits are already inside AllChecks, so mixing one in
  * again, before or after, cannot bring the search rules back.
  */
class GovukServiceChecksMixedFirstSpec
    extends AnyWordSpec
    with Matchers
    with Bilingual
    with GovukServiceChecks
    with io.github.frikit.twirlspec.quality.QualityChecks {

  "GovukServiceChecks mixed in before QualityChecks" should {
    "still leave the search rules out" in {
      standardsRules.map(_.id) must contain noneOf (
        "not-noindex",
        "has-meta-description",
        "title-is-concise"
      )
    }
  }
}

class GovukServiceChecksMixedLastSpec
    extends AnyWordSpec
    with Matchers
    with Bilingual
    with io.github.frikit.twirlspec.quality.QualityChecks
    with GovukServiceChecks {

  "GovukServiceChecks mixed in after QualityChecks" should {
    "leave the search rules out" in {
      standardsRules.map(_.id) must contain noneOf (
        "not-noindex",
        "has-meta-description",
        "title-is-concise"
      )
    }
  }
}

/** A house trait of the spec's own, mixed in later, adds after the filter —
  * which is why the documentation says to mix GovukServiceChecks in last.
  */
class GovukServiceChecksThenHouseRulesSpec
    extends AnyWordSpec
    with Matchers
    with Bilingual
    with GovukServiceChecks
    with GovukServiceChecksThenHouseRulesSpec.HouseRules {

  "a house trait mixed in after GovukServiceChecks" should {
    "have its rules run, search rules included if it adds them" in {
      standardsRules.map(_.id) must contain("has-meta-description")
    }
  }
}

object GovukServiceChecksThenHouseRulesSpec {
  trait HouseRules extends TwirlSpecDsl {
    override def standardsRules: Seq[Rule] =
      super.standardsRules ++ MetadataStandards.searchRules
  }
}
