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
package io.github.frikit.twirlspec.standards

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{Page, Text}
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** Markup that works but says the wrong thing about its own structure. */
object SemanticStandards extends RuleSet {

  private val Interactive = "a[href], button, input:not([type=hidden]), select, textarea"

  def all: Seq[Rule] = rules

  private def rules: Seq[Rule] = Seq(
    Rule("no-nested-interactive", "no control contains another control") { page =>
      page.document
        .select(Interactive)
        .asScala
        .toList
        // Jsoup's Element.select matches the element itself as well as its
        // descendants, so every control would otherwise contain itself.
        .filter(e => e.select(Interactive).asScala.exists(_ ne e))
        .map(e =>
          Violation(
            "no-nested-interactive",
            s"a <${e.tagName()}> contains another control",
            actual = Some(Text.preview(e.outerHtml(), 100))
          ).withHint("keyboard and pointer disagree about what was activated, and screen readers announce both")
        )
    },
    Rule("lists-contain-list-items", "a list contains only list items") { page =>
      page.document
        .select("ul > *:not(li), ol > *:not(li)")
        .asScala
        .toList
        .filterNot(e => Set("script", "template")(e.tagName()))
        .map(e =>
          Violation("lists-contain-list-items", s"a <${e.parent().tagName()}> has a <${e.tagName()}> child")
            .withHint("only <li> may be a direct child, or the list loses its length and position announcements")
        )
    },
    Rule("no-presentational-markup", "meaning is carried by markup, not by looks", severity = Warning) { page =>
      page.document
        .select("b, i, u, big, center, font, tt")
        .asScala
        .toList
        .map(e =>
          Violation("no-presentational-markup", s"a <${e.tagName()}> carries emphasis by appearance alone").warn
            .withHint("<strong> and <em> say what is meant; <b> and <i> only say how it looks")
        )
    },
    Rule("no-br-for-layout", "spacing comes from styling, not from line breaks", severity = Warning) { page =>
      page.document
        .select("br + br")
        .asScala
        .toList
        .map(_ =>
          Violation("no-br-for-layout", "consecutive <br> elements are being used for spacing").warn
            .withHint("a screen reader announces nothing for them; use separate elements and styling")
        )
    }
  )

}
