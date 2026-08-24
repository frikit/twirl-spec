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
package io.github.frikit.twirlspec.aria

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.standards.Rule.Warning
import io.github.frikit.twirlspec.standards.{Rule, RuleSet}
import org.jsoup.nodes.Element

import scala.jdk.CollectionConverters._

/** The checks an automated accessibility tool makes that need no browser.
  *
  * Roughly the static half of what axe-core reports: whether an ARIA name is
  * real, whether its value is one the specification allows, and whether roles
  * that only mean something together actually appear together. None of it
  * depends on layout or paint, so it runs against the parsed page in
  * microseconds rather than against a headless Chrome.
  */
object AriaStandards extends RuleSet {

  def all: Seq[Rule] = rules

  private def rules: Seq[Rule] = Seq(
    Rule("aria-attr-is-real", "every aria- attribute is one the specification defines") { page =>
      elementsWith(page, _.attributes.asScala.exists(a => a.getKey.startsWith("aria-"))).flatMap { e =>
        e.attributes.asScala
          .map(_.getKey)
          .filter(k => k.startsWith("aria-") && !AriaVocabulary.attributes.contains(k))
          .map { unknown =>
            Violation("aria-attr-is-real", s"$unknown is not an ARIA attribute", actual = Some(unknown))
              .at(describe(e))
              .withHint("assistive technology ignores an attribute it does not recognise")
          }
          .toSeq
      }
    },
    Rule("aria-attr-value-is-allowed", "an aria- attribute taking a fixed set of values carries one of them") { page =>
      elementsWith(page, _ => true).flatMap { e =>
        AriaVocabulary.tokenValues.toSeq.sortBy(_._1).flatMap { case (attr, allowed) =>
          val value = e.attr(attr)
          if (e.hasAttr(attr) && !allowed.contains(value.toLowerCase.trim))
            Seq(
              Violation(
                "aria-attr-value-is-allowed",
                s"""$attr="$value" is not a value the specification allows""",
                expected = Some(allowed.toList.sorted.mkString(", ")),
                actual = Some(value)
              ).at(describe(e))
            )
          else Nil
        }
      }
    },
    Rule("aria-role-is-real", "every role is one the specification defines, and not an abstract one") { page =>
      elementsWith(page, _.hasAttr("role")).flatMap { e =>
        e.attr("role").split("\\s+").filter(_.nonEmpty).toSeq.flatMap { role =>
          if (AriaVocabulary.roles.contains(role.toLowerCase)) Nil
          else
            Seq(
              Violation("aria-role-is-real", s"$role is not a role that may appear in markup", actual = Some(role))
                .at(describe(e))
                .withHint("abstract roles exist to organise the specification and cannot be used directly")
            )
        }
      }
    },
    Rule("aria-required-attr", "a role that depends on state declares it") { page =>
      rolesOn(page).flatMap { case (e, role) =>
        AriaVocabulary.requiredAttributes.getOrElse(role, Set.empty).toList.sorted.collect {
          case attr if !e.hasAttr(attr) =>
            Violation("aria-required-attr", s"""role="$role" needs $attr""", expected = Some(attr))
              .at(describe(e))
              .withHint("without it the control announces no state at all")
        }
      }
    },
    Rule("aria-required-parent", "a role that only means something inside another sits inside one") { page =>
      rolesOn(page).flatMap { case (e, role) =>
        AriaVocabulary.requiredParents.get(role).toSeq.flatMap { allowed =>
          if (ancestorRoles(e).exists(allowed.contains)) Nil
          else
            Seq(
              Violation(
                "aria-required-parent",
                s"""role="$role" is not inside ${allowed.toList.sorted.mkString(" or ")}""",
                expected = Some(allowed.toList.sorted.mkString(", ")),
                actual = Some(ancestorRoles(e).headOption.getOrElse("no role"))
              ).at(describe(e))
            )
        }
      }
    },
    Rule("aria-required-children", "a role that must contain something is not empty") { page =>
      rolesOn(page).flatMap { case (e, role) =>
        AriaVocabulary.requiredChildren.get(role).toSeq.flatMap { allowed =>
          val found = e.getAllElements.asScala.filterNot(_ eq e).flatMap(roleOf)
          if (found.exists(allowed.contains)) Nil
          else
            Seq(
              Violation(
                "aria-required-children",
                s"""role="$role" contains none of the roles it is defined by""",
                expected = Some(allowed.toList.sorted.mkString(", ")),
                actual = Some(if (found.isEmpty) "nothing with a role" else found.toList.distinct.mkString(", "))
              ).at(describe(e))
            )
        }
      }
    },
    Rule("aria-hidden-not-on-body", "the whole page is not hidden from assistive technology") { page =>
      page.document.select("body[aria-hidden=true], html[aria-hidden=true]").asScala.toSeq.map { e =>
        Violation("aria-hidden-not-on-body", s"${e.tagName} carries aria-hidden=true, hiding the entire page")
          .withHint("nothing on the page reaches a screen reader")
      }
    },
    Rule("no-role-conflict", "an element made presentational is not also announced") { page =>
      page.document.select("[role=presentation], [role=none]").asScala.toSeq.flatMap { e =>
        val labelled  = e.hasAttr("aria-label") || e.hasAttr("aria-labelledby")
        val focusable = e.hasAttr("tabindex") && e.attr("tabindex") != "-1"
        if (labelled || focusable)
          Seq(
            Violation(
              "no-role-conflict",
              s"""role="${e.attr("role")}" removes this from the accessibility tree, but it is """ +
                (if (labelled) "given a name" else "focusable")
            ).at(describe(e)).withHint("the role is ignored, so the element is announced anyway")
          )
        else Nil
      }
    },
    Rule("accesskey-unique", "no two elements answer to the same access key") { page =>
      page.document
        .select("[accesskey]")
        .asScala
        .toSeq
        .groupBy(_.attr("accesskey").toLowerCase)
        .toSeq
        .sortBy(_._1)
        .collect {
          case (key, elements) if key.nonEmpty && elements.size > 1 =>
            Violation(
              "accesskey-unique",
              s"""accesskey="$key" is on ${elements.size} elements""",
              actual = Some(elements.map(describe).mkString(", "))
            )
        }
    },
    Rule("autocomplete-is-valid", "an autocomplete attribute uses tokens the specification defines") { page =>
      page.document.select("[autocomplete]").asScala.toSeq.flatMap { e =>
        val raw    = e.attr("autocomplete").toLowerCase.trim
        val tokens = raw.split("\\s+").filter(_.nonEmpty).toList
        val body   = tokens.filterNot(AriaVocabulary.autocompleteModifiers.contains)
        val ok     = raw.isEmpty || body.nonEmpty && body.forall(AriaVocabulary.autocompleteTokens.contains)
        if (ok) Nil
        else
          Seq(
            Violation(
              "autocomplete-is-valid",
              s"""autocomplete="$raw" is not made of tokens the specification defines""",
              actual = Some(raw)
            ).at(describe(e)).withHint("WCAG 1.3.5 — a browser can only fill a field it recognises")
          )
      }
    },
    Rule("no-meta-refresh", "the page does not redirect or refresh itself on a timer") { page =>
      page.document.select("meta[http-equiv=refresh]").asScala.toSeq.map { e =>
        Violation("no-meta-refresh", s"""the page refreshes itself after ${e.attr("content")}""")
          .at("meta[http-equiv=refresh]")
          .withHint("WCAG 2.2.1 — a reader cannot ask for more time")
      }
    },
    Rule("no-deprecated-effects", "nothing on the page blinks or scrolls by itself") { page =>
      page.document.select("blink, marquee").asScala.toSeq.map { e =>
        Violation("no-deprecated-effects", s"<${e.tagName}> moves without being asked to")
          .withHint("WCAG 2.2.2 — movement must be stoppable")
      }
    },
    Rule("embedded-content-has-name", "an object, embedded image or svg carries a name", severity = Warning) { page =>
      page.document.select("object, input[type=image], svg[role=img]").asScala.toSeq.flatMap { e =>
        val named =
          e.hasAttr("aria-label") || e.hasAttr("aria-labelledby") || e.hasAttr("title") ||
            e.attr("alt").trim.nonEmpty || e.select("> title").asScala.exists(_.text().trim.nonEmpty)
        if (named) Nil
        else
          Seq(
            Violation("embedded-content-has-name", s"<${e.tagName}> has nothing to announce it by")
              .at(describe(e))
              .withHint("WCAG 1.1.1 — give it alt, aria-label, or a <title> child")
          )
      }
    },
    Rule("table-headers-resolve", "every headers attribute points at a header on the same table") { page =>
      page.document.select("table").asScala.toSeq.flatMap { table =>
        val ids = table.select("th[id]").asScala.map(_.id()).toSet
        table.select("td[headers], th[headers]").asScala.toSeq.flatMap { cell =>
          cell.attr("headers").split("\\s+").filter(_.nonEmpty).toSeq.collect {
            case target if !ids.contains(target) =>
              Violation(
                "table-headers-resolve",
                s"headers names $target, but no th on this table has that id",
                expected = Some(if (ids.isEmpty) "a th with an id" else ids.toList.sorted.mkString(", ")),
                actual = Some(target)
              ).at(describe(cell))
          }
        }
      }
    },
    Rule("definition-list-structure", "a definition list contains only terms and descriptions") { page =>
      page.document.select("dl").asScala.toSeq.flatMap { dl =>
        val stray = dl.children.asScala.filterNot(c => Set("dt", "dd", "div", "script", "template").contains(c.tagName))
        if (stray.isEmpty) Nil
        else
          Seq(
            Violation(
              "definition-list-structure",
              s"a <dl> contains ${stray.map(c => s"<${c.tagName}>").distinct.mkString(", ")}",
              expected = Some("dt and dd")
            ).at(describe(dl))
          )
      }
    },
    Rule("landmarks-are-distinguishable", "two landmarks of the same kind are told apart by name") { page =>
      val landmarkRoles =
        Set("banner", "complementary", "contentinfo", "form", "main", "navigation", "region", "search")
      page.document
        .select("[role], nav, aside, header, footer, main, form, section")
        .asScala
        .toSeq
        .flatMap(e => roleOf(e).filter(landmarkRoles.contains).map(r => (e, r)))
        .groupBy(_._2)
        .toSeq
        .sortBy(_._1)
        .flatMap { case (role, group) =>
          // A landmark is named by aria-label or aria-labelledby, never by what it contains.
          val names = group.map { case (e, _) => landmarkName(page, e) }
          if (group.size > 1 && names.distinct.size < group.size)
            Seq(
              Violation(
                "landmarks-are-distinguishable",
                s"${group.size} $role landmarks share a name",
                expected = Some("a distinct aria-label on each"),
                actual = Some(names.map(n => if (n.isEmpty) "(unnamed)" else n).mkString(", "))
              )
            )
          else Nil
        }
    }
  )

  private def landmarkName(page: Page, e: Element): String =
    if (e.hasAttr("aria-label")) e.attr("aria-label").trim
    else if (e.hasAttr("aria-labelledby"))
      e.attr("aria-labelledby")
        .split("\\s+")
        .filter(_.nonEmpty)
        .flatMap(id => Option(page.document.getElementById(id)).map(_.text().trim))
        .mkString(" ")
    else ""

  private def elementsWith(page: Page, p: Element => Boolean): Seq[Element] =
    page.document.getAllElements.asScala.toSeq.filter(p)

  private def rolesOn(page: Page): Seq[(Element, String)] =
    page.document.getAllElements.asScala.toSeq.flatMap(e => roleOf(e).map(r => (e, r)))

  /** The explicit role, or the one the tag implies for the landmarks that have one. */
  private def roleOf(e: Element): Option[String] = {
    val explicit = e.attr("role").split("\\s+").find(_.nonEmpty).map(_.toLowerCase)
    explicit.orElse(implicitRole(e))
  }

  /** A header or footer is the page's own only when nothing sectioning encloses it. */
  private val sectioning = "article, aside, main, nav, section"

  private def implicitRole(e: Element): Option[String] = e.tagName match {
    case "nav"       => Some("navigation")
    case "aside"     => Some("complementary")
    case "main"      => Some("main")
    case "form"      => Some("form")
    case "header"    => Option.when(e.closest(sectioning) == null)("banner")
    case "footer"    => Option.when(e.closest(sectioning) == null)("contentinfo")
    case "ul" | "ol" => Some("list")
    case "li"        => Some("listitem")
    case "table"     => Some("table")
    case "tr"        => Some("row")
    case "td"        => Some("gridcell")
    case "th"        => Some("columnheader")
    case _           => None
  }

  private def ancestorRoles(e: Element): Seq[String] =
    e.parents.asScala.toSeq.flatMap(roleOf)

  private def describe(e: Element): String = {
    val id      = e.id()
    val classes = e.className().split("\\s+").filter(_.nonEmpty).headOption.map("." + _).getOrElse("")
    if (id.nonEmpty) s"#$id" else s"<${e.tagName}>$classes"
  }

}
