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

import io.github.frikit.twirlspec.aria.AriaStandards
import io.github.frikit.twirlspec.expect.Severity
import io.github.frikit.twirlspec.govuk.GovukStandards
import io.github.frikit.twirlspec.html.HtmlStandards
import io.github.frikit.twirlspec.i18n.TranslationStandards
import io.github.frikit.twirlspec.quality.{
  MetadataStandards,
  PerformanceStandards,
  SemanticStandards
}
import io.github.frikit.twirlspec.standards.RuleSet
import io.github.frikit.twirlspec.wcag.{
  SecurityStandards,
  TwirlStandards,
  WcagStandards
}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** The README's rule tables are derived from the rule sets, and this holds them
  * to that. A rule added, renamed, reworded or re-graded without the README
  * following fails here, and the failure carries the table to paste in.
  */
class ReadmeSpec extends AnyWordSpec with Matchers {

  private val readme: String = {
    val start = new File(sys.props("user.dir")).getAbsoluteFile
    val root = Iterator
      .iterate(start)(_.getParentFile)
      .takeWhile(_ != null)
      .find(dir => new File(dir, "README.md").isFile)
      .getOrElse(fail(s"no README.md at or above $start"))
    new String(
      Files.readAllBytes(new File(root, "README.md").toPath),
      StandardCharsets.UTF_8
    )
  }

  /** Every rule set, with the module that ships it, in the order the README
    * lists them.
    */
  private val sets: Seq[(String, String, RuleSet)] = Seq(
    ("WcagStandards", "twirl-spec-wcag", WcagStandards),
    ("TwirlStandards", "twirl-spec-wcag", TwirlStandards),
    ("SecurityStandards", "twirl-spec-wcag", SecurityStandards),
    ("GovukStandards", "twirl-spec-govuk", GovukStandards),
    ("AriaStandards", "twirl-spec-aria", AriaStandards),
    ("HtmlStandards", "twirl-spec-html", HtmlStandards),
    ("SemanticStandards", "twirl-spec-quality", SemanticStandards),
    ("PerformanceStandards", "twirl-spec-quality", PerformanceStandards),
    ("MetadataStandards", "twirl-spec-quality", MetadataStandards)
  )

  "the README" should {

    sets.foreach { case (name, module, set) =>
      s"list every rule of $name as the code defines it" in {
        mustCarry(s"the `$name` table", rulesTable(set))
        mustCarry(
          s"the `$name` row of the sets table",
          s"| `$name` | `$module` | ${set.all.size} |"
        )
      }
    }

    "list the WCAG criteria the rules cover" in
      mustCarry("the criteria table", criteriaTable)

    "list every translation rule" in
      mustCarry("the translation rules table", translationTable)

    "count the rules the way the code does" in {
      val wcag =
        WcagStandards.all.size + TwirlStandards.all.size + SecurityStandards.all.size
      val tagged = WcagStandards.all.count(_.criterion.nonEmpty)
      val quality =
        SemanticStandards.all.size + PerformanceStandards.all.size + MetadataStandards.all.size
      val everyone = sets.map(_._3.all.size).sum

      mustCarry("the total", s"$everyone rules in nine sets")
      mustCarry(
        "the wcag module row",
        s"| `twirl-spec-wcag` | core | $wcag rules: $tagged tagged with a WCAG success criterion, " +
          s"${WcagStandards.conventions.size} structural convention, ${TwirlStandards.all.size} for Twirl rendering, " +
          s"${SecurityStandards.all.size} for safety |"
      )
      mustCarry(
        "the govuk module row",
        s"| `twirl-spec-govuk` | core, wcag | ${GovukStandards.all.size} GOV.UK"
      )
      mustCarry(
        "the quality module row",
        s"| `twirl-spec-quality` | core | $quality rules:"
      )
      mustCarry(
        "the aria module row",
        s"| `twirl-spec-aria` | core | ${AriaStandards.all.size} ARIA"
      )
      mustCarry(
        "the html module row",
        s"| `twirl-spec-html` | core | ${HtmlStandards.all.size} HTML"
      )
      mustCarry(
        "the WcagStandards introduction",
        s"Accessibility: $tagged rules each enforce a WCAG success criterion"
      )
    }
  }

  private def rulesTable(set: RuleSet): String = {
    val withCriteria = set.all.exists(_.criterion.nonEmpty)
    val header =
      if (withCriteria) "| Rule | Checks | Criterion |\n|---|---|---|"
      else "| Rule | Checks |\n|---|---|"
    val rows = set.all.map { rule =>
      val checks = marked(rule.description, rule.severity)
      val criterion =
        rule.criterion.fold("convention")(c =>
          s"${c.number} ${c.title} · ${c.level.name} · WCAG ${c.since.name}"
        )
      if (withCriteria) s"| `${rule.id}` | $checks | $criterion |"
      else s"| `${rule.id}` | $checks |"
    }
    (header +: rows).mkString("\n")
  }

  private def criteriaTable: String = {
    val header = "| Criterion | Level | Since | Rules |\n|---|---|---|---|"
    val rows = WcagStandards.criteria.map { c =>
      val rules = WcagStandards.all
        .filter(_.criterion.contains(c))
        .map(r => s"`${r.id}`")
        .mkString(", ")
      s"| ${c.number} ${c.title} | ${c.level.name} | ${c.since.name} | $rules |"
    }
    (header +: rows).mkString("\n")
  }

  private def translationTable: String = {
    val header = "| Rule | Checks |\n|---|---|"
    val rows = TranslationStandards
      .all()
      .map(rule =>
        s"| `${rule.id}` | ${marked(rule.description, rule.severity)} |"
      )
    (header +: rows).mkString("\n")
  }

  private def marked(description: String, severity: Severity): String =
    if (severity == Severity.Warning) s"$description *(warning)*"
    else description

  private def mustCarry(what: String, block: String): Unit =
    if (!readme.contains(block))
      fail(
        s"README.md does not carry $what as the code defines it. Paste this in:\n\n$block\n"
      )

}
