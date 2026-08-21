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

package io.github.frikit.twirlspec.standards

import org.jsoup.nodes.Element
import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{Page, Text}
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** Accessibility and document-structure rules that hold for any HTML page,
  * checked without the spec having to name a single selector.
  *
  * Nothing here assumes a design system. Each rule cites the WCAG success
  * criterion it comes from, or the structural mistake it catches: duplicate ids
  * that silently break `for` and `aria-describedby`, inputs with no label,
  * headings that skip a level, links with no accessible name.
  *
  * Rules that only make sense for a complete page (title, `<h1>`, `lang`) skip
  * themselves when the rendered fragment is a component rather than a whole
  * page, so the same call is safe in a partial's spec.
  */
object WcagStandards extends RuleSet {

  def all: Seq[Rule] = rules

  /** Rules enforcing a success criterion at exactly this conformance level. */
  def atLevel(level: Level): Seq[Rule] = all.filter(_.level.contains(level))

  /** Rules for a conformance claim, which is cumulative: `AA` includes `A`.
    *
    * Rules that enforce no success criterion — structural conventions rather
    * than accessibility requirements — are excluded, so what you get back is
    * exactly the WCAG surface this library covers at that level.
    */
  def conformingTo(level: Level, version: WcagVersion = WcagVersion.V2_2): Seq[Rule] = {
    val levels   = Level.upTo(level).toSet
    val versions = WcagVersion.upTo(version).toSet
    all.filter(r => r.criterion.exists(c => levels.contains(c.level) && versions.contains(c.since)))
  }

  /** Rules introduced in exactly this version of WCAG. */
  def introducedIn(version: WcagVersion): Seq[Rule] = all.filter(_.wcagVersion.contains(version))

  /** Rules that enforce no WCAG success criterion: structural conventions this
    * library checks because they are widely held, not because WCAG requires it.
    */
  def conventions: Seq[Rule] = all.filter(_.criterion.isEmpty)

  /** The success criteria this rule set covers, deduplicated and ordered. */
  def criteria: Seq[Criterion] =
    all.flatMap(_.criterion).distinct.sortBy(c => (c.since.order, c.number))

  private def rules: Seq[Rule] = Seq(
    Rule("one-h1", "a page has exactly one <h1>", pageLevel = true) { page =>
      page.h1.size match {
        case 1 => Nil
        case 0 => Seq(Violation("one-h1", "the page has no <h1>").withHint("every GOV.UK page needs one heading"))
        case n =>
          Seq(
            Violation(
              "one-h1",
              s"the page has $n <h1> elements",
              expected = Some("1"),
              actual = Some(page.h1.texts.mkString(" | "))
            )
          )
      }
    },
    Rule(
      "title-present",
      "a page has a non-empty <title>",
      pageLevel = true,
      criterion = Some(Criterion("2.4.2", "Page Titled", Level.A, WcagVersion.V2_0))
    ) { page =>
      if (page.title.nonEmpty) Nil else Seq(Violation("title-present", "the page has an empty <title>"))
    },
    Rule(
      "html-lang",
      "the <html> element declares the rendered language",
      pageLevel = true,
      criterion = Some(Criterion("3.1.1", "Language of Page", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.htmlLang match {
        case None                                     =>
          Seq(Violation("html-lang", "the <html> element has no lang attribute").withHint("WCAG 3.1.1"))
        case Some(l) if !l.startsWith(page.lang.code) =>
          Seq(Violation.mismatch("html-lang", page.lang.code, l, "page was rendered in a different language"))
        case _                                        => Nil
      }
    },
    Rule(
      "main-landmark",
      "a page has a <main> landmark",
      pageLevel = true,
      Warning,
      criterion = Some(Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0))
    ) { page =>
      if (page.main.nonEmpty) Nil
      else Seq(Violation("main-landmark", "the page has no <main> landmark").warn.withHint("WCAG 1.3.1"))
    },
    Rule(
      "heading-order",
      "heading levels are not skipped",
      criterion = Some(Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0))
    ) { page =>
      val levels = page.headings.map(_._1)
      levels
        .sliding(2)
        .collect { case Seq(a, b) if b > a + 1 => (a, b) }
        .map { case (a, b) =>
          Violation(
            "heading-order",
            s"heading level jumps from h$a to h$b",
            expected = Some(s"h${a + 1}"),
            actual = Some(s"h$b")
          ).withHint("WCAG 1.3.1 — screen reader users navigate by heading level")
        }
        .toSeq
    },
    Rule(
      "no-empty-headings",
      "headings have text",
      criterion = Some(Criterion("2.4.6", "Headings and Labels", Level.AA, WcagVersion.V2_0))
    ) { page =>
      page.headings.filter(_._2.isEmpty).map { case (level, _) =>
        Violation("no-empty-headings", s"an <h$level> is empty")
      }
    },
    Rule(
      "unique-ids",
      "element ids are unique",
      criterion = Some(Criterion("4.1.2", "Name, Role, Value", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("[id]")
        .asScala
        .toList
        .map(_.id())
        .filter(_.nonEmpty)
        .groupBy(identity)
        .collect { case (id, occurrences) if occurrences.size > 1 => (id, occurrences.size) }
        .toSeq
        .sortBy(_._1)
        .map { case (id, n) =>
          Violation("unique-ids", s"""id "$id" appears $n times""")
            .withHint("duplicate ids break label/for, aria-describedby and error summary links")
        }
    },
    Rule(
      "labelled-controls",
      "every form control has an accessible name",
      criterion = Some(Criterion("3.3.2", "Labels or Instructions", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.formControls.filterNot(hasAccessibleName(page, _)).map { e =>
        Violation(
          "labelled-controls",
          s"<${e.tagName()}${describeControl(e)}> has no label",
          expected = Some(s"""label[for="${e.id()}"], aria-label or aria-labelledby"""),
          actual = Some(Text.preview(e.outerHtml(), 120))
        ).withHint("WCAG 3.3.2 / 4.1.2")
      }
    },
    Rule(
      "grouped-choices",
      "radios and checkboxes sit in a fieldset with a legend",
      criterion = Some(Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0))
    ) { page =>
      val groups = page.document
        .select("input[type=radio], input[type=checkbox]")
        .asScala
        .toList
        .groupBy(_.attr("name"))
        .filter(_._2.size > 1)

      groups.toSeq.sortBy(_._1).flatMap { case (name, members) =>
        Option(members.head.closest("fieldset")) match {
          case None           =>
            Seq(
              Violation("grouped-choices", s"""the "$name" group is not inside a <fieldset>""")
                .withHint("WCAG 1.3.1 — use govukRadios / govukCheckboxes with a fieldset")
            )
          case Some(fieldset) =>
            if (fieldset.select("legend").isEmpty)
              Seq(Violation("grouped-choices", s"""the "$name" fieldset has no <legend>"""))
            else Nil
        }
      }
    },
    Rule(
      "submit-has-name",
      "the submit control has visible text",
      criterion = Some(Criterion("4.1.2", "Name, Role, Value", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("button[type=submit], input[type=submit], button:not([type])")
        .asScala
        .toList
        .filter(e => Text.normalise(e.text()).isEmpty && e.attr("value").isEmpty && e.attr("aria-label").isEmpty)
        .map(_ => Violation("submit-has-name", "a submit control has no accessible name").withHint("WCAG 4.1.2"))
    },
    Rule(
      "table-header-scope",
      "table headers declare a scope",
      severity = Warning,
      criterion = Some(Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("table th:not([scope])")
        .asScala
        .toList
        .map(e =>
          Violation("table-header-scope", s"""a <th> has no scope: "${Text.preview(e.text(), 60)}"""").warn
            .withHint("WCAG 1.3.1 — scope=\"col\" or scope=\"row\"")
        )
    },
    Rule(
      "link-has-name",
      "every link has an accessible name",
      criterion = Some(Criterion("2.4.4", "Link Purpose (In Context)", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.links.elements
        .filter(e => Text.normalise(e.text()).isEmpty && e.attr("aria-label").isEmpty && e.select("img[alt]").isEmpty)
        .map(e =>
          Violation("link-has-name", "a link has no text", actual = Some(Text.preview(e.outerHtml(), 100)))
            .withHint("WCAG 2.4.4")
        )
    },
    Rule(
      "link-text-is-meaningful",
      "link text makes sense out of context",
      severity = Warning,
      criterion = Some(Criterion("2.4.9", "Link Purpose (Link Only)", Level.AAA, WcagVersion.V2_0))
    ) { page =>
      page.links.texts
        .map(_.toLowerCase.replaceAll("[^a-z ]", "").trim)
        .filter(VagueLinkText.contains)
        .distinct
        .map(t =>
          Violation("link-text-is-meaningful", s"""link text "$t" does not describe its destination""").warn
            .withHint("GOV.UK style — link text should make sense on its own")
        )
    },
    Rule(
      "new-tab-is-announced",
      "links opening a new tab say so",
      severity = Warning,
      criterion = Some(Criterion("3.2.5", "Change on Request", Level.AAA, WcagVersion.V2_0))
    ) { page =>
      page.links.elements
        .filter(_.attr("target") == "_blank")
        .filterNot { e =>
          val t = Text.normalise(e.text()).toLowerCase
          t.contains("new tab") || t.contains("new window") || t.contains("tab newydd") ||
          e.attr("aria-label").toLowerCase.contains("new tab")
        }
        .map(e =>
          Violation(
            "new-tab-is-announced",
            s"""link "${Text.preview(e.text(), 50)}" opens a new tab without saying so"""
          ).warn
            .withHint("WCAG 3.2.5 — append \"(opens in new tab)\"")
        )
    },
    Rule(
      "input-purpose-autocomplete",
      "inputs collecting information about the user declare an autocomplete purpose",
      severity = Warning,
      criterion = Some(Criterion("1.3.5", "Identify Input Purpose", Level.AA, WcagVersion.V2_1))
    ) { page =>
      // 1.3.5 applies to fields collecting information *about the user*, which
      // cannot be detected in general. Matching the field name against the
      // autofill tokens WCAG itself lists keeps this useful without guessing:
      // a field called "email" almost certainly wants autocomplete="email", and
      // a field called "reference" is left alone.
      page.formControls
        .filter(e => e.attr("autocomplete").isEmpty)
        .filter { e =>
          val name = (e.attr("name") + " " + e.id()).toLowerCase
          PersonalFieldNames.exists(name.contains)
        }
        .map { e =>
          Violation(
            "input-purpose-autocomplete",
            s"""field "${if (e.attr("name").nonEmpty) e.attr("name")
              else e.id()}" collects information about the user but declares no autocomplete""",
            expected = Some("an autocomplete token, for example autocomplete=\"email\"")
          ).warn.withHint("WCAG 1.3.5 — lets a browser fill the field on the user's behalf")
        }
    },
    Rule(
      "image-alt",
      "every image has an alt attribute",
      criterion = Some(Criterion("1.1.1", "Non-text Content", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.images.elements
        .filterNot(_.hasAttr("alt"))
        .map(e =>
          Violation("image-alt", "an <img> has no alt attribute", actual = Some(e.attr("src")))
            .withHint("""WCAG 1.1.1 — use alt="" for decorative images""")
        )
    }
  )

  /** Substrings of the autofill tokens WCAG 1.3.5 enumerates, which is what
    * makes the heuristic defensible rather than a guess.
    */
  private val PersonalFieldNames =
    Set(
      "email",
      "tel",
      "phone",
      "postcode",
      "postal-code",
      "given-name",
      "family-name",
      "firstname",
      "lastname",
      "surname",
      "address",
      "birthday",
      "username",
      "organization"
    )

  private val VagueLinkText =
    Set("click here", "here", "more", "read more", "link", "this page", "this", "click", "more information")

  private def hasAccessibleName(page: Page, e: Element): Boolean = {
    val id = e.id()
    (id.nonEmpty && page.labelFor(id).nonEmpty) ||
    e.attr("aria-label").nonEmpty ||
    e.attr("aria-labelledby").nonEmpty ||
    e.attr("title").nonEmpty ||
    Option(e.closest("label")).isDefined
  }

  private def describeControl(e: Element): String = {
    val id   = Option(e.id()).filter(_.nonEmpty).map(v => s""" id="$v"""").getOrElse("")
    val name = Option(e.attr("name")).filter(_.nonEmpty).map(v => s""" name="$v"""").getOrElse("")
    s"$id$name"
  }

}
