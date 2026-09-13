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

/** Whitespace and punctuation normalisation. */
object Text {

  private val Nbsp = 0x00a0.toChar.toString // non-breaking space
  private val NarrowNbsp = 0x202f.toChar.toString
  private val SoftHyphen = 0x00ad.toChar.toString

  private val RightQuote =
    0x2019.toChar.toString // curly apostrophe, used all over GOV.UK content
  private val LeftQuote = 0x2018.toChar.toString
  private val LeftDouble = 0x201c.toChar.toString
  private val RightDouble = 0x201d.toChar.toString

  private val Whitespace = "\\s+".r

  /** Canonical form used for every text comparison twirl-spec makes. */
  def normalise(raw: String): String =
    if (raw == null) ""
    else {
      val substituted = raw
        .replace(Nbsp, " ")
        .replace(NarrowNbsp, " ")
        .replace("&nbsp;", " ")
        .replace(SoftHyphen, "")
        .replace(RightQuote, "'")
        .replace(LeftQuote, "'")
        .replace(LeftDouble, "\"")
        .replace(RightDouble, "\"")
      Whitespace.replaceAllIn(substituted, " ").trim
    }

  /** True when two pieces of page/message text are equal after normalisation.
    */
  def same(a: String, b: String): Boolean = normalise(a) == normalise(b)

  /** Whether one piece of text contains another, after both are normalised. */
  def containsText(haystack: String, needle: String): Boolean =
    normalise(haystack).contains(normalise(needle))

  /** A short single-line preview of some text, for failure messages. */
  def preview(raw: String, max: Int = 140): String = {
    val n = normalise(raw)
    if (n.length <= max) n else n.take(max - 3) + "..."
  }

}
