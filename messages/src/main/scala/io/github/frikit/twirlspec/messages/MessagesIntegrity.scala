/*
 * Copyright 2026 Victor Osipov
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

package io.github.frikit.twirlspec.messages

import play.api.i18n.MessagesApi
import io.github.frikit.twirlspec.expect.Violation

import java.io.File
import scala.io.Source
import scala.util.Using

/** Checks over a service's message files. */
object MessagesIntegrity {

  final case class Config(
    requireWelsh: Boolean = true,
    /** How much of the Welsh file may be identical to the English before it is treated as untranslated. */
    maxUntranslatedRatio: Double = 0.06,
    ignoreKeys: Set[String] = Set.empty,
    ignoreKeyPrefixes: Set[String] = Set.empty,
    /** Keys whose value is a URL are expected to be identical in both files. */
    urlKeySuffixes: Set[String] = Set(".url", ".href", ".link.url")
  ) {

    def ignores(key: String): Boolean =
      ignoreKeys.contains(key) || ignoreKeyPrefixes.exists(key.startsWith)

  }

  object Config {
    val default: Config = Config()
  }

  /** Placeholder indices used by a message, in order: "{0} of {1}" -> 0, 1. */
  private val Placeholder = "\\{(\\d+)[^}]*\\}".r

  private def placeholders(value: String): List[Int] =
    Placeholder.findAllMatchIn(value).map(_.group(1).toInt).toList

  /** How a message's single quotes behave under `MessageFormat`, which Play applies to every message whether or not it takes arguments.
    */
  sealed private[twirlspec] trait QuoteState

  private[twirlspec] object QuoteState {
    case object Fine extends QuoteState
    case object Unbalanced extends QuoteState
    case object PlaceholderInQuotes extends QuoteState
    case object DeliberatelyQuoted extends QuoteState
  }

  private[twirlspec] def quoteState(value: String): QuoteState = {
    var i         = 0
    var inQuote   = false
    var sawQuoted = false
    var quotedArg = false
    while (i < value.length) {
      val c = value.charAt(i)
      if (c == '\'') {
        if (i + 1 < value.length && value.charAt(i + 1) == '\'') i += 2 // escaped apostrophe
        else { inQuote = !inQuote; if (inQuote) sawQuoted = true; i += 1 }
      } else {
        if (inQuote && c == '{' && i + 1 < value.length && Character.isDigit(value.charAt(i + 1))) quotedArg = true
        i += 1
      }
    }
    if (inQuote) QuoteState.Unbalanced
    else if (quotedArg) QuoteState.PlaceholderInQuotes
    else if (sawQuoted) QuoteState.DeliberatelyQuoted
    else QuoteState.Fine
  }

  def englishMessages(api: MessagesApi): Map[String, String] =
    api.messages.getOrElse("default", Map.empty) ++ api.messages.getOrElse("en", Map.empty)

  def messagesFor(api: MessagesApi, langCode: String): Map[String, String] =
    api.messages.getOrElse(langCode, Map.empty)

  /** Every problem across the message files. */
  def check(api: MessagesApi, config: Config = Config.default): Seq[Violation] = {
    val english = englishMessages(api).filterNot { case (k, _) => config.ignores(k) }
    val welsh   = messagesFor(api, "cy").filterNot { case (k, _) => config.ignores(k) }

    val parity =
      if (!config.requireWelsh) Nil
      else {
        val missingWelsh   = (english.keySet -- welsh.keySet).toList.sorted
        val missingEnglish = (welsh.keySet -- english.keySet).toList.sorted

        (if (missingWelsh.isEmpty) Nil
         else
           Seq(
             Violation(
               "messages.welsh-parity",
               s"${missingWelsh.size} key(s) are in conf/messages but not conf/messages.cy",
               actual = Some(preview(missingWelsh))
             )
           )) ++
          (if (missingEnglish.isEmpty) Nil
           else
             Seq(
               Violation(
                 "messages.english-parity",
                 s"${missingEnglish.size} key(s) are in conf/messages.cy but not conf/messages",
                 actual = Some(preview(missingEnglish))
               )
             ))
      }

    val allEntries = english.toList.map(("en", _)) ++ welsh.toList.map(("cy", _))

    val empty = allEntries.collect {
      case (lang, (key, value)) if value.trim.isEmpty =>
        Violation("messages.empty-value", s"[$lang] `$key` has an empty value")
    }

    val quotes = allEntries.flatMap { case (lang, (key, value)) =>
      quoteState(value) match {
        case QuoteState.Unbalanced          =>
          Seq(
            Violation(
              "messages.unescaped-quote",
              s"[$lang] `$key` has an unpaired apostrophe, so it is dropped and any placeholder after it stops substituting",
              actual = Some(value)
            ).withHint("double it: don''t — Play runs every message through MessageFormat")
          )
        case QuoteState.PlaceholderInQuotes =>
          Seq(
            Violation(
              "messages.quoted-placeholder",
              s"[$lang] `$key` has a placeholder inside a quoted section, so it will be shown literally",
              actual = Some(value)
            ).withHint("close the quoted section before the placeholder, or double the apostrophes")
          )
        case QuoteState.DeliberatelyQuoted  =>
          Seq(
            Violation(
              "messages.quoted-literal",
              s"[$lang] `$key` contains a MessageFormat quoted section",
              actual = Some(value)
            ).warn.withHint("correct if deliberate; if you meant an apostrophe, double it")
          )
        case QuoteState.Fine                => Nil
      }
    }

    val placeholderParity =
      if (!config.requireWelsh) Nil
      else
        english.toList.sortBy(_._1).flatMap { case (key, en) =>
          welsh.get(key).toList.flatMap { cy =>
            val enArgs = placeholders(en)
            val cyArgs = placeholders(cy)
            if (enArgs.sorted == cyArgs.sorted) Nil
            else
              Seq(
                Violation(
                  "messages.placeholder-parity",
                  s"`$key` uses different placeholders in each language",
                  expected = Some(s"en: ${enArgs.mkString(", ")}"),
                  actual = Some(s"cy: ${cyArgs.mkString(", ")}")
                ).withHint("a missing placeholder shows the citizen a blank where a value should be")
              )
          }
        }

    val coverage =
      if (!config.requireWelsh || english.isEmpty) Nil
      else {
        val identical = english.count { case (key, value) =>
          welsh.get(key).contains(value) && !config.urlKeySuffixes.exists(key.endsWith)
        }
        val ratio     = identical.toDouble / english.size.toDouble
        if (ratio <= config.maxUntranslatedRatio) Nil
        else
          Seq(
            Violation(
              "messages.translation-coverage",
              f"${ratio * 100}%.1f%% of Welsh messages are identical to the English",
              expected = Some(f"at most ${config.maxUntranslatedRatio * 100}%.1f%%"),
              actual = Some(s"$identical of ${english.size} keys")
            ).warn
          )
      }

    parity ++ empty ++ quotes ++ placeholderParity ++ coverage
  }

  /** Keys defined more than once in a single messages file. */
  def duplicateKeys(files: Seq[File]): Seq[Violation] =
    files.filter(_.exists()).flatMap { file =>
      val keys = Using.resource(Source.fromFile(file, "UTF-8")) { source =>
        source
          .getLines()
          .map(_.trim)
          .filter(line => line.nonEmpty && !line.startsWith("#") && line.contains("="))
          .map(_.takeWhile(_ != '=').trim)
          .toList
      }
      keys
        .groupBy(identity)
        .collect { case (key, occurrences) if occurrences.size > 1 => (key, occurrences.size) }
        .toList
        .sortBy(_._1)
        .map { case (key, n) =>
          Violation("messages.duplicate-key", s"${file.getName}: `$key` is defined $n times")
            .withHint("Play keeps the last definition, so the earlier ones are dead content")
        }
    }

  private def preview(keys: List[String]): String =
    if (keys.size <= 12) keys.mkString(", ") else keys.take(12).mkString(", ") + s", ... (${keys.size - 12} more)"

}
