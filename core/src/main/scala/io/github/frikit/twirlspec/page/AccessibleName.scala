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

import org.jsoup.nodes.{Document, Element}

import scala.jdk.CollectionConverters._

/** The name an assistive technology announces for an element.
  *
  * A working subset of the accessible name computation: the parts that a
  * server-rendered page can actually be judged on. It does not resolve
  * `aria-labelledby` chains beyond one hop, and knows nothing that depends on
  * CSS or JavaScript.
  *
  * This is the one answer to "what is this element called". Every rule that
  * asks the question asks it here, so the same markup cannot pass one check and
  * fail another.
  */
object AccessibleName {

  /** The input types whose `value` is the name the control announces. For every
    * other type the value is what the user entered, which names nothing: a text
    * box carrying "Jane" is not a box labelled "Jane".
    */
  private val ValueNamedInputTypes = Set("button", "submit", "reset")

  /** The controls whose content is their value rather than their name: the text
    * inside a `<textarea>` and the options inside a `<select>` are not what the
    * control is announced as.
    */
  private val ContentIsNotTheName = Set("input", "select", "textarea")

  /** The accessible name of an element in this document. */
  def of(document: Document, e: Element): String =
    firstNonEmpty(
      () => fromLabelledBy(document, e),
      () => e.attr("aria-label"),
      () => labelFor(document, e),
      () => if (e.tagName() == "img") e.attr("alt") else "",
      () =>
        if (e.tagName() == "fieldset")
          e.select("legend")
            .asScala
            .headOption
            .map(legend => announcedText(legend))
            .getOrElse("")
        else "",
      () => if (valueIsTheName(e)) e.attr("value") else "",
      () => fromContent(e),
      () => e.attr("title")
    )

  /** The first source that says anything, normalised.
    *
    * The sources are passed unevaluated: the later ones copy a subtree, and an
    * element named by `aria-labelledby` should not pay for that.
    */
  private def firstNonEmpty(sources: (() => String)*): String =
    sources.iterator
      .map(source => Text.normalise(source()))
      .find(_.nonEmpty)
      .getOrElse("")

  private def fromLabelledBy(document: Document, e: Element): String =
    e.attr("aria-labelledby")
      .split("\\s+")
      .filter(_.nonEmpty)
      .flatMap(id => Option(document.getElementById(id)))
      .map(target => announcedText(target))
      .mkString(" ")

  private def valueIsTheName(e: Element): Boolean =
    e.tagName() == "input" &&
      ValueNamedInputTypes.contains(e.attr("type").toLowerCase)

  /** Content taken out of the accessibility tree. It is announced to nobody, so
    * it names nothing it sits inside: the arrow in
    * `<a>Next<span aria-hidden="true"> →</span></a>` is not part of the name.
    */
  private val NotAnnounced = "[aria-hidden=true]"

  /** An image whose alt text is announced. A presentational image's alt is
    * ignored, so it names nothing either.
    */
  private val AnnouncedImage =
    "img[alt]:not([role=presentation]):not([role=none])"

  /** The form controls whose own content a wrapping label must not borrow. */
  private val NestedControls = "input, select, textarea, button"

  /** What the element says for itself: its own text, or — for a link or button
    * whose content is an image — the alt text of that image, which is what is
    * announced in its place. Either way, only the part of the content that is
    * announced counts.
    */
  private def fromContent(e: Element): String =
    if (ContentIsNotTheName.contains(e.tagName())) ""
    else {
      val said = announced(e)
      val text = Text.normalise(said.text())
      if (text.nonEmpty) text
      else
        said
          .select(AnnouncedImage)
          .asScala
          .map(_.attr("alt"))
          .filter(_.trim.nonEmpty)
          .mkString(" ")
    }

  /** What is announced for an element, as a traversal starting at it.
    *
    * A hidden root carries its whole subtree. ACCNAME exempts every node in a
    * traversal whose directly referenced node was itself hidden — which is what
    * makes `aria-labelledby` at a hidden element work at all, and it would be
    * incoherent to honour the reference and then drop half of what it points
    * at. A root that is not hidden drops the parts of itself that are.
    *
    * @param alsoDrop
    *   content to take out whether or not it is hidden
    */
  private def announced(root: Element, alsoDrop: String*): Element =
    // `closest`, not `is`: aria-hidden covers the subtree under it, so a root
    // is hidden when an ancestor says so, and the traversal is exempt either
    // way.
    if (root.closest(NotAnnounced) != null) without(root, alsoDrop: _*)
    else without(root, alsoDrop :+ NotAnnounced: _*)

  /** The text of [[announced]]. Every name taken from another element — a
    * label, a legend, the target of an `aria-labelledby` — comes through here,
    * so a visible label reading only `<span aria-hidden="true">Required</span>`
    * names nothing.
    */
  private def announcedText(root: Element, alsoDrop: String*): String =
    announced(root, alsoDrop: _*).text()

  /** A copy of the element with the matching content taken out. The copy's own
    * root is kept even where it matches: this removes content from an element,
    * not the element itself.
    */
  private def without(e: Element, selectors: String*): Element = {
    val copy = e.clone()
    selectors.foreach(selector =>
      copy.select(selector).asScala.filter(_ ne copy).foreach(_.remove())
    )
    copy
  }

  private def labelFor(document: Document, e: Element): String = {
    // The id is compared rather than built into a selector string: an id is
    // allowed to contain a quote, and `label[for="a"b"]` does not parse — it
    // would throw rather than fail to match. The comparison is exact, because
    // an id reference is case-sensitive, and only `<label>` is consulted,
    // because `for` on anything else — `<output for="x">` — labels nothing.
    val byId = Option(e.id())
      .filter(_.nonEmpty)
      .map(id =>
        document
          .getElementsByTag("label")
          .asScala
          .toList
          .filter(_.attr("for") == id)
          .map(label => announcedText(label))
          .mkString(" ")
      )
      .getOrElse("")
    if (byId.nonEmpty) byId else wrappingLabel(e)
  }

  /** The text of a label wrapped around a control, without the control's own
    * content. A `<select>` inside a label is labelled by the label's words, not
    * by whichever option happens to be selected.
    */
  private def wrappingLabel(e: Element): String =
    Option(e.closest("label"))
      .map(label => announcedText(label, NestedControls))
      .getOrElse("")

}
