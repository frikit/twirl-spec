package io.github.frikit.twirlspec

import io.github.frikit.twirlspec.standards.{CoverageChecks, EntryPoints, QualityChecks}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.twirl.api.Html

/** Twirl's generated entry points, reached by reflection so a spec does not have
  * to spell out each template's arity.
  */
class EntryPointsSpec extends AnyWordSpec with Matchers with TwirlSpec with QualityChecks with CoverageChecks {

  /** Shaped like a Twirl template with two parameter lists. */
  object GoodTemplate {
    def apply(a: String)(b: Int): Html  = Html(s"$a-$b")
    def render(a: String, b: Int): Html = apply(a)(b)
    def f: String => Int => Html        = a => b => apply(a)(b)
    def ref: this.type                  = this
  }

  object SingleListTemplate {
    def apply(a: String, b: Int): Html  = Html(s"$a/$b")
    def render(a: String, b: Int): Html = apply(a, b)
    def f: (String, Int) => Html        = (a, b) => apply(a, b)
    def ref: this.type                  = this
  }

  object NoRender {
    def f: String => Html = a => Html(a)
    def ref: this.type    = this
  }

  object NoF {
    def render(a: String): Html = Html(a)
    def ref: this.type          = this
  }

  object NoRef {
    def render(a: String): Html = Html(a)
    def f: String => Html       = a => Html(a)
  }

  object DriftedRender {
    def apply(a: String): Html  = Html(a)
    def render(a: String): Html = Html(s"$a and more")
    def f: String => Html       = a => apply(a)
    def ref: this.type          = this
  }

  object DriftedF {
    def apply(a: String): Html  = Html(a)
    def render(a: String): Html = apply(a)
    def f: String => Html       = a => Html(s"$a and more")
    def ref: this.type          = this
  }

  object WrongRef {
    def apply(a: String): Html  = Html(a)
    def render(a: String): Html = apply(a)
    def f: String => Html       = a => apply(a)
    def ref: AnyRef             = "not the template"
  }

  object ThrowingRender {
    def apply(a: String): Html  = Html(a)
    def render(a: String): Html = throw new RuntimeException("boom")
    def f: String => Html       = a => apply(a)
    def ref: this.type          = this
  }

  object ThrowingWithCause {
    def apply(a: String): Html = Html(a)

    def render(a: String): Html =
      throw new RuntimeException("outer", new IllegalStateException("inner"))

    def f: String => Html = a => apply(a)
    def ref: this.type    = this
  }

  object GreedyF {
    def apply(a: String): Html      = Html(a)
    def render(a: String): Html     = apply(a)
    def f: String => String => Html = a => b => Html(a + b)
    def ref: this.type              = this
  }

  "EntryPoints" should {

    "be satisfied when render, f and ref all agree with apply" in {
      EntryPoints.problems(GoodTemplate, GoodTemplate("x")(1), Seq("x", 1)) mustBe empty
    }

    "handle a template whose parameters are one list" in {
      EntryPoints.problems(SingleListTemplate, SingleListTemplate("x", 1), Seq("x", 1)) mustBe empty
    }

    "report a render of the wrong arity as missing" in {
      EntryPoints.problems(NoRender, Html("x"), Seq("x")) must contain("render/1 not found on NoRender")
    }

    "report a missing f" in {
      EntryPoints.problems(NoF, Html("x"), Seq("x")) must contain("f not found on NoF")
    }

    "report a missing ref" in {
      EntryPoints.problems(NoRef, Html("x"), Seq("x")) must contain("ref not found on NoRef")
    }

    "catch a render that has drifted from apply" in {
      EntryPoints.problems(DriftedRender, DriftedRender("x"), Seq("x")) must
        contain("render produced different html from apply")
    }

    "catch an f that has drifted from apply" in {
      EntryPoints.problems(DriftedF, DriftedF("x"), Seq("x")) must
        contain("f produced different html from apply")
    }

    "catch a ref that does not return the template" in {
      EntryPoints.problems(WrongRef, WrongRef("x"), Seq("x")).head must include("ref returned")
    }

    "surface an exception thrown by an entry point" in {
      val thrown = the[AssertionError] thrownBy
        EntryPoints.problems(ThrowingRender, Html("x"), Seq("x"))
      thrown.getMessage must include("render threw")
      thrown.getMessage must include("boom")
    }

    "unwrap an exception to its root cause" in {
      val thrown = the[AssertionError] thrownBy
        EntryPoints.problems(ThrowingWithCause, Html("x"), Seq("x"))
      thrown.getMessage must include("inner")
    }

    "complain when f wants more arguments than were supplied" in {
      val thrown = the[AssertionError] thrownBy
        EntryPoints.problems(GreedyF, GreedyF("x"), Seq("x"))
      thrown.getMessage must include("f threw")
    }
  }

  "the entryPointsAgree matcher" should {

    "pass a template whose entry points agree" in {
      GoodTemplate must entryPointsAgree(GoodTemplate("x")(1), "x", 1)
    }

    "fail a template whose render has drifted, naming it" in {
      val thrown = the[org.scalatest.exceptions.TestFailedException] thrownBy {
        DriftedRender must entryPointsAgree(DriftedRender("x"), "x")
      }
      thrown.getMessage must include("DriftedRender")
      thrown.getMessage must include("render produced different html")
    }
  }

}
