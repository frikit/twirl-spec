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

/** Finding elements the way an assistive technology does: by role and accessible name. */
object Roles {

  /** The HTML that carries each ARIA role implicitly, per HTML-AAM. */
  private val implicitly: Map[String, String] = Map(
    "button"       -> "button, input[type=submit], input[type=button], input[type=reset]",
    "link"         -> "a[href], area[href]",
    "heading"      -> "h1, h2, h3, h4, h5, h6",
    "textbox"      -> "input[type=text], input[type=email], input[type=tel], input[type=url], input[type=search], input:not([type]), textarea",
    "checkbox"     -> "input[type=checkbox]",
    "radio"        -> "input[type=radio]",
    "combobox"     -> "select",
    "list"         -> "ul, ol",
    "listitem"     -> "li",
    "group"        -> "fieldset",
    "table"        -> "table",
    "row"          -> "tr",
    "cell"         -> "td",
    "columnheader" -> "th",
    "form"         -> "form",
    "main"         -> "main",
    "navigation"   -> "nav",
    "banner"       -> "header",
    "contentinfo"  -> "footer",
    "img"          -> "img[alt]:not([alt=''])",
    "separator"    -> "hr",
    "article"      -> "article"
  )

  /** Elements with this role, implicit or explicit.
    *
    * An element carrying an explicit `role` is matched only by that role, so
    * `<a role="button">` is a button and not a link.
    */
  def matching(document: org.jsoup.nodes.Document, role: String): List[Element] = {
    val explicit  = document.select(s"[role=$role]").asScala.toList
    val inherited = implicitly
      .get(role)
      .map(sel => document.select(sel).asScala.toList.filterNot(_.hasAttr("role")))
      .getOrElse(Nil)
    (inherited ++ explicit).distinct
  }

  def selectorFor(role: String): String =
    implicitly.get(role).fold(s"[role=$role]")(sel => s"$sel, [role=$role]")

  def knownRoles: Set[String] = implicitly.keySet
}
