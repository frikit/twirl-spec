package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Lang}
import io.github.frikit.twirlspec.messages.MessagesIntegrity

import java.io.PrintWriter
import java.nio.file.Files

class MessagesEdgeCasesSpec extends AnyWordSpec with Matchers with TwirlSpec {

  "baseMessages" should {

    "merge the default and en maps when both are present" in {
      val api = new DefaultMessagesApi(
        messages = Map("default" -> Map("a" -> "A"), "en" -> Map("b" -> "B")),
        langs = new DefaultLangs(Seq(Lang("en")))
      )
      MessagesIntegrity.baseMessages(api).keySet mustBe Set("a", "b")
    }

    "cope with an application that defines no default map at all" in {
      val api = new DefaultMessagesApi(
        messages = Map("en" -> Map("b" -> "B")),
        langs = new DefaultLangs(Seq(Lang("en")))
      )
      MessagesIntegrity.baseMessages(api).keySet mustBe Set("b")
    }

    "cope with an application that defines no en map at all" in {
      val api = new DefaultMessagesApi(
        messages = Map("default" -> Map("a" -> "A")),
        langs = new DefaultLangs(Seq(Lang("en")))
      )
      MessagesIntegrity.baseMessages(api).keySet mustBe Set("a")
    }
  }

  "duplicate key detection" should {

    "order its findings, so a diff of two runs is stable" in {
      val file = Files.createTempFile("messages", "").toFile
      val out  = new PrintWriter(file, "UTF-8")
      try out.write("zebra = one\napple = two\nzebra = three\napple = four\nmiddle = five\n")
      finally out.close()

      val found = MessagesIntegrity.duplicateKeys(Seq(file))
      found.map(_.message.split("`")(1)) mustBe Seq("apple", "zebra")
      file.delete()
    }
  }

}
