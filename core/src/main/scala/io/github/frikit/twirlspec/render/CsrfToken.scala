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

package io.github.frikit.twirlspec.render

import play.api.mvc.Request

/** A CSRF token for the request a view is rendered with.
  *
  * Play's `@helper.CSRF.formField` throws on a request without one, and
  * play-frontend-hmrc's `formWithCSRF` quietly leaves the field out, so a spec
  * would render a form either not at all or unlike the one a citizen sees.
  *
  * The token comes from Play's `CSRFTokenHelper`, which lives in
  * `play-filters-helpers`. A Play application built with the Play sbt plugin
  * has it; one built without the filters does not, and there the request is
  * left as it is rather than failing on a class that is not there.
  */
private[twirlspec] object CsrfToken {

  /** Where Play keeps the helper that signs a token onto a test request. */
  val helperClass: String = "play.api.test.CSRFTokenHelper"

  /** Whether a class can be found without initialising it. Only an absent class
    * answers no: anything else wrong with it is left to surface.
    */
  def isOnClasspath(className: String): Boolean =
    try {
      Class.forName(className, false, getClass.getClassLoader)
      true
    } catch { case _: ClassNotFoundException => false }

  /** Whether `play-filters-helpers` is on this classpath. */
  lazy val available: Boolean = isOnClasspath(helperClass)

  /** The request with a signed CSRF token, when the helper is there to sign
    * one.
    */
  def add[A](request: Request[A], helperPresent: Boolean): Request[A] =
    if (helperPresent) Helper.add(request) else request

  /** The only reference to the helper, so the class is resolved when a token is
    * added and never on a classpath that lacks it.
    */
  private object Helper {
    def add[A](request: Request[A]): Request[A] =
      play.api.test.CSRFTokenHelper.addCSRFToken(request)
  }

}
