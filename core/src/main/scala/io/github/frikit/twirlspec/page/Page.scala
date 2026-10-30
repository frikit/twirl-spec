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

package io.github.frikit.twirlspec.page

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.nodes.{Document, Element}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.twirl.api.Html

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** A rendered page, ready to be asked questions.
  *
  * The accessors know GOV.UK and HMRC markup, so a page from another design
  * system is still readable through `css`, `byId`, `byRole` and `doc`.
  *
  * @param document
  *   the parsed markup, repaired where the parser had to
  * @param lang
  *   the language the page was rendered in
  * @param messages
  *   the messages for that language, used to resolve message keys
  * @param source
  *   the markup as rendered, before any repair
  * @param parseErrors
  *   what the parser had to repair, up to fifty entries
  */
final class Page(
    val document: Document,
    val lang: Lang,
    val messages: Messages,
    val source: String,
    val parseErrors: List[String] = Nil
) {

  // ---------------------------------------------------------------- selectors

  /** Escape hatch: raw Jsoup. */
  def doc: Document = document

  // ------------------------------------------------------------- coverage

  /** The spec that rendered this page, so one spec's assertions never count for
    * another's; empty for a page built directly.
    */
  private var owner: String = ""

  /** Which of the spec's pages this is: empty for its exact markup, so every
    * render of one view in one state shares a record and a different view, or
    * the same view in another state, does not; or the name a spec gave it.
    */
  private var group: String = ""

  /** The markup, digested. A digest rather than `hashCode`, whose collisions
    * are easy to come by, so two pages share a record only if their markup is
    * the same.
    */
  private lazy val markupKey: String = java.util.HexFormat
    .of()
    .formatHex(
      java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    )

  /** The record of a page built directly rather than rendered by a spec, which
    * belongs to no spec and so is kept with the page: the same page read in
    * another language shares it, and nothing else does.
    */
  private var ownRecord: mutable.Set[String] = mutable.Set.empty[String]

  private[twirlspec] def coverageGroup: String =
    s"$owner#${if (group.isEmpty) markupKey else group}"

  /** Every anchor the assertions in this page's record have touched. */
  private[twirlspec] def touchedAnchors: Set[String] =
    if (owner.isEmpty) ownRecord.synchronized(ownRecord.toSet)
    else CoverageRegistry.touched(coverageGroup)

  private[twirlspec] def belongingTo(spec: String): this.type = {
    if (spec.nonEmpty) owner = spec
    this
  }

  /** Count this page's assertions together with those on every other page this
    * spec covers under the same name — the states of one view, say, so that
    * what was asserted on the page with errors counts for the page without:
    *
    * {{{
    * render(view(form)).coveredAs("name-page")
    * render(view(formWithErrors)).coveredAs("name-page")
    * }}}
    *
    * Without it, a page is grouped with the renders that produce exactly its
    * markup. Only a page a spec rendered can share a record; one built directly
    * keeps its own.
    */
  def coveredAs(name: String): this.type = {
    require(name.nonEmpty, "a coverage group needs a name")
    require(
      owner.nonEmpty,
      "only a page a spec rendered can share a coverage record"
    )
    group = s"named:$name"
    this
  }

  private var recording: Boolean = true

  /** Standards rules sweep the whole document, so what they touch says nothing
    * about what the spec asserted.
    */
  private[twirlspec] def withRecordingPaused[A](f: => A): A = {
    val was = recording
    recording = false
    try f
    finally recording = was
  }

  private[twirlspec] def record(elements: List[Element]): Unit =
    if (recording && elements.nonEmpty) {
      val names = elements.flatMap(Anchors.touchedBy)
      if (owner.isEmpty) ownRecord.synchronized(ownRecord ++= names)
      else CoverageRegistry.record(coverageGroup, names)
    }

  /** Every element matching this CSS selector. */
  def css(selector: String): Selection = named(selector, selector)

  /** Every element matching this selector, under a name used in failure
    * messages.
    */
  def named(name: String, selector: String): Selection = {
    val elements = document.select(selector).asScala.toList
    record(elements)
    Selection(name, selector, elements)
  }

  /** Elements with this ARIA role, implicit or explicit. */
  def byRole(role: String): Selection = {
    val elements = Roles.matching(document, role)
    record(elements)
    Selection(s"role=$role", Roles.selectorFor(role), elements)
  }

  /** Elements with this role whose accessible name matches. */
  def byRole(role: String, name: String): Selection =
    Selection(
      s"""role=$role name="$name"""",
      Roles.selectorFor(role),
      Roles
        .matching(document, role)
        .filter(e => Text.same(AccessibleName.of(document, e), name))
    )

  /** Position in document order, for asserting one thing comes before another.
    */
  def positionOf(e: org.jsoup.nodes.Element): Int =
    document.getAllElements.indexOf(e)

  /** Disabled as a browser sees it: on the control, or inherited from a
    * disabled fieldset — except inside that fieldset's first legend, which HTML
    * leaves enabled so a legend can hold the control that switches the rest on.
    */
  def isDisabled(e: org.jsoup.nodes.Element): Boolean =
    e.hasAttr("disabled") || e.parents.asScala.exists(ancestor =>
      ancestor.tagName() == "fieldset" && ancestor.hasAttr("disabled") &&
        !ancestor.children.asScala
          .find(_.tagName() == "legend")
          .exists(legend => e.parents.contains(legend))
    )

  /** The description an assistive technology reads after the name. */
  def accessibleDescription(e: org.jsoup.nodes.Element): String =
    Text.normalise(describedBy(e).map(_.text()).mkString(" "))

  /** Whether assistive technology is told about this element, as far as the
    * markup says: neither it nor anything above it is hidden. It knows nothing
    * of stylesheets, so it answers for `hidden` and an inline style, not for a
    * class that hides.
    */
  private[twirlspec] def isExposed(e: Element): Boolean = {
    val lineage = e :: e.parents.asScala.toList
    e.closest(Page.NotExposed) == null &&
    !lineage.exists(Page.inlineStyle(_, "display").contains("none")) &&
    // An element can show itself inside something hidden, so the nearest that
    // says anything of its own decides.
    !lineage
      .flatMap(Page.inlineStyle(_, "visibility"))
      .find(value => value != "inherit" && value != "unset")
      .contains("hidden")
  }

  /** The elements that say what `aria-describedby` points at. */
  private[twirlspec] def describedBy(e: Element): List[Element] =
    e.attr("aria-describedby")
      .split("\\s+")
      .filter(_.nonEmpty)
      .toList
      .flatMap(id => Option(document.getElementById(id)))

  /** What the page's forms would submit as their markup stands — before any
    * script runs or anyone types — as name and value pairs in document order.
    *
    * It is a static reading of the markup, not a submission: every form on the
    * page is read together, and no button is the submitter, so no button's name
    * is included. Within that, it follows the browser. A checkbox counts only
    * when it is checked, and says `on` when it has no value; of several radios
    * marked checked in one group, only the last is, as checking one unchecks
    * the rest. A select sends its selected options, and a single select with
    * none marked sends the first option that is not disabled, which is what a
    * browser shows as chosen. A textarea sends its text as written, and a file
    * input an empty file name, since markup cannot choose a file. A disabled
    * control sends nothing, and neither do buttons, reset controls or image
    * inputs. A name can appear more than once, as a group of checkboxes does.
    */
  def formSubmission: Seq[(String, String)] =
    submittedControls.map { case (_, name, value) => (name, value) }

  /** [[formSubmission]], with the control each name and value comes from. */
  private[twirlspec] def submittedControls: List[(Element, String, String)] = {
    // Disabled radios still take part in their group, so the survivor is
    // found before disabled controls are left out.
    val keptRadios = document
      .select("input[type=radio][checked]")
      .asScala
      .toList
      .groupBy(r => (formOwner(r), r.attr("name")))
      .values
      .map(_.last)
      .toSet
    document
      .select("input, select, textarea")
      .asScala
      .toList
      .filter(e => e.attr("name").nonEmpty && !isDisabled(e))
      .flatMap { e =>
        val values = e.tagName() match {
          case "textarea" => List(e.wholeText())
          case "select"   => selectedOptions(e).map(optionValue)
          case _          =>
            e.attr("type").toLowerCase match {
              case "checkbox" =>
                if (e.hasAttr("checked")) List(checkedValue(e)) else Nil
              case "radio" =>
                if (keptRadios.contains(e)) List(checkedValue(e)) else Nil
              case "file"                                  => List("")
              case "submit" | "button" | "reset" | "image" => Nil
              case _ => List(e.attr("value"))
            }
        }
        values.map(value => (e, e.attr("name"), value))
      }
  }

  /** The form a control belongs to. A `form` attribute names it by id, and then
    * it is that form or, where the id names no form, none at all; without one
    * it is the form the control sits in.
    */
  private[twirlspec] def formOwner(
      e: org.jsoup.nodes.Element
  ): Option[org.jsoup.nodes.Element] =
    if (e.hasAttr("form"))
      Option(document.getElementById(e.attr("form")))
        .filter(_.tagName() == "form")
    else Option(e.closest("form"))

  /** What a checked box or radio submits: its value, or `on` without one. */
  private[twirlspec] def checkedValue(e: org.jsoup.nodes.Element): String =
    if (e.hasAttr("value")) e.attr("value") else "on"

  /** The value each name submits: [[formSubmission]] as a map, keeping the last
    * value for a name that is submitted more than once.
    */
  def formValues: Map[String, String] = formSubmission.toMap

  /** The options a select submits: the marked ones for a multiple select; for a
    * single select the last marked one, or else the first a browser would pick.
    * A disabled option, or one in a disabled group, is never submitted.
    */
  private def selectedOptions(select: org.jsoup.nodes.Element) = {
    val options = select.select("option").asScala.toList
    val usable = options.filterNot(o =>
      o.hasAttr("disabled") || Option(o.closest("optgroup[disabled]")).isDefined
    )
    val marked = options.filter(_.hasAttr("selected"))
    val displaySize = select.attr("size").trim.toIntOption.filter(_ > 0)
    if (select.hasAttr("multiple")) marked.filter(usable.contains)
    else if (marked.nonEmpty) marked.lastOption.filter(usable.contains).toList
    else if (displaySize.forall(_ == 1)) usable.headOption.toList
    else Nil
  }

  /** An option's value, or its text where it has none. */
  private def optionValue(o: org.jsoup.nodes.Element): String =
    if (o.hasAttr("value")) o.attr("value") else o.text()

  /** The name an assistive technology would announce for an element. */
  def accessibleName(e: org.jsoup.nodes.Element): String =
    AccessibleName.of(document, e)

  /** The element with this id, matched by attribute rather than CSS syntax, so
    * an id with a dot in it works.
    */
  def byId(elementId: String): Selection =
    exactly(s"#$elementId", Page.idSelector(elementId), "[id]")(e =>
      says(e.id(), elementId)
    )

  /** The elements among these whose attributes pass an exact test. Jsoup's
    * `[a="x"]` ignores case, where an id and a name do not, and throws on a
    * value with a quote in it, so values are compared rather than written into
    * the selector, and `shown` is what a failure message calls it.
    */
  private def exactly(name: String, shown: String, among: String)(
      test: Element => Boolean
  ): Selection = {
    val elements = document.select(among).asScala.toList.filter(test)
    record(elements)
    Selection(name, shown, elements)
  }

  private def isCalled(e: Element, nameOrId: String): Boolean =
    says(e.attr("name"), nameOrId) || says(e.id(), nameOrId)

  /** An attribute that is absent reads as empty, so nothing is called "". */
  private def says(attribute: String, wanted: String): Boolean =
    wanted.nonEmpty && attribute == wanted

  // ------------------------------------------------------------- page framing

  /** The browser title, normalised. */
  def title: String = Text.normalise(document.title())

  /** The `lang` attribute of the `<html>` element, if it has one. */
  def htmlLang: Option[String] =
    Option(document.selectFirst("html")).map(_.attr("lang")).filter(_.nonEmpty)

  /** Whether a `lang` value names this page's language: in any region and any
    * case, as a language tag is read.
    */
  private[twirlspec] def declaresItsLanguage(declared: String): Boolean =
    declared.toLowerCase.takeWhile(_ != '-') == lang.language.toLowerCase

  /** The `<h1>` elements; a page should have exactly one. */
  def h1: Selection = named("h1", "h1")

  /** Every heading with its level, in document order. */
  def headings: List[(Int, String)] =
    document
      .select("h1, h2, h3, h4, h5, h6")
      .asScala
      .toList
      .map(e => (e.tagName().substring(1).toInt, Text.normalise(e.text())))

  /** The GOV.UK caption above a heading, in any of its sizes. */
  def caption: Selection =
    named(
      "caption",
      ".govuk-caption-xl, .govuk-caption-l, .govuk-caption-m, .govuk-caption-s, .hmrc-caption"
    )

  /** The service name in a GOV.UK or HMRC header. */
  def serviceName: Selection =
    named(
      "service name",
      ".govuk-service-navigation__service-name, .govuk-header__service-name, .hmrc-header__service-name"
    )

  /** The alpha or beta phase banner. */
  def phaseBanner: Selection = named("phase banner", ".govuk-phase-banner")

  /** A switcher offers another language, so a link declaring the one it is
    * already in does not count — a "report a technical issue" link carrying
    * `hreflang="en"` on an English page is not a toggle.
    */
  def languageToggle: Selection = {
    val selector =
      ".hmrc-language-select, nav[aria-label='Language switcher'], a[hreflang]"
    val elements = document.select(selector).asScala.toList.filter { e =>
      !e.hasAttr("hreflang") || !e
        .attr("hreflang")
        .toLowerCase
        .startsWith(lang.code.toLowerCase)
    }
    record(elements)
    Selection("language toggle", selector, elements)
  }

  /** `govukBackLink` renders `.govuk-back-link`; services that set their own id
    * use `back` or `back-link`.
    */
  def backLink: Selection =
    named("back link", ".govuk-back-link, #back-link, #back")

  /** The GOV.UK breadcrumbs. */
  def breadcrumbs: Selection = named("breadcrumbs", ".govuk-breadcrumbs")

  /** The main content landmark. */
  def main: Selection = named("main content", "main, #main-content")

  /** The HMRC session timeout dialog. */
  def timeoutDialog: Selection =
    named(
      "timeout dialog",
      "meta[name=hmrc-timeout-dialog], [data-module=hmrc-timeout-dialog]"
    )

  /** The sign out link of an HMRC header. */
  def signOutLink: Selection =
    named("sign out link", "#sign-out, .hmrc-sign-out-nav__link")

  // ------------------------------------------------------------------- errors

  /** The GOV.UK error summary. */
  def errorSummary: Selection = named("error summary", ".govuk-error-summary")

  /** The heading of the error summary. */
  def errorSummaryTitle: Selection =
    named("error summary title", ".govuk-error-summary__title")

  /** (href target without the leading '#', link text) for each error summary
    * entry.
    */
  def errorSummaryLinks: List[(String, String)] =
    errorSummaryLinkElements.map(e =>
      (e.attr("href").stripPrefix("#"), Text.normalise(e.text()))
    )

  private[twirlspec] def errorSummaryLinkElements: List[Element] =
    document
      .select(
        ".govuk-error-summary a[href], .govuk-error-summary__list a[href]"
      )
      .asScala
      .toList

  /** The error summary entries whose link lands on nothing: a link to
    * `#firstName` when the input is `id="value"` leaves a keyboard user
    * stranded.
    */
  def errorSummaryDanglingLinks: List[(String, String)] =
    errorSummaryLinks.filter { case (target, _) =>
      target.nonEmpty && byId(target).isEmpty
    }

  /** Every inline error message. */
  def errorMessages: Selection = named("error message", ".govuk-error-message")

  /** Inline error text keyed by the field it belongs to, with the visually
    * hidden "Error:" prefix stripped.
    */
  def fieldErrors: Map[String, String] =
    document
      .select(".govuk-error-message")
      .asScala
      .toList
      .map(e => e.id().stripSuffix("-error") -> errorWords(e))
      .toMap

  /** What an inline error says, with the visually hidden prefix taken off. */
  private[twirlspec] def errorWords(e: Element): String = {
    val hidden = e
      .select(".govuk-visually-hidden")
      .asScala
      .toList
      .map(_.text())
      .mkString
    Text.normalise(e.text().replace(hidden, ""))
  }

  /** A field's inline error: by the GOV.UK id convention, or else the one its
    * control or that control's fieldset announces, since a component may build
    * the error's id from something other than the field's name.
    */
  private[twirlspec] def errorOf(field: String): Option[Element] = {
    val controls = document.select("input, select, textarea").asScala.toList
    document
      .select(".govuk-error-message")
      .asScala
      .find(_.id() == s"$field-error")
      .orElse(announcedBy(controls.filter(isCalled(_, field))))
      .orElse(
        announcedBy(
          controls.filter(c =>
            isPartOf(c.attr("name"), field) || isPartOf(c.id(), field)
          )
        )
      )
  }

  /** The inline error these controls, or their fieldsets, announce. */
  private def announcedBy(controls: List[Element]): Option[Element] =
    controls
      .flatMap(c => c :: Option(c.closest("fieldset")).toList)
      .flatMap(describedBy)
      .find(_.hasClass("govuk-error-message"))

  /** The inline error an error summary link leads to: the one that the element
    * it lands on says it is described by, and only then a field's of that name.
    */
  private[twirlspec] def errorAt(target: String): Option[Element] =
    announcedBy(withRecordingPaused(byId(target)).elements)
      .orElse(errorOf(target))

  /** Whether a name or id is one of a date's parts, as `dob.day` is of `dob`.
    * It goes by the parts rather than the prefix, because `a-b` is usually a
    * field of its own.
    */
  private def isPartOf(value: String, field: String): Boolean =
    Seq("day", "month", "year").exists(part =>
      value == s"$field.$part" || value == s"$field-$part"
    )

  /** Whether an error summary link to this target lands on this field: on the
    * element with its id, or on one of its controls, which is where a GOV.UK
    * radio group's and date input's links go, to the first radio and the day.
    */
  private[twirlspec] def landsOn(target: String, field: String): Boolean =
    target == field ||
      withRecordingPaused(byId(target)).elements.exists { e =>
        e.attr("name") == field || isPartOf(e.attr("name"), field)
      }

  // -------------------------------------------------------------------- forms

  /** Every form on the page. */
  def forms: Selection = named("form", "form")

  /** The action of the first form, if there is one. */
  def formAction: Option[String] = forms.attr("action")

  /** The method of the first form, upper-cased, if there is one. */
  def formMethod: Option[String] = forms.attr("method").map(_.toUpperCase)

  /** The inputs with this name or id. */
  def input(nameOrId: String): Selection =
    exactly(s"input($nameOrId)", Page.control("input", nameOrId), "input")(
      isCalled(_, nameOrId)
    )

  /** Any control by name or id: input, select or textarea. */
  def formControl(nameOrId: String): Selection =
    exactly(
      s"control($nameOrId)",
      Seq("input", "select", "textarea")
        .map(Page.control(_, nameOrId))
        .mkString(", "),
      "input, select, textarea"
    )(isCalled(_, nameOrId))

  /** The textareas with this name or id. */
  def textarea(nameOrId: String): Selection =
    exactly(
      s"textarea($nameOrId)",
      Page.control("textarea", nameOrId),
      "textarea"
    )(isCalled(_, nameOrId))

  /** The selects with this name or id. */
  def selectBox(nameOrId: String): Selection =
    exactly(s"select($nameOrId)", Page.control("select", nameOrId), "select")(
      isCalled(_, nameOrId)
    )

  /** The radio buttons sharing this name. */
  def radios(fieldName: String): Selection =
    exactly(
      s"radios($fieldName)",
      s"""input[type=radio][name="$fieldName"]""",
      "input[type=radio]"
    )(e => says(e.attr("name"), fieldName))

  /** The checkboxes sharing this name. */
  def checkboxes(fieldName: String): Selection =
    exactly(
      s"checkboxes($fieldName)",
      s"""input[type=checkbox][name="$fieldName"]""",
      "input[type=checkbox]"
    )(e => says(e.attr("name"), fieldName))

  /** The fields of a date input, under either id convention. */
  def dateInput(fieldName: String): Selection = {
    val ids = Seq("day", "month", "year")
      .flatMap(part => Seq(s"$fieldName.$part", s"$fieldName-$part"))
    exactly(
      s"date input($fieldName)",
      ids.map(Page.idSelector).mkString(", "),
      "[id]"
    )(e => ids.contains(e.id()))
  }

  /** The file inputs with this name or id. */
  def fileUpload(nameOrId: String): Selection =
    exactly(
      s"file upload($nameOrId)",
      s"""input[type=file][name="$nameOrId"], input[type=file][id="$nameOrId"]""",
      "input[type=file]"
    )(isCalled(_, nameOrId))

  /** Every control on the page that a user can type into or choose from. */
  def formControls: List[Element] =
    document
      .select(
        "input:not([type=hidden]):not([type=submit]):not([type=button]), select, textarea"
      )
      .asScala
      .toList

  /** The labels pointing at this field. */
  def labelFor(fieldId: String): Selection =
    exactly(s"label for $fieldId", s"""label[for="$fieldId"]""", "label[for]")(
      e => says(e.attr("for"), fieldId)
    )

  /** Every legend. */
  def legends: Selection = named("legend", "legend")

  /** Every fieldset. */
  def fieldsets: Selection = named("fieldset", "fieldset")

  /** The hint for this field, by the GOV.UK id convention or inside the field's
    * wrapper.
    */
  def hint(fieldId: String): Selection =
    exactly(
      s"hint for $fieldId",
      s"""[id="$fieldId-hint"], [id="$fieldId"] .govuk-hint""",
      "[id], .govuk-hint"
    )(e =>
      e.id() == s"$fieldId-hint" ||
        e.hasClass("govuk-hint") &&
        e.parents.asScala.exists(parent => says(parent.id(), fieldId))
    )

  /** Every GOV.UK hint. */
  def hints: Selection = named("hint", ".govuk-hint")

  /** Every button and submit input. */
  def buttons: Selection =
    named("button", ".govuk-button, button, input[type=submit]")

  /** The control that submits the form. */
  def submitButton: Selection = {
    val candidates = Seq(
      "button[type=submit], input[type=submit]",
      "[id=submit]",
      "form .govuk-button:not(a)",
      ".govuk-button:not(a)"
    )
    // One candidate at a time: `named` records what it matches as asserted, and trying every
    // selector would record every button on the page rather than the one this resolves to.
    candidates.iterator
      .map(selector => named("submit button", selector))
      .find(_.nonEmpty)
      .getOrElse(Selection.empty("submit button", candidates.mkString(", ")))
  }

  // --------------------------------------------------------------- components

  /** Every GOV.UK summary list. */
  def summaryList: Selection = named("summary list", ".govuk-summary-list")

  /** Summary list rows as (key, value, action texts). */
  def summaryRows: List[(String, String, List[String])] =
    document
      .select(".govuk-summary-list__row")
      .asScala
      .toList
      .map { row =>
        val k = Text.normalise(row.select(".govuk-summary-list__key").text())
        val v = Text.normalise(row.select(".govuk-summary-list__value").text())
        val a = row
          .select(".govuk-summary-list__actions a")
          .asScala
          .toList
          .map(e => Text.normalise(e.text()))
        (k, v, a)
      }

  /** The text of the GOV.UK warning callouts. */
  def warningText: Selection =
    named("warning text", ".govuk-warning-text__text")

  /** The GOV.UK inset text blocks. */
  def insetText: Selection = named("inset text", ".govuk-inset-text")

  /** The GOV.UK panels. */
  def panel: Selection = named("panel", ".govuk-panel")

  /** The details components, GOV.UK or plain. */
  def details: Selection = named("details", ".govuk-details, details")

  /** The GOV.UK tabs. */
  def tabs: Selection = named("tabs", ".govuk-tabs")

  /** The GOV.UK accordions. */
  def accordion: Selection = named("accordion", ".govuk-accordion")

  /** Every table. */
  def tables: Selection = named("table", "table")

  /** The GOV.UK or HMRC pagination. */
  def pagination: Selection =
    named("pagination", ".govuk-pagination, .hmrc-pagination")

  /** The GOV.UK tags. */
  def tags: Selection = named("tag", ".govuk-tag")

  /** The GOV.UK bullet lists. */
  def bulletList: Selection = named("bullet list", ".govuk-list--bullet")

  /** The GOV.UK numbered lists. */
  def numberedList: Selection = named("numbered list", ".govuk-list--number")

  /** The HMRC add-to-a-list components. */
  def addToAList: Selection = named("add to a list", ".hmrc-add-to-a-list")

  /** The paragraphs of body text. */
  def paragraphs: Selection =
    named("paragraph", "p.govuk-body, p.govuk-body-l, p.govuk-body-s, main p")

  /** Every link with a destination. */
  def links: Selection = named("link", "a[href]")

  /** Every image. */
  def images: Selection = named("image", "img")

  /** The GOV.UK notification banners. */
  def notificationBanner: Selection =
    named("notification banner", ".govuk-notification-banner")

  // ----------------------------------------------------------------- messages

  /** Resolve a message key in this page's language. A key the language does not
    * define resolves to nothing, rather than to the English Play would render
    * in its place, so a Welsh assertion cannot pass on English words.
    */
  def message(key: String, args: Seq[Any] = Nil): Option[String] =
    if (definedInLanguage(key)) Some(Text.normalise(messages(key, args: _*)))
    else None

  /** Play's `isDefinedAt` also answers from the default files, which are
    * written in English or in the first language `play.i18n.langs` names. For
    * any other language only its own file counts, by its code and then, as Play
    * reads it, by the bare language. A `Messages` other than the one Play hands
    * out can only be asked `isDefinedAt`, fallback and all.
    */
  private def definedInLanguage(key: String): Boolean =
    messages match {
      case MessagesImpl(language, api)
          if language.language != "en" &&
            language.language != api.preferred(Nil).lang.language =>
        Seq(language.code, language.language).exists(code =>
          api.messages.get(code).exists(_.contains(key))
        )
      case _ => messages.isDefinedAt(key)
    }

  /** The first segment of every key this page's messages define, in any
    * language.
    */
  private[twirlspec] def messageKeyFamilies: Set[String] =
    messages match {
      case MessagesImpl(_, api) =>
        api.messages.values.flatMap(_.keys).map(_.takeWhile(_ != '.')).toSet
      case _ => Set.empty
    }

  /** Resolve a key, falling back to the key itself (Play's default behaviour).
    */
  def messageOrKey(key: String, args: Seq[Any] = Nil): String =
    message(key, args).getOrElse(key)

  // ------------------------------------------------------------------ content

  /** The page's visible text, normalised. */
  def text: String = Text.normalise(document.text())

  /** Whether the page's visible text contains this, after normalisation. */
  def contains(needle: String): Boolean =
    Text.containsText(document.text(), needle)

  /** The page's structural skeleton, for review and snapshotting. */
  /** Building this touches most of the page, and a failure message is not an
    * assertion, so it must not count as coverage.
    */
  def outline: String = withRecordingPaused(Outline.of(this))

  /** The same page, read in another language. It is the same page for coverage
    * too: what is asserted on either counts for both.
    */
  def withLang(newLang: Lang, newMessages: Messages): Page = {
    val read = new Page(document, newLang, newMessages, source, parseErrors)
    read.owner = owner
    read.group = group
    read.ownRecord = ownRecord
    read
  }

  override def toString: String =
    s"Page(lang=${lang.code}, title=${Text.preview(title, 60)})"

}

/** Ways to build a page from rendered HTML. */
object Page {

  /** `[id="x"]` rather than `#x`: GOV.UK date and address fields carry ids like
    * `value.day`, which a CSS id selector reads as a class.
    */
  private[twirlspec] def idSelector(elementId: String): String =
    s"""[id="$elementId"]"""

  private[twirlspec] def control(tag: String, nameOrId: String): String =
    s"""$tag[name="$nameOrId"], $tag[id="$nameOrId"]"""

  /** What keeps an element and all it holds from assistive technology, or from
    * being rendered at all: a tag manager's frame in `<noscript>`, say, or what
    * a `<template>` holds until a script uses it.
    */
  private val NotExposed =
    "[hidden], [inert], [aria-hidden=true], noscript, template"

  /** Comments, strings and brackets in a style attribute. A semicolon in one
    * ends no declaration, and `display` and `visibility` need none of them.
    */
  private val StyleNoise = """(?s)/\*.*?\*/|"[^"]*"|'[^']*'|\([^)]*\)"""

  /** A property's value in an element's own style attribute: the last it
    * declares, unless an earlier one is `!important`.
    */
  private def inlineStyle(e: Element, property: String): Option[String] = {
    val declared = e
      .attr("style")
      .replaceAll(StyleNoise, "")
      .split(";")
      .toList
      .map(_.split(":", 2).map(_.replaceAll("\\s+", "").toLowerCase))
      .collect { case Array(`property`, value) => value }
    declared
      .filter(_.endsWith("!important"))
      .lastOption
      .orElse(declared.lastOption)
      .map(_.stripSuffix("!important"))
  }

  /** Parse rendered HTML into a page in the language of the implicit messages.
    */
  def apply(html: Html)(implicit messages: Messages): Page =
    fromString(html.body, messages.lang, messages)

  /** Parse rendered HTML into a page for an explicit language and its messages.
    */
  def apply(html: Html, lang: Lang, messages: Messages): Page =
    fromString(html.body, lang, messages)

  /** Parse a string of HTML for a language and its messages, recording what the
    * parser had to repair.
    */
  def fromString(html: String, lang: Lang, messages: Messages): Page = {
    // Twirl does not check that a template produces well-formed markup, and
    // Jsoup will silently repair what it is given. Asking it to record what it
    // repaired is free, and turns a whole class of template bug into a finding.
    val parser = Parser.htmlParser().setTrackErrors(50)
    val document = Jsoup.parse(html, "", parser)
    val errors = parser.getErrors.asScala.toList.map(_.toString)
    new Page(document, lang, messages, html, errors)
  }

}
