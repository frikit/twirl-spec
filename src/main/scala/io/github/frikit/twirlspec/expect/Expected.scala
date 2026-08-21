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

package io.github.frikit.twirlspec.expect

import io.github.frikit.twirlspec.page.Page

/** Something a check expects to find on the page, expressed either as a
  * message key (the default, because that is what GOV.UK content always is) or
  * as a literal string.
  *
  * Resolving a key that is not in `conf/messages` produces a violation rather
  * than silently comparing against the key text. Play returns the key itself
  * for an undefined key, so a hand-written `doc.title mustBe messages("x.y")`
  * passes happily when both sides are the missing key — a green test over a
  * page showing "x.y" to a citizen. This type is where that stops.
  */
sealed trait Expected {
  def describe: String
  def resolve(page: Page): Either[Violation, String]
}

object Expected {

  final case class Key(key: String, args: Seq[Any] = Nil) extends Expected {
    def describe: String = if (args.isEmpty) s"messages($key)" else s"messages($key, ${args.mkString(", ")})"

    def resolve(page: Page): Either[Violation, String] =
      page.message(key, args) match {
        case Some(value) => Right(value)
        case None        =>
          Left(
            Violation(
              rule = "message key",
              message = s"message key `$key` is not defined for lang `${page.lang.code}`",
              expected = Some(key)
            ).withHint(s"add `$key` to conf/messages${if (page.lang.code == "en") "" else "." + page.lang.code}")
          )
      }

  }

  final case class Literal(value: String) extends Expected {
    def describe: String                               = s""""$value""""
    def resolve(page: Page): Either[Violation, String] = Right(io.github.frikit.twirlspec.page.Text.normalise(value))
  }

  /** Matches anything non-empty — for "there is a heading, I don't care what". */
  case object Anything extends Expected {
    def describe: String                               = "anything non-empty"
    def resolve(page: Page): Either[Violation, String] = Right("")
  }

}
