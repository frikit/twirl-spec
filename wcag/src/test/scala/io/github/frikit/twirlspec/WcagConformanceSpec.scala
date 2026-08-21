package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.{Level, TwirlStandards, WcagStandards, WcagVersion}

/** Selecting rules by conformance level and WCAG version.
  *
  * A project claiming AA should be able to run exactly the rules that bear on
  * that claim, without the AAA rules it never promised and without the house
  * conventions that are not WCAG at all.
  */
class WcagConformanceSpec extends AnyWordSpec with Matchers with TwirlSpec {

  "selecting by conformance level" should {

    "be cumulative, because a conformance claim is" in {
      val a  = WcagStandards.conformingTo(Level.A).map(_.id).toSet
      val aa = WcagStandards.conformingTo(Level.AA).map(_.id).toSet
      val aaa = WcagStandards.conformingTo(Level.AAA).map(_.id).toSet
      a.subsetOf(aa) mustBe true
      aa.subsetOf(aaa) mustBe true
      a.size must be < aaa.size
    }

    "exclude the AAA rules from an AA claim" in {
      val aa = WcagStandards.conformingTo(Level.AA).map(_.id)
      aa must not contain "link-text-is-meaningful" // 2.4.9, Level AAA
      aa must not contain "new-tab-is-announced"    // 3.2.5, Level AAA
      aa must contain("labelled-controls")          // 3.3.2, Level A
      aa must contain("no-empty-headings")          // 2.4.6, Level AA
    }

    "exclude rules that enforce no success criterion" in {
      // one-h1 is a widely held convention, not a WCAG requirement, so it must
      // not appear in a set a project is using to back a conformance claim.
      WcagStandards.conformingTo(Level.AAA).map(_.id) must not contain "one-h1"
      WcagStandards.conventions.map(_.id) must contain("one-h1")
    }
  }

  "selecting by WCAG version" should {

    "be cumulative, because WCAG is additive" in {
      val v20 = WcagStandards.conformingTo(Level.AAA, WcagVersion.V2_0).map(_.id).toSet
      val v21 = WcagStandards.conformingTo(Level.AAA, WcagVersion.V2_1).map(_.id).toSet
      val v22 = WcagStandards.conformingTo(Level.AAA, WcagVersion.V2_2).map(_.id).toSet
      v20.subsetOf(v21) mustBe true
      v21.subsetOf(v22) mustBe true
    }

    "leave a 2.1 rule out of a 2.0 selection" in {
      WcagStandards.introducedIn(WcagVersion.V2_1).map(_.id) must contain("input-purpose-autocomplete")
      WcagStandards.conformingTo(Level.AA, WcagVersion.V2_0).map(_.id) must
        not contain "input-purpose-autocomplete"
      WcagStandards.conformingTo(Level.AA, WcagVersion.V2_1).map(_.id) must
        contain("input-purpose-autocomplete")
    }
  }

  "every tagged rule" should {
    "cite a plausible success criterion" in {
      WcagStandards.all.flatMap(_.criterion).foreach { c =>
        c.number must fullyMatch regex """\d+\.\d+\.\d+"""
        c.title.trim must not be empty
      }
    }

    "report which criteria the rule set covers" in {
      val numbers = WcagStandards.criteria.map(_.number)
      numbers must contain allOf ("1.1.1", "1.3.1", "2.4.4", "3.3.2", "4.1.2", "1.3.5")
      numbers.distinct.size mustBe numbers.size
    }
  }

  "the 2.1 autocomplete rule" should {

    "flag a personal field with no autocomplete" in {
      fired("""<label for="email">Email</label><input id="email" name="email" type="email">""") must
        contain("input-purpose-autocomplete")
    }

    "accept a personal field that declares one" in {
      fired("""<label for="email">Email</label>
              |<input id="email" name="email" type="email" autocomplete="email">""".stripMargin) must
        not contain "input-purpose-autocomplete"
    }

    "leave a field that is not about the user alone" in {
      fired("""<label for="reference">Reference</label><input id="reference" name="reference">""") must
        not contain "input-purpose-autocomplete"
    }
  }

  private def fired(body: String): Seq[String] = {
    val page = Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title></head>
         |<body><main><h1>A</h1><form action="/x" method="post">$body</form></main></body></html>""".stripMargin,
      english,
      messages
    )
    io.github.frikit.twirlspec.standards.Rule
      .expectation(WcagStandards.all ++ TwirlStandards.all)
      .check(page)
      .map(_.rule)
  }
}
