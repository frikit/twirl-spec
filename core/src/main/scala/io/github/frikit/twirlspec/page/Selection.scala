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

/** A named set of elements pulled out of a page.
  *
  * @param name
  *   what the selection is called in failure messages
  * @param selector
  *   the CSS selector that produced it, also for failure messages
  * @param elements
  *   the matches, in document order
  */
final class Selection private (
    val name: String,
    val selector: String,
    val elements: List[Element]
) {

  /** Whether nothing matched. */
  def isEmpty: Boolean = elements.isEmpty

  /** Whether anything matched. */
  def nonEmpty: Boolean = elements.nonEmpty

  /** How many elements matched. */
  def size: Int = elements.size

  /** The first match, if there is one. */
  def headOption: Option[Element] = elements.headOption

  /** The match at this position in document order. */
  def apply(i: Int): Element = elements(i)

  /** Combined, normalised text of every matched element. */
  def text: String = Text.normalise(elements.map(_.text()).mkString(" "))

  /** Normalised text of each matched element, in document order. */
  def texts: List[String] = elements.map(e => Text.normalise(e.text()))

  /** Inner HTML of the first match. */
  def html: String = headOption.map(_.html()).getOrElse("")

  /** The first match's value for this attribute, when it has one. */
  def attr(attribute: String): Option[String] =
    headOption.map(_.attr(attribute)).filter(_.nonEmpty)

  /** Every match's value for this attribute, empty ones left out. */
  def attrs(attribute: String): List[String] =
    elements.map(_.attr(attribute)).filter(_.nonEmpty)

  /** The first match's id, if it has one. */
  def id: Option[String] = attr("id")

  /** Every match's id. */
  def ids: List[String] = attrs("id")

  /** The first match's classes. */
  def classes: Set[String] =
    headOption.map(_.classNames().asScala.toSet).getOrElse(Set.empty)

  /** Whether any match carries this class. */
  def hasClass(c: String): Boolean = elements.exists(_.hasClass(c))

  /** Narrow this selection with a further CSS selector. */
  def select(css: String): Selection =
    Selection(
      s"$name $css",
      s"$selector $css",
      elements.flatMap(_.select(css).asScala.toList)
    )

  /** Only the matches satisfying a predicate. */
  def filter(p: Element => Boolean): Selection =
    new Selection(name, selector, elements.filter(p))

  /** Only the matches whose text contains this, after normalisation. */
  def containing(needle: String): Selection =
    filter(e => Text.containsText(e.text(), needle))

  override def toString: String =
    if (isEmpty) s"$name (no match for `$selector`)"
    else s"$name x$size: ${texts.map(Text.preview(_, 60)).mkString(" | ")}"

}

/** Ways to build a selection. */
object Selection {

  /** A selection from a name, the selector that produced it and its matches. */
  def apply(
      name: String,
      selector: String,
      elements: List[Element]
  ): Selection =
    new Selection(name, selector, elements)

  /** A selection that matched nothing. */
  def empty(name: String, selector: String): Selection =
    new Selection(name, selector, Nil)

}
