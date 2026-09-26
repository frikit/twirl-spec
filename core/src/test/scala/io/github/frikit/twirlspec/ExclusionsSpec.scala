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

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{CoverageRegistry, Page}
import io.github.frikit.twirlspec.standards.{ExclusionRegistry, Rule, RuleSet}
import org.scalatest.events.Event
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Args, DoNotDiscover, Reporter}
import play.twirl.api.Html

/** Two rules to exclude: one wants an `<x>`, the other forbids a `<y>`. */
object ExclusionsSpec {

  val needsX: Rule = Rule("needs-x", "the page has an x") { page =>
    if (page.document.select("x").isEmpty) Seq(Violation("needs-x", "no x"))
    else Nil
  }

  val noY: Rule = Rule("no-y", "the page has no y") { page =>
    if (page.document.select("y").isEmpty) Nil
    else Seq(Violation("no-y", "a y"))
  }

  object Things extends RuleSet { lazy val all: Seq[Rule] = Seq(needsX, noY) }

  val silent: Reporter = new Reporter { def apply(event: Event): Unit = () }

  /** A suite run by hand, to watch what it leaves behind. */
  @DoNotDiscover
  class Inner extends AnyWordSpec with Matchers with TwirlSpec {
    @volatile var group: String = ""
    @volatile var recorded: Boolean = false

    "inner" should {
      "record what it touches" in {
        val page = render(Html("""<p id="x">x</p>"""))
        page.byId("x")
        group = page.coverageGroup
        recorded = CoverageRegistry.touched(group).contains("#x")
        ExclusionRegistry.record(getClass.getName, "some-rule", fired = false)
        succeed
      }
    }
  }
}

class ExclusionsSpec extends AnyWordSpec with Matchers with TwirlSpec {
  import ExclusionsSpec._

  override def standardsRules: Seq[Rule] = Seq(needsX, noY)

  private def page(markup: String): Page = render(Html(markup))

  "meetStandardsExcept" should {

    "fail on an id that names no rule the spec runs" in {
      val result = meetStandardsExcept("needs-z").apply(page("<x></x>"))
      result.matches mustBe false
      result.failureMessage must include(
        "needs-z, which is not among the rules this spec runs"
      )
      result.failureMessage must include("The rules it runs: needs-x, no-y")
    }

    "leave out the rules it names and keep the rest" in {
      page("<p>no x here</p>") must meetStandardsExcept("needs-x")
      meetStandardsExcept("needs-x").apply(page("<y></y>")).matches mustBe false
    }

    "carry its reason into a failure" in {
      val failed = meetStandardsExcept(Seq("needs-x"), because = "legacy page")
        .apply(page("<y></y>"))
      failed.matches mustBe false
      failed.failureMessage must include("excluded: needs-x — legacy page")
      meetStandardsExcept(Seq("needs-x"), because = "legacy page")
        .apply(page("<p></p>"))
        .matches mustBe true
    }
  }

  "a rule set" should {

    "refuse to select or exclude a rule it does not have" in {
      val only = the[IllegalArgumentException] thrownBy Things.only("nope")
      only.getMessage mustBe
        "Things has no rule nope; its rules are needs-x, no-y"
      an[IllegalArgumentException] must be thrownBy Things.allExcept("nope")
      Things.only("no-y") mustBe Seq(noY)
      Things.allExcept("no-y") mustBe Seq(needsX)
    }
  }

  "the exclusion record" should {

    "keep a rule fired once it has fired, and forget a spec on request" in {
      ExclusionRegistry.record("spec.A", "r", fired = false)
      ExclusionRegistry.unused("spec.A") mustBe Seq("r")
      ExclusionRegistry.record("spec.A", "r", fired = true)
      ExclusionRegistry.record("spec.A", "r", fired = false)
      ExclusionRegistry.unused("spec.A") mustBe empty
      ExclusionRegistry.forget("spec.A")
      ExclusionRegistry.unused("spec.A") mustBe empty
      ExclusionRegistry.unused("never.seen") mustBe empty
    }
  }

  "the coverage record" should {

    "forget one spec's groups and no other's" in {
      CoverageRegistry.record("spec.B#1", List("#a"))
      CoverageRegistry.record("spec.B#2", List("#b"))
      CoverageRegistry.record("spec.Bee#1", List("#c"))
      CoverageRegistry.forget("spec.B")
      CoverageRegistry.touched("spec.B#1") mustBe empty
      CoverageRegistry.touched("spec.B#2") mustBe empty
      CoverageRegistry.touched("spec.Bee#1") mustBe Set("#c")
      CoverageRegistry.forget("spec.Bee")
    }
  }

  "a TwirlSpec suite" should {

    "leave no coverage or exclusion record behind once it has run" in {
      val inner = new Inner
      inner.run(None, Args(silent)).succeeds() mustBe true
      inner.recorded mustBe true
      CoverageRegistry.touched(inner.group) mustBe empty
      ExclusionRegistry.unused(classOf[Inner].getName) mustBe empty
    }

    "leave them alone when it runs one named test" in {
      val inner = new Inner
      inner
        .run(Some("inner should record what it touches"), Args(silent))
        .succeeds() mustBe true
      CoverageRegistry.touched(inner.group) must contain("#x")
      ExclusionRegistry.unused(classOf[Inner].getName) mustBe Seq("some-rule")
      CoverageRegistry.forget(classOf[Inner].getName)
      ExclusionRegistry.forget(classOf[Inner].getName)
    }
  }
}

/** An exclusion whose rule never fires is reported; one whose rule fired on any
  * page is not.
  */
class UnusedExclusionsSpec extends AnyWordSpec with Matchers with TwirlSpec {
  import ExclusionsSpec._

  override def standardsRules: Seq[Rule] = Seq(needsX, noY)

  "unusedExclusions" should {

    "name the exclusions whose rules found nothing" in {
      render(Html("<x></x>")) must meetStandardsExcept("needs-x")
      render(Html("<x></x><y></y>")) must meetStandardsExcept("no-y")
      unusedExclusions mustBe Seq("needs-x")
    }
  }
}
