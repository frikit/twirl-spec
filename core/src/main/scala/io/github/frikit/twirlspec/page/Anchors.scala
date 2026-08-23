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

/** What makes something on a page worth asserting, and what to call it.
  *
  * Identity is the id, the link, or the tracking attribute rather than a
  * position in the document, so a view asserted in one state still counts as
  * asserted when the same view is rendered in another.
  */
object Anchors {

  val defaultTrackedAttributes: Set[String] = Set("data-journey-click")

  def namesOf(e: Element, tracked: Set[String] = defaultTrackedAttributes): List[String] = {
    val id       = e.id()
    val idName   = if (id.nonEmpty) List(s"#$id") else Nil
    val link     = if (isLink(e)) List(linkName(e)) else Nil
    val tracking = tracked.toList.sorted.collect { case a if e.hasAttr(a) => s"$a=${e.attr(a)}" }
    idName ::: link ::: tracking
  }

  def isLink(e: Element): Boolean = e.tagName == "a" && e.hasAttr("href")

  def linkName(e: Element): String = {
    val text = e.text().trim
    if (text.nonEmpty) s"""link "$text"""" else s"link ${e.attr("href")}"
  }

}
