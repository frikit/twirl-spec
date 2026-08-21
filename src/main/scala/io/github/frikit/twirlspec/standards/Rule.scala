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

package io.github.frikit.twirlspec.standards

import io.github.frikit.twirlspec.expect.{Severity, Violation}
import io.github.frikit.twirlspec.page.Page

/** One named GOV.UK / WCAG rule.
  *
  * `pageLevel` rules are skipped when the rendered HTML is a fragment rather
  * than a whole page, so a spec for a partial can run the same standards call
  * as a spec for a full page without drowning in "this component has no
  * `<title>`".
  */
final class Rule(
  val id: String,
  val description: String,
  val pageLevel: Boolean,
  val severity: Severity,
  run: Page => Seq[Violation]
) {

  def check(page: Page): Seq[Violation] =
    if (pageLevel && !Rule.isFullPage(page)) Nil
    else {
      val found = run(page)
      if (severity == Severity.Warning) found.map(_.warn) else found
    }

  override def toString: String = s"$id — $description"
}

object Rule {

  val Warning: Severity  = Severity.Warning
  val Blocking: Severity = Severity.Error

  def apply(
    id: String,
    description: String,
    pageLevel: Boolean = false,
    severity: Severity = Severity.Error
  )(run: Page => Seq[Violation]): Rule =
    new Rule(id, description, pageLevel, severity, run)

  /** Jsoup wraps every fragment in `<html><head><body>`, so the parsed tree
    * cannot tell us whether the template included the layout. The rendered
    * source can.
    */
  private[twirlspec] def isFullPage(page: Page): Boolean = {
    val head = page.source.take(2000).toLowerCase
    head.contains("<!doctype html") || head.contains("<html")
  }

}
