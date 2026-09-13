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
  */
object AccessibleName {

  def of(document: Document, e: Element): String = {
    val fromLabelledBy = e
      .attr("aria-labelledby")
      .split("\\s+")
      .filter(_.nonEmpty)
      .flatMap(id => Option(document.getElementById(id)))
      .map(_.text())
      .mkString(" ")

    val candidates = Seq(
      fromLabelledBy,
      e.attr("aria-label"),
      labelFor(document, e),
      if (e.tagName() == "img") e.attr("alt") else "",
      if (e.tagName() == "fieldset")
        e.select("legend").asScala.headOption.map(_.text()).getOrElse("")
      else "",
      if (e.tagName() == "input") e.attr("value") else "",
      e.text()
    )

    Text.normalise(
      candidates.find(c => Text.normalise(c).nonEmpty).getOrElse("")
    )
  }

  private def labelFor(document: Document, e: Element): String = {
    val byId = Option(e.id())
      .filter(_.nonEmpty)
      .map(id =>
        document
          .select(s"""label[for="$id"]""")
          .asScala
          .toList
          .map(_.text())
          .mkString(" ")
      )
      .getOrElse("")
    if (byId.nonEmpty) byId
    else Option(e.closest("label")).map(_.text()).getOrElse("")
  }

}
