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
import io.github.frikit.twirlspec.messages.{MessagesIntegrity, MessagesMatchers}

import java.io.{File, PrintWriter}
import java.nio.file.Files

/** A spec on the DSL, which decides what happens to warnings. */
private class MessagesPolicy extends TwirlSpecDsl with MessagesMatchers {
  var surfaced: List[String] = Nil
  override protected def alertHook(message: String): Unit =
    surfaced = message :: surfaced
}

/** The message-file matchers as a consuming spec would use them. */
class MessagesMatchersSpec
    extends AnyWordSpec
    with Matchers
    with Bilingual
    with MessagesMatchers {

  "the message file matchers" should {

    "check a file for duplicate keys" in {
      val good = Files.createTempFile("messages", "").toFile
      val out = new PrintWriter(good, "UTF-8")
      try out.write("a = one\nb = two\n")
      finally out.close()
      Seq(good) must haveNoDuplicateKeys
      good.delete()
    }

    "report warnings alongside errors" in {
      // A file pair with a warning (untranslated) and no error.
      val untranslated = (1 to 10).map(i => s"k$i" -> s"value $i").toMap
      val api = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> untranslated, "cy" -> untranslated),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      val found = MessagesIntegrity.check(api)
      found.map(_.severity) must contain(
        io.github.frikit.twirlspec.expect.Severity.Warning
      )
      api must beConsistentAcrossLanguages() // warnings alone do not fail
    }

    "hold warnings to the spec's own policy" in {
      val copied = (1 to 10).map(i => s"k$i" -> s"value $i").toMap
      val api = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> copied, "cy" -> copied),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      val spec = new MessagesPolicy
      spec.beConsistentAcrossLanguages()(api).matches mustBe true
      spec.surfaced.mkString must include("[cy]")

      val quiet = new MessagesPolicy { override def reportWarnings = false }
      quiet.beConsistentAcrossLanguages()(api).matches mustBe true
      quiet.surfaced mustBe empty

      val strict = new MessagesPolicy { override def failOnWarnings = true }
      val failed = strict.beConsistentAcrossLanguages()(api)
      failed.matches mustBe false
      failed.failureMessage must include(
        "message files failed on 1 warning(s), as failOnWarnings asks:"
      )
    }

    "say warnings on standard output without a spec to decide" in {
      val copied = (1 to 10).map(i => s"k$i" -> s"value $i").toMap
      val api = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> copied, "cy" -> copied),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      val alone = new MessagesMatchers {}
      val out = new java.io.ByteArrayOutputStream
      Console.withOut(out) {
        alone.beConsistentAcrossLanguages()(api).matches mustBe true
      }
      out.toString must include("messages.translation-coverage")

      val clean = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> Map("a" -> "A"), "cy" -> Map("a" -> "B")),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      val nothing = new java.io.ByteArrayOutputStream
      Console.withOut(nothing) {
        alone.beConsistentAcrossLanguages()(clean).matches mustBe true
      }
      nothing.toString mustBe empty

      val broken = new java.io.PrintStream(new java.io.OutputStream {
        def write(b: Int): Unit = throw new IllegalStateException("closed")
      })
      Console.withOut(broken) {
        alone.beConsistentAcrossLanguages()(api).matches mustBe true
      }
    }

    "ignore keys a service has excluded" in {
      val api = new play.api.i18n.DefaultMessagesApi(
        messages = Map(
          "default" -> Map("skip.me" -> "x", "a" -> "A"),
          "cy" -> Map("a" -> "A cy")
        ),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      MessagesIntegrity.check(api).map(_.rule) must contain(
        "messages.translation-parity"
      )
      MessagesIntegrity
        .check(api, MessagesIntegrity.Config(ignoreKeyPrefixes = Set("skip.")))
        .map(_.rule) must not contain "messages.translation-parity"
      MessagesIntegrity
        .check(api, MessagesIntegrity.Config(ignoreKeys = Set("skip.me")))
        .map(_.rule) must not contain "messages.translation-parity"
      MessagesIntegrity.check(
        api,
        MessagesIntegrity.Config(requireTranslations = false)
      ) mustBe empty
    }

    "report a file that is not there, since nothing in it was checked" in {
      val found =
        MessagesIntegrity.duplicateKeys(Seq(new File("/definitely/not/here")))
      found.map(_.rule) mustBe Seq("messages.missing-file")
      found.head.message must include("/definitely/not/here")
      a[org.scalatest.exceptions.TestFailedException] must be thrownBy {
        Seq(new File("/definitely/not/here")) must haveNoDuplicateKeys
      }
    }

    "report being given no files at all" in {
      MessagesIntegrity.duplicateKeys(Nil).map(_.rule) mustBe
        Seq("messages.no-files")
    }

    "read a value carrying on past the end of a line as part of the value" in {
      def keysFoundIn(text: String) = {
        val file = Files.createTempFile("messages", "").toFile
        val out = new PrintWriter(file, "UTF-8")
        try out.write(text)
        finally out.close()
        try MessagesIntegrity.duplicateKeys(Seq(file)).map(_.message)
        finally file.delete()
      }
      // Both values carry on, and what they carry on with looks like a key.
      keysFoundIn(
        "first=<a \\\n  href=\"/a\">One</a>\nsecond=<a \\\n  href=\"/b\">Two</a>\n"
      ) mustBe empty
      // A value can carry on over several lines.
      keysFoundIn("a=one \\\n  b=two \\\n  b=three\n") mustBe empty
      // An escaped backslash ends the value, so the next line is a key again.
      keysFoundIn("a=one\\\\\na=two\n").map(_.takeRight(16)) mustBe
        Seq("is defined 2 times".takeRight(16))
      // A comment does not carry on.
      keysFoundIn("# note \\\na=one\na=two\n") must have size 1
      // Nor does a line that is not a message.
      keysFoundIn("not a message \\\na=one\na=two\n") must have size 1
    }
  }

}
