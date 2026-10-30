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

/** What makes something on a page worth asserting, and what to call it.
  *
  * Identity is the id, the link, or the tracking attribute rather than a
  * position in the document, so a view asserted in one state still counts as
  * asserted when the same view is rendered in another.
  */
object Anchors {

  /** Attributes that make an element worth asserting on their own: analytics
    * markers, by default.
    */
  val defaultTrackedAttributes: Set[String] = Set("data-journey-click")

  /** The names this element is known by for coverage: its id, its link, and any
    * tracked attribute it carries.
    */
  def namesOf(
      e: Element,
      tracked: Set[String] = defaultTrackedAttributes
  ): List[String] = names(e, tracked, linkName)

  /** What an assertion touching this element counts for: its id, its link with
    * where it goes, and every attribute it carries, because which of them a
    * spec tracks is only known when coverage is measured.
    */
  def touchedBy(e: Element): List[String] =
    names(e, e.attributes.asScala.map(_.getKey).toSet, linkTo)

  private def names(
      e: Element,
      tracked: Set[String],
      link: Element => String
  ): List[String] = {
    val id = e.id()
    val idName = if (id.nonEmpty) List(s"#$id") else Nil
    val linked = if (isLink(e)) List(link(e)) else Nil
    val tracking = tracked.toList.sorted.collect {
      case a if e.hasAttr(a) => s"$a=${e.attr(a)}"
    }
    idName ::: linked ::: tracking
  }

  /** Whether the element is a link with a destination. */
  def isLink(e: Element): Boolean = e.tagName == "a" && e.hasAttr("href")

  /** The coverage name of a link: its text, or its destination when it has no
    * text.
    */
  def linkName(e: Element): String = {
    val text = e.text().trim
    if (text.nonEmpty) s"""link "$text"""" else s"link ${e.attr("href")}"
  }

  /** A link's name with where it goes: two links reading the same and going to
    * different places are two things to assert.
    */
  def linkTo(e: Element): String = {
    val name = linkName(e)
    if (e.text().trim.nonEmpty) s"$name -> ${e.attr("href")}" else name
  }

}
