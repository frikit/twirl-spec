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
package io.github.frikit.twirlspec.quality

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{Page, Text}
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** What a page says about itself in its head.
  *
  * All page level, so a component's spec is not told it has no charset.
  */
object MetadataStandards extends RuleSet {

  /** Beyond this a title is usually truncated in a browser tab and in search. */
  private val TitleBudget = 65

  def all: Seq[Rule] = rules

  private def rules: Seq[Rule] = Seq(
    Rule("has-charset", "the page declares its character encoding", pageLevel = true) { page =>
      if (page.document.select("meta[charset], meta[http-equiv=Content-Type]").asScala.nonEmpty) Nil
      else
        Seq(
          Violation("has-charset", "the page declares no character encoding")
            .withHint("""<meta charset="utf-8"> first in the head, or a browser guesses""")
        )
    },
    Rule("not-noindex", "the page is not accidentally hidden from search", pageLevel = true, Warning) { page =>
      page.document
        .select("meta[name=robots]")
        .asScala
        .toList
        .filter(_.attr("content").toLowerCase.contains("noindex"))
        .map(e =>
          Violation("not-noindex", "the page asks not to be indexed", actual = Some(e.attr("content"))).warn
            .withHint("deliberate on a holding page, and a surprise everywhere else")
        )
    },
    Rule("has-meta-description", "the page describes itself for a search result", pageLevel = true, Warning) { page =>
      val description = page.document.select("meta[name=description]").asScala.toList
      if (description.exists(_.attr("content").trim.nonEmpty)) Nil
      else Seq(Violation("has-meta-description", "the page has no meta description").warn)
    },
    Rule("title-is-concise", "the title survives being truncated", pageLevel = true, Warning) { page =>
      val title = Text.normalise(page.document.title())
      if (title.length <= TitleBudget) Nil
      else
        Seq(
          Violation(
            "title-is-concise",
            s"the title is ${title.length} characters",
            expected = Some(s"at most $TitleBudget"),
            actual = Some(title)
          ).warn.withHint("a browser tab and a search result both cut it off before the end")
        )
    }
  )

}
