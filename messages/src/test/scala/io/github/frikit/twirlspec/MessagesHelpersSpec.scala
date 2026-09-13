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
import io.github.frikit.twirlspec.messages.MessagesIntegrity

/** Edge cases of the message-file helpers. */
class MessagesHelpersSpec extends AnyWordSpec with Matchers with Bilingual {

  "message file helpers" should {
    "expose each language's map" in {
      MessagesIntegrity.baseMessages(messagesApi)        must contain key "service.name"
      MessagesIntegrity.messagesFor(messagesApi, "cy")   must contain key "service.name"
      MessagesIntegrity.messagesFor(messagesApi, "fr") mustBe empty
    }

    "abbreviate a long list of missing keys" in {
      val english = (1 to 30).map(i => s"k$i" -> s"v$i").toMap
      val api     = new play.api.i18n.DefaultMessagesApi(
        messages = Map("default" -> english, "cy" -> Map("k1" -> "v1")),
        langs = new play.api.i18n.DefaultLangs(Seq(this.english, welsh))
      )
      val v       = MessagesIntegrity.check(api).find(_.rule == "messages.translation-parity")
      v.flatMap(_.actual).getOrElse("") must include("more)")
    }
  }

}
