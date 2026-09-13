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
  */
trait TwirlSpec extends TwirlSpecDsl { self: Suite with Alerting =>

  /** Extra configuration for this suite's application. */
  def applicationConfig: Map[String, Any] = Map.empty

  /** The application this suite renders with, shared across the JVM with every
    * suite that uses the same [[applicationConfig]].
    */
  lazy val app: Application = SharedApplication(applicationConfig)

  /** An instance from the application's injector: `inject[MyView]` is how a
    * spec gets the view it tests.
    */
  def inject[A](implicit tag: ClassTag[A]): A = app.injector.instanceOf[A]

  /** The application's `MessagesApi`, held as a stable value for the implicits
    * below.
    */
  lazy val messagesApiInstance: MessagesApi = inject[MessagesApi]

  /** The application's `MessagesApi`, implicitly, for `renderIn` and the
    * message-file matchers.
    */
  implicit def messagesApi: MessagesApi = messagesApiInstance

  /** The English `Lang`, the base language for everything here. */
  val english: Lang = Lang("en")

  /** Every language the application is configured for, English first. */
  def languages: Seq[Lang] = {
    // Play's reference.conf always defines this, so there is no fallback to write.
    val configured =
      app.configuration.get[Seq[String]]("play.i18n.langs").map(Lang(_))
    configured.sortBy(l => if (l.code == "en") 0 else 1)
  }

  private val currentLanguage = new DynamicVariable[Lang](english)

  /** The language the current block runs in: [[english]] unless inside
    * [[inLanguage]].
    */
  def currentLang: Lang = currentLanguage.value

  /** `Messages` for the current language, implicitly, so a view can be applied
    * directly.
    */
  implicit def messages: Messages =
    messagesApiInstance.preferred(Seq(currentLang))

  /** A GET request carrying the language cookie for the current language,
    * implicitly, for a view that takes one.
    */
  implicit def request: Request[AnyContentAsEmpty.type] =
    FakeRequest("GET", "/").withCookies(
      Cookie(messagesApiInstance.langCookieName, currentLang.code)
    )

  /** Run a block in English, whatever language the enclosing block is in. */
  def inEnglish[A](block: => A): A = inLanguage(english)(block)

  /** Render this block with `messages` and a request for the given language in
    * scope.
    */
  def inLanguage[A](lang: Lang)(block: => A): A =
    currentLanguage.withValue(lang)(block)

  /** Run the same block once per configured language. */
  def inEachLanguage(block: Lang => Unit): Unit =
    languages.foreach(lang => inLanguage(lang)(block(lang)))

  /** Render a view in the current language: `render`, with the language made
    * explicit.
    */
  def renderPage(html: => Html): Page = render(html, currentLang, messages)

  /** Render the same view in every configured language. */
  def renderInEachLanguage(html: => Html): Seq[Page] =
    languages.map(lang => inLanguage(lang)(render(html, lang, messages)))

  override protected def alertHook(message: String): Unit = alert(message)
}
