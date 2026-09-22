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

package io.github.frikit.twirlspec.wcag

import io.github.frikit.twirlspec.standards._

import org.jsoup.nodes.Element
import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.Text
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** Accessibility and document-structure rules that hold for any HTML page,
  * checked without the spec having to name a single selector.
  */
object WcagStandards extends RuleSet {

  /** Rules enforcing a success criterion at exactly this conformance level. */
  def atLevel(level: Level): Seq[Rule] = all.filter(_.level.contains(level))

  /** Rules for a conformance claim, which is cumulative: `AA` includes `A`. */
  def conformingTo(
      level: Level,
      version: WcagVersion = WcagVersion.V2_2
  ): Seq[Rule] = {
    val levels = Level.upTo(level).toSet
    val versions = WcagVersion.upTo(version).toSet
    all.filter(r =>
      r.criterion.exists(c =>
        levels.contains(c.level) && versions.contains(c.since)
      )
    )
  }

  /** Rules introduced in exactly this version of WCAG. */
  def introducedIn(version: WcagVersion): Seq[Rule] =
    all.filter(_.wcagVersion.contains(version))

  /** Rules that enforce no WCAG success criterion: structural conventions this
    * library checks because they are widely held, not because WCAG requires it.
    */
  def conventions: Seq[Rule] = all.filter(_.criterion.isEmpty)

  /** The success criteria this rule set covers, deduplicated and ordered. */
  def criteria: Seq[Criterion] =
    all.flatMap(_.criterion).distinct.sortBy(c => (c.since.order, c.number))

  lazy val all: Seq[Rule] = Seq(
    Rule("one-h1", "a page has exactly one <h1>", pageLevel = true) { page =>
      page.h1.size match {
        case 1 => Nil
        case 0 =>
          Seq(
            Violation("one-h1", "the page has no <h1>").withHint(
              "every GOV.UK page needs one heading"
            )
          )
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
      criterion =
        Some(Criterion("2.4.2", "Page Titled", Level.A, WcagVersion.V2_0))
    ) { page =>
      if (page.title.nonEmpty) Nil
      else Seq(Violation("title-present", "the page has an empty <title>"))
    },
    Rule(
      "html-lang",
      "the <html> element declares the rendered language",
      pageLevel = true,
      criterion =
        Some(Criterion("3.1.1", "Language of Page", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.htmlLang match {
        case None =>
          Seq(
            Violation("html-lang", "the <html> element has no lang attribute")
              .withHint("WCAG 3.1.1")
          )
        case Some(l) if !l.startsWith(page.lang.code) =>
          Seq(
            Violation.mismatch(
              "html-lang",
              page.lang.code,
              l,
              "page was rendered in a different language"
            )
          )
        case _ => Nil
      }
    },
    Rule(
      "main-landmark",
      "a page has a main landmark",
      pageLevel = true,
      Warning,
      criterion = Some(
        Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      // The selector `single-main` counts with, so the two rules cannot
      // disagree about what a main landmark is. `page.main` is deliberately
      // wider: it also answers to GOV.UK's #main-content, which scopes an
      // assertion but is not itself a landmark.
      if (page.document.select(MainLandmark).asScala.nonEmpty) Nil
      else
        Seq(
          Violation("main-landmark", "the page has no <main> landmark").warn
            .withHint("""WCAG 1.3.1 — <main> or role="main"""")
        )
    },
    Rule(
      "heading-order",
      "heading levels are not skipped",
      criterion = Some(
        Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0)
      )
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
          ).withHint(
            "WCAG 1.3.1 — screen reader users navigate by heading level"
          )
        }
        .toSeq
    },
    Rule(
      "no-empty-headings",
      "headings have text",
      criterion = Some(
        Criterion("2.4.6", "Headings and Labels", Level.AA, WcagVersion.V2_0)
      )
    ) { page =>
      page.headings.filter(_._2.isEmpty).map { case (level, _) =>
        Violation("no-empty-headings", s"an <h$level> is empty")
      }
    },
    Rule(
      "unique-ids",
      "element ids are unique",
      criterion =
        Some(Criterion("4.1.2", "Name, Role, Value", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("[id]")
        .asScala
        .toList
        .map(_.id())
        .filter(_.nonEmpty)
        .groupBy(identity)
        .collect {
          case (id, occurrences) if occurrences.size > 1 =>
            (id, occurrences.size)
        }
        .toSeq
        .sortBy(_._1)
        .map { case (id, n) =>
          Violation("unique-ids", s"""id "$id" appears $n times""")
            .withHint(
              "duplicate ids break label/for, aria-describedby and error summary links"
            )
        }
    },
    Rule(
      "labelled-controls",
      "every form control has an accessible name",
      criterion = Some(
        Criterion("3.3.2", "Labels or Instructions", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      page.formControls.filter(page.accessibleName(_).isEmpty).map { e =>
        Violation(
          "labelled-controls",
          s"<${e.tagName()}${describeControl(e)}> has no label",
          expected =
            Some(s"""label[for="${e.id()}"], aria-label or aria-labelledby"""),
          actual = Some(Text.preview(e.outerHtml(), 120))
        ).withHint("WCAG 3.3.2 / 4.1.2")
      }
    },
    Rule(
      "grouped-choices",
      "radios and checkboxes sit in a fieldset with a legend",
      criterion = Some(
        Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      val groups = page.document
        .select("input[type=radio], input[type=checkbox]")
        .asScala
        .toList
        .groupBy(_.attr("name"))
        .filter(_._2.size > 1)

      groups.toSeq.sortBy(_._1).flatMap { case (name, members) =>
        Option(members.head.closest("fieldset")) match {
          case None =>
            Seq(
              Violation(
                "grouped-choices",
                s"""the "$name" group is not inside a <fieldset>"""
              )
                .withHint(
                  "WCAG 1.3.1 — use govukRadios / govukCheckboxes with a fieldset"
                )
            )
          case Some(fieldset) =>
            if (fieldset.select("legend").isEmpty)
              Seq(
                Violation(
                  "grouped-choices",
                  s"""the "$name" fieldset has no <legend>"""
                )
              )
            else Nil
        }
      }
    },
    Rule(
      "submit-has-name",
      "the submit control has an accessible name",
      criterion =
        Some(Criterion("4.1.2", "Name, Role, Value", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("button[type=submit], input[type=submit], button:not([type])")
        .asScala
        .toList
        .filter(e => page.accessibleName(e).isEmpty)
        .map(_ =>
          Violation(
            "submit-has-name",
            "a submit control has no accessible name"
          ).withHint("WCAG 4.1.2")
        )
    },
    Rule(
      "table-header-scope",
      "table headers declare a scope",
      severity = Warning,
      criterion = Some(
        Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      page.document
        .select("table th:not([scope])")
        .asScala
        .toList
        .map(e =>
          Violation(
            "table-header-scope",
            s"""a <th> has no scope: "${Text.preview(e.text(), 60)}""""
          ).warn
            .withHint("WCAG 1.3.1 — scope=\"col\" or scope=\"row\"")
        )
    },
    Rule(
      "link-has-name",
      "every link has an accessible name",
      criterion = Some(
        Criterion(
          "2.4.4",
          "Link Purpose (In Context)",
          Level.A,
          WcagVersion.V2_0
        )
      )
    ) { page =>
      page.links.elements
        .filter(e => page.accessibleName(e).isEmpty)
        .map(e =>
          Violation(
            "link-has-name",
            "a link has no accessible name",
            actual = Some(Text.preview(e.outerHtml(), 100))
          )
            .withHint("WCAG 2.4.4")
        )
    },
    Rule(
      "link-text-is-meaningful",
      "link text makes sense out of context",
      severity = Warning,
      criterion = Some(
        Criterion(
          "2.4.9",
          "Link Purpose (Link Only)",
          Level.AAA,
          WcagVersion.V2_0
        )
      )
    ) { page =>
      page.links.texts
        .map(_.toLowerCase.replaceAll("[^a-z ]", "").trim)
        .filter(VagueLinkText.contains)
        .distinct
        .map(t =>
          Violation(
            "link-text-is-meaningful",
            s"""link text "$t" does not describe its destination"""
          ).warn
            .withHint("GOV.UK style — link text should make sense on its own")
        )
    },
    Rule(
      "new-tab-is-announced",
      "links opening a new tab say so",
      severity = Warning,
      criterion = Some(
        Criterion("3.2.5", "Change on Request", Level.AAA, WcagVersion.V2_0)
      )
    ) { page =>
      page.links.elements
        .filter(_.attr("target") == "_blank")
        .filterNot { e =>
          val t = Text.normalise(e.text()).toLowerCase
          t.contains("new tab") || t.contains("new window") || t.contains(
            "tab newydd"
          ) ||
          e.attr("aria-label").toLowerCase.contains("new tab")
        }
        .map(e =>
          Violation(
            "new-tab-is-announced",
            s"""link "${Text
                .preview(e.text(), 50)}" opens a new tab without saying so"""
          ).warn
            .withHint("WCAG 3.2.5 — append \"(opens in new tab)\"")
        )
    },
    Rule(
      "input-purpose-autocomplete",
      "inputs collecting information about the user declare an autocomplete purpose",
      severity = Warning,
      criterion = Some(
        Criterion("1.3.5", "Identify Input Purpose", Level.AA, WcagVersion.V2_1)
      )
    ) { page =>
      // 1.3.5 applies to fields collecting information *about the user*, which cannot be detected in general.
      page.formControls
        .filter(e => e.attr("autocomplete").isEmpty)
        .filter { e =>
          val name = (e.attr("name") + " " + e.id()).toLowerCase
          PersonalFieldNames.exists(name.contains)
        }
        .map { e =>
          Violation(
            "input-purpose-autocomplete",
            s"""field "${
                if (e.attr("name").nonEmpty) e.attr("name")
                else e.id()
              }" collects information about the user but declares no autocomplete""",
            expected =
              Some("an autocomplete token, for example autocomplete=\"email\"")
          ).warn.withHint(
            "WCAG 1.3.5 — lets a browser fill the field on the user's behalf"
          )
        }
    },
    Rule(
      "aria-references-resolve",
      "every aria reference points at an element that exists",
      criterion = Some(
        Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      val attributes =
        Seq("aria-describedby", "aria-labelledby", "aria-controls", "aria-owns")
      page.document
        .select(attributes.map(a => s"[$a]").mkString(", "))
        .asScala
        .toList
        .flatMap { e =>
          attributes.flatMap { attribute =>
            e.attr(attribute)
              .split("\\s+")
              .filter(_.nonEmpty)
              .filter(id => page.byId(id).isEmpty)
              .map(id =>
                Violation(
                  "aria-references-resolve",
                  s"""$attribute names "$id", but nothing on the page has that id""",
                  actual = Some(Text.preview(e.outerHtml(), 90))
                ).withHint(
                  "a reference that resolves to nothing is silently dropped by a screen reader"
                )
              )
          }
        }
    },
    Rule(
      "no-aria-hidden-focusable",
      "nothing hidden from assistive technology can still take focus",
      criterion =
        Some(Criterion("4.1.2", "Name, Role, Value", Level.A, WcagVersion.V2_0))
    ) { page =>
      val focusable = "a[href], button, input, select, textarea, [tabindex]"
      // The hint below tells a reader to add tabindex="-1". That has to be
      // enough to satisfy the rule, so an element taken out of the tab order
      // no longer counts as focusable — otherwise following the advice leaves
      // the test red.
      def stillFocusable(e: Element): Boolean =
        e.is(focusable) && !removedFromTabOrder(e)
      page.document
        .select("[aria-hidden=true]")
        .asScala
        .toList
        .filter(e =>
          stillFocusable(e) || e
            .select(focusable)
            .asScala
            .exists(stillFocusable)
        )
        .map(e =>
          Violation(
            "no-aria-hidden-focusable",
            "an element hidden from assistive technology can still be reached by keyboard",
            actual = Some(Text.preview(e.outerHtml(), 90))
          ).withHint("""add tabindex="-1", or do not hide it""")
        )
    },
    Rule(
      "no-positive-tabindex",
      "focus order follows the document",
      criterion =
        Some(Criterion("2.4.3", "Focus Order", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("[tabindex]")
        .asScala
        .toList
        .filter(e => tabIndexOf(e).exists(_ > 0))
        .map(e =>
          Violation(
            "no-positive-tabindex",
            s"""tabindex="${e.attr(
                "tabindex"
              )}" pulls this element out of the natural focus order""",
            actual = Some(Text.preview(e.outerHtml(), 90))
          ).withHint(
            "order the markup instead; a positive tabindex must be kept correct against every change"
          )
        )
    },
    Rule(
      "zoom-not-blocked",
      "the page can be zoomed",
      pageLevel = true,
      criterion =
        Some(Criterion("1.4.4", "Resize Text", Level.AA, WcagVersion.V2_0))
    ) { page =>
      page.document
        .select("meta[name=viewport]")
        .asScala
        .toList
        .map(_.attr("content"))
        .filter(c => scalingTurnedOff(c) || capsZoomBelow(c, MinimumZoom))
        .map(c =>
          Violation(
            "zoom-not-blocked",
            "the viewport stops the page being zoomed",
            actual = Some(c)
          )
            .withHint(
              "WCAG 1.4.4 — a reader who needs larger text cannot get it"
            )
        )
    },
    Rule(
      "label-for-resolves",
      "every label points at a control that exists",
      criterion = Some(
        Criterion("3.3.2", "Labels or Instructions", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      page.document
        .select("label[for]")
        .asScala
        .toList
        .filter(e => page.byId(e.attr("for")).isEmpty)
        .map(e =>
          Violation(
            "label-for-resolves",
            s"""a label points at "${e.attr(
                "for"
              )}", but no control has that id""",
            actual = Some(Text.preview(e.text(), 60))
          ).withHint(
            "clicking the label does nothing, and the control is announced without a name"
          )
        )
    },
    Rule(
      "single-main",
      "a page has one main landmark",
      pageLevel = true,
      criterion = Some(
        Criterion("1.3.1", "Info and Relationships", Level.A, WcagVersion.V2_0)
      )
    ) { page =>
      val mains = page.document.select(MainLandmark).asScala.toList
      if (mains.size <= 1) Nil
      else
        Seq(
          Violation(
            "single-main",
            s"the page has ${mains.size} main landmarks",
            expected = Some("1")
          )
            .withHint("skip links and landmark navigation become ambiguous")
        )
    },
    Rule(
      "image-alt",
      "every image has an alt attribute",
      criterion =
        Some(Criterion("1.1.1", "Non-text Content", Level.A, WcagVersion.V2_0))
    ) { page =>
      page.images.elements
        .filterNot(_.hasAttr("alt"))
        .map(e =>
          Violation(
            "image-alt",
            "an <img> has no alt attribute",
            actual = Some(e.attr("src"))
          )
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
    Set(
      "click here",
      "here",
      "more",
      "read more",
      "link",
      "this page",
      "this",
      "click",
      "more information"
    )

  /** What both `main-landmark` and `single-main` count. GOV.UK's
    * `#main-content` is not in here: it identifies the content area, and an id
    * is not a landmark.
    */
  private val MainLandmark = "main, [role=main]"

  /** WCAG 1.4.4 asks for text at 200%, so a viewport that caps the scale below
    * 2 is the one that takes the zoom away.
    */
  private val MinimumZoom = 2.0

  /** What ends a property name or a value: the separators a browser uses, plus
    * the semicolon. A browser does not treat a semicolon as one, but nothing is
    * written that way in a viewport except by someone who meant it to separate,
    * and reading it as part of a value would swallow whatever came after.
    */
  private def isSeparator(c: Char): Boolean =
    c.isWhitespace || c == '=' || c == ',' || c == ';'

  /** The directives a viewport `content` declares, in the order it declares
    * them, read the way a browser reads them.
    *
    * This is a scan, not a search for well-formed pairs, because the two
    * disagree: a browser takes the property name up to the first separator and
    * then scans forward for the `=`, so `maximum-scale ignored=1` caps the page
    * at 1, where looking for pairs would find `ignored=1` and miss the cap.
    * Whitespace separates one directive from the next as a comma does, and ends
    * a value rather than joining what surrounds it, so `maximum-scale=1 0` is a
    * cap of 1 and not of 10.
    */
  private def viewportDirectives(content: String): Seq[(String, String)] = {
    val text = content.toLowerCase
    val directives = Seq.newBuilder[(String, String)]
    var at = 0

    def skipSeparators(): Unit =
      while (at < text.length && isSeparator(text(at))) at += 1

    def takeToken(): String = {
      val from = at
      while (at < text.length && !isSeparator(text(at))) at += 1
      text.substring(from, at)
    }

    // Only whitespace and a repeated `=` stand between a property and its
    // value. A comma or semicolon ends the directive instead, leaving the
    // value empty — skipping those too would take the next property name as
    // this one's value and lose the directive it belonged to.
    def skipToValue(): Unit =
      while (at < text.length && (text(at).isWhitespace || text(at) == '='))
        at += 1

    // Every turn of this loop moves `at` on by at least one character: there
    // is either a separator to skip or a token to take.
    while (at < text.length) {
      skipSeparators()
      val key = takeToken()
      while (
        at < text.length && text(at) != '=' && text(at) != ','
        && text(at) != ';'
      ) at += 1
      if (at < text.length && text(at) == '=') {
        at += 1
        skipToValue()
        directives += (key -> takeToken())
      }
    }

    directives.result()
  }

  /** Whether a viewport `content` turns scaling off outright.
    *
    * By the same translation a browser applies: `yes`, `device-width`,
    * `device-height` and a number at 1 or beyond in either direction all leave
    * scaling on; a number between -1 and 1, and any value it does not know —
    * including no value at all — turn it off.
    */
  private def scalingTurnedOff(content: String): Boolean =
    viewportDirectives(content)
      .collect { case ("user-scalable", value) => value }
      .lastOption
      .exists(scalingOff)

  private def scalingOff(raw: String): Boolean =
    NumericPrefix.findFirstIn(raw).flatMap(_.toDoubleOption) match {
      case Some(number) => number > -1 && number < 1
      case None         => !ScalableWords.contains(raw)
    }

  /** The words that leave scaling on. */
  private val ScalableWords = Set("yes", "device-width", "device-height")

  /** Whether a viewport `content` caps zoom below this factor.
    *
    * Read as a number rather than matched as text: `maximum-scale=10` contains
    * `maximum-scale=1` but allows ten times the size. Where the directive is
    * given more than once the last one is the one that applies — whatever it
    * says, including a value that lifts the cap the earlier one set.
    */
  private def capsZoomBelow(content: String, factor: Double): Boolean =
    viewportDirectives(content)
      .collect { case ("maximum-scale", value) => value }
      .lastOption
      .flatMap(scaleValue)
      .exists(_ < factor)

  /** The words the viewport algorithm translates to a scale. `no`, and any
    * other word it does not know, is not in here because it translates to 0 —
    * see [[scaleValue]].
    */
  private val KeywordScales =
    Map("yes" -> 1.0, "device-width" -> 10.0, "device-height" -> 10.0)

  /** The leading number of a value, which is what the algorithm reads: "a
    * prefix of property-value … converted to a number using strtod … the
    * remainder of the string is ignored", so `maximum-scale=10junk` caps at 10.
    * The grammar is strtod's, including the trailing point that makes `10.e-1`
    * a tenth of ten rather than ten.
    */
  private val NumericPrefix =
    "^[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:e[+-]?[0-9]+)?".r

  /** The cap a directive declares, where it declares one at all, by the five
    * translations a browser applies: a non-negative number is itself, a
    * negative number is auto and caps nothing, `yes` is 1, `device-width` and
    * `device-height` are 10, and `no` — along with anything else at all,
    * including nothing at all — is 0.
    *
    * So an unreadable value is restrictive rather than absent: Blink says "no
    * and unknown values are translated to 0.0", clamped to 0.1, which is a page
    * that will not zoom. The clamp is to 0.1–10 and is not modelled, because it
    * cannot carry a value across a threshold of 2.
    */
  private def scaleValue(raw: String): Option[Double] =
    NumericPrefix.findFirstIn(raw).flatMap(_.toDoubleOption) match {
      case Some(number) => Option.when(number >= 0)(number)
      case None         => Some(KeywordScales.getOrElse(raw, 0.0))
    }

  /** An element's tabindex, where it has one that parses. HTML allows the
    * surrounding whitespace that `toInt` does not.
    */
  private def tabIndexOf(e: Element): Option[Int] =
    scala.util.Try(e.attr("tabindex").trim.toInt).toOption

  /** An element the author has taken out of the tab order, which is how you
    * stop a hidden control being reachable without removing it.
    */
  private def removedFromTabOrder(e: Element): Boolean =
    tabIndexOf(e).exists(_ < 0)

  private def describeControl(e: Element): String = {
    val id =
      Option(e.id()).filter(_.nonEmpty).map(v => s""" id="$v"""").getOrElse("")
    val name = Option(e.attr("name"))
      .filter(_.nonEmpty)
      .map(v => s""" name="$v"""")
      .getOrElse("")
    s"$id$name"
  }

}
