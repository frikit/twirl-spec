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

import org.scalatest.{Alerting, Suite}
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.{AnyContentAsEmpty, Cookie, Request}
import play.api.test.FakeRequest
import play.twirl.api.Html
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.render.SharedApplication

import scala.reflect.ClassTag
import scala.util.DynamicVariable

/** The batteries-included entry point: an application, the implicits a Twirl
  * view needs, and language switching, on top of the [[TwirlSpecDsl]] surface.
  *
  * {{{
  * class WhatIsYourNameViewSpec extends AnyWordSpec with Matchers with TwirlSpec {
  *
  *   private val view = inject[WhatIsYourNameView]
  *   private val form = inject[WhatIsYourNameFormProvider].apply()
  *
  *   "WhatIsYourNameView" should {
  *     "render the question" in {
  *       render(view(form, NormalMode)) must display(
  *         title("whatIsYourName.title"),
  *         heading("whatIsYourName.heading"),
  *         textInput("firstName").labelled("whatIsYourName.firstName"),
  *         submitButton()
  *       )
  *     }
  *   }
  * }
  * }}}
  *
  * A frontend that already has a working spec base should mix in
  * [[TwirlSpecDsl]] instead — it adds the DSL and nothing that could clash.
  */
trait TwirlSpec extends TwirlSpecDsl { self: Suite with Alerting =>

  /** Extra configuration for this suite's application. Suites sharing a
    * configuration share the application; see [[SharedApplication]].
    */
  def applicationConfig: Map[String, Any] = Map.empty

  lazy val app: Application = SharedApplication(applicationConfig)

  def inject[A](implicit tag: ClassTag[A]): A = app.injector.instanceOf[A]

  lazy val messagesApiInstance: MessagesApi = inject[MessagesApi]

  implicit def messagesApi: MessagesApi = messagesApiInstance

  val english: Lang = Lang("en")
  val welsh: Lang   = Lang("cy")

  /** Every language the application is configured for, English first. */
  def languages: Seq[Lang] = {
    // Play's reference.conf always defines this, so there is no fallback to write.
    val configured = app.configuration.get[Seq[String]]("play.i18n.langs").map(Lang(_))
    configured.sortBy(l => if (l.code == "en") 0 else 1)
  }

  private val currentLanguage = new DynamicVariable[Lang](english)

  def currentLang: Lang = currentLanguage.value

  implicit def messages: Messages = messagesApiInstance.preferred(Seq(currentLang))

  implicit def request: Request[AnyContentAsEmpty.type] =
    FakeRequest("GET", "/").withCookies(Cookie(messagesApiInstance.langCookieName, currentLang.code))

  /** Render this block with Welsh `messages` and a Welsh request in scope. */
  def inWelsh[A](block: => A): A = inLanguage(welsh)(block)

  def inEnglish[A](block: => A): A = inLanguage(english)(block)

  def inLanguage[A](lang: Lang)(block: => A): A = currentLanguage.withValue(lang)(block)

  /** Run the same block once per configured language.
    *
    * Projects commonly check that their message files have matching keys, but
    * rarely render a page in the second language. A translation that breaks the
    * layout — an untranslated placeholder, a string long enough to wrap a
    * button, a stray quote that eats the rest of the sentence — then ships
    * unseen. This closes that gap for the cost of one wrapper.
    */
  def inEachLanguage(block: Lang => Unit): Unit = languages.foreach(lang => inLanguage(lang)(block(lang)))

  /** Render a view in the current language. */
  def renderPage(html: => Html): Page = Page(html, currentLang, messages)

  /** Render the same view in every configured language. */
  def renderInEachLanguage(html: => Html): Seq[Page] =
    languages.map(lang => inLanguage(lang)(Page(html, lang, messages)))

  override protected def alertHook(message: String): Unit = alert(message)
}
