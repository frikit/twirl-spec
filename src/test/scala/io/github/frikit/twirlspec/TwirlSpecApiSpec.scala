package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import testviews.html.nameView
import io.github.frikit.twirlspec.messages.MessagesIntegrity
import io.github.frikit.twirlspec.standards.{GovukStandards, Rule, TwirlStandards, WcagStandards}

import java.io.{File, PrintWriter}
import java.nio.file.Files

/** The entry points a consuming service actually calls: the render helpers, the
  * language switching, and the matcher variants.
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

  "the matcher variants" should {

    "check expectations without the standards" in {
      // A page that breaks a standard but satisfies the expectation: displayOnly
      // passes where display would not.
      val broken = io.github.frikit.twirlspec.page.Page
        .fromString(
          """<html lang="en"><head><title>t</title></head><body><main>
                      |<h1>One</h1><h1>Two</h1></main></body></html>""".stripMargin,
          english,
          messages
        )
      broken                                            must displayOnly(headingText("One Two"))
      allStandardsExpectation.check(broken).map(_.rule) must contain("one-h1")
    }

    "run the standards with named rules switched off" in {
      val noLabel = io.github.frikit.twirlspec.page.Page
        .fromString(
          """<html lang="en"><head><title>t</title></head><body><main>
                      |<h1>A</h1><input id="x" name="x"></main></body></html>""".stripMargin,
          english,
          messages
        )
      noLabel                                            must meetStandardsExcept("labelled-controls")
      allStandardsExpectation.check(noLabel).map(_.rule) must contain("labelled-controls")
    }

    "surface warnings on a page that otherwise passes" in {
      val vague  = io.github.frikit.twirlspec.page.Page
        .fromString(
          """<html lang="en"><head><title>t</title></head><body><main>
                      |<h1>A</h1><a href="/x">click here</a></main></body></html>""".stripMargin,
          english,
          messages
        )
      val report = checkPage(vague, Seq(allStandardsExpectation))
      report.passed             mustBe true
      report.warnings.map(_.rule) must contain("link-text-is-meaningful")
      vague                       must meetGovukStandards // passes, and routes the warning to the reporter
    }
  }

  "the message file matchers" should {

    "check a file for duplicate keys" in {
      val good = Files.createTempFile("messages", "").toFile
      val out  = new PrintWriter(good, "UTF-8")
      try out.write("a = one\nb = two\n")
      finally out.close()
      Seq(good) must haveNoDuplicateKeys
      good.delete()
    }

    "report warnings alongside errors" in {
      // A file pair with a warning (untranslated) and no error.
      val untranslated = (1 to 10).map(i => s"k$i" -> s"value $i").toMap
      val api          = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> untranslated, "cy" -> untranslated),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      val found        = MessagesIntegrity.check(api)
      found.map(_.severity) must contain(io.github.frikit.twirlspec.expect.Severity.Warning)
      api                   must beConsistentAcrossLanguages() // warnings alone do not fail
    }

    "ignore keys a service has excluded" in {
      val api = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> Map("skip.me" -> "x", "a" -> "A"), "cy" -> Map("a" -> "A cy")),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      MessagesIntegrity.check(api).map(_.rule) must contain("messages.welsh-parity")
      MessagesIntegrity
        .check(api, MessagesIntegrity.Config(ignoreKeyPrefixes = Set("skip.")))
        .map(_.rule)                                                                 must not contain "messages.welsh-parity"
      MessagesIntegrity
        .check(api, MessagesIntegrity.Config(ignoreKeys = Set("skip.me")))
        .map(_.rule)                                                                 must not contain "messages.welsh-parity"
      MessagesIntegrity.check(api, MessagesIntegrity.Config(requireWelsh = false)) mustBe empty
    }

    "read a missing file as no findings" in {
      MessagesIntegrity.duplicateKeys(Seq(new File("/definitely/not/here"))) mustBe empty
    }
  }

  "the standards rule set" should {

    "select a named subset" in {
      WcagStandards.only("one-h1").map(_.id)    mustBe Seq("one-h1")
      WcagStandards.allExcept("one-h1").map(_.id) must not contain "one-h1"
      WcagStandards.all.head.toString             must include("—")
    }

    "skip page-level rules for a fragment" in {
      val fragment = io.github.frikit.twirlspec.page.Page.fromString("<p>just a component</p>", english, messages)
      Rule.isFullPage(fragment)               mustBe false
      allStandardsExpectation.check(fragment) mustBe empty
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
