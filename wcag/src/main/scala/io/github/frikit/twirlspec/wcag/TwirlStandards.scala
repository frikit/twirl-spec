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

package io.github.frikit.twirlspec.wcag

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{Page, Text}
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** Mistakes specific to rendering a Twirl template with Play i18n. */
object TwirlStandards extends RuleSet {

  def all: Seq[Rule] = rules

  private def rules: Seq[Rule] = Seq(
    Rule("no-raw-message-keys", "no unresolved message key is shown to a citizen") { page =>
      val scope = if (page.main.nonEmpty) page.main(0) else page.document.body()
      scope
        .select("h1, h2, h3, h4, h5, h6, p, li, label, legend, span, td, th, a, strong, dt, dd, caption, summary")
        .asScala
        .toList
        .map(e => Text.normalise(e.ownText()))
        .filter(t => t.nonEmpty && KeyShaped.pattern.matcher(t).matches())
        .filterNot(t => FileLike.contains(t.split("\\.").last.toLowerCase))
        .distinct
        .map { t =>
          Violation("no-raw-message-keys", s"""the page renders "$t", which looks like an unresolved message key""")
            .withHint(s"""add "$t" to conf/messages (and conf/messages.cy)""")
        }
    },
    Rule("well-formed-html", "the template produced markup a browser does not have to repair") { page =>
      page.parseErrors.take(5).map { e =>
        Violation("well-formed-html", s"the parser had to repair the markup: $e")
          .withHint("Twirl does not check that a template closes its tags")
      }
    },
    Rule("unambiguous-field-names", "no two controls submit under the same name") { page =>
      page.document
        .select("input:not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]), select, textarea")
        .asScala
        .toList
        .filter(_.attr("name").nonEmpty)
        .groupBy(_.attr("name"))
        .collect { case (name, controls) if controls.size > 1 => (name, controls.size) }
        .toSeq
        .sortBy(_._1)
        .map { case (name, n) =>
          Violation("unambiguous-field-names", s"""$n controls submit under the name "$name"""")
            .withHint("the server sees one value and cannot tell which control produced it")
        }
    },
    Rule("no-scala-leakage", "no Scala value leaks into the rendered page") { page =>
      val body    = page.text
      val markers = Seq("Some(", "None)", "List(", "Vector(", "Map(", "ArraySeq(", "$anonfun", "@scala.", "null null")
      markers.filter(body.contains).map { m =>
        Violation("no-scala-leakage", s"""the page contains "$m"""", actual = Some(snippetAround(body, m)))
          .withHint("a value reached the template without being unwrapped or formatted")
      }
    }
  )

  /** Looks like `some.message.key` and resolves to nothing. */
  private val KeyShaped = "^[a-z][A-Za-z0-9]*(\\.[A-Za-z0-9_]+){1,6}$".r

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

  /** Callers only reach this for a marker they have already found, so there is no not-found case to handle: clamping the index covers it either way.
    */
  private def snippetAround(body: String, marker: String): String = {
    val i = math.max(0, body.indexOf(marker))
    Text.preview(body.substring(math.max(0, i - 40), math.min(body.length, i + 60)), 110)
  }

}
