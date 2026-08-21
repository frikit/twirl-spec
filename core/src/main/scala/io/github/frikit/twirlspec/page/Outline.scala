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

package io.github.frikit.twirlspec.page

import org.jsoup.nodes.Element

import scala.jdk.CollectionConverters._

/** A readable skeleton of a rendered page.
  *
  * Two jobs. It is printed underneath every failure so you can see what the
  * view actually produced without re-reading 400 lines of HTML, and it is
  * stable enough to be committed as a structural snapshot — the shape of a
  * page changes far less often than its markup, so a diff of the outline is a
  * meaningful review artefact where a diff of the HTML is not.
  */
object Outline {

  private val Gutter = 13 // widest label is "lang toggle" plus separation

  def of(page: Page): String = {
    val sections = Seq(
      framing(page),
      headingTree(page),
      forms(page),
      components(page),
      errors(page)
    ).filter(_.nonEmpty)

    sections.map(_.mkString("\n")).mkString("\n")
  }

  private def field(label: String, value: String): String =
    s"  ${label.padTo(Gutter, ' ')}$value"

  private def framing(page: Page): Seq[String] = {
    val rows = Seq(
      Some(field("title", Text.preview(page.title))),
      page.htmlLang.map(l => field("lang", l)),
      page.serviceName.headOption.map(_ => field("service", page.serviceName.text)),
      page.h1.headOption.map(_ => field("h1", page.h1.text)),
      page.caption.headOption.map(_ => field("caption", page.caption.text)),
      page.backLink.attr("href").map(h => field("back link", h)),
      if (page.languageToggle.nonEmpty) Some(field("lang toggle", "present")) else None
    ).flatten
    if (rows.isEmpty) Nil else "page" +: rows
  }

  private def headingTree(page: Page): Seq[String] = {
    val hs = page.headings
    if (hs.size <= 1) Nil
    else
      "headings" +: hs.map { case (level, txt) =>
        s"  ${"  " * (level - 1)}h$level  ${Text.preview(txt, 90)}"
      }
  }

  private def forms(page: Page): Seq[String] = {
    val formElements = page.document.select("form").asScala.toList
    if (formElements.isEmpty) Nil
    else
      formElements.flatMap { form =>
        val method = Option(form.attr("method")).filter(_.nonEmpty).map(_.toUpperCase).getOrElse("GET")
        val action = Option(form.attr("action")).filter(_.nonEmpty).getOrElse("(none)")
        val header = s"form $method $action"

        val controls = form
          .select("input:not([type=hidden]), select, textarea, button")
          .asScala
          .toList
          .map(describeControl(page, _))

        header +: controls
      }
  }

  private def describeControl(page: Page, e: Element): String = {
    val tag     = e.tagName()
    val kind    = if (tag == "input") e.attr("type") else tag
    val name    = Option(e.attr("name")).filter(_.nonEmpty).orElse(Option(e.id()).filter(_.nonEmpty)).getOrElse("?")
    val id      = e.id()
    val label   = if (id.nonEmpty) Text.preview(page.labelFor(id).text, 45) else ""
    val hint    = if (id.nonEmpty) Text.preview(page.hint(id).text, 40) else ""
    val checked = if (e.hasAttr("checked")) " checked" else ""
    val value   = Option(e.attr("value")).filter(_.nonEmpty).map(v => s" value=${Text.preview(v, 25)}").getOrElse("")

    val text = if (kind == "submit" || tag == "button") s""" "${Text.preview(e.text(), 40)}"""" else ""
    val bits = Seq(
      if (label.nonEmpty) s"""label "$label"""" else "",
      if (hint.nonEmpty) s"""hint "$hint"""" else ""
    ).filter(_.nonEmpty).mkString("  ")

    s"  ${kind.padTo(9, ' ')}${name.padTo(22, ' ')}$bits$text$value$checked".replaceAll("\\s+$", "")
  }

  private def components(page: Page): Seq[String] = {
    val present = Seq(
      "summary list"        -> page.summaryList.size,
      "warning text"        -> page.warningText.size,
      "inset text"          -> page.insetText.size,
      "details"             -> page.details.size,
      "panel"               -> page.panel.size,
      "tabs"                -> page.tabs.size,
      "accordion"           -> page.accordion.size,
      "table"               -> page.tables.size,
      "notification banner" -> page.notificationBanner.size,
      "pagination"          -> page.pagination.size,
      "add to a list"       -> page.addToAList.size,
      "bullet list"         -> page.bulletList.size
    ).filter(_._2 > 0)

    if (present.isEmpty) Nil
    else "components" +: present.map { case (n, c) => field(n, if (c == 1) "1" else s"$c") }
  }

  private def errors(page: Page): Seq[String] =
    if (page.errorSummary.isEmpty && page.errorMessages.isEmpty) Nil
    else {
      val summary = page.errorSummaryLinks.map { case (target, txt) =>
        s"  summary -> #$target  ${Text.preview(txt, 70)}"
      }
      val inline  = page.fieldErrors.toList.sortBy(_._1).map { case (f, m) => s"  inline  $f: ${Text.preview(m, 70)}" }
      "errors" +: (summary ++ inline)
    }

}
