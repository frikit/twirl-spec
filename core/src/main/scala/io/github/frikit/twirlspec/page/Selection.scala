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

/** A named set of elements pulled out of a page. */
final class Selection private (
    val name: String,
    val selector: String,
    val elements: List[Element]
) {

  def isEmpty: Boolean = elements.isEmpty
  def nonEmpty: Boolean = elements.nonEmpty
  def size: Int = elements.size

  def headOption: Option[Element] = elements.headOption

  def apply(i: Int): Element = elements(i)

  /** Combined, normalised text of every matched element. */
  def text: String = Text.normalise(elements.map(_.text()).mkString(" "))

  /** Normalised text of each matched element, in document order. */
  def texts: List[String] = elements.map(e => Text.normalise(e.text()))

  /** Inner HTML of the first match. */
  def html: String = headOption.map(_.html()).getOrElse("")

  def attr(attribute: String): Option[String] =
    headOption.map(_.attr(attribute)).filter(_.nonEmpty)

  def attrs(attribute: String): List[String] =
    elements.map(_.attr(attribute)).filter(_.nonEmpty)

  def id: Option[String] = attr("id")

  def ids: List[String] = attrs("id")

  def classes: Set[String] =
    headOption.map(_.classNames().asScala.toSet).getOrElse(Set.empty)

  def hasClass(c: String): Boolean = elements.exists(_.hasClass(c))

  /** Narrow this selection with a further CSS selector. */
  def select(css: String): Selection =
    Selection(
      s"$name $css",
      s"$selector $css",
      elements.flatMap(_.select(css).asScala.toList)
    )

  def filter(p: Element => Boolean): Selection =
    new Selection(name, selector, elements.filter(p))

  def containing(needle: String): Selection =
    filter(e => Text.containsText(e.text(), needle))

  override def toString: String =
    if (isEmpty) s"$name (no match for `$selector`)"
    else s"$name x$size: ${texts.map(Text.preview(_, 60)).mkString(" | ")}"

}

object Selection {

  def apply(
      name: String,
      selector: String,
      elements: List[Element]
  ): Selection =
    new Selection(name, selector, elements)

  def empty(name: String, selector: String): Selection =
    new Selection(name, selector, Nil)

}
