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

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.standards.{Rule, RuleSet}

import scala.jdk.CollectionConverters._

/** Basic HTML validity, checked without a browser or a 20-dependency validator.
  *
  * `well-formed-html` reports what the parser had to repair, which catches a
  * lot but describes the symptom rather than the cause. These read the markup
  * as written and name the element and line responsible.
  *
  * @param attributePrefixes
  *   prefixes a project uses for its own attributes, beyond `data-` and `aria-`
  */
class HtmlStandards(attributePrefixes: Set[String]) extends RuleSet {

  lazy val all: Seq[Rule] = Seq(
    Rule(
      "tags-are-balanced",
      "every element that is opened is closed",
      pageLevel = true
    ) { page =>
      TagBalance.check(page.source).take(10).map { p =>
        Violation("tags-are-balanced", p.message)
          .at(s"line ${p.line}")
          .withHint(
            "a browser will guess where the element ends, and guess differently from you"
          )
      }
    },
    Rule(
      "known-elements",
      "every element is one the HTML specification defines"
    ) { page =>
      page.document.getAllElements.asScala.toSeq
        .map(_.tagName.toLowerCase)
        .distinct
        .sorted
        .filterNot(
          _.startsWith("#")
        ) // jsoup's document root is a pseudo-element, not markup
        .filterNot(HtmlVocabulary.isKnownElement)
        .map { name =>
          Violation(
            "known-elements",
            s"<$name> is not an HTML element",
            actual = Some(name)
          )
            .withHint(
              "a browser renders an unknown element as an inline span with no meaning"
            )
        }
    },
    Rule(
      "known-attributes",
      "every attribute is one the HTML specification defines"
    ) { page =>
      page.document.getAllElements.asScala.toSeq
        .flatMap(e => e.attributes.asScala.map(a => (e.tagName.toLowerCase, a.getKey.toLowerCase)))
        .distinct
        .sortBy(_._2)
        .filterNot { case (_, a) =>
          HtmlVocabulary.isKnownAttribute(a, attributePrefixes)
        }
        .map { case (tag, a) =>
          Violation(
            "known-attributes",
            s"$a is not an HTML attribute",
            actual = Some(a)
          )
            .at(s"<$tag>")
            .withHint(
              "a typo here is silent: the browser keeps the attribute and ignores it"
            )
        }
    }
  )

}

/** The rules with no project-specific attribute prefixes. */
object HtmlStandards extends HtmlStandards(Set.empty)
