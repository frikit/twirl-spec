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

import play.api.i18n.{Lang, Messages, MessagesApi}
import play.twirl.api.Html
import io.github.frikit.twirlspec.expect._
import io.github.frikit.twirlspec.page.{Page, Text}

/** The whole authoring surface. Declares no implicits and builds no application, so it drops into an existing spec base. */
trait TwirlSpecDsl extends FramingExpectations with FormExpectations with ContentExpectations with TwirlMatchers {

  /** Parse rendered HTML into a page that can be asked questions. */
  def render(html: Html)(implicit messages: Messages): Page = Page(html).belongingTo(getClass.getName)

  def render(html: Html, lang: Lang, messages: Messages): Page =
    Page(html, lang, messages).belongingTo(getClass.getName)

  /** Render the same view in a given language. */
  def renderIn(lang: Lang)(html: Messages => Html)(implicit messagesApi: MessagesApi): Page = {
    val messages = messagesApi.preferred(Seq(lang))
    Page(html(messages), lang, messages).belongingTo(getClass.getName)
  }

  /** Text expectations take message keys by default; wrap a string in `literal` when you really do mean the exact words.
    */
  def literal(exactWords: String): Expected = Expected.Literal(exactWords)

  /** For "there is a heading, its wording is asserted elsewhere". */
  val anyText: Expected = Expected.Anything

  /** Match text against a regular expression rather than an exact string. */
  def matching(regex: scala.util.matching.Regex): Expected = Expected.Pattern(regex)

  /** Find an element the way an assistive technology does: by role, then by the
    * name it announces. Prefer this over an id or a class — if a control cannot
    * be found by role and name, it cannot be found by a screen reader either.
    */
  def role(role: String): RoleExpectation = RoleExpectation(role, None, None)

  /** Page text has its quotes, spaces and soft hyphens normalised before comparison, and a raw
    * `messages(...)` value has not — so `page.text must include(messages("x"))` fails on a curly
    * apostrophe that looks identical. Normalise the expected side the same way.
    */
  def normalised(raw: String): String = Text.normalise(raw)

  /** A message, resolved and normalised, ready to compare against page text. */
  def messageText(key: String, args: Any*)(implicit messages: Messages): String =
    Text.normalise(messages(key, args: _*))

  /** Bundle expectations so a service can name its own house rules once. */
  def expectations(es: Expectation*): Expectation = Expectation.all(es.toSeq)

  /** Overridden by [[TwirlSpec]] to route warnings into the ScalaTest reporter. */
  protected def alertHook(message: String): Unit = ()
}
