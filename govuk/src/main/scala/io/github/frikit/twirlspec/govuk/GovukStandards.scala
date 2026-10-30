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

package io.github.frikit.twirlspec.govuk

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.expect.{Matching, Violation}
import io.github.frikit.twirlspec.page.Text
import io.github.frikit.twirlspec.standards.Rule.Warning
import org.jsoup.nodes.Element

import scala.jdk.CollectionConverters._

/** Conventions of the GOV.UK Design System, on top of
  * [[io.github.frikit.twirlspec.wcag.WcagStandards]].
  */
object GovukStandards extends RuleSet {

  lazy val all: Seq[Rule] = Seq(
    Rule(
      "error-title-prefix",
      "an error state prefixes the browser title",
      pageLevel = true
    ) { page =>
      val hasErrors = page.errorSummary.nonEmpty
      val prefixes = Matching.errorTitlePrefixes(page)
      val prefixed = prefixes.exists(p => page.title.startsWith(p))
      if (hasErrors && !prefixed)
        Seq(
          Violation(
            "error-title-prefix",
            "the page shows an error summary but its title is not prefixed",
            expected = Some(s"${prefixes.head} ${page.title}"),
            actual = Some(page.title)
          ).withHint("WCAG 2.4.2 — the tab title must announce the error")
        )
      else if (!hasErrors && prefixed)
        Seq(
          Violation(
            "error-title-prefix",
            "the title is prefixed for an error but the page has no errors",
            actual = Some(page.title)
          )
        )
      else Nil
    },
    Rule(
      "error-aria-describedby",
      "an inline error is announced with its field"
    ) { page =>
      page.document
        .select(".govuk-error-message[id]")
        .asScala
        .toList
        .flatMap { errorElement =>
          val errorId = errorElement.id()
          val fieldId = errorId.stripSuffix("-error")
          val field = page.byId(fieldId)
          if (field.isEmpty) Nil // covered by error-summary-targets
          else {
            // A single input references its own error.
            val own = field.attr("aria-describedby").getOrElse("")
            val ancestor = field.headOption
              .flatMap(e => Option(e.closest("fieldset[aria-describedby]")))
              .map(_.attr("aria-describedby"))
              .getOrElse("")
            val describedBy = Seq(own, ancestor).filter(_.nonEmpty)

            if (describedBy.exists(_.split("\\s+").contains(errorId))) Nil
            else
              Seq(
                Violation(
                  "error-aria-describedby",
                  s"""field "$fieldId" does not reference its error message""",
                  expected = Some(
                    s"aria-describedby containing `$errorId`, on the field or its fieldset"
                  ),
                  actual = Some(
                    if (describedBy.isEmpty) "(no aria-describedby)"
                    else describedBy.mkString(" | ")
                  )
                ).withHint("WCAG 3.3.1")
              )
          }
        }
    },
    Rule(
      "error-hidden-prefix",
      "an inline error carries a visually hidden prefix"
    ) { page =>
      page.document
        .select(".govuk-error-message")
        .asScala
        .toList
        .filter(_.select(".govuk-visually-hidden").isEmpty)
        .map { e =>
          Violation(
            "error-hidden-prefix",
            "an inline error has no visually hidden prefix",
            actual = Some(Text.preview(e.text(), 90))
          ).withHint(
            """govukErrorMessage renders <span class="govuk-visually-hidden">Error:</span>"""
          )
        }
    },
    Rule(
      "error-summary-targets",
      "every error summary link lands on an element"
    ) { page =>
      page.errorSummaryDanglingLinks.map { case (target, text) =>
        Violation(
          "error-summary-targets",
          s"error summary links to #$target but nothing on the page has that id",
          actual = Some(text)
        ).withHint("WCAG 2.4.3 — the link must move focus to the field")
      }
    },
    Rule(
      "error-summary-focusable",
      "the error summary can take focus",
      severity = Warning
    ) { page =>
      page.errorSummary.headOption.toSeq.flatMap { e =>
        val focusable = e.hasAttr("tabindex") || e
          .attr("data-module")
          .contains("govuk-error-summary")
        if (focusable) Nil
        else
          Seq(
            Violation(
              "error-summary-focusable",
              "the error summary is neither focusable nor a govuk-error-summary module"
            ).warn
              .withHint("use govukErrorSummary so focus moves to it on load")
          )
      }
    },
    Rule(
      "error-in-summary",
      "an inline error has an entry in the error summary",
      pageLevel = true,
      severity = Warning
    ) { page =>
      val entered = page.errorSummaryLinks.flatMap { case (target, _) =>
        page.errorAt(target)
      }
      page.document
        .select(".govuk-error-message")
        .asScala
        .toList
        .filter(page.isExposed)
        .filterNot(entered.contains)
        .map(e =>
          Violation(
            "error-in-summary",
            "an inline error has no entry in the error summary",
            actual = Some(page.errorWords(e))
          ).withHint(
            "a page with an error shows a summary of them at the top, linking to each field"
          )
        )
    },
    Rule(
      "error-summary-has-inline",
      "an error summary entry has an inline error beside its field",
      severity = Warning
    ) { page =>
      page.errorSummaryLinks
        .filter { case (target, _) =>
          target.nonEmpty && page.byId(target).nonEmpty &&
          page.errorAt(target).isEmpty
        }
        .map { case (target, text) =>
          Violation(
            "error-summary-has-inline",
            s"the error summary's entry for #$target has no inline error beside that field",
            actual = Some(text)
          )
        }
    },
    Rule(
      "error-summary-wording",
      "an error reads the same in the summary and beside its field",
      severity = Warning
    ) { page =>
      page.errorSummaryLinks.flatMap { case (target, text) =>
        page
          .errorAt(target)
          .map(page.errorWords)
          .filter(_ != text)
          .map(inline =>
            Violation(
              "error-summary-wording",
              s"the error for #$target reads differently beside its field",
              expected = Some(text),
              actual = Some(inline)
            )
          )
      }
    },
    Rule(
      "skip-link-target",
      "the skip link lands on an element",
      pageLevel = true,
      severity = Warning
    ) { page =>
      page.document
        .select("a.govuk-skip-link[href^=#]")
        .asScala
        .toList
        .filter(e => page.byId(e.attr("href").stripPrefix("#")).isEmpty)
        .map(e =>
          Violation(
            "skip-link-target",
            s"the skip link goes to ${e.attr("href")} but nothing on the page has that id",
            actual = Some(Text.normalise(e.text()))
          ).withHint(
            "WCAG 2.4.1 — usually the main content's id, main-content"
          )
        )
    },
    Rule(
      "link-names-distinct",
      "links called the same go to the same place",
      severity = Warning
    ) { page =>
      page.links.elements
        .filter(page.isExposed)
        .groupBy(page.accessibleName)
        .toList
        .sortBy(_._1)
        .map { case (name, links) =>
          (name, links, links.map(destination).distinct.size)
        }
        .collect {
          case (name, links, places) if name.nonEmpty && places > 1 =>
            Violation(
              "link-names-distinct",
              s"""links called "$name" go to $places different places""",
              actual = Some(links.map(_.attr("href")).distinct.mkString(" | "))
            ).withHint(
              "say what each is for: a summary list's action takes visuallyHiddenText"
            )
        }
    }
  )

  /** Where a link goes: resolved, where the page has a `<base>` to resolve it
    * against, so `/help` and its full address are the one place.
    */
  private def destination(link: Element): String =
    Option(link.absUrl("href")).filter(_.nonEmpty).getOrElse(link.attr("href"))

}
