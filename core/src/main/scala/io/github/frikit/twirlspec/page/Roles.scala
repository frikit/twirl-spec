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

import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

/** Finding elements the way an assistive technology does: by role and
  * accessible name.
  */
object Roles {

  /** A `<select>` showing more than one row at once, which makes it a list. */
  private val ListboxSize = Pattern.compile("^0*([2-9]|[1-9][0-9]+)$")

  private def showsRows(e: Element): Boolean =
    Attrs.matches(e, "size", ListboxSize)

  private val Said = Pattern.compile("\\S")

  /** Whether the element has a name its author gave it, which is what makes a
    * section a landmark. This sees that the name is there, not whether an
    * `aria-labelledby` points at anything.
    */
  private def named(e: Element): Boolean =
    Seq("aria-label", "aria-labelledby", "title").exists(
      Attrs.matches(e, _, Said)
    )

  /** What a header, footer or aside inside it belongs to, by tag or by role. */
  private def sectioning(e: Element): Boolean =
    e.is("article, aside, nav, section") ||
      Seq("article", "complementary", "navigation", "region").exists(
        Attrs.is(e, "role", _)
      )

  /** What a header or footer inside it is not the page's own within. */
  private def pageSection(e: Element): Boolean =
    sectioning(e) || e.is("main") || Attrs.is(e, "role", "main")

  /** Whether something around the element, not the element itself, passes. */
  private def inside(e: Element)(p: Element => Boolean): Boolean =
    Option(e.parent()).flatMap(Tree.closest(_)(p)).nonEmpty

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
    "combobox" -> "select",
    "listbox" -> "select",
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
    "complementary" -> "aside",
    "region" -> "section",
    "dialog" -> "dialog",
    "progressbar" -> "progress",
    "meter" -> "meter",
    "status" -> "output",
    "term" -> "dt, dfn",
    "definition" -> "dd",
    "figure" -> "figure",
    "main" -> "main",
    "navigation" -> "nav",
    "banner" -> "header",
    "contentinfo" -> "footer",
    "img" -> "img[alt]:not([alt=''])",
    "separator" -> "hr",
    "article" -> "article"
  )

  /** What a selector cannot say alike in every jsoup: whether a select shows
    * more than one row, whether an aside or a section is named, and what a
    * header, footer or aside sits in. Each keeps those of its role's elements
    * that hold it.
    */
  private val refined: Map[String, Element => Boolean] = Map(
    "combobox" -> (e => !e.hasAttr("multiple") && !showsRows(e)),
    "listbox" -> (e => e.hasAttr("multiple") || showsRows(e)),
    "complementary" -> (e => !inside(e)(sectioning) || named(e)),
    "region" -> (e => named(e)),
    "banner" -> (e => !inside(e)(pageSection)),
    "contentinfo" -> (e => !inside(e)(pageSection))
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
    // A role compares in any case, as the selector [role=x] did.
    val explicit =
      document.select("[role]").asScala.toList.filter(Attrs.is(_, "role", role))
    val inherited = implicitly
      .get(role)
      .map(sel =>
        document
          .select(sel)
          .asScala
          .toList
          .filterNot(_.hasAttr("role"))
          .filter(refined.getOrElse(role, (_: Element) => true))
      )
      .getOrElse(Nil)
    (inherited ++ explicit).distinct
  }

  /** A CSS selector for the elements that can carry this role, implicitly or
    * explicitly, for failure messages. What a selector cannot say alike in
    * every jsoup, such as a select's rows, a section's name, what a header sits
    * in or a role written in capitals, [[matching]] decides.
    */
  def selectorFor(role: String): String =
    implicitly.get(role).fold(s"[role=$role]")(sel => s"$sel, [role=$role]")

  /** The roles resolved implicitly from HTML elements. */
  def knownRoles: Set[String] = implicitly.keySet
}
