package views

import io.github.frikit.twirlspec.{GovukServiceChecks, TwirlSpec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.i18n.Lang

/** The path a service takes: TwirlSpec for the application and implicits,
  * GovukServiceChecks for the rules, the service's own configuration throughout.
  */
class NameViewSpec extends AnyWordSpec with Matchers with TwirlSpec with GovukServiceChecks {

  private val view = inject[views.html.NameView]
  private val form: Form[String] = Form(single("value" -> text))

  "the application" should {
    "be built from the service's own configuration" in {
      app.configuration.get[String]("consumer.marker") mustBe "from-the-service"
    }
  }

  "NameView" should {

    "render its CSRF field, as it does in production" in {
      render(view(form)).css("input[type=hidden][name=csrfToken]").elements must have size 1
    }

    "render in Welsh from the languages the service declares" in {
      inLanguage(Lang("cy"))(renderPage(view(form)).h1.text) mustBe "Beth yw eich enw?"
    }

    "meet the standards and say what it should" in {
      render(view(form)) must display(
        heading("name.heading"),
        textInput("value").labelled("name.heading"),
        submitButton("site.continue")
      )
    }

    "have been asserted in full" in {
      render(view(form)) must assertEverything
    }
  }

  "the message files" should {
    "agree across the service's languages" in {
      messagesApi must beConsistentAcrossLanguages()
    }
  }
}
