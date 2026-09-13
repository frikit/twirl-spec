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
import io.github.frikit.twirlspec.expect.{Expectation, Violation}
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Rule

/** The knobs on the matcher: whether warnings fail, and how they are surfaced.
  */
class MatcherConfigurationSpec extends AnyWordSpec with Matchers {

  private val warnOnly: Rule =
    Rule("advisory", "an advisory rule", severity = Rule.Warning)(_ =>
      Seq(Violation("advisory", "worth a look"))
    )

  private def pageOf(implicit m: play.api.i18n.Messages): Page =
    Page.fromString(
      "<html lang=\"en\"><head><title>t</title></head><body><h1>A</h1></body></html>",
      m.lang,
      m
    )

  "failOnWarnings" should {

    "leave a warning advisory when off" in new Fixture {
      override def failOnWarnings = false
      pageOf(messages) must meetStandards
    }

    "turn a warning into a failure when on" in new Fixture {
      override def failOnWarnings = true
      a[org.scalatest.exceptions.TestFailedException] must be thrownBy {
        pageOf(messages) must meetStandards
      }
    }
  }

  "surfacing warnings" should {

    "not let a broken reporter fail an otherwise passing check" in new Fixture {
      override def alertHook(message: String): Unit =
        throw new RuntimeException("reporter is unavailable")
      // The check passes; routing its warning to a reporter that throws must
      // not turn that into a failure.
      pageOf(messages) must meetStandards
    }

    "stay silent when warning reporting is switched off" in new Fixture {
      override def reportWarnings = false
      var called = false
      override def alertHook(message: String): Unit = called = true
      pageOf(messages) must meetStandards
      called mustBe false
    }
  }

  /** A spec base built on TwirlSpecDsl alone, which is the entry point a
    * project with its own application uses — and which leaves `alertHook` at
    * its default no-op.
    */
  private trait Fixture extends TwirlSpecDsl with Matchers {
    override def standardsRules: Seq[Rule] = Seq(warnOnly)

    private val app = play.api.inject.guice
      .GuiceApplicationBuilder()
      .configure(
        Map(
          "play.i18n.langs" -> Seq("en"),
          "metrics.enabled" -> false,
          "auditing.enabled" -> false
        )
      )
      .build()

    val messages: play.api.i18n.Messages =
      app.injector
        .instanceOf[play.api.i18n.MessagesApi]
        .preferred(Seq(play.api.i18n.Lang("en")))

  }

  "TwirlSpecDsl on its own" should {
    "have a no-op alert hook, so it needs no ScalaTest reporter" in new Fixture {
      // Exercises the default implementation rather than TwirlSpec's override.
      noException must be thrownBy alertHook("anything")
      expectations(Expectation.satisfied).check(pageOf(messages)) mustBe empty
    }
  }

}
