/*
 * Copyright 2026 Victor Osipov
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
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import testviews.html.nameView

/** What a developer sees when a view test goes red.
  *
  * The failure message is the product. A view spec that says
  * "false was not true" costs more time than it saves, so these are asserted
  * as carefully as the checks themselves.
  */
class FailureReportingSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val form = Form(mapping("firstName" -> text, "lastName" -> text)(Tuple2.apply)(t => Some((t._1, t._2))))
  private val page = render(inject[nameView].apply(form))

  "a failing check" should {

    "report every problem at once, not just the first" in {
      val report = checkPage(
        page,
        Seq(
          title("checkAnswers.name"),
          heading("checkAnswers.dob"),
          textInput("middleName")
        )
      )
      report.errors.size mustBe 3
      report.message       must include("failed 3 checks")
    }

    "name the rule, the expectation and what was actually rendered" in {
      val message = checkPage(page, Seq(title("checkAnswers.name"))).message
      message must include("title")
      message must include("expected  \"Name\"")
      message must include("actual    \"What is your name?")
    }

    "print the page outline so the shape of the page is visible" in {
      val message = checkPage(page, Seq(textInput("middleName"))).message
      message must include("--- page outline (en) ---")
      message must include("form POST /register/name")
      message must include("firstName")
      message must include("""label "First name"""")
    }

    "say when a message key does not exist rather than comparing key to key" in {
      val message = checkPage(page, Seq(title("whatIsYourName.notAKey"))).message
      message must include("message key `whatIsYourName.notAKey` is not defined")
      message must include("add `whatIsYourName.notAKey` to conf/messages")
    }

    "name the missing key in the right language" in
      inWelsh {
        val welshPage = render(inject[nameView].apply(form))
        val message   = checkPage(welshPage, Seq(title("untranslated.missing"))).message
        message must include("not defined for lang `cy`")
        message must include("conf/messages.cy")
      }

    "point at the field when an input has no label" in {
      val message = checkPage(page, Seq(textInput("firstName").labelled("checkAnswers.dob"))).message
      message must include("input(firstName) label")
      message must include("Date of birth")
    }

    "carry an accessibility hint when the failure is an accessibility one" in {
      val brokenHtml =
        """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
          |<body><main><h1>A</h1><input id="x" name="x" type="text"></main></body></html>""".stripMargin
      val broken     = io.github.frikit.twirlspec.page.Page.fromString(brokenHtml, english, messages)
      val message    = checkPage(broken, Seq(textInput("x").labelled("whatIsYourName.firstName"))).message
      message must include("WCAG 3.3.2")
    }
  }

  "a passing check" should {
    "produce no report at all" in {
      checkPage(page, Seq(title("whatIsYourName.title"))).passed mustBe true
      checkPage(page, Seq(title("whatIsYourName.title"))).errors mustBe empty
    }
  }

  "the outline" should {

    "describe the page structure on its own" in {
      // Asserted on content, not column widths — the gutter is presentation.
      val lines = page.outline.linesIterator.map(_.trim.replaceAll("\\s+", " ")).toList
      lines                                                    must contain("h1 What is your name?")
      lines                                                    must contain("caption Personal details")
      lines                                                    must contain("back link /back")
      lines                                                    must contain("lang toggle present")
      lines                                                    must contain("form POST /register/name")
      lines.exists(_.startsWith("button submit"))            mustBe true
      lines.exists(_.startsWith("title What is your name?")) mustBe true
    }

    "list the errors when the page is in an error state" in {
      val errorPage = render(inject[nameView].apply(form, showErrors = true))
      val outline   = errorPage.outline
      outline must include("errors")
      outline must include("summary -> #firstName")
      outline must include("inline  firstName: Enter their first name")
    }
  }

}
