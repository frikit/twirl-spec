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

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.Text
import io.github.frikit.twirlspec.standards.Rule.Warning

import scala.jdk.CollectionConverters._

/** Markup that is a safety problem rather than an accessibility one. */
object SecurityStandards extends RuleSet {

  lazy val all: Seq[Rule] = Seq(
    Rule("no-password-in-get", "a password is never submitted in a URL") { page =>
      page.document
        .select("form[method=get]")
        .asScala
        .toList
        .filter(_.select("input[type=password]").asScala.nonEmpty)
        .map { form =>
          Violation(
            "no-password-in-get",
            "a password field sits in a form that submits by GET",
            expected = Some("""method="post""""),
            actual = Some(
              Option(form.attr("action"))
                .filter(_.nonEmpty)
                .getOrElse("(no action)")
            )
          ).withHint(
            "a GET puts the password in the URL, and so in history, logs and referrers"
          )
        }
    },
    Rule("no-javascript-href", "links do not carry javascript: URLs") { page =>
      page.document
        .select("a[href^=javascript:]")
        .asScala
        .toList
        .map(e =>
          Violation(
            "no-javascript-href",
            s"""a link has a javascript: URL: "${Text.preview(e.text(), 40)}""""
          )
            .withHint(
              "it breaks without JavaScript and is refused by a strict content security policy"
            )
        )
    },
    Rule(
      "target-blank-is-safe",
      "a link opening a new tab cannot reach back",
      severity = Warning
    ) { page =>
      page.links.elements
        .filter(_.attr("target") == "_blank")
        .filterNot(_.attr("rel").toLowerCase.split("\\s+").contains("noopener"))
        .map(e =>
          Violation(
            "target-blank-is-safe",
            s"""a link opens a new tab without rel="noopener": "${Text
                .preview(e.text(), 40)}"""",
            expected = Some("""rel="noopener"""")
          ).warn.withHint(
            "current browsers imply this for target=_blank; older ones let the new tab rewrite yours"
          )
        )
    }
  )

}
