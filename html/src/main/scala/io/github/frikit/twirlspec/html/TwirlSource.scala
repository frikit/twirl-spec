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

package io.github.frikit.twirlspec.html

/** A Twirl template with the Scala taken out, so that what is left is the HTML it writes.
  *
  * Scanning a template rather than a rendered page is cheap — no application, no
  * injector, no render — but the source is two languages at once, and one of them
  * uses the angle brackets the other reserves for tags. `@if(page < total)` is a
  * comparison, not the start of an element, and a scanner that cannot tell the
  * difference reports every paginated page in a large codebase.
  *
  * Everything belonging to an `@` expression is blanked to spaces, keeping newlines
  * so line numbers still point at the template. The `{ … }` block that follows a
  * construct like `@if` or `@formHelper` is left alone, because that part is HTML.
  */
object TwirlSource {

  def htmlOnly(template: String): String = {
    val out = template.toCharArray
    var i   = 0
    while (i < out.length)
      if (out(i) == '@') i = blankExpression(template, out, i)
      else if (out(i) == '}') i = blankElseContinuation(template, out, i)
      else i += 1
    new String(out)
  }

  /** `} else if (…) {` continues an `@if` without an at-sign of its own, so the
    * condition is Scala even though nothing marks it as such. Only a closing brace
    * introduces one, which keeps the word "else" in ordinary prose out of it.
    */
  private def blankElseContinuation(src: String, out: Array[Char], at: Int): Int = {
    val elseAt = skipWhitespace(src, at + 1)
    if (!src.startsWith("else", elseAt) || isIdentPart(charAtOr(src, elseAt + 4, ' '))) at + 1
    else {
      blank(out, elseAt, elseAt + 4)
      val ifAt = skipWhitespace(src, elseAt + 4)
      if (!src.startsWith("if", ifAt) || isIdentPart(charAtOr(src, ifAt + 2, ' '))) ifAt
      else {
        blank(out, ifAt, ifAt + 2)
        val paren = skipWhitespace(src, ifAt + 2)
        if (charAtOr(src, paren, ' ') == '(') blankBalanced(src, out, paren, '(', ')') else paren
      }
    }
  }

  private def skipWhitespace(src: String, from: Int): Int = {
    var i = from
    while (i < src.length && src.charAt(i).isWhitespace) i += 1
    i
  }

  private def charAtOr(src: String, at: Int, fallback: Char): Char =
    if (at >= 0 && at < src.length) src.charAt(at) else fallback

  private def blankExpression(src: String, out: Array[Char], at: Int): Int =
    if (at + 1 >= src.length) {
      blank(out, at, at + 1)
      at + 1
    } else
      src.charAt(at + 1) match {
        case '@'                  => blank(out, at, at + 2); at + 2 // an escaped at-sign
        case '*'                  => blankComment(src, out, at)
        case '{'                  => blank(out, at, at + 1); blankBalanced(src, out, at + 1, '{', '}')
        case '('                  => blank(out, at, at + 1); blankBalanced(src, out, at + 1, '(', ')')
        case c if isIdentStart(c) => blankInvocation(src, out, at)
        case _                    => at + 1 // a literal @, as in an email address
      }

  private def blankComment(src: String, out: Array[Char], at: Int): Int = {
    val end = src.indexOf("*@", at + 2)
    val to  = if (end < 0) src.length else end + 2
    blank(out, at, to)
    to
  }

  /** `@name`, and whatever continues it — but not a following block, which is HTML. */
  private def blankInvocation(src: String, out: Array[Char], at: Int): Int = {
    var j    = at + 1
    while (j < src.length && isIdentPart(src.charAt(j))) j += 1
    val name = src.substring(at + 1, j)
    blank(out, at, j)

    if (name == "import") blankToEndOfLine(src, out, j)
    else if (isFragmentDefinition(src, j)) blankFragmentDefinition(src, out, j)
    else blankContinuations(src, out, j)
  }

  /** `@name = { … }` names a piece of markup for use elsewhere. Half a tag pair is
    * the whole point of one, so its contents are not the balance check's business.
    */
  private def isFragmentDefinition(src: String, from: Int): Boolean = {
    val eq = skipWhitespace(src, from)
    charAtOr(src, eq, ' ') == '=' && charAtOr(src, skipWhitespace(src, eq + 1), ' ') == '{'
  }

  private def blankFragmentDefinition(src: String, out: Array[Char], from: Int): Int = {
    val eq    = skipWhitespace(src, from)
    val brace = skipWhitespace(src, eq + 1)
    blank(out, eq, brace)
    blankBalanced(src, out, brace, '{', '}')
  }

  private def blankToEndOfLine(src: String, out: Array[Char], from: Int): Int = {
    val eol = src.indexOf('\n', from)
    val to  = if (eol < 0) src.length else eol
    blank(out, from, to)
    to
  }

  /** Argument lists, type parameters and `.field` chains all belong to the expression. */
  private def blankContinuations(src: String, out: Array[Char], from: Int): Int = {
    var j        = from
    var continue = true
    while (continue)
      if (j < src.length && src.charAt(j) == '(') j = blankBalanced(src, out, j, '(', ')')
      else if (j < src.length && src.charAt(j) == '[') j = blankBalanced(src, out, j, '[', ']')
      else if (j + 1 < src.length && src.charAt(j) == '.' && isIdentStart(src.charAt(j + 1))) {
        val start = j
        j += 1
        while (j < src.length && isIdentPart(src.charAt(j))) j += 1
        blank(out, start, j)
      } else continue = false
    j
  }

  /** From an opening bracket to the one that matches it, ignoring brackets inside strings. */
  private def blankBalanced(src: String, out: Array[Char], start: Int, open: Char, close: Char): Int = {
    var depth  = 0
    var i      = start
    var quote  = ' '
    var result = -1
    while (i < src.length && result < 0) {
      val c = src.charAt(i)
      if (quote != ' ') {
        if (c == '\\') i += 1
        else if (c == quote) quote = ' '
      } else if (c == '"' || c == '\'') quote = c
      else if (c == open) depth += 1
      else if (c == close) {
        depth -= 1
        if (depth == 0) result = i + 1
      }
      i += 1
    }
    val to     = if (result < 0) src.length else result
    blank(out, start, to)
    to
  }

  private def blank(out: Array[Char], from: Int, to: Int): Unit =
    (from until math.min(to, out.length)).foreach(i => if (out(i) != '\n') out(i) = ' ')

  private def isIdentStart(c: Char): Boolean = c.isLetter || c == '_'
  private def isIdentPart(c: Char): Boolean  = c.isLetterOrDigit || c == '_'
}
