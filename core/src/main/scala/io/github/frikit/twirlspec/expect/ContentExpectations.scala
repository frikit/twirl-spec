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

package io.github.frikit.twirlspec.expect

import io.github.frikit.twirlspec.expect.Matching._
import io.github.frikit.twirlspec.page.{Page, Roles, Selection, Text}

import scala.jdk.CollectionConverters._

/** Expectations about the words on the page and the components carrying them.
  */
trait ContentExpectations {

  /** The resolved message appears somewhere in the page's visible text. */
  def content(key: String, args: Any*): Expectation =
    contentIn("content", _.text, Expected.Key(key, args.toSeq))

  /** Somewhere in the page body, given as the exact words rather than a message
    * key.
    */
  def contentText(literal: String): Expectation =
    contentIn("content", _.text, Expected.Literal(literal))

  /** The resolved message appears nowhere in the page's visible text. */
  def noContent(key: String, args: Any*): Expectation =
    Expectation("noContent") { page =>
      Expected.Key(key, args.toSeq).resolve(page) match {
        case Left(v)      => Seq(v.copy(rule = "noContent"))
        case Right(value) =>
          if (!Text.containsText(page.text, value)) Nil
          else
            Seq(
              Violation(
                rule = "noContent",
                message = "text was rendered but should not have been",
                expected = Some(s"absent: $value")
              )
            )
      }
    }

  /** The message appears inside a paragraph. */
  def paragraph(key: String, args: Any*): Expectation =
    contentIn("paragraph", _.paragraphs.text, Expected.Key(key, args.toSeq))

  /** The text of a GOV.UK warning callout. */
  def warning(key: String, args: Any*): Expectation =
    componentText("warning", _.warningText, Expected.Key(key, args.toSeq))

  /** The text of an inset text block. */
  def insetText(key: String, args: Any*): Expectation =
    componentText("insetText", _.insetText, Expected.Key(key, args.toSeq))

  /** The text of a notification banner. */
  def notificationBanner(key: String, args: Any*): Expectation =
    componentText(
      "notificationBanner",
      _.notificationBanner,
      Expected.Key(key, args.toSeq)
    )

  /** The title of a confirmation panel. */
  def panelTitle(key: String, args: Any*): Expectation =
    componentText(
      "panelTitle",
      _.css(".govuk-panel__title"),
      Expected.Key(key, args.toSeq)
    )

  /** The body of a confirmation panel. */
  def panelBody(key: String, args: Any*): Expectation =
    componentText(
      "panelBody",
      _.css(".govuk-panel__body"),
      Expected.Key(key, args.toSeq)
    )

  /** The visible summary of a collapsed details block. */
  def detailsSummary(key: String, args: Any*): Expectation =
    componentText(
      "detailsSummary",
      _.css(".govuk-details__summary-text, summary"),
      Expected.Key(key, args.toSeq)
    )

  /** The bullet list contains exactly these items, in order. */
  def bullets(keys: String*): Expectation =
    listItems("bullets", ".govuk-list--bullet > li", keys.toList)

  /** The numbered list contains exactly these items, in order. */
  def numberedItems(keys: String*): Expectation =
    listItems("numberedItems", ".govuk-list--number > li", keys.toList)

  private def listItems(
      rule: String,
      selector: String,
      keys: List[String]
  ): Expectation =
    Expectation(rule) { page =>
      val resolved = keys.map(k => (k, Expected.Key(k).resolve(page)))
      val unresolved = resolved.collect { case (_, Left(v)) =>
        v.copy(rule = rule)
      }
      if (unresolved.nonEmpty) unresolved
      else {
        val expected = resolved.collect { case (_, Right(v)) => v }
        val actual = page.css(selector).texts
        if (actual == expected) Nil
        else
          Seq(
            Violation
              .mismatch(
                rule,
                expected.mkString(" | "),
                if (actual.isEmpty) "(no list items matched)"
                else actual.mkString(" | ")
              )
              .at(selector)
          )
      }
    }

  /** A link with the given text pointing at the given URL. */
  def link(key: String, args: Any*): LinkExpectation =
    LinkExpectation(Expected.Key(key, args.toSeq), None, None)

  /** A link, given as the exact words rather than a message key. */
  def linkText(literal: String): LinkExpectation =
    LinkExpectation(Expected.Literal(literal), None, None)

  /** A link found by id, whatever it says. */
  def linkWithId(id: String): LinkExpectation =
    LinkExpectation(Expected.Anything, None, Some(id))

  // -------------------------------------------------------------- check answers

  /** A row of a `govukSummaryList`, by its key. */
  def summaryRow(key: String, args: Any*): SummaryRowExpectation =
    SummaryRowExpectation(Expected.Key(key, args.toSeq))

  /** The summary list holds exactly these `key -> value` rows, in order. */
  def summaryList(rows: (String, String)*): Expectation =
    Expectation("summaryList") { page =>
      val resolved = rows.toList.map { case (k, v) =>
        (Expected.Key(k).resolve(page), v)
      }
      val unresolved = resolved.collect { case (Left(v), _) =>
        v.copy(rule = "summaryList")
      }
      if (unresolved.nonEmpty) unresolved
      else {
        val expected = resolved.collect { case (Right(k), v) =>
          (k, Text.normalise(v))
        }
        val actual = page.summaryRows.map { case (k, v, _) => (k, v) }
        if (actual == expected) Nil
        else
          Seq(
            Violation.mismatch(
              "summaryList",
              expected.map { case (k, v) => s"$k = $v" }.mkString(" | "),
              if (actual.isEmpty) "(no summary list rows)"
              else actual.map { case (k, v) => s"$k = $v" }.mkString(" | ")
            )
          )
      }
    }

  // ------------------------------------------------------------------- tables

  /** The table header cells, in order. */
  def tableHeaders(keys: String*): Expectation = Expectation("tableHeaders") {
    page =>
      val resolved = keys.toList.map(k => Expected.Key(k).resolve(page))
      val unresolved = resolved.collect { case Left(v) =>
        v.copy(rule = "tableHeaders")
      }
      if (unresolved.nonEmpty) unresolved
      else {
        val expected = resolved.collect { case Right(v) => v }
        val actual = page.css("table th").texts
        if (actual == expected) Nil
        else
          Seq(
            Violation.mismatch(
              "tableHeaders",
              expected.mkString(" | "),
              if (actual.isEmpty) "(no table headers)"
              else actual.mkString(" | ")
            )
          )
      }
  }

  /** A row whose cells read exactly like this. */
  def tableRow(cells: String*): Expectation = Expectation("tableRow") { page =>
    val expected = cells.toList.map(Text.normalise)
    val rows = page.css("table tbody tr").elements.map { tr =>
      tr.select("td, th").asScala.toList.map(c => Text.normalise(c.text()))
    }
    if (rows.contains(expected)) Nil
    else
      Seq(
        Violation.mismatch(
          "tableRow",
          expected.mkString(" | "),
          if (rows.isEmpty) "(no table rows)"
          else rows.map(_.mkString(" | ")).mkString("\n                ")
        )
      )
  }

  // --------------------------------------------------------------- structural

  /** One selector's first match comes before another's in document order.
    *
    * Reading order is not a detail: an error summary announced after the form
    * it describes is announced too late to be useful.
    */
  def appearsBefore(first: String, second: String): Expectation =
    Expectation(s"$first before $second") { page =>
      val rule = s"order($first before $second)"
      (page.css(first).headOption, page.css(second).headOption) match {
        case (None, _)          => Seq(Violation.missing(rule, first))
        case (_, None)          => Seq(Violation.missing(rule, second))
        case (Some(a), Some(b)) =>
          if (page.positionOf(a) < page.positionOf(b)) Nil
          else
            Seq(
              Violation(
                rule,
                s"`$first` comes after `$second` in the document",
                expected = Some(s"$first, then $second")
              )
            )
      }
    }

  /** An element with this id exists. */
  def element(id: String): Expectation =
    Expectation(s"element($id)")(p =>
      Matching.exactlyOne(s"element($id)", p.byId(id)).left.toSeq
    )

  /** No element with this id exists. */
  def noElement(id: String): Expectation =
    Expectation(s"noElement($id)")(p => absent(s"noElement($id)", p.byId(id)))

  /** The element with this id says this. */
  def elementWithText(id: String, key: String, args: Any*): Expectation =
    Expectation(s"element($id)") { page =>
      Matching.exactlyOne(s"element($id)", page.byId(id)) match {
        case Left(v)  => Seq(v)
        case Right(e) =>
          compare(
            s"element($id)",
            Expected.Key(key, args.toSeq),
            e.text(),
            page,
            Exact
          )
      }
    }

  /** At least one element matches this selector. The plural form, where several
    * matches are legitimate.
    */
  def cssSelector(selector: String): Expectation =
    Expectation(s"css($selector)")(p =>
      present(s"css($selector)", p.css(selector))
    )

  /** Nothing matches this selector. */
  def noCssSelector(selector: String): Expectation =
    Expectation(s"noCss($selector)")(p =>
      absent(s"noCss($selector)", p.css(selector))
    )

  /** Exactly this many elements match. */
  def elementCount(selector: String, expected: Int): Expectation =
    Expectation(s"count($selector)") { page =>
      val actual = page.css(selector).size
      if (actual == expected) Nil
      else
        Seq(
          Violation
            .mismatch(s"count($selector)", expected.toString, actual.toString)
        )
    }

  /** The element with this id carries this class. */
  def elementHasClass(id: String, className: String): Expectation =
    Expectation(s"class($id)") { page =>
      Matching.exactlyOne(s"class($id)", page.byId(id)) match {
        case Left(v)                           => Seq(v)
        case Right(e) if e.hasClass(className) => Nil
        case Right(e)                          =>
          import scala.jdk.CollectionConverters._
          Seq(
            Violation.mismatch(
              s"class($id)",
              className,
              e.classNames().asScala.toList.sorted.mkString(" "),
              "class not present"
            )
          )
      }
    }

  // ----------------------------------------------------------------- internal

  private def contentIn(
      rule: String,
      extract: Page => String,
      expected: Expected
  ): Expectation =
    Expectation(rule) { page =>
      expected.resolve(page) match {
        case Left(v)      => Seq(v.copy(rule = rule))
        case Right(value) =>
          if (Text.containsText(extract(page), value)) Nil
          else
            Seq(
              Violation(
                rule = rule,
                message = "text was not found on the page",
                expected = Some(value),
                actual = Some(Text.preview(extract(page), 220))
              )
            )
      }
    }

  private def componentText(
      rule: String,
      select: Page => Selection,
      expected: Expected
  ): Expectation =
    Expectation(rule) { page =>
      val sel = select(page)
      present(rule, sel) match {
        case Nil  => compare(rule, expected, sel.text, page, Contains)
        case errs => errs
      }
    }

}

/** A link, by text and/or id, optionally pinned to a URL. */
final case class LinkExpectation(
    text: Expected,
    href: Option[String],
    id: Option[String]
) extends Expectation {

  def to(url: String): LinkExpectation = copy(href = Some(url))
  def withId(elementId: String): LinkExpectation = copy(id = Some(elementId))

  def saying(key: String, args: Any*): LinkExpectation =
    copy(text = Expected.Key(key, args.toSeq))

  def description: String = s"link(${id.map("#" + _).getOrElse(text.describe)})"

  def check(page: Page): Seq[Violation] = {
    val rule = description

    val candidates = id match {
      case Some(elementId) => page.byId(elementId)
      case None            =>
        text.resolve(page) match {
          case Left(_)      => Selection.empty(rule, "a[href]")
          case Right(value) => page.links.containing(value)
        }
    }

    text.resolve(page) match {
      case Left(v)             => Seq(v.copy(rule = rule))
      case Right(expectedText) =>
        if (candidates.isEmpty)
          Seq(
            Violation(
              rule = rule,
              message = "no link matched",
              expected = Some(
                if (expectedText.nonEmpty) expectedText
                else id.map("#" + _).getOrElse("")
              ),
              actual = Some(
                if (page.links.isEmpty) "(no links on the page)"
                else
                  page.links.texts.filter(_.nonEmpty).take(12).mkString(" | ")
              )
            )
          )
        else {
          val textIssues =
            if (id.isEmpty || expectedText.isEmpty) Nil
            else
              Matching.compare(
                rule,
                text,
                candidates.text,
                page,
                Matching.Exact
              )

          val hrefIssues = href.toSeq.flatMap { url =>
            val actual = candidates.attrs("href")
            if (actual.contains(url)) Nil
            else
              Seq(Violation.mismatch(s"$rule href", url, actual.mkString(", ")))
          }

          textIssues ++ hrefIssues
        }
    }
  }

}

/** One row of a check-your-answers summary list. */
final case class SummaryRowExpectation(
    key: Expected,
    value: Option[String] = None,
    changeHref: Option[String] = None,
    actionTexts: Option[List[Expected]] = None
) extends Expectation {

  def withValue(v: String): SummaryRowExpectation = copy(value = Some(v))

  def withChangeLinkTo(url: String): SummaryRowExpectation =
    copy(changeHref = Some(url))

  def withActions(keys: String*): SummaryRowExpectation =
    copy(actionTexts = Some(keys.toList.map(k => Expected.Key(k): Expected)))

  def description: String = s"summaryRow(${key.describe})"

  def check(page: Page): Seq[Violation] = {
    val rule = description
    key.resolve(page) match {
      case Left(v)         => Seq(v.copy(rule = rule))
      case Right(keyValue) =>
        // Locate the row once and read everything off it.
        val rows = page.document
          .select(".govuk-summary-list__row")
          .asScala
          .toList
          .filter(r =>
            Text.same(r.select(".govuk-summary-list__key").text(), keyValue)
          )

        rows match {
          case _ :: _ :: _ =>
            Seq(
              Violation(
                rule = rule,
                message =
                  s"${rows.size} summary list rows have this key, so this assertion is ambiguous",
                expected = Some("exactly one row"),
                actual = Some(keyValue)
              )
            )
          case Nil =>
            Seq(
              Violation(
                rule = rule,
                message = "no summary list row had this key",
                expected = Some(keyValue),
                actual = Some(
                  if (page.summaryRows.isEmpty) "(no summary list on the page)"
                  else page.summaryRows.map(_._1).mkString(" | ")
                )
              )
            )
          case r :: Nil =>
            val actualValue =
              Text.normalise(r.select(".govuk-summary-list__value").text())
            val actions =
              r.select(".govuk-summary-list__actions a").asScala.toList
            val actionText = actions.map(a => Text.normalise(a.text()))
            val actionHrefs = actions.map(_.attr("href"))

            val valueIssues = value.toSeq.flatMap { expected =>
              if (Text.same(actualValue, expected)) Nil
              else
                Seq(Violation.mismatch(s"$rule value", expected, actualValue))
            }

            val actionIssues = actionTexts.toSeq.flatMap { expectedActions =>
              val resolved = expectedActions.map(_.resolve(page))
              resolved.collect { case Left(v) => v.copy(rule = rule) } match {
                case Nil =>
                  val want = resolved.collect { case Right(v) => v }
                  if (
                    want.forall(w =>
                      actionText.exists(a => Text.containsText(a, w))
                    )
                  ) Nil
                  else
                    Seq(
                      Violation.mismatch(
                        s"$rule actions",
                        want.mkString(", "),
                        actionText.mkString(", ")
                      )
                    )
                case es => es
              }
            }

            val hrefIssues = changeHref.toSeq.flatMap { url =>
              if (actionHrefs.contains(url)) Nil
              else
                Seq(
                  Violation.mismatch(
                    s"$rule change link",
                    url,
                    actionHrefs.mkString(", ")
                  )
                )
            }

            valueIssues ++ actionIssues ++ hrefIssues
        }
    }
  }

}

/** An element located by ARIA role and accessible name. */
final case class RoleExpectation(
    roleName: String,
    name: Option[Expected],
    count: Option[Int]
) extends Expectation {

  /** The name an assistive technology announces, from a message key. */
  def named(key: String, args: Any*): RoleExpectation =
    copy(name = Some(Expected.Key(key, args.toSeq)))

  /** The accessible name, given as the exact words rather than a message key.
    */
  def namedText(literal: String): RoleExpectation =
    copy(name = Some(Expected.Literal(literal)))

  /** The accessible name matches this pattern. */
  def namedMatching(regex: scala.util.matching.Regex): RoleExpectation =
    copy(name = Some(Expected.Pattern(regex)))

  /** Exactly this many elements carry the role. */
  def occurring(times: Int): RoleExpectation = copy(count = Some(times))

  def description: String =
    name.fold(s"role($roleName)")(n => s"role($roleName, ${n.describe})")

  def check(page: Page): Seq[Violation] = {
    val rule = description
    val matches = page.byRole(roleName)

    val countIssues = count.toSeq.flatMap { expected =>
      if (matches.size == expected) Nil
      else
        Seq(
          Violation
            .mismatch(s"$rule count", expected.toString, matches.size.toString)
        )
    }

    val nameIssues = name.toSeq.flatMap { expectedName =>
      expectedName.resolve(page) match {
        case Left(v)      => Seq(v.copy(rule = rule))
        case Right(value) =>
          val named = expectedName match {
            case Expected.Pattern(regex) =>
              matches.elements.filter(e =>
                regex.findFirstIn(page.accessibleName(e)).isDefined
              )
            case _ => page.byRole(roleName, value).elements
          }
          if (named.nonEmpty) Nil
          else
            Seq(
              Violation(
                rule = rule,
                message =
                  s"no element with role `$roleName` announces this name",
                expected = Some(value),
                actual = Some(
                  if (matches.isEmpty) s"(no element has role `$roleName`)"
                  else
                    matches.elements
                      .map(page.accessibleName)
                      .filter(_.nonEmpty)
                      .mkString(" | ")
                )
              ).withHint(
                "an element a screen reader cannot name is one it cannot describe"
              )
            )
      }
    }

    val presence =
      if (matches.isEmpty && count.isEmpty && name.isEmpty)
        Seq(Violation.missing(rule, Roles.selectorFor(roleName)))
      else Nil

    presence ++ countIssues ++ nameIssues
  }

}
