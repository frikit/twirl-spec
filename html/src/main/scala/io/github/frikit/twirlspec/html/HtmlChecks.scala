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

package io.github.frikit.twirlspec.html

import io.github.frikit.twirlspec.TwirlSpecDsl
import io.github.frikit.twirlspec.standards.Rule
import org.scalatest.matchers.{MatchResult, Matcher}

import java.nio.file.{Files, Path}

/** Adds the HTML validity rules to every `display(...)`. */
trait HtmlChecks extends TwirlSpecDsl {

  /** Prefixes this project uses for its own attributes, beyond `data-` and
    * `aria-`.
    */
  def attributePrefixes: Set[String] = Set.empty

  private lazy val htmlStandards: HtmlStandards = new HtmlStandards(
    attributePrefixes
  )

  override def standardsRules: Seq[Rule] =
    super.standardsRules ++ htmlStandards.all

  /** Every Twirl template under this directory closes what it opens:
    *
    * {{{
    * Path.of("app/views") must haveBalancedTemplates
    * }}}
    *
    * A directory that is not there, or holds no templates, fails: nothing was
    * checked, and a wrong path should not pass for ever.
    */
  def haveBalancedTemplates: Matcher[Path] = new Matcher[Path] {
    def apply(root: Path): MatchResult = {
      val templates = TagBalance.templatesUnder(root)
      val problems = templates.flatMap(template =>
        TagBalance
          .checkTemplate(Files.readString(template))
          .map(problem => s"$template:${problem.line} ${problem.message}")
      )
      MatchResult(
        templates.nonEmpty && problems.isEmpty,
        if (templates.isEmpty)
          s"no Twirl templates were found under $root, so nothing was checked"
        else
          s"${problems.size} problem(s) in the ${templates.size} templates under $root:\n  " +
            problems.mkString("\n  "),
        s"the ${templates.size} templates under $root closed everything they opened, but were expected not to"
      )
    }
  }

}
