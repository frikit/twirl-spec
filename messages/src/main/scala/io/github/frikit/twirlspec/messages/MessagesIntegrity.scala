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

package io.github.frikit.twirlspec.messages

import play.api.i18n.MessagesApi
import io.github.frikit.twirlspec.expect.Violation

import java.io.File
import scala.io.Source
import scala.util.Using

/** Checks over a service's message files. */
object MessagesIntegrity {

  /** What the checks measure against, and what they leave alone.
    *
    * @param baseLanguage
    *   the language the others are measured against: Play's `default` file plus
    *   this one
    * @param requireTranslations
    *   whether every other configured language must carry every base key; off
    *   for a single-language service
    * @param maxUntranslatedRatio
    *   how much of a translation may be identical to the base before it is
    *   treated as untranslated
    * @param ignoreKeys
    *   keys left out of every check
    * @param ignoreKeyPrefixes
    *   prefixes of keys left out of every check
    * @param urlKeySuffixes
    *   suffixes of keys whose value is a URL, expected to read the same in
    *   every language and so not counted as untranslated
    */
  final case class Config(
      baseLanguage: String = "en",
      requireTranslations: Boolean = true,
      maxUntranslatedRatio: Double = 0.06,
      ignoreKeys: Set[String] = Set.empty,
      ignoreKeyPrefixes: Set[String] = Set.empty,
      urlKeySuffixes: Set[String] = Set(".url", ".href", ".link.url")
  ) {

    /** Whether a key is left out of every check. */
    def ignores(key: String): Boolean =
      ignoreKeys.contains(key) || ignoreKeyPrefixes.exists(key.startsWith)

  }

  /** The default configuration. */
  object Config {

    /** The default: English as the base, translations required, and up to 6% of
      * values allowed to be identical.
      */
    val default: Config = Config()
  }

  /** Placeholder indices used by a message, in order: "{0} of {1}" -> 0, 1. */
  private val Placeholder = "\\{(\\d+)[^}]*\\}".r

  private def placeholders(value: String): List[Int] =
    Placeholder.findAllMatchIn(value).map(_.group(1).toInt).toList

  /** How a message's single quotes behave under `MessageFormat`, which Play
    * applies to every message whether or not it takes arguments.
    */
  sealed private[twirlspec] trait QuoteState

  private[twirlspec] object QuoteState {
    case object Fine extends QuoteState
    case object Unbalanced extends QuoteState
    case object PlaceholderInQuotes extends QuoteState
    case object DeliberatelyQuoted extends QuoteState
  }

  private[twirlspec] def quoteState(value: String): QuoteState = {
    var i = 0
    var inQuote = false
    var sawQuoted = false
    var quotedArg = false
    while (i < value.length) {
      val c = value.charAt(i)
      if (c == '\'') {
        if (i + 1 < value.length && value.charAt(i + 1) == '\'')
          i += 2 // escaped apostrophe
        else { inQuote = !inQuote; if (inQuote) sawQuoted = true; i += 1 }
      } else {
        if (
          inQuote && c == '{' && i + 1 < value.length && Character.isDigit(
            value.charAt(i + 1)
          )
        ) quotedArg = true
        i += 1
      }
    }
    if (inQuote) QuoteState.Unbalanced
    else if (quotedArg) QuoteState.PlaceholderInQuotes
    else if (sawQuoted) QuoteState.DeliberatelyQuoted
    else QuoteState.Fine
  }

  /** The base language's messages: Play's `default` file plus the file for the
    * base code.
    */
  def baseMessages(
      api: MessagesApi,
      config: Config = Config.default
  ): Map[String, String] =
    api.messages.getOrElse("default", Map.empty) ++ api.messages.getOrElse(
      config.baseLanguage,
      Map.empty
    )

  /** The messages of one language, empty when the api holds none for it. */
  def messagesFor(api: MessagesApi, langCode: String): Map[String, String] =
    api.messages.getOrElse(langCode, Map.empty)

  /** Every language the api holds messages for, other than the base.
    *
    * Play keeps its own framework messages under `default.play`, alongside the
    * service's `default` file. Neither is a language.
    */
  def translationLanguages(
      api: MessagesApi,
      config: Config = Config.default
  ): List[String] =
    (api.messages.keySet - config.baseLanguage)
      .filterNot(_.startsWith("default"))
      .toList
      .sorted

  /** Every problem across the message files. */
  def check(
      api: MessagesApi,
      config: Config = Config.default
  ): Seq[Violation] = {
    val base = baseMessages(api, config).filterNot { case (k, _) =>
      config.ignores(k)
    }
    val translations = translationLanguages(api, config).map { lang =>
      lang -> messagesFor(api, lang).filterNot { case (k, _) =>
        config.ignores(k)
      }
    }

    val noTranslations =
      if (config.requireTranslations && translations.isEmpty)
        Seq(
          Violation(
            "messages.translation-parity",
            s"no messages file exists for any language other than ${config.baseLanguage}"
          ).withHint(
            "add conf/messages.<lang>, or set requireTranslations = false for a single-language service"
          )
        )
      else Nil

    val parity =
      if (!config.requireTranslations) Nil
      else
        translations.flatMap { case (lang, translated) =>
          val missing = (base.keySet -- translated.keySet).toList.sorted
          val extra = (translated.keySet -- base.keySet).toList.sorted

          (if (missing.isEmpty) Nil
           else
             Seq(
               Violation(
                 "messages.translation-parity",
                 s"[$lang] ${missing.size} key(s) are in the ${config.baseLanguage} messages but not the $lang ones",
                 actual = Some(preview(missing))
               )
             )) ++
            (if (extra.isEmpty) Nil
             else
               Seq(
                 Violation(
                   "messages.base-parity",
                   s"[$lang] ${extra.size} key(s) are in the $lang messages but not the ${config.baseLanguage} ones",
                   actual = Some(preview(extra))
                 )
               ))
        }

    val allEntries =
      base.toList.map((config.baseLanguage, _)) ++ translations.flatMap {
        case (lang, m) =>
          m.toList.map((lang, _))
      }

    val empty = allEntries.collect {
      case (lang, (key, value)) if value.trim.isEmpty =>
        Violation("messages.empty-value", s"[$lang] `$key` has an empty value")
    }

    val quotes = allEntries.flatMap { case (lang, (key, value)) =>
      quoteState(value) match {
        case QuoteState.Unbalanced =>
          Seq(
            Violation(
              "messages.unescaped-quote",
              s"[$lang] `$key` has an unpaired apostrophe, so it is dropped and any placeholder after it stops substituting",
              actual = Some(value)
            ).withHint(
              "double it: don''t — Play runs every message through MessageFormat"
            )
          )
        case QuoteState.PlaceholderInQuotes =>
          Seq(
            Violation(
              "messages.quoted-placeholder",
              s"[$lang] `$key` has a placeholder inside a quoted section, so it will be shown literally",
              actual = Some(value)
            ).withHint(
              "close the quoted section before the placeholder, or double the apostrophes"
            )
          )
        case QuoteState.DeliberatelyQuoted =>
          Seq(
            Violation(
              "messages.quoted-literal",
              s"[$lang] `$key` contains a MessageFormat quoted section",
              actual = Some(value)
            ).warn.withHint(
              "correct if deliberate; if you meant an apostrophe, double it"
            )
          )
        case QuoteState.Fine => Nil
      }
    }

    val placeholderParity =
      if (!config.requireTranslations) Nil
      else
        translations.flatMap { case (lang, translated) =>
          base.toList.sortBy(_._1).flatMap { case (key, baseValue) =>
            translated.get(key).toList.flatMap { value =>
              val baseArgs = placeholders(baseValue)
              val args = placeholders(value)
              if (baseArgs.sorted == args.sorted) Nil
              else
                Seq(
                  Violation(
                    "messages.placeholder-parity",
                    s"`$key` uses different placeholders in $lang than in ${config.baseLanguage}",
                    expected = Some(
                      s"${config.baseLanguage}: ${baseArgs.mkString(", ")}"
                    ),
                    actual = Some(s"$lang: ${args.mkString(", ")}")
                  ).withHint(
                    "a missing placeholder shows the reader a blank where a value should be"
                  )
                )
            }
          }
        }

    val coverage =
      if (!config.requireTranslations || base.isEmpty) Nil
      else
        translations.flatMap { case (lang, translated) =>
          val identical = base.count { case (key, value) =>
            translated.get(key).contains(value) && !config.urlKeySuffixes
              .exists(key.endsWith)
          }
          val ratio = identical.toDouble / base.size.toDouble
          if (ratio <= config.maxUntranslatedRatio) Nil
          else
            Seq(
              Violation(
                "messages.translation-coverage",
                f"[$lang] ${ratio * 100}%.1f%% of messages are identical to the ${config.baseLanguage} ones",
                expected =
                  Some(f"at most ${config.maxUntranslatedRatio * 100}%.1f%%"),
                actual = Some(s"$identical of ${base.size} keys")
              ).warn
            )
        }

    noTranslations ++ parity ++ empty ++ quotes ++ placeholderParity ++ coverage
  }

  /** Keys defined more than once in a single messages file. */
  def duplicateKeys(files: Seq[File]): Seq[Violation] =
    files.filter(_.exists()).flatMap { file =>
      val keys = Using.resource(Source.fromFile(file, "UTF-8")) { source =>
        source
          .getLines()
          .map(_.trim)
          .filter(line =>
            line.nonEmpty && !line.startsWith("#") && line.contains("=")
          )
          .map(_.takeWhile(_ != '=').trim)
          .toList
      }
      keys
        .groupBy(identity)
        .collect {
          case (key, occurrences) if occurrences.size > 1 =>
            (key, occurrences.size)
        }
        .toList
        .sortBy(_._1)
        .map { case (key, n) =>
          Violation(
            "messages.duplicate-key",
            s"${file.getName}: `$key` is defined $n times"
          )
            .withHint(
              "Play keeps the last definition, so the earlier ones are dead content"
            )
        }
    }

  private def preview(keys: List[String]): String =
    if (keys.size <= 12) keys.mkString(", ")
    else keys.take(12).mkString(", ") + s", ... (${keys.size - 12} more)"

}
