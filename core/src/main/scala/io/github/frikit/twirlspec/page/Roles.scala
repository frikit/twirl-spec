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

import org.jsoup.nodes.Element

import scala.jdk.CollectionConverters._

/** Finding elements the way an assistive technology does: by role and
  * accessible name.
  */
object Roles {

  /** A `<select>` showing more than one row at once, which makes it a list. */
  private val ListboxSize = "[size~=^0*([2-9]|[1-9][0-9]+)$]"

  /** This element with a name its author gave it, which is what makes a section
    * a landmark. A selector sees that the name is there, not whether an
    * `aria-labelledby` points at anything.
    */
  private def named(tag: String) =
    s"$tag[aria-label~=\\S], $tag[aria-labelledby~=\\S], $tag[title~=\\S]"

  /** A header or footer that is the page's own, with nothing sectioning around
    * it.
    */
  private def pagesOwn(tag: String) =
    outside(tag, Sectioning ++ Seq("main", "[role=main]"))

  /** What a header, footer or aside inside it belongs to, by tag or by role. */
  private val Sectioning = Seq(
    "article",
    "aside",
    "nav",
    "section",
    "[role=article]",
    "[role=complementary]",
    "[role=navigation]",
    "[role=region]"
  )

  private def outside(tag: String, around: Seq[String]) =
    s"$tag:not(${around.map(a => s"$a $tag").mkString(", ")})"

  /** The HTML that carries each ARIA role implicitly, per HTML-AAM. */
  private val implicitly: Map[String, String] = Map(
    "button" -> "button, input[type=submit], input[type=button], input[type=reset], input[type=image]",
    "link" -> "a[href], area[href]",
    "heading" -> "h1, h2, h3, h4, h5, h6",
    "textbox" -> "input[type=text], input[type=email], input[type=tel], input[type=url], input:not([type]), textarea",
    "searchbox" -> "input[type=search]",
    "spinbutton" -> "input[type=number]",
    "slider" -> "input[type=range]",
    "checkbox" -> "input[type=checkbox]",
    "radio" -> "input[type=radio]",
    "combobox" -> s"select:not([multiple]):not($ListboxSize)",
    "listbox" -> s"select[multiple], select$ListboxSize",
    "option" -> "option",
    "list" -> "ul, ol",
    "listitem" -> "li",
    "group" -> "fieldset",
    "table" -> "table",
    "rowgroup" -> "thead, tbody, tfoot",
    "row" -> "tr",
    "cell" -> "td",
    "columnheader" -> "th:not([scope=row]):not([scope=rowgroup])",
    "rowheader" -> "th[scope=row], th[scope=rowgroup]",
    "form" -> "form",
    "complementary" -> s"${outside("aside", Sectioning)}, ${named("aside")}",
    "region" -> named("section"),
    "dialog" -> "dialog",
    "progressbar" -> "progress",
    "meter" -> "meter",
    "status" -> "output",
    "term" -> "dt, dfn",
    "definition" -> "dd",
    "figure" -> "figure",
    "main" -> "main",
    "navigation" -> "nav",
    "banner" -> pagesOwn("header"),
    "contentinfo" -> pagesOwn("footer"),
    "img" -> "img[alt]:not([alt=''])",
    "separator" -> "hr",
    "article" -> "article"
  )

  /** Elements with this role, implicit or explicit.
    *
    * An element carrying an explicit `role` is matched only by that role, so
    * `<a role="button">` is a button and not a link.
    */
  def matching(
      document: org.jsoup.nodes.Document,
      role: String
  ): List[Element] = {
    val explicit = document.select(s"[role=$role]").asScala.toList
    val inherited = implicitly
      .get(role)
      .map(sel =>
        document.select(sel).asScala.toList.filterNot(_.hasAttr("role"))
      )
      .getOrElse(Nil)
    (inherited ++ explicit).distinct
  }

  /** A CSS selector for everything carrying this role, implicitly or
    * explicitly.
    */
  def selectorFor(role: String): String =
    implicitly.get(role).fold(s"[role=$role]")(sel => s"$sel, [role=$role]")

  /** The roles resolved implicitly from HTML elements. */
  def knownRoles: Set[String] = implicitly.keySet
}
