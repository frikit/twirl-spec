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

package templateshapes.html

import play.twirl.api.Html

/** Shaped as Twirl compiles a template, in the package a template under
  * `templateshapes` compiles to, and doing what Twirl itself would not.
  */
class Disagreeing {
  def apply(a: String): Html = Html(a)
  def render(a: String): Html = Html(s"$a!")
  def f: String => Html = apply
  def ref: this.type = this
}

class Throwing {
  def apply(a: String): Html = throw new IllegalStateException("boom")
  def render(a: String): Html = apply(a)
  def f: String => Html = apply
  def ref: this.type = this
}

class TakingPrimitives {
  def apply(a: Int, b: Boolean): Html = Html(s"$a$b")
  def render(a: Int, b: Boolean): Html = apply(a, b)
  def f: (Int, Boolean) => Html = (a, b) => apply(a, b)
  def ref: this.type = this
}

class TakingAnArray {
  def apply(a: Array[String]): Html = Html(a.mkString)
  def render(a: Array[String]): Html = apply(a)
  def f: Array[String] => Html = apply
  def ref: this.type = this
}

class Renderless {
  def apply(a: String): Html = Html(a)
}
