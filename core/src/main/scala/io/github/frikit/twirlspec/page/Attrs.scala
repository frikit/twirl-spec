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

import java.util.Locale
import java.util.regex.Pattern

/** An element's attributes compared as jsoup's selectors compared them before
  * its Web query mode, which reads `~=` as one word among several and compares
  * most values in their case. Done here rather than in a selector, a comparison
  * reads alike whichever jsoup a service has and whichever mode it runs in.
  */
private[twirlspec] object Attrs {

  /** As `[name~=regex]` read: the value holds a match for the pattern. */
  def matches(e: Element, name: String, pattern: Pattern): Boolean =
    e.hasAttr(name) && pattern.matcher(e.attr(name)).find()

  /** As `[name=value]` read: the whole value is this one, in any case. */
  def is(e: Element, name: String, value: String): Boolean =
    e.hasAttr(name) && e.attr(name).equalsIgnoreCase(value)

  /** As `[name^=prefix]` read: the value in lower case starts with the prefix
    * in lower case, which is narrower than comparing in any case: `ſ` does not
    * become `s`.
    */
  def startsWith(e: Element, name: String, prefix: String): Boolean =
    e.hasAttr(name) && e
      .attr(name)
      .toLowerCase(Locale.ENGLISH)
      .startsWith(prefix.toLowerCase(Locale.ENGLISH))
}
