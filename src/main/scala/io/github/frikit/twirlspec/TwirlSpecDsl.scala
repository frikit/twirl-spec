/*
 * Copyright 2026 HM Revenue & Customs
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
import io.github.frikit.twirlspec.messages.MessagesMatchers
import io.github.frikit.twirlspec.page.Page

/** The whole authoring surface, and nothing else.
  *
  * This trait declares no implicits and builds no application, so it drops into
  * an existing `ViewSpecBase` — however that base already gets hold of
  * `Messages`, a `FakeRequest` and the view itself — without a single ambiguous
  * implicit. That is the intended way in for the 53 frontends that already have
  * a working spec base they do not want to rewrite.
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpecDsl { ... }
  * }}}
  *
  * Greenfield specs can take [[TwirlSpec]] instead, which adds the application,
  * the implicits and the language switching.
  */
trait TwirlSpecDsl
    extends FramingExpectations
    with FormExpectations
    with ContentExpectations
    with TwirlMatchers
    with MessagesMatchers {

  /** Parse rendered HTML into a page that can be asked questions. */
  def render(html: Html)(implicit messages: Messages): Page = Page(html)

  def render(html: Html, lang: Lang, messages: Messages): Page = Page(html, lang, messages)

  /** Render the same view in a given language. */
  def renderIn(lang: Lang)(html: Messages => Html)(implicit messagesApi: MessagesApi): Page = {
    val messages = messagesApi.preferred(Seq(lang))
    Page(html(messages), lang, messages)
  }

  /** Text expectations take message keys by default; wrap a string in
    * `literal` when you really do mean the exact words.
    *
    * Deliberately not called `text` — that is `play.api.data.Forms.text`, which
    * is imported in most specs that build a form inline.
    */
  def literal(exactWords: String): Expected = Expected.Literal(exactWords)

  /** For "there is a heading, its wording is asserted elsewhere". */
  val anyText: Expected = Expected.Anything

  /** Bundle expectations so a service can name its own house rules once.
    *
    * {{{
    * val aCheckAnswersPage = expectations(heading("checkYourAnswers.heading"), submitButton("site.confirm"))
    * page must display(aCheckAnswersPage)
    * }}}
    */
  def expectations(es: Expectation*): Expectation = Expectation.all(es.toSeq)

  /** Overridden by [[TwirlSpec]] to route warnings into the ScalaTest reporter. */
  protected def alertHook(message: String): Unit = ()
}
