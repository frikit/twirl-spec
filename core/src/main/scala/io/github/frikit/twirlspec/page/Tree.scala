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

import java.util.ArrayDeque

/** A page's elements walked without a query, which jsoup's query modes cannot
  * read apart: its Web query mode leaves the element a query starts from out of
  * the result, for `getAllElements` as for `select`.
  */
private[twirlspec] object Tree {

  /** This element and every element inside it, in document order. The walk
    * keeps its own stack, since an older jsoup sets no limit on how deep a page
    * may nest.
    */
  def withDescendants(e: Element): List[Element] = {
    val out = List.newBuilder[Element]
    val pending = new ArrayDeque[Element]
    pending.push(e)
    while (!pending.isEmpty) {
      val next = pending.pop()
      out += next
      val children = next.children()
      var i = children.size - 1
      while (i >= 0) {
        pending.push(children.get(i))
        i -= 1
      }
    }
    out.result()
  }

  /** The element, or the nearest element around it, that passes. */
  def closest(e: Element)(p: Element => Boolean): Option[Element] =
    Iterator.iterate(e)(_.parent()).takeWhile(_ != null).find(p)
}
