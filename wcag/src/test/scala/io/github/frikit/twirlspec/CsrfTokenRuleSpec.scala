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

import io.github.frikit.twirlspec.expect.Severity
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Rule
import io.github.frikit.twirlspec.wcag.SecurityStandards
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The CSRF token, for a service that opts in. */
class CsrfTokenRuleSpec extends AnyWordSpec with Matchers with TwirlSpec {

  override def standardsRules: Seq[Rule] =
    super.standardsRules :+ SecurityStandards.csrfToken()

  private val token = """<input type="hidden" name="csrfToken" value="t">"""

  private def found(
      body: String,
      rule: Rule = SecurityStandards.csrfToken()
  ) = rule.check(Page.fromString(s"<main>$body</main>", english, messages))

  private def form(inside: String, action: String = "/save") =
    s"""<form method="post" action="$action">$inside</form>"""

  "SecurityStandards.csrfToken" should {

    "be in no rule set, so that a service has to ask for it" in {
      SecurityStandards.all.map(
        _.id
      ) must not contain "post-form-has-csrf-token"
    }

    "pass the form Play renders, once a spec has added the rule" in {
      Page.fromString(
        s"""<main><form method="POST" action="/save" novalidate>$token</form></main>""",
        english,
        messages
      ) must meetStandards
    }

    "fail a form posting here with no token, and block" in {
      val problems = found(form(""))
      problems.map(_.rule) mustBe Seq("post-form-has-csrf-token")
      problems.map(_.severity) mustBe Seq(Severity.Error)
      problems.flatMap(_.expected) mustBe Seq("csrfToken")
    }

    "pass a token in the form, or in its action" in {
      found(form(token)) mustBe empty
      found(form("", "/save?csrfToken=t")) mustBe empty
      found(form("", "/save?a=1&amp;csrfToken=t")) mustBe empty
    }

    "not take an empty or disabled token for one" in {
      found(form("""<input type="hidden" name="csrfToken" value="">""")) must
        have size 1
      found(
        form("""<input type="hidden" name="csrfToken" value="t" disabled>""")
      ) must have size 1
      found(form("", "/save?csrfToken=")) must have size 1
      found(form("", "/save#csrfToken=t")) must have size 1
      found(form("", "/save?notcsrfToken=t")) must have size 1
    }

    "read an action's query as Play does" in {
      found(form("", "/save?next=/a?csrfToken=t")) must have size 1
      found(form("", "/save?csrfToken=t%zz")) mustBe empty
      val plus = SecurityStandards.csrfToken("csrf+token")
      found(form("", "/save?csrf%2Btoken=t"), plus) mustBe empty
      found(form("", "/save?csrf+token=t"), plus) must have size 1
      found(form("", "/save?%zz=t"), plus) must have size 1
    }

    "go by how and where a button says it submits" in {
      def button(attributes: String) = s"<button $attributes>Go</button>"
      def getForm(inside: String) =
        s"""<form method="get" action="/search">$inside</form>"""
      found(getForm(button("""formmethod="post""""))) must have size 1
      found(getForm(button("""formmethod="POST" formaction="/save"""")))
        .map(_.message) mustBe Seq(
        """a form posting to "/save" carries no csrfToken"""
      )
      found(form(button("""formmethod="get""""))) mustBe empty
      found(form(button("""formaction="/other""""), "/save?csrfToken=t")) must
        have size 1
      found(form(button("""formaction="https://elsewhere.example/""""))) mustBe
        empty
    }

    "not ask how a button it cannot press would submit" in {
      found(
        s"""<form method="get" action="/search"><button formmethod="post" disabled>Go</button></form>"""
      ) mustBe empty
      found(
        s"""<form method="get" action="/search"><button type="button" formmethod="post">Go</button></form>"""
      ) mustBe empty
    }

    "find a token anywhere it belongs to the form" in {
      found(
        s"""<form id="f" method="post" action="/save"></form>
           |<input form="f" type="hidden" name="csrfToken" value="t">""".stripMargin
      ) mustBe empty
    }

    "hold each form to its own token" in {
      val problems = found(form(token) + form("", "/other"))
      problems.map(_.message) mustBe Seq(
        """a form posting to "/other" carries no csrfToken"""
      )
    }

    "read a post however it is written, and leave anything else alone" in {
      found("""<form method="POST" action="/save"></form>""") must have size 1
      found("""<form action="/search"></form>""") mustBe empty
      found("""<form method="get" action="/search"></form>""") mustBe empty
    }

    "leave alone a form leaving for another address" in {
      found(form("", "https://elsewhere.example/save")) mustBe empty
      found(form("", "//elsewhere.example/save")) mustBe empty
    }

    "go by the name the service has set" in {
      val rule = SecurityStandards.csrfToken("token")
      val renamed = """<input type="hidden" name="token" value="t">"""
      found(form(renamed), rule) mustBe empty
      found(form(token), rule).map(_.message) mustBe Seq(
        """a form posting to "/save" carries no token"""
      )
    }
  }

}
