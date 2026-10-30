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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.read.ListAppender
import io.github.frikit.twirlspec.expect.{CheckReport, Violation}
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{DefaultMessagesApi, Lang, MessagesImpl}

import scala.jdk.CollectionConverters._

class TraceSpec extends AnyWordSpec with Matchers {

  private val logback = Trace.logger.logger.asInstanceOf[Logger]

  private val page = Page.fromString(
    "<title>A page</title>",
    Lang("en"),
    MessagesImpl(Lang("en"), new DefaultMessagesApi())
  )

  private def written(step: => Unit): List[String] = {
    val appender = new ListAppender[ILoggingEvent]
    appender.start()
    logback.addAppender(appender)
    try {
      step
      appender.list.asScala.toList.map(_.getFormattedMessage)
    } finally logback.detachAppender(appender)
  }

  "The trace" should {

    "go through Play's logger, named after the library, so a service's logging configuration governs it" in {
      Trace.logger.logger.getName mustBe "io.github.frikit.twirlspec"
    }

    "be enabled in the library's own tests, so the coverage gate sees every trace evaluated" in {
      Trace.logger.isDebugEnabled mustBe true
    }

    "drop a message that cannot be built, so it does not fail the step it describes" in {
      noException must be thrownBy Trace.debug(
        throw new IllegalStateException("boom")
      )
    }

    "name the rules behind errors and warnings alike, since a warning can fail a check" in {
      val report = CheckReport(
        page,
        Seq(Violation("wrong", "x"), Violation("soft", "y").warn),
        "page"
      )
      written(Trace.checked(report)) mustBe List(
        "checked 'A page' (en): errors [wrong], warnings [soft]"
      )
    }

    "read nothing from a report while DEBUG is off" in {
      var reads = 0
      val violations = LazyList.from(0).take(1).map { _ =>
        reads += 1
        Violation("wrong", "x")
      }
      val unread = CheckReport(page, violations, "page")
      val level = logback.getLevel
      logback.setLevel(Level.INFO)
      try Trace.checked(unread) must be theSameInstanceAs unread
      finally logback.setLevel(level)
      reads mustBe 0
    }
  }

}
