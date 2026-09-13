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
import io.github.frikit.twirlspec.page.Page

/** Finding elements by role and accessible name, the way a screen reader does. */
class RoleQuerySpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val html =
    """<!DOCTYPE html>
      |<html lang="en"><head><title>t</title></head>
      |<body>
      |  <nav><a href="/home">Home</a></nav>
      |  <main>
      |    <h1>Sign up</h1>
      |    <h2>Your details</h2>
      |    <form action="/x" method="post">
      |      <label for="email">Email address</label>
      |      <input id="email" name="email" type="email">
      |      <label for="notes">Notes</label>
      |      <textarea id="notes" name="notes"></textarea>
      |      <fieldset><legend>Contact preference</legend>
      |        <input type="radio" id="c1" name="c" value="e"><label for="c1">Email</label>
      |        <input type="radio" id="c2" name="c" value="p"><label for="c2">Post</label>
      |      </fieldset>
      |      <select id="country" name="country" aria-label="Country"><option value="GB">GB</option></select>
      |      <button type="submit">Continue</button>
      |      <a href="/cancel" role="button">Cancel</a>
      |      <img src="/logo.png" alt="Our logo">
      |    </form>
      |  </main>
      |</body></html>""".stripMargin

  private lazy val page: Page                                         = Page.fromString(html, english, messages)
  private def check(e: io.github.frikit.twirlspec.expect.Expectation) = e.check(page).map(_.rule)

  "querying by role" should {

    "find controls by the role they expose" in {
      page.byRole("button").size   mustBe 2 // the <button>, and the link that says it is one
      page.byRole("link").size     mustBe 1 // only the nav link: the cancel <a> declared role="button"
      page.byRole("textbox").size  mustBe 2
      page.byRole("radio").size    mustBe 2
      page.byRole("combobox").size mustBe 1
      page.byRole("heading").size  mustBe 2
      page.byRole("img").size      mustBe 1
    }

    "treat an explicit role as overriding the implicit one" in {
      // <a role="button"> is a button, and is no longer a link.
      page.byRole("button", "Cancel").size mustBe 1
      page.byRole("link").texts              must not contain "Cancel"
    }

    "name a control from its label, aria-label, legend or alt" in {
      page.byRole("textbox", "Email address").size    mustBe 1
      page.byRole("combobox", "Country").size         mustBe 1
      page.byRole("group", "Contact preference").size mustBe 1
      page.byRole("img", "Our logo").size             mustBe 1
      page.byRole("radio", "Post").size               mustBe 1
    }
  }

  "a role with no implicit HTML equivalent" should {
    "still be found when declared explicitly" in {
      val p = Page.fromString(
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1><div role="alert">Something went wrong</div></main></body></html>""".stripMargin,
        english,
        messages
      )
      p.byRole("alert").size                                     mustBe 1
      p.byRole("alert", "Something went wrong").size             mustBe 1
      io.github.frikit.twirlspec.page.Roles.selectorFor("alert") mustBe "[role=alert]"
      io.github.frikit.twirlspec.page.Roles.knownRoles             must contain("button")
    }
  }

  "the role expectation" should {

    "pass when a control is reachable by role and name" in {
      check(role("button").named("site.continue"))         mustBe empty
      check(role("textbox").namedText("Email address"))    mustBe empty
      check(role("heading").namedMatching("Sign\\s+up".r)) mustBe empty
      check(role("navigation"))                            mustBe empty
      check(role("button").occurring(2))                   mustBe empty
    }

    "fail when nothing announces that name, and list what does" in {
      val message = checkPage(page, Seq(role("button").namedText("Submit"))).message
      message must include("no element with role `button` announces this name")
      message must include("Continue")
    }

    "fail when the role is absent entirely" in {
      check(role("table"))                                           mustBe Seq("role(table)")
      checkPage(page, Seq(role("table").namedText("Results"))).message must
        include("(no element has role `table`)")
    }

    "fail on the wrong count" in {
      check(role("button").occurring(9)) mustBe Seq("role(button) count")
    }

    "report a name key that does not resolve" in {
      check(role("button").named("kitchenSink.notAKey")) mustBe
        Seq("role(button, messages(kitchenSink.notAKey))")
    }
  }

  "regex text matching" should {
    "work anywhere an expected value is taken" in {
      check(heading(matching("Sign.*".r))) mustBe empty
      check(heading(matching("Nope.*".r))) mustBe Seq("heading")
      matching("x".r).describe               must include("matching")
    }
  }

}
