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

package io.github.frikit.twirlspec.expect

import org.jsoup.nodes.Element
import io.github.frikit.twirlspec.expect.Matching._
import io.github.frikit.twirlspec.page.{Page, Text}

import scala.jdk.CollectionConverters._

/** Expectations about form controls and the error states they can be in. */
trait FormExpectations {

  def textInput(name: String): TextInputExpectation = TextInputExpectation(name)
  def textArea(name: String): TextInputExpectation  = TextInputExpectation(name, tag = "textarea")
  def dropdown(name: String): DropdownExpectation   = DropdownExpectation(name)

  def radioGroup(name: String = "value"): ChoiceGroupExpectation =
    ChoiceGroupExpectation(name, kind = "radio")

  def checkboxGroup(name: String = "value"): ChoiceGroupExpectation =
    ChoiceGroupExpectation(name, kind = "checkbox")

  def dateInput(name: String = "value"): DateInputExpectation = DateInputExpectation(name)

  def fileUpload(name: String): Expectation = Expectation(s"fileUpload($name)") { page =>
    present(s"fileUpload($name)", page.fileUpload(name))
  }

  def hiddenInput(name: String, value: String): Expectation =
    Expectation(s"hiddenInput($name)") { page =>
      val sel = page.css(s"""input[type=hidden][name="$name"]""")
      if (sel.isEmpty) Seq(Violation.missing(s"hiddenInput($name)", sel.selector))
      else {
        val actual = sel.attr("value").getOrElse("")
        if (actual == value) Nil else Seq(Violation.mismatch(s"hiddenInput($name)", value, actual))
      }
    }

  def submitButton(key: String = "site.continue"): Expectation = buttonWith("submitButton", Expected.Key(key), None)
  def submitButtonText(literal: String): Expectation           = buttonWith("submitButton", Expected.Literal(literal), None)

  val hasSubmitButton: Expectation = Expectation("submitButton") { page =>
    present("submitButton", page.submitButton)
  }

  def button(id: String, key: String): Expectation = buttonWith(s"button($id)", Expected.Key(key), Some(id))

  private def buttonWith(rule: String, expected: Expected, id: Option[String]): Expectation =
    Expectation(rule) { page =>
      val sel = id.fold(page.submitButton)(page.byId)
      present(rule, sel) match {
        case Nil  => compare(rule, expected, sel.text, page, Exact)
        case errs => errs
      }
    }

  /** The form posts (or gets) to a specific URL. */
  def formPostsTo(url: String): Expectation  = formTo("POST", url)
  def formGetsFrom(url: String): Expectation = formTo("GET", url)

  private def formTo(method: String, url: String): Expectation = Expectation(s"form $method $url") { page =>
    if (page.forms.isEmpty) Seq(Violation.missing("form", "form"))
    else {
      val actualUrl    = page.formAction.getOrElse("")
      val actualMethod = page.formMethod.getOrElse("GET")
      val urlIssue     =
        if (actualUrl == url) Nil else Seq(Violation.mismatch("form action", url, actualUrl))
      val methodIssue  =
        if (actualMethod.equalsIgnoreCase(method)) Nil
        else Seq(Violation.mismatch("form method", method, actualMethod))
      urlIssue ++ methodIssue
    }
  }

  // ------------------------------------------------------------------ errors

  /** No error summary and no inline error messages anywhere on the page. */
  val noErrors: Expectation = Expectation("noErrors") { page =>
    absent("noErrors (summary)", page.errorSummary) ++
      absent("noErrors (inline)", page.errorMessages) ++
      (if (errorTitlePrefixes(page).exists(p => page.title.startsWith(p)))
         Seq(
           Violation(
             "noErrors (title)",
             "browser title carries the error prefix but the page has no errors",
             actual = Some(page.title)
           )
         )
       else Nil)
  }

  /** GOV.UK requires the browser title of a page in an error state to be
    * prefixed, so screen reader users hear that something went wrong before
    * the page name.
    */
  val errorTitlePrefix: Expectation = Expectation("errorTitlePrefix") { page =>
    val prefixes = errorTitlePrefixes(page)
    if (prefixes.exists(p => page.title.startsWith(p))) Nil
    else
      Seq(
        Violation(
          rule = "errorTitlePrefix",
          message = "browser title is not prefixed for an error state",
          expected = Some(s"${prefixes.head} ..."),
          actual = Some(page.title)
        ).withHint("""titles on an error state read: @messages("error.browser.title.prefix") @messages("x.title")""")
      )
  }

  def errorSummaryTitle(key: String = "error.summary.title"): Expectation =
    Expectation("errorSummaryTitle") { page =>
      present("errorSummaryTitle", page.errorSummaryTitle) match {
        case Nil  => compare("errorSummaryTitle", Expected.Key(key), page.errorSummaryTitle.text, page, Exact)
        case errs => errs
      }
    }

  /** The error summary lists exactly these `field -> message key` entries, in
    * order, and every entry links to an element that exists on the page.
    */
  def errorSummary(entries: (String, String)*): Expectation =
    ErrorSummaryExpectation(entries.toList.map { case (f, k) => (f, Expected.Key(k): Expected) })

  def errorSummaryContaining(field: String, key: String, args: Any*): Expectation =
    ErrorSummaryExpectation(List((field, Expected.Key(key, args.toSeq))), exhaustive = false)

  /** The inline error message rendered against a specific field. */
  def fieldError(field: String, key: String, args: Any*): Expectation =
    Expectation(s"fieldError($field)") { page =>
      Expected.Key(key, args.toSeq).resolve(page) match {
        case Left(v)      => Seq(v.copy(rule = s"fieldError($field)"))
        case Right(value) =>
          page.fieldErrors.get(field) match {
            case None        =>
              Seq(
                Violation(
                  rule = s"fieldError($field)",
                  message = "no inline error message was rendered for this field",
                  expected = Some(value),
                  actual = Some(
                    if (page.fieldErrors.isEmpty) "(no inline errors on the page)"
                    else s"errors on: ${page.fieldErrors.keys.toList.sorted.mkString(", ")}"
                  )
                ).withHint(s"""the govuk error message element should have id="$field-error"""")
              )
            case Some(found) =>
              if (Text.same(found, value)) Nil
              else Seq(Violation.mismatch(s"fieldError($field)", value, found))
          }
      }
    }

}

// ---------------------------------------------------------------------------

/** A text input (or textarea), its label, hint, value and autocomplete. */
final case class TextInputExpectation(
  name: String,
  tag: String = "input",
  label: Option[Expected] = None,
  hint: Option[Expected] = None,
  value: Option[String] = None,
  autocomplete: Option[String] = None,
  inputType: Option[String] = None,
  labelRequired: Boolean = true
) extends Expectation {

  def labelled(key: String, args: Any*): TextInputExpectation = copy(label = Some(Expected.Key(key, args.toSeq)))
  def labelledText(literal: String): TextInputExpectation     = copy(label = Some(Expected.Literal(literal)))
  def hinted(key: String, args: Any*): TextInputExpectation   = copy(hint = Some(Expected.Key(key, args.toSeq)))
  def hintedText(literal: String): TextInputExpectation       = copy(hint = Some(Expected.Literal(literal)))
  def withValue(v: String): TextInputExpectation              = copy(value = Some(v))
  def withAutocomplete(a: String): TextInputExpectation       = copy(autocomplete = Some(a))
  def ofType(t: String): TextInputExpectation                 = copy(inputType = Some(t))

  /** For the rare control that is legitimately labelled by a legend instead. */
  def labelledByLegend: TextInputExpectation = copy(labelRequired = false)

  private val rule = s"$tag($name)"

  def description: String = rule

  def check(page: Page): Seq[Violation] = {
    val sel = if (tag == "textarea") page.textarea(name) else page.input(name)
    if (sel.isEmpty) Seq(Violation.missing(rule, sel.selector))
    else {
      val element = sel(0)
      val id      = if (element.id().nonEmpty) element.id() else name

      val labelIssues = FormChecks.labelIssues(page, rule, id, label, labelRequired)
      val hintIssues  = hint.toSeq.flatMap(FormChecks.hintIssues(page, rule, id, _))

      val valueIssues = value.toSeq.flatMap { expectedValue =>
        val actual = if (tag == "textarea") Text.normalise(element.text()) else element.attr("value")
        if (actual == expectedValue) Nil
        else Seq(Violation.mismatch(s"$rule value", expectedValue, actual, "field was not populated as expected"))
      }

      val autocompleteIssues = autocomplete.toSeq.flatMap { expectedAc =>
        val actual = element.attr("autocomplete")
        if (actual == expectedAc) Nil
        else
          Seq(
            Violation
              .mismatch(s"$rule autocomplete", expectedAc, if (actual.isEmpty) "(absent)" else actual)
              .withHint("WCAG 1.3.5 — identify input purpose")
          )
      }

      val typeIssues = inputType.toSeq.flatMap { expectedType =>
        val actual = element.attr("type")
        if (actual == expectedType) Nil else Seq(Violation.mismatch(s"$rule type", expectedType, actual))
      }

      labelIssues ++ hintIssues ++ valueIssues ++ autocompleteIssues ++ typeIssues
    }
  }

}

/** A radio or checkbox group: its legend, its options and what is selected. */
final case class ChoiceGroupExpectation(
  name: String,
  kind: String,
  options: Option[List[(String, Expected)]] = None,
  legend: Option[Expected] = None,
  hint: Option[Expected] = None,
  selected: Option[Set[String]] = None,
  exhaustive: Boolean = true
) extends Expectation {

  def withOptions(opts: (String, String)*): ChoiceGroupExpectation =
    copy(options = Some(opts.toList.map { case (v, k) => (v, Expected.Key(k): Expected) }))

  def withOptionValues(values: String*): ChoiceGroupExpectation =
    copy(options = Some(values.toList.map(v => (v, Expected.Anything: Expected))))

  def containingOption(value: String, key: String): ChoiceGroupExpectation =
    copy(options = Some(options.getOrElse(Nil) :+ ((value, Expected.Key(key): Expected))), exhaustive = false)

  def legendIs(key: String, args: Any*): ChoiceGroupExpectation = copy(legend = Some(Expected.Key(key, args.toSeq)))
  def hinted(key: String, args: Any*): ChoiceGroupExpectation   = copy(hint = Some(Expected.Key(key, args.toSeq)))
  def selectedIs(values: String*): ChoiceGroupExpectation       = copy(selected = Some(values.toSet))
  def nothingSelected: ChoiceGroupExpectation                   = copy(selected = Some(Set.empty))

  private val rule = s"${kind}Group($name)"

  def description: String = rule

  def check(page: Page): Seq[Violation] = {
    val sel = if (kind == "radio") page.radios(name) else page.checkboxes(name)
    if (sel.isEmpty) Seq(Violation.missing(rule, sel.selector))
    else {
      val elements     = sel.elements
      val actualValues = elements.map(_.attr("value"))

      val optionIssues = options.toSeq.flatMap { expectedOptions =>
        val expectedValues = expectedOptions.map(_._1)
        val missing        = expectedValues.filterNot(actualValues.contains)
        val unexpected     = if (exhaustive) actualValues.filterNot(expectedValues.contains) else Nil

        val valueIssues =
          (if (missing.nonEmpty)
             Seq(
               Violation.mismatch(
                 s"$rule options",
                 expectedValues.mkString(", "),
                 actualValues.mkString(", "),
                 s"missing option(s): ${missing.mkString(", ")}"
               )
             )
           else Nil) ++
            (if (unexpected.nonEmpty)
               Seq(
                 Violation.mismatch(
                   s"$rule options",
                   expectedValues.mkString(", "),
                   actualValues.mkString(", "),
                   s"unexpected option(s): ${unexpected.mkString(", ")}"
                 )
               )
             else Nil)

        val labelIssues = expectedOptions.flatMap { case (value, expectedLabel) =>
          elements.find(_.attr("value") == value).toSeq.flatMap { element =>
            val id = element.id()
            FormChecks.labelIssues(page, s"$rule[$value]", id, Some(expectedLabel), labelRequired = true)
          }
        }

        valueIssues ++ labelIssues
      }

      val legendIssues = legend.toSeq.flatMap(FormChecks.legendIssues(page, rule, elements.head, _))
      val hintIssues   = hint.toSeq.flatMap { h =>
        val fieldsetId = FormChecks.enclosingFieldsetHintId(elements.head).getOrElse(name)
        FormChecks.hintIssues(page, rule, fieldsetId, h, elements.take(1))
      }

      val selectedIssues = selected.toSeq.flatMap { expectedSelected =>
        val actualSelected = elements.filter(_.hasAttr("checked")).map(_.attr("value")).toSet
        if (actualSelected == expectedSelected) Nil
        else
          Seq(
            Violation.mismatch(
              s"$rule selected",
              if (expectedSelected.isEmpty) "(nothing selected)" else expectedSelected.toList.sorted.mkString(", "),
              if (actualSelected.isEmpty) "(nothing selected)" else actualSelected.toList.sorted.mkString(", ")
            )
          )
      }

      optionIssues ++ legendIssues ++ hintIssues ++ selectedIssues
    }
  }

}

/** A `<select>` and its options. */
final case class DropdownExpectation(
  name: String,
  label: Option[Expected] = None,
  optionValues: Option[List[String]] = None,
  optionCount: Option[Int] = None
) extends Expectation {

  def labelled(key: String, args: Any*): DropdownExpectation = copy(label = Some(Expected.Key(key, args.toSeq)))
  def withOptionValues(values: String*): DropdownExpectation = copy(optionValues = Some(values.toList))
  def withOptionCount(n: Int): DropdownExpectation           = copy(optionCount = Some(n))

  private val rule = s"dropdown($name)"

  def description: String = rule

  def check(page: Page): Seq[Violation] = {
    val sel = page.selectBox(name)
    if (sel.isEmpty) Seq(Violation.missing(rule, sel.selector))
    else {
      val element = sel(0)
      val id      = if (element.id().nonEmpty) element.id() else name
      val actual  = element.select("option").asScala.toList.map(_.attr("value"))

      val labelIssues = FormChecks.labelIssues(page, rule, id, label, labelRequired = true)
      val valueIssues = optionValues.toSeq.flatMap { expected =>
        if (expected.forall(actual.contains)) Nil
        else
          Seq(
            Violation.mismatch(
              s"$rule options",
              expected.mkString(", "),
              actual.mkString(", "),
              s"missing: ${expected.filterNot(actual.contains).mkString(", ")}"
            )
          )
      }
      val countIssues = optionCount.toSeq.flatMap { n =>
        if (actual.size == n) Nil
        else Seq(Violation.mismatch(s"$rule option count", n.toString, actual.size.toString))
      }
      labelIssues ++ valueIssues ++ countIssues
    }
  }

}

/** The GOV.UK date input: three fields under one legend. */
final case class DateInputExpectation(
  name: String,
  legend: Option[Expected] = None,
  hint: Option[Expected] = None,
  parts: List[String] = List("day", "month", "year")
) extends Expectation {

  def legendIs(key: String, args: Any*): DateInputExpectation = copy(legend = Some(Expected.Key(key, args.toSeq)))
  def hinted(key: String, args: Any*): DateInputExpectation   = copy(hint = Some(Expected.Key(key, args.toSeq)))
  def withParts(p: String*): DateInputExpectation             = copy(parts = p.toList)

  private val rule = s"dateInput($name)"

  def description: String = rule

  def check(page: Page): Seq[Violation] = {
    // Two conventions are both correct and both common. `govukDateInput` with
    // default items emits `dateOfBirth-day`; services that bind a Play form to
    // the component pass explicit items and get `value.day`. Accept either
    // rather than making every service that used the defaults write it out.
    val resolved: List[(String, Option[String])] =
      parts.map(part => part -> DateInputExpectation.partId(page, name, part))

    val missingParts = resolved.collect { case (part, None) => part }

    if (missingParts.nonEmpty)
      Seq(
        Violation(
          rule = rule,
          message = s"date input is missing field(s): ${missingParts.mkString(", ")}",
          expected = Some(missingParts.map(p => s"$name.$p or $name-$p").mkString(", ")),
          actual = Some(
            page.css(".govuk-date-input input").ids match {
              case Nil => "(no date input fields on the page)"
              case ids => s"ids present: ${ids.mkString(", ")}"
            }
          )
        ).withHint("govukDateInput emits `<id>-day`; explicit InputItems usually emit `<id>.day`")
      )
    else {
      val partIds      = resolved.collect { case (part, Some(id)) => (part, id) }
      val labelIssues  = partIds.flatMap { case (part, id) =>
        FormChecks.labelIssues(page, s"$rule[$part]", id, None, labelRequired = true)
      }
      val first        = page.css(io.github.frikit.twirlspec.page.Page.idSelector(partIds.head._2))(0)
      val legendIssues = legend.toSeq.flatMap(FormChecks.legendIssues(page, rule, first, _))
      val hintIssues   = hint.toSeq.flatMap(FormChecks.hintIssues(page, rule, name, _, List(first)))
      labelIssues ++ legendIssues ++ hintIssues
    }
  }

}

object DateInputExpectation {

  /** The id a date part actually rendered with, trying each convention. */
  private[twirlspec] def partId(page: Page, name: String, part: String): Option[String] =
    List(s"$name.$part", s"$name-$part", part)
      .find(candidate => page.css(io.github.frikit.twirlspec.page.Page.idSelector(candidate)).nonEmpty)

}

/** The error summary and the links inside it. */
final case class ErrorSummaryExpectation(
  entries: List[(String, Expected)],
  exhaustive: Boolean = true
) extends Expectation {

  def description: String = s"errorSummary(${entries.map(_._1).mkString(", ")})"

  def check(page: Page): Seq[Violation] =
    if (page.errorSummary.isEmpty)
      Seq(
        Violation
          .missing("errorSummary", page.errorSummary.selector)
          .withHint("a page rendered from a form with errors must show govukErrorSummary")
      )
    else {
      val actual = page.errorSummaryLinks

      val entryIssues = entries.flatMap { case (field, expected) =>
        expected.resolve(page) match {
          case Left(v)      => Seq(v.copy(rule = s"errorSummary($field)"))
          case Right(value) =>
            actual.find(_._1 == field) match {
              case None                                       =>
                Seq(
                  Violation(
                    rule = s"errorSummary($field)",
                    message = "the error summary has no entry linking to this field",
                    expected = Some(s"""<a href="#$field">$value</a>"""),
                    actual = Some(
                      if (actual.isEmpty) "(summary has no links)"
                      else actual.map { case (t, x) => s"#$t -> $x" }.mkString(" | ")
                    )
                  )
                )
              case Some((_, text)) if !Text.same(text, value) =>
                Seq(Violation.mismatch(s"errorSummary($field)", value, text))
              case _                                          => Nil
            }
        }
      }

      val unexpected =
        if (!exhaustive) Nil
        else {
          val expectedFields = entries.map(_._1).toSet
          actual.map(_._1).filterNot(expectedFields.contains) match {
            case Nil     => Nil
            case surplus =>
              Seq(
                Violation(
                  rule = "errorSummary",
                  message = s"the error summary has entries that were not expected: ${surplus.mkString(", ")}",
                  expected = Some(entries.map(_._1).mkString(", ")),
                  actual = Some(actual.map(_._1).mkString(", "))
                )
              )
          }
        }

      // Every summary link must land somewhere. A link to #firstName when the
      // input is id="value" leaves a keyboard user stranded.
      val danglingLinks =
        actual.filter { case (target, _) => target.nonEmpty && page.byId(target).isEmpty }.map { case (target, text) =>
          Violation(
            rule = "errorSummary link target",
            message = s"summary entry links to #$target but no element on the page has that id",
            expected = Some(s"an element with id `$target`"),
            actual = Some(text)
          ).withHint("WCAG 2.4.3 — the link must move focus to the field it describes")
        }

      entryIssues ++ unexpected ++ danglingLinks
    }

}

/** Label, hint and legend checks shared by the form expectations. */
private[twirlspec] object FormChecks {

  def labelIssues(
    page: Page,
    rule: String,
    fieldId: String,
    expected: Option[Expected],
    labelRequired: Boolean
  ): Seq[Violation] = {
    val label = page.labelFor(fieldId)

    if (label.isEmpty) {
      if (!labelRequired) Nil
      else
        Seq(
          Violation
            .missing(s"$rule label", s"""label[for="$fieldId"]""", "field has no associated label")
            .withHint("WCAG 3.3.2 — every input needs a label whose `for` matches the input id")
        )
    } else
      expected.toSeq.flatMap(e => Matching.compare(s"$rule label", e, label.text, page, Matching.Exact))
  }

  /** Check the hint's wording, and that something announces it with the field.
    *
    * Which element carries `aria-describedby` depends on the component. A text
    * input references its own hint; a radio group, a checkbox group and a date
    * input reference theirs from the enclosing `<fieldset>`, because the hint
    * describes the whole group rather than any one control. Accept either, and
    * anything else on the path between them.
    */
  def hintIssues(
    page: Page,
    rule: String,
    fieldId: String,
    expected: Expected,
    referrers: List[Element] = Nil
  ): Seq[Violation] = {
    val hint = page.hint(fieldId)
    if (hint.isEmpty)
      Seq(Violation.missing(s"$rule hint", s"""[id="$fieldId-hint"]""", "no hint was rendered for this field"))
    else {
      val textIssues = Matching.compare(s"$rule hint", expected, hint.text, page, Matching.Exact)
      val hintId     = hint.attr("id").getOrElse(s"$fieldId-hint")

      val candidates =
        if (referrers.nonEmpty) referrers
        else page.css(io.github.frikit.twirlspec.page.Page.idSelector(fieldId)).elements

      val describedByValues = candidates.flatMap { element =>
        val own      = element.attr("aria-describedby")
        val ancestor = Option(element.closest("fieldset[aria-describedby]")).map(_.attr("aria-describedby"))
        (own +: ancestor.toList).filter(_.nonEmpty)
      }

      val ariaIssues =
        if (describedByValues.exists(_.split("\\s+").contains(hintId))) Nil
        else
          Seq(
            Violation(
              rule = s"$rule hint",
              message = "the hint is not announced with the field",
              expected = Some(s"aria-describedby containing `$hintId`"),
              actual = Some(if (describedByValues.isEmpty) "(no aria-describedby)" else describedByValues.mkString(" "))
            ).withHint("WCAG 1.3.1 — govukInput and govukRadios wire this up when you pass `hint`")
          )
      textIssues ++ ariaIssues
    }
  }

  def legendIssues(page: Page, rule: String, member: Element, expected: Expected): Seq[Violation] =
    Option(member.closest("fieldset")) match {
      case None           =>
        Seq(
          Violation
            .missing(s"$rule legend", "fieldset", "the group is not wrapped in a fieldset")
            .withHint("WCAG 1.3.1 — radios and checkboxes belong in a fieldset with a legend")
        )
      case Some(fieldset) =>
        val legend = fieldset.select("legend").asScala.toList
        if (legend.isEmpty) Seq(Violation.missing(s"$rule legend", "fieldset > legend"))
        else Matching.compare(s"$rule legend", expected, legend.head.text(), page, Matching.Exact)
    }

  def enclosingFieldsetHintId(member: Element): Option[String] =
    Option(member.closest("fieldset"))
      .flatMap(fs => Option(fs.attr("aria-describedby")).filter(_.nonEmpty))
      .map(_.split("\\s+").head.stripSuffix("-hint"))

}
