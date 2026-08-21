package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import testviews.html.nameView

/** The entry points a consuming service actually calls: the render helpers, the language switching, and the matcher variants.
  */
class TwirlSpecApiSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val form = Form(mapping("firstName" -> text, "lastName" -> text)(Tuple2.apply)(t => Some((t._1, t._2))))
  private val view = inject[nameView]

  "the render helpers" should {

    "render with an explicit language and messages" in {
      val page = render(view(form)(messagesIn(welsh)), welsh, messagesIn(welsh))
      page.lang    mustBe welsh
      page.h1.text mustBe "Beth yw eu henw?"
    }

    "render in a named language with renderIn" in {
      val page = renderIn(welsh)(m => view(form)(m))
      page.lang mustBe welsh
      page.title  must startWith("Beth yw eu henw?")
    }

    "render the current language with renderPage" in {
      renderPage(view(form)).lang          mustBe english
      inWelsh(renderPage(view(form)).lang) mustBe welsh
    }

    "render every configured language at once" in {
      val pages = renderInEachLanguage(view(form))
      pages.map(_.lang.code) mustBe Seq("en", "cy")
      pages.map(_.h1.text)   mustBe Seq("What is your name?", "Beth yw eu henw?")
    }
  }

  "the language helpers" should {

    "expose the configured languages, english first" in {
      languages.map(_.code) mustBe Seq("en", "cy")
    }

    "switch language for a block and switch back" in {
      currentLang                    mustBe english
      inWelsh(currentLang)           mustBe welsh
      inEnglish(currentLang)         mustBe english
      inLanguage(welsh)(currentLang) mustBe welsh
      currentLang                    mustBe english
    }

    "carry the language on the request" in {
      request.cookies.get(messagesApi.langCookieName).map(_.value)          mustBe Some("en")
      inWelsh(request.cookies.get(messagesApi.langCookieName).map(_.value)) mustBe Some("cy")
    }
  }

  "the shared application" should {
    "hand back the same instance for the same configuration" in {
      val a = io.github.frikit.twirlspec.render.SharedApplication(Map.empty)
      val b = io.github.frikit.twirlspec.render.SharedApplication(Map.empty)
      a                                                                 must be theSameInstanceAs b
      io.github.frikit.twirlspec.render.SharedApplication.instanceCount must be >= 1
    }
  }

  "a violation" should {
    "render its location and hint" in {
      val v        = io.github.frikit.twirlspec.expect
        .Violation("rule", "message")
        .at("#somewhere")
        .withHint("do this instead")
      val rendered = v.render()
      rendered          must include("#somewhere")
      rendered          must include("do this instead")
      v.warn.severity mustBe io.github.frikit.twirlspec.expect.Severity.Warning
    }

    "truncate very long values" in {
      val long = "x" * 500
      io.github.frikit.twirlspec.expect.Violation.mismatch("rule", long, long).render() must include("...")
    }
  }

  private def messagesIn(lang: play.api.i18n.Lang) = messagesApi.preferred(Seq(lang))

}
