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

/** The whole authoring surface. Declares no implicits and builds no
  * application, so it drops into an existing spec base.
  */
trait TwirlSpecDsl
    extends FramingExpectations
    with FormExpectations
    with ContentExpectations
    with TwirlMatchers {

  /** Parse rendered HTML into a page that can be asked questions. */
  def render(html: Html)(implicit messages: Messages): Page =
    Page(html).belongingTo(getClass.getName)

  /** Parse rendered HTML into a page for an explicit language and its messages,
    * rather than the implicit ones.
    */
  def render(html: Html, lang: Lang, messages: Messages): Page =
    Page(html, lang, messages).belongingTo(getClass.getName)

  /** Render the same view in a given language, which must be one the
    * application is configured for.
    */
  def renderIn(
      lang: Lang
  )(html: Messages => Html)(implicit messagesApi: MessagesApi): Page = {
    val messages = configuredMessages(lang, messagesApi)
    Page(html(messages), messages.lang, messages).belongingTo(getClass.getName)
  }

  /** `Messages` for a language, refusing the fallback Play makes when the
    * language is not configured. Play would hand back the first configured
    * language instead, and a spec meant for Welsh would pass on English text.
    *
    * Play matches a language the way RFC 4647 lookup does: it narrows a request
    * (`en-GB` finds a configured `en`) but never widens one (`cy` does not find
    * `cy-GB`). So a request without a region is first pointed at the configured
    * form of its language, and then whatever Play settles on is accepted as
    * long as it is the language asked for. The `Messages` returned are for the
    * configured form, so a caller can render and label the page in it.
    */
  private[twirlspec] def configuredMessages(
      lang: Lang,
      messagesApi: MessagesApi
  ): Messages = {
    val configured = messagesApi.messages.keySet
      .filterNot(_.startsWith("default"))
      .toList
      .sorted
      .map(Lang(_))
    val target =
      if (lang.country.nonEmpty || configured.contains(lang)) lang
      else configured.find(_.language == lang.language).getOrElse(lang)
    val messages = messagesApi.preferred(Seq(target))
    if (messages.lang.language != lang.language)
      throw new IllegalArgumentException(
        s"${lang.code} is not a configured language (play.i18n.langs: " +
          s"${configured.map(_.code).mkString(", ")}), so Play would render " +
          s"${messages.lang.code} in its place. Declare it in " +
          "conf/application.conf, or through applicationConfig."
      )
    messages
  }

  /** Text expectations take message keys by default; wrap a string in `literal`
    * when you really do mean the exact words.
    */
  def literal(exactWords: String): Expected = Expected.Literal(exactWords)

  /** For "there is a heading, its wording is asserted elsewhere". */
  val anyText: Expected = Expected.Anything

  /** Match text against a regular expression rather than an exact string. */
  def matching(regex: scala.util.matching.Regex): Expected =
    Expected.Pattern(regex)

  /** Find an element the way an assistive technology does: by role, then by the
    * name it announces. Prefer this over an id or a class — if a control cannot
    * be found by role and name, it cannot be found by a screen reader either.
    */
  def role(role: String): RoleExpectation = RoleExpectation(role, None, None)

  /** Page text has its quotes, spaces and soft hyphens normalised before
    * comparison, and a raw `messages(...)` value has not — so
    * `page.text must include(messages("x"))` fails on a curly apostrophe that
    * looks identical. Normalise the expected side the same way.
    */
  def normalised(raw: String): String = Text.normalise(raw)

  /** A message, resolved and normalised, ready to compare against page text. */
  def messageText(key: String, args: Any*)(implicit
      messages: Messages
  ): String =
    Text.normalise(messages(key, args: _*))

  /** Bundle expectations so a service can name its own house rules once. */
  def expectations(es: Expectation*): Expectation = Expectation.all(es.toSeq)

  /** Overridden by [[TwirlSpec]] to route warnings into the ScalaTest reporter.
    */
  protected def alertHook(message: String): Unit = ()
}
