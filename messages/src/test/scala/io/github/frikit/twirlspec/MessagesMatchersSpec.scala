package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.messages.{MessagesIntegrity, MessagesMatchers}

import java.io.{File, PrintWriter}
import java.nio.file.Files

/** The message-file matchers as a consuming spec would use them. */
class MessagesMatchersSpec extends AnyWordSpec with Matchers with TwirlSpec with MessagesMatchers {

"the message file matchers" should {

    "check a file for duplicate keys" in {
      val good = Files.createTempFile("messages", "").toFile
      val out  = new PrintWriter(good, "UTF-8")
      try out.write("a = one\nb = two\n")
      finally out.close()
      Seq(good) must haveNoDuplicateKeys
      good.delete()
    }

    "report warnings alongside errors" in {
      // A file pair with a warning (untranslated) and no error.
      val untranslated = (1 to 10).map(i => s"k$i" -> s"value $i").toMap
      val api          = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> untranslated, "cy" -> untranslated),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      val found        = MessagesIntegrity.check(api)
      found.map(_.severity) must contain(io.github.frikit.twirlspec.expect.Severity.Warning)
      api                   must beConsistentAcrossLanguages() // warnings alone do not fail
    }

    "ignore keys a service has excluded" in {
      val api = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> Map("skip.me" -> "x", "a" -> "A"), "cy" -> Map("a" -> "A cy")),
        langs = new play.api.i18n.DefaultLangs(Seq(english, welsh))
      )
      MessagesIntegrity.check(api).map(_.rule) must contain("messages.welsh-parity")
      MessagesIntegrity
        .check(api, MessagesIntegrity.Config(ignoreKeyPrefixes = Set("skip.")))
        .map(_.rule)                                                                 must not contain "messages.welsh-parity"
      MessagesIntegrity
        .check(api, MessagesIntegrity.Config(ignoreKeys = Set("skip.me")))
        .map(_.rule)                                                                 must not contain "messages.welsh-parity"
      MessagesIntegrity.check(api, MessagesIntegrity.Config(requireWelsh = false)) mustBe empty
    }

    "read a missing file as no findings" in {
      MessagesIntegrity.duplicateKeys(Seq(new File("/definitely/not/here"))) mustBe empty
    }
  }
}
