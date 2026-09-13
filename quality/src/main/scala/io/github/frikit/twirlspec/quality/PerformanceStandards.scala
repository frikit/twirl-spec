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

package io.github.frikit.twirlspec.quality

import io.github.frikit.twirlspec.standards._

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** Markup that makes a page slower or shakier than it needs to be.
  *
  * Everything here is a warning. None of it is wrong, and a template can have a
  * good reason for any of it; they are worth seeing rather than worth failing.
  */
object PerformanceStandards extends RuleSet {

  /** Beyond this, an inline data URI or style block is worth extracting. */
  private val InlineBudgetBytes = 10000

  lazy val all: Seq[Rule] = Seq(
    Rule(
      "images-have-dimensions",
      "images reserve their space before they load",
      severity = Warning
    ) { page =>
      page.images.elements
        .filterNot(e => e.hasAttr("width") && e.hasAttr("height"))
        .map(e =>
          Violation(
            "images-have-dimensions",
            "an image declares no width and height",
            actual = Some(
              Option(e.attr("src")).filter(_.nonEmpty).getOrElse("(no src)")
            )
          ).warn.withHint(
            "without them the page reflows as the image arrives, moving what someone is reading"
          )
        )
    },
    Rule(
      "scripts-are-deferred",
      "scripts in the head do not block rendering",
      severity = Warning
    ) { page =>
      page.document
        .select("head script[src]")
        .asScala
        .toList
        .filterNot(e => e.hasAttr("defer") || e.hasAttr("async") || e.attr("type") == "module")
        .map(e =>
          Violation(
            "scripts-are-deferred",
            "a script in the head blocks rendering",
            actual = Some(e.attr("src"))
          ).warn
            .withHint("add defer, or move it to the end of the body")
        )
    },
    Rule(
      "no-oversized-data-uri",
      "large assets are files, not attributes",
      severity = Warning
    ) { page =>
      page.document
        .select("[src^=data:], [href^=data:]")
        .asScala
        .toList
        .map(e => (e, Seq(e.attr("src"), e.attr("href")).map(_.length).max))
        .filter(_._2 > InlineBudgetBytes)
        .map { case (e, size) =>
          Violation(
            "no-oversized-data-uri",
            s"a <${e.tagName()}> inlines ${size / 1000}kB as a data URI",
            expected = Some(s"under ${InlineBudgetBytes / 1000}kB")
          ).warn.withHint(
            "an inlined asset cannot be cached separately and is paid for on every page load"
          )
        }
    },
    Rule(
      "no-large-inline-style",
      "styling lives in a stylesheet",
      severity = Warning
    ) { page =>
      page.document
        .select("style")
        .asScala
        .toList
        .filter(_.data().length > InlineBudgetBytes)
        .map(e =>
          Violation(
            "no-large-inline-style",
            s"an inline <style> block is ${e.data().length / 1000}kB",
            expected = Some(s"under ${InlineBudgetBytes / 1000}kB")
          ).warn.withHint("it is re-sent with every page and cannot be cached")
        )
    }
  )

}
