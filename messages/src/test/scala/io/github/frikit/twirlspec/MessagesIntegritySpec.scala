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
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Lang, MessagesApi}
import io.github.frikit.twirlspec.messages.{MessagesIntegrity, MessagesMatchers}

import java.io.{File, PrintWriter}
import java.nio.file.Files

class MessagesIntegritySpec extends AnyWordSpec with Matchers with TwirlSpec with MessagesMatchers {

  private def api(english: Map[String, String], welsh: Map[String, String]): MessagesApi =
    new DefaultMessagesApi(
      messages = Map("default" -> english, "cy" -> welsh),
      langs = new DefaultLangs(Seq(Lang("en"), Lang("cy")))
    )

  private def rules(english: Map[String, String], welsh: Map[String, String]): Set[String] =
    MessagesIntegrity.check(api(english, welsh)).map(_.rule).toSet

  "the message files of this library's own fixtures" should {
    "already be consistent" in {
      messagesApi must beConsistentAcrossLanguages()
    }
  }

  "key parity" should {

    "flag an english key with no welsh translation" in {
      rules(Map("a" -> "A", "b" -> "B"), Map("a" -> "A cy")) must contain("messages.welsh-parity")
    }

    "flag a welsh key with no english original" in {
      rules(Map("a" -> "A"), Map("a" -> "A cy", "orphan" -> "amddifad")) must contain("messages.english-parity")
    }

    "say which keys are missing" in {
      val violation = MessagesIntegrity
        .check(api(Map("a" -> "A", "missing.one" -> "x"), Map("a" -> "A cy")))
        .find(_.rule == "messages.welsh-parity")
      violation.flatMap(_.actual) mustBe Some("missing.one")
    }

    "accept files that agree" in {
      rules(Map("a" -> "A"), Map("a" -> "A cy")) mustBe empty
    }
  }

  "value checks" should {

    "flag an empty value" in {
      rules(Map("a" -> "A", "b" -> "  "), Map("a" -> "A cy", "b" -> "B cy")) must contain("messages.empty-value")
    }

    "flag an unpaired apostrophe" in {
      rules(Map("a" -> "Don't do that"), Map("a" -> "cy")) must contain("messages.unescaped-quote")
    }

    "accept a correctly doubled apostrophe" in {
      rules(Map("a" -> "Don''t do that"), Map("a" -> "cy")) must not contain "messages.unescaped-quote"
    }

    "not flag a deliberate MessageFormat quoted section as broken" in {
      // MessageFormat reads '...' as a literal section
      // and renders correctly. Calling this an error would be wrong.
      val found = rules(Map("a" -> "'x quoted x' by the supplier"), Map("a" -> "cy"))
      found must not contain "messages.unescaped-quote"
      found must not contain "messages.quoted-placeholder"
    }

    "flag a placeholder trapped inside a balanced quoted section" in {
      rules(Map("a" -> "the value '{0}' is literal"), Map("a" -> "cy")) must contain("messages.quoted-placeholder")
    }

    "treat an unpaired quote before a placeholder as the more severe unbalanced case" in {
      // "It's {0} of {1}" renders as "Its {0} of {1}" — the quote is dropped and
      // both placeholders are shown raw. Unbalanced already says so.
      rules(Map("a" -> "It's {0} of {1}"), Map("a" -> "cy")) must contain("messages.unescaped-quote")
    }

    "classify each MessageFormat case correctly" in {
      import io.github.frikit.twirlspec.messages.MessagesIntegrity.QuoteState._
      MessagesIntegrity.quoteState("no quotes here")             mustBe Fine
      MessagesIntegrity.quoteState("Don''t stop")                mustBe Fine
      MessagesIntegrity.quoteState("Don't stop")                 mustBe Unbalanced
      MessagesIntegrity.quoteState("'literal section' after")    mustBe DeliberatelyQuoted
      MessagesIntegrity.quoteState("It's {0} of {1}")            mustBe Unbalanced
      MessagesIntegrity.quoteState("the value '{0}' is literal") mustBe PlaceholderInQuotes
    }
  }

  "placeholder parity" should {

    "flag a translation that drops a placeholder" in {
      rules(Map("a" -> "{0} of {1}"), Map("a" -> "{0} o")) must contain("messages.placeholder-parity")
    }

    "accept placeholders reordered by grammar" in {
      rules(Map("a" -> "{0} of {1}"), Map("a" -> "{1} o {0}")) must not contain "messages.placeholder-parity"
    }
  }

  "translation coverage" should {

    "warn when too much of the welsh file is still english" in {
      val english = (1 to 10).map(i => s"k$i" -> s"value $i").toMap
      val welsh   = english // nothing translated at all
      val found   = MessagesIntegrity.check(api(english, welsh))
      found.map(_.rule)                                                       must contain("messages.translation-coverage")
      found.find(_.rule == "messages.translation-coverage").map(_.severity) mustBe
        Some(io.github.frikit.twirlspec.expect.Severity.Warning)
    }

    "not count url keys as untranslated" in {
      val english = Map("a.url" -> "/x", "b" -> "B")
      val welsh   = Map("a.url" -> "/x", "b" -> "B cy")
      rules(english, welsh) mustBe empty
    }
  }

  "duplicate key detection" should {

    "find a key defined twice in one file" in {
      val file = Files.createTempFile("messages", "").toFile
      val out  = new PrintWriter(file, "UTF-8")
      try out.write("a = one\n# a comment\nb = two\na = three\n")
      finally out.close()

      val found = MessagesIntegrity.duplicateKeys(Seq(file))
      found.map(_.rule) mustBe Seq("messages.duplicate-key")
      found.head.message  must include("`a` is defined 2 times")
      file.delete()
    }

    "pass a file with no duplicates" in {
      val file = Files.createTempFile("messages", "").toFile
      val out  = new PrintWriter(file, "UTF-8")
      try out.write("a = one\nb = two\n")
      finally out.close()
      MessagesIntegrity.duplicateKeys(Seq(file)) mustBe empty
      file.delete()
    }

    "ignore a file that is not there" in {
      MessagesIntegrity.duplicateKeys(Seq(new File("/nope/messages"))) mustBe empty
    }
  }

  "the apostrophe rule" should {

    "match what Play actually does with a lone apostrophe" in {
      // Documents the behaviour the rule exists for: Play runs every message
      // through MessageFormat, so an unescaped quote is not cosmetic.
      val single  = new DefaultMessagesApi(
        messages = Map("default" -> Map("q" -> "Don't stop", "qq" -> "Don''t stop")),
        langs = new DefaultLangs(Seq(Lang("en")))
      )
      val m       = single.preferred(Seq(Lang("en")))
      val lone    = m("q")
      val doubled = m("qq")

      doubled mustBe "Don't stop"
      withClue(s"Play rendered a lone apostrophe as: '$lone' — ") {
        lone must not be "Don't stop"
      }
    }
  }

}
