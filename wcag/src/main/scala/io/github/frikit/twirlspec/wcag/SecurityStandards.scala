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

package io.github.frikit.twirlspec.wcag

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{Page, Text}
import io.github.frikit.twirlspec.standards.Rule.Warning
import org.jsoup.nodes.Element

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Markup that is a safety problem rather than an accessibility one. */
object SecurityStandards extends RuleSet {

  lazy val all: Seq[Rule] = Seq(
    Rule("no-password-in-get", "a password is never submitted in a URL") {
      page =>
        page.document
          // A form with no method attribute submits by GET, which is the shape
          // this is most likely to be found in: nobody writes method="get" on
          // a sign-in form, they just leave the method off.
          .select("form[method=get], form:not([method])")
          .asScala
          .toList
          .filter(_.select("input[type=password]").asScala.nonEmpty)
          .map { form =>
            Violation(
              "no-password-in-get",
              "a password field sits in a form that submits by GET",
              expected = Some("""method="post""""),
              actual = Some(
                Option(form.attr("action"))
                  .filter(_.nonEmpty)
                  .getOrElse("(no action)")
              )
            ).withHint(
              "a GET puts the password in the URL, and so in history, logs and referrers"
            )
          }
    },
    Rule("no-javascript-href", "links do not carry javascript: URLs") { page =>
      page.document
        .select("a[href^=javascript:]")
        .asScala
        .toList
        .map(e =>
          Violation(
            "no-javascript-href",
            s"""a link has a javascript: URL: "${Text.preview(e.text(), 40)}""""
          )
            .withHint(
              "it breaks without JavaScript and is refused by a strict content security policy"
            )
        )
    },
    Rule(
      "target-blank-is-safe",
      "a link opening a new tab cannot reach back",
      severity = Warning
    ) { page =>
      // noreferrer severs window.opener as well as the referrer, so a link
      // carrying it is already safe and must not be asked for noopener too.
      val seversTheOpener = Set("noopener", "noreferrer")
      page.links.elements
        .filter(_.attr("target") == "_blank")
        .filterNot(
          _.attr("rel").toLowerCase.split("\\s+").exists(seversTheOpener)
        )
        .map(e =>
          Violation(
            "target-blank-is-safe",
            s"""a link opens a new tab without rel="noopener": "${Text
                .preview(e.text(), 40)}"""",
            expected = Some("""rel="noopener" or rel="noreferrer"""")
          ).warn.withHint(
            "current browsers imply this for target=_blank; older ones let the new tab rewrite yours"
          )
        )
    }
  )

  /** A form posting back to this service carries Play's CSRF token, without
    * which Play's filter answers 403:
    *
    * {{{
    * override def standardsRules = super.standardsRules :+ SecurityStandards.csrfToken()
    * }}}
    *
    * It is in no rule set, because it does not hold of every page: a form may
    * post elsewhere, a service may send its token another way or exempt a
    * route. It blocks, as whoever adds it has asked for it. A form posting to a
    * full address is taken to leave the service, and is left alone.
    *
    * @param tokenName
    *   the field Play reads, as `play.filters.csrf.token.name` sets it
    */
  def csrfToken(tokenName: String = "csrfToken"): Rule = {
    val rule = "post-form-has-csrf-token"
    Rule(rule, "a form posting to this service carries its CSRF token") {
      page =>
        val carrying = page.submittedControls.collect {
          case (control, name, value) if name == tokenName && value.nonEmpty =>
            page.formOwner(control)
        }.flatten
        page.document
          .select("form")
          .asScala
          .toList
          .filterNot(carrying.contains)
          .flatMap(postsTo(page, _))
          .filterNot(LeavesTheService.findFirstIn(_).isDefined)
          .filterNot(queryCarries(_, tokenName))
          .map(action =>
            Violation(
              rule,
              s"""a form posting to "$action" carries no $tokenName""",
              expected = Some(tokenName)
            ).withHint(
              "use formWithCSRF or @helper.CSRF.formField; a spec's request carries a token only where play-filters-helpers is on its classpath"
            )
          )
    }
  }

  /** A full address, with a scheme or starting at the host. */
  private val LeavesTheService = "^([a-zA-Z][a-zA-Z0-9+.-]*:|//)".r

  /** Where a form posts to. A button can say for itself how and where it
    * submits the form, and with no button there is only what the form says.
    */
  private def postsTo(page: Page, form: Element): List[String] = {
    def own(e: Element, attribute: String, fallback: String) =
      if (e.hasAttr(attribute)) e.attr(attribute) else form.attr(fallback)
    val submitters = page.document
      .select(
        "button[type=submit], button:not([type]), input[type=submit], input[type=image]"
      )
      .asScala
      .toList
      .filter(s => page.formOwner(s).contains(form) && !page.isDisabled(s))
    val ways =
      if (submitters.isEmpty) List((form.attr("method"), form.attr("action")))
      else
        submitters.map(s =>
          (own(s, "formmethod", "method"), own(s, "formaction", "action"))
        )
    ways.collect {
      case (method, action) if method.equalsIgnoreCase("post") =>
        action
    }.distinct
  }

  /** Whether an action's query has this parameter with a value, as Play reads
    * it.
    */
  private def queryCarries(action: String, name: String): Boolean = {
    def decoded(s: String) =
      Try(URLDecoder.decode(s, StandardCharsets.UTF_8)).getOrElse(s)
    action
      .takeWhile(_ != '#')
      .dropWhile(_ != '?')
      .drop(1)
      .split('&')
      .map(_.span(_ != '='))
      .exists { case (key, value) => decoded(key) == name && value.length > 1 }
  }

}
