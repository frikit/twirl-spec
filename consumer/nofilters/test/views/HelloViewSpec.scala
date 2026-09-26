package views

import io.github.frikit.twirlspec.TwirlSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A Play application built without the filters has no CSRF helper, and
  * TwirlSpec's request carries no token there rather than failing.
  */
class HelloViewSpec extends AnyWordSpec with Matchers with TwirlSpec {

  "a service built without Play's filters" should {

    "have no CSRF helper on its classpath" in {
      an[ClassNotFoundException] must be thrownBy Class.forName("play.api.test.CSRFTokenHelper")
    }

    "still get a request from TwirlSpec, and render with it" in {
      request.method mustBe "GET"
      render(inject[views.html.HelloView].apply()).css("#hello").elements.map(_.text) mustBe List("Hello")
    }
  }
}
