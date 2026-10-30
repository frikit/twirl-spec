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

package io.github.frikit.twirlspec.wcag

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.expect.{Matching, Violation}
import io.github.frikit.twirlspec.page.Text

import scala.jdk.CollectionConverters._

/** Mistakes specific to rendering a Twirl template with Play i18n. */
object TwirlStandards extends RuleSet {

  lazy val all: Seq[Rule] = Seq(
    Rule(
      "no-raw-message-keys",
      "no unresolved message key is shown to a citizen"
    ) { page =>
      val scope = if (page.main.nonEmpty) page.main(0) else page.document.body()
      val families = page.messageKeyFamilies
      // Everything a citizen can be shown, wherever a template put it, but not
      // what is someone's own text or is there to be read literally.
      val written = scope
        .select("*")
        .asScala
        .toList
        .filter(_.closest("textarea, pre, code, kbd, samp") == null)
        .map(_.ownText())
      val attributes = Seq("aria-label", "alt", "placeholder").flatMap(a =>
        scope.select(s"[$a]").asScala.map(_.attr(a))
      )
      val buttons = scope
        .select("input[type=submit], input[type=button], input[type=reset]")
        .asScala
        .map(_.attr("value"))
      // A layout appends the service's name, and an error state prefixes one.
      val title = Matching.stripErrorPrefix(page, page.title).split(" - ").head
      (title +: (written ++ attributes ++ buttons))
        .map(Text.normalise)
        .filter(looksLikeAKey(_, families))
        .distinct
        .map { t =>
          Violation(
            "no-raw-message-keys",
            s"""the page renders "$t", which looks like an unresolved message key"""
          )
            .withHint(s"""add "$t" to conf/messages (and conf/messages.cy)""")
        }
    },
    Rule(
      "well-formed-html",
      "the template produced markup a browser does not have to repair"
    ) { page =>
      page.parseErrors.take(5).map { e =>
        Violation(
          "well-formed-html",
          s"the parser had to repair the markup: $e"
        )
          .withHint("Twirl does not check that a template closes its tags")
      }
    },
    Rule(
      "unambiguous-field-names",
      "no two controls submit under the same name"
    ) { page =>
      page.document
        .select(
          "input:not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]):not([type=reset]):not([type=image]), select, textarea"
        )
        .asScala
        .toList
        // A form submits only its own controls and only enabled ones, so two
        // forms each with a token are not an ambiguity, and a name ending in []
        // asks Play for a list.
        .filter(e => e.attr("name").nonEmpty && !page.isDisabled(e))
        .filter(page.formOwner(_).isDefined)
        .filterNot(_.attr("name").endsWith("[]"))
        .groupBy(e => (page.formOwner(e), e.attr("name")))
        .collect {
          case ((_, name), controls) if controls.size > 1 =>
            (name, controls.size)
        }
        .toSeq
        .sortBy(_._1)
        .map { case (name, n) =>
          Violation(
            "unambiguous-field-names",
            s"""$n controls submit under the name "$name""""
          )
            .withHint(
              "the server sees one value and cannot tell which control produced it"
            )
        }
    },
    Rule("no-scala-leakage", "no Scala value leaks into the rendered page") {
      page =>
        val body = page.text
        val markers = Seq(
          "Some(",
          "None)",
          "List(",
          "Vector(",
          "Map(",
          "ArraySeq(",
          "$anonfun",
          "@scala.",
          "null null"
        )
        markers.filter(body.contains).map { m =>
          Violation(
            "no-scala-leakage",
            s"""the page contains "$m"""",
            actual = Some(snippetAround(body, m))
          )
            .withHint(
              "a value reached the template without being unwrapped or formatted"
            )
        }
    }
  )

  /** Looks like `some.message.key`. */
  private val KeyShaped = "^[a-z][A-Za-z0-9_-]*(\\.[A-Za-z0-9_-]+){1,6}$".r

  /** A version rather than a key: `v1.2`. */
  private val VersionShaped = "^[a-z]?[0-9]+(\\.[0-9]+)+$".r

  /** Could as well be someone's answer as a key: `john.smith`. */
  private val NameShaped = "^[a-z-]+\\.[a-z-]+$".r

  /** Whether these words read as an unresolved message key rather than as
    * something with a dot in it. A pair of plain lowercase words is a key only
    * when the service has keys beginning with the first of them: `site.maybe`
    * beside `site.yes`, and not a username.
    */
  private def looksLikeAKey(text: String, families: Set[String]): Boolean =
    KeyShaped.pattern.matcher(text).matches() &&
      !text.startsWith("www.") &&
      !VersionShaped.pattern.matcher(text).matches() &&
      !FileLike.contains(text.split("\\.").last.toLowerCase) &&
      (!NameShaped.pattern.matcher(text).matches() ||
        families.contains(text.takeWhile(_ != '.')))

  private val FileLike = Set(
    "pdf",
    "csv",
    "xml",
    "json",
    "html",
    "htm",
    "xls",
    "xlsx",
    "doc",
    "docx",
    "zip",
    "txt",
    "png",
    "jpg",
    "jpeg",
    "gov",
    "uk",
    "com",
    "org",
    "net",
    "js",
    "css"
  )

  /** Callers only reach this for a marker they have already found, so there is
    * no not-found case to handle: clamping the index covers it either way.
    */
  private def snippetAround(body: String, marker: String): String = {
    val i = math.max(0, body.indexOf(marker))
    Text.preview(
      body.substring(math.max(0, i - 40), math.min(body.length, i + 60)),
      110
    )
  }

}
