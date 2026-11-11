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

/** An input's value as a browser sends it, after HTML's value sanitisation for
  * the common types: line breaks out of a text, a URL or an email trimmed, and
  * a number, date or time that is not one sent empty. A range and a colour are
  * sent as written, since their rules also clamp and convert.
  */
private[twirlspec] object InputValue {

  def sanitised(e: Element): String = {
    val value = e.attr("value")
    e.attr("type").toLowerCase(Locale.ROOT) match {
      case "hidden" | "range" | "color"     => value
      case "url"                            => trim(noBreaks(value))
      case "email" if e.hasAttr("multiple") =>
        value.split(",", -1).map(trim).mkString(",")
      case "email" => trim(noBreaks(value))
      // in HTML's grammar, and a number a double can hold: 2e308 is not one
      case "number" =>
        keepIf(value, Number.matches(value) && value.toDouble.isFinite)
      case "date"           => keepIf(value, date(value))
      case "month"          => keepIf(value, month(value))
      case "week"           => keepIf(value, week(value))
      case "time"           => keepIf(value, time(value).isDefined)
      case "datetime-local" => localDateTime(value).getOrElse("")
      case _                => noBreaks(value)
    }
  }

  private def keepIf(value: String, valid: Boolean) = if (valid) value else ""

  private def noBreaks(s: String) = s.filterNot(c => c == '\r' || c == '\n')

  private def isAsciiSpace(c: Char) = " \t\n\f\r".indexOf(c) >= 0

  private def trim(s: String) =
    s.dropWhile(isAsciiSpace).reverse.dropWhile(isAsciiSpace).reverse

  /** HTML's valid floating-point number. */
  private val Number =
    "-?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?".r

  private val YearMonth = "([0-9]{4,})-([0-9]{2})".r
  private val YearMonthDay = "([0-9]{4,})-([0-9]{2})-([0-9]{2})".r
  private val YearWeek = "([0-9]{4,})-W([0-9]{2})".r
  private val Time =
    "([0-9]{2}):([0-9]{2})(?::([0-9]{2})(?:\\.([0-9]{1,3}))?)?".r
  private val DateAndTime = "([0-9]{4,}-[0-9]{2}-[0-9]{2})[T ](.+)".r

  private def leap(year: BigInt) =
    year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)

  private def daysIn(year: BigInt, month: Int) = month match {
    case 2              => if (leap(year)) 29 else 28
    case 4 | 6 | 9 | 11 => 30
    case _              => 31
  }

  private def month(s: String) = s match {
    case YearMonth(y, m) => BigInt(y) > 0 && (1 to 12).contains(m.toInt)
    case _               => false
  }

  private def date(s: String) = s match {
    case YearMonthDay(y, m, d) =>
      BigInt(y) > 0 && (1 to 12).contains(m.toInt) &&
      (1 to daysIn(BigInt(y), m.toInt)).contains(d.toInt)
    case _ => false
  }

  /** A week exists up to 52, or 53 when the year starts on a Thursday, or on a
    * Wednesday in a leap year.
    */
  private def week(s: String) = s match {
    case YearWeek(y, w) =>
      val year = BigInt(y)
      // Gauss's weekday of 1 January: 0 is Sunday.
      val prior = year - 1
      val jan1 =
        ((1 + 5 * (prior % 4) + 4 * (prior % 100) + 6 * (prior % 400)) % 7).toInt
      val weeks = if (jan1 == 4 || (jan1 == 3 && leap(year))) 53 else 52
      year > 0 && (1 to weeks).contains(w.toInt)
    case _ => false
  }

  /** The time's hour, minute, second and fraction as written, when it is one.
    */
  private def time(s: String): Option[(String, String, String, String)] =
    s match {
      case Time(h, m, sec, ms)
          if h.toInt < 24 && m.toInt < 60 &&
            Option(sec).forall(_.toInt < 60) =>
        Some((h, m, Option(sec).getOrElse("00"), Option(ms).getOrElse("")))
      case _ => None
    }

  /** A local date and time in its shortest form: `T` between them, and no
    * seconds or fraction that say nothing. The digits are the value's own, so
    * no default locale can turn them into another script's.
    */
  private def localDateTime(s: String): Option[String] = s match {
    case DateAndTime(d, t) if date(d) =>
      time(t).map { case (h, m, sec, ms) =>
        val fraction = ms.reverse.dropWhile(_ == '0').reverse
        val seconds =
          if (sec == "00" && fraction.isEmpty) ""
          else if (fraction.isEmpty) s":$sec"
          else s":$sec.$fraction"
        s"${d}T$h:$m$seconds"
      }
    case _ => None
  }
}
