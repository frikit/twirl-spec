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
import play.api.i18n.{Lang, Messages}
import play.twirl.api.Html

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

  /** Which spec this page belongs to, so several renders of one view share a
    * coverage record.
    */
  private var group: String = Integer.toHexString(source.hashCode)

  private[twirlspec] def coverageGroup: String = group

  private[twirlspec] def belongingTo(newGroup: String): this.type = {
    if (newGroup.nonEmpty) group = newGroup
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
    if (recording && elements.nonEmpty)
      CoverageRegistry.record(group, elements.flatMap(e => Anchors.namesOf(e)))

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
    * fieldset.
    */
  def isDisabled(e: org.jsoup.nodes.Element): Boolean =
    e.hasAttr("disabled") || Option(e.closest("fieldset[disabled]")).isDefined

  /** The description an assistive technology reads after the name. */
  def accessibleDescription(e: org.jsoup.nodes.Element): String =
    Text.normalise(
      e.attr("aria-describedby")
        .split("\\s+")
        .filter(_.nonEmpty)
        .flatMap(id => Option(document.getElementById(id)))
        .map(_.text())
        .mkString(" ")
    )

  /** Current value of every named control, as a browser would submit it. */
  def formValues: Map[String, String] = {
    val simple = document
      .select(
        "input:not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]), textarea, select"
      )
      .asScala
      .toList
      .filter(_.attr("name").nonEmpty)
      .map { e =>
        val v =
          if (e.tagName() == "textarea") Text.normalise(e.text())
          else if (e.tagName() == "select")
            e.select("option[selected]")
              .asScala
              .headOption
              .map(_.attr("value"))
              .getOrElse("")
          else e.attr("value")
        e.attr("name") -> v
      }
    val chosen = document
      .select("input[type=checkbox][checked], input[type=radio][checked]")
      .asScala
      .toList
      .filter(_.attr("name").nonEmpty)
      .map(e => e.attr("name") -> e.attr("value"))
    (simple ++ chosen).toMap
  }

  /** The name an assistive technology would announce for an element. */
  def accessibleName(e: org.jsoup.nodes.Element): String =
    AccessibleName.of(document, e)

  /** The element with this id, matched by attribute rather than CSS syntax, so
    * an id with a dot in it works.
    */
  def byId(elementId: String): Selection =
    named(s"#$elementId", Page.idSelector(elementId))

  // ------------------------------------------------------------- page framing

  /** The browser title, normalised. */
  def title: String = Text.normalise(document.title())

  /** The `lang` attribute of the `<html>` element, if it has one. */
  def htmlLang: Option[String] =
    Option(document.selectFirst("html")).map(_.attr("lang")).filter(_.nonEmpty)

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
    named("timeout dialog", "[data-module=hmrc-timeout-dialog]")

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
    document
      .select(
        ".govuk-error-summary a[href], .govuk-error-summary__list a[href]"
      )
      .asScala
      .toList
      .map(e => (e.attr("href").stripPrefix("#"), Text.normalise(e.text())))

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
      .map { e =>
        val field = e.id().stripSuffix("-error")
        val hidden = e
          .select(".govuk-visually-hidden")
          .asScala
          .toList
          .map(_.text())
          .mkString
        field -> Text.normalise(e.text().replace(hidden, ""))
      }
      .toMap

  // -------------------------------------------------------------------- forms

  /** Every form on the page. */
  def forms: Selection = named("form", "form")

  /** The action of the first form, if there is one. */
  def formAction: Option[String] = forms.attr("action")

  /** The method of the first form, upper-cased, if there is one. */
  def formMethod: Option[String] = forms.attr("method").map(_.toUpperCase)

  /** The inputs with this name or id. */
  def input(nameOrId: String): Selection =
    named(s"input($nameOrId)", Page.control("input", nameOrId))

  /** Any control by name or id: input, select or textarea. */
  def formControl(nameOrId: String): Selection =
    named(
      s"control($nameOrId)",
      Seq("input", "select", "textarea")
        .map(Page.control(_, nameOrId))
        .mkString(", ")
    )

  /** The textareas with this name or id. */
  def textarea(nameOrId: String): Selection =
    named(s"textarea($nameOrId)", Page.control("textarea", nameOrId))

  /** The selects with this name or id. */
  def selectBox(nameOrId: String): Selection =
    named(s"select($nameOrId)", Page.control("select", nameOrId))

  /** The radio buttons sharing this name. */
  def radios(fieldName: String): Selection =
    named(s"radios($fieldName)", s"""input[type=radio][name="$fieldName"]""")

  /** The checkboxes sharing this name. */
  def checkboxes(fieldName: String): Selection =
    named(
      s"checkboxes($fieldName)",
      s"""input[type=checkbox][name="$fieldName"]"""
    )

  /** The fields of a date input, under either id convention. */
  def dateInput(fieldName: String): Selection =
    named(
      s"date input($fieldName)",
      Seq("day", "month", "year")
        .flatMap(part => Seq(s"$fieldName.$part", s"$fieldName-$part"))
        .map(Page.idSelector)
        .mkString(", ")
    )

  /** The file inputs with this name or id. */
  def fileUpload(nameOrId: String): Selection =
    named(
      s"file upload($nameOrId)",
      s"""input[type=file][name="$nameOrId"], input[type=file][id="$nameOrId"]"""
    )

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
    named(s"label for $fieldId", s"""label[for="$fieldId"]""")

  /** Every legend. */
  def legends: Selection = named("legend", "legend")

  /** Every fieldset. */
  def fieldsets: Selection = named("fieldset", "fieldset")

  /** The hint for this field, by the GOV.UK id convention or inside the field's
    * wrapper.
    */
  def hint(fieldId: String): Selection =
    named(
      s"hint for $fieldId",
      s"""[id="$fieldId-hint"], [id="$fieldId"] .govuk-hint"""
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

  /** Resolve a message key in this page's language. */
  def message(key: String, args: Seq[Any] = Nil): Option[String] =
    if (messages.isDefinedAt(key)) Some(Text.normalise(messages(key, args: _*)))
    else None

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

  /** The same page, read in another language. */
  def withLang(newLang: Lang, newMessages: Messages): Page =
    new Page(document, newLang, newMessages, source, parseErrors)

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
