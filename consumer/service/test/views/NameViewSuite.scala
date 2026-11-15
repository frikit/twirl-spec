package views

import io.github.frikit.twirlspec.{GovukServiceChecks, TwirlSuite}
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.i18n.Lang

/** The same view, from an MUnit suite: TwirlSuite for the application and
  * implicits, the page catalogue as a test for each check, and assertions over
  * the same expectations.
  */
class NameViewSuite extends TwirlSuite with GovukServiceChecks {

  private val view = inject[views.html.NameView]
  private val form: Form[String] = Form(single("value" -> text))

  cataloguePage(staticPage("NameView")(view(form))) {

    test("NameView renders in Welsh from the languages the service declares") {
      assertEquals(
        inLanguage(Lang("cy"))(renderPage(view(form)).h1.text),
        "Beth yw eich enw?"
      )
    }

    test("NameView says what it should") {
      assertDisplays(
        renderPage(view(form)),
        heading("name.heading"),
        textInput("value").labelled("name.heading"),
        submitButton("site.continue")
      )
    }
  }

}
