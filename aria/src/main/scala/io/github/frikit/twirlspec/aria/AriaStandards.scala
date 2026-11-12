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

package io.github.frikit.twirlspec.aria

import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.page.{Attrs, Page, Tree}
import io.github.frikit.twirlspec.standards.Rule.Warning
import io.github.frikit.twirlspec.standards.{Rule, RuleSet}
import org.jsoup.nodes.{Element, TextNode}

import java.util.regex.Pattern
import java.util.Locale
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

  // In blocks because from Scala 3.3.9 the compiler measures no coverage in a
  // block over 3000 tree nodes, and build.sbt fails the build if it skips one.
  lazy val all: Seq[Rule] = rolesAndAriaRules ++ attributesAndStructureRules

  private def rolesAndAriaRules: Seq[Rule] = Seq(
    Rule(
      "aria-attr-is-real",
      "every aria- attribute is one the specification defines"
    ) { page =>
      elementsWith(
        page,
        _.attributes.asScala.exists(a => a.getKey.startsWith("aria-"))
      ).flatMap { e =>
        e.attributes.asScala
          .map(_.getKey)
          .filter(k =>
            k.startsWith("aria-") && !AriaVocabulary.attributes.contains(k)
          )
          .map { unknown =>
            Violation(
              "aria-attr-is-real",
              s"$unknown is not an ARIA attribute",
              actual = Some(unknown)
            )
              .at(describe(e))
              .withHint(
                "assistive technology ignores an attribute it does not recognise"
              )
          }
          .toSeq
      }
    },
    Rule(
      "aria-attr-value-is-allowed",
      "an aria- attribute taking a fixed set of values carries one of them"
    ) { page =>
      val anywhere = elementsWith(page, _ => true).flatMap { e =>
        AriaVocabulary.tokenValues.toSeq.sortBy(_._1).flatMap {
          case (attr, allowed) =>
            val value = e.attr(attr)
            if (
              e.hasAttr(attr) && !allowed
                .contains(value.toLowerCase(Locale.ROOT).trim)
            )
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
      val onItsRole = rolesOn(page).collect {
        case (e, role)
            if AriaVocabulary.noMixedState.contains(role) &&
              e.attr("aria-checked").trim.equalsIgnoreCase("mixed") =>
          Violation(
            "aria-attr-value-is-allowed",
            s"""aria-checked="mixed" means nothing on role="$role", which reads it as false""",
            actual = Some(e.attr("aria-checked"))
          ).at(describe(e))
      }
      anywhere ++ onItsRole
    },
    Rule(
      "aria-role-is-real",
      "every role is one the specification defines, and not an abstract one"
    ) { page =>
      elementsWith(page, _.hasAttr("role")).flatMap { e =>
        e.attr("role").split("\\s+").filter(_.nonEmpty).toSeq.flatMap { role =>
          if (AriaVocabulary.roles.contains(role.toLowerCase(Locale.ROOT))) Nil
          else
            Seq(
              Violation(
                "aria-role-is-real",
                s"$role is not a role that may appear in markup",
                actual = Some(role)
              )
                .at(describe(e))
                .withHint(
                  "abstract roles exist to organise the specification and cannot be used directly"
                )
            )
        }
      }
    },
    Rule("aria-required-attr", "a role that depends on state declares it") {
      page =>
        rolesOn(page).flatMap { case (e, role) =>
          val whenFocusable =
            if (focusable(e) && !(e.is(Control) && page.isDisabled(e)))
              AriaVocabulary.requiredWhenFocusable.getOrElse(role, Set.empty)
            else Set.empty[String]
          val whenExpanded =
            if (Attrs.is(e, "aria-expanded", "true"))
              AriaVocabulary.requiredWhenExpanded.getOrElse(role, Set.empty)
            else Set.empty[String]
          (AriaVocabulary.requiredAttributes.getOrElse(role, Set.empty)
            ++ whenFocusable ++ whenExpanded).toList.sorted
            .collect {
              // a value of nothing but spaces says nothing, nor names anything
              case attr
                  if e.attr(attr).trim.isEmpty &&
                    !NativeState.get(role).exists(e.is) =>
                Violation(
                  "aria-required-attr",
                  s"""role="$role" needs $attr""",
                  expected = Some(attr)
                )
                  .at(describe(e))
                  .withHint("without it the control announces no state at all")
            }
        }
    },
    Rule(
      "aria-required-parent",
      "a role that only means something inside another sits inside one"
    ) { page =>
      rolesOn(page).flatMap { case (e, role) =>
        AriaVocabulary.requiredParents.get(role).toSeq.flatMap { allowed =>
          val owners = ownersOf(page, e)
          val context = owners.flatMap(roleOf) ++ owners.flatMap(ancestorRoles)
          if ((ancestorRoles(e) ++ context).exists(allowed.contains)) Nil
          else
            Seq(
              Violation(
                "aria-required-parent",
                s"""role="$role" is not inside ${allowed.toList.sorted
                    .mkString(" or ")}""",
                expected = Some(allowed.toList.sorted.mkString(", ")),
                actual = Some(ancestorRoles(e).headOption.getOrElse("no role"))
              ).at(describe(e))
            )
        }
      }
    },
    Rule(
      "aria-required-children",
      "a role that must contain something is not empty"
    ) { page =>
      rolesOn(page).flatMap { case (e, role) =>
        AriaVocabulary.requiredChildren
          .get(role)
          // HTML lets a native row be empty; one given role="row" is asked
          .filterNot(_ => role == "row" && !e.hasAttr("role"))
          .toSeq
          .flatMap { allowed =>
            val contained =
              e.getAllElements.asScala.filterNot(_ eq e).flatMap(childRole)
            val owned =
              ownedBy(e).flatMap(Tree.withDescendants).flatMap(childRole)
            val found = contained ++ owned
            // ARIA lets what is still loading, marked busy, go without them for now.
            val busy = (e +: e.parents.asScala.toSeq).exists(
              _.attr("aria-busy").trim.equalsIgnoreCase("true")
            )
            if (busy || found.exists(allowed.contains)) Nil
            else
              Seq(
                Violation(
                  "aria-required-children",
                  s"""role="$role" contains none of the roles it is defined by""",
                  expected = Some(allowed.toList.sorted.mkString(", ")),
                  actual = Some(
                    if (found.isEmpty) "nothing with a role"
                    else found.toList.distinct.mkString(", ")
                  )
                ).at(describe(e))
              )
          }
      }
    },
    Rule(
      "aria-hidden-not-on-body",
      "the whole page is not hidden from assistive technology"
    ) { page =>
      page.document
        .select("body[aria-hidden], html[aria-hidden]")
        .asScala
        .toSeq
        .filter(Attrs.is(_, "aria-hidden", "true"))
        .map { e =>
          Violation(
            "aria-hidden-not-on-body",
            s"${e.tagName} carries aria-hidden=true, hiding the entire page"
          )
            .withHint("nothing on the page reaches a screen reader")
        }
    },
    Rule(
      "no-role-conflict",
      "an element made presentational is not also announced"
    ) { page =>
      page.document
        .select("[role]")
        .asScala
        .toSeq
        .filter(e =>
          Attrs.is(e, "role", "presentation") || Attrs.is(e, "role", "none")
        )
        .flatMap { e =>
          val labelled = e.hasAttr("aria-label") || e.hasAttr("aria-labelledby")
          val focusable = e.hasAttr("tabindex") && e.attr("tabindex") != "-1"
          if (labelled || focusable)
            Seq(
              Violation(
                "no-role-conflict",
                s"""role="${e.attr(
                    "role"
                  )}" removes this from the accessibility tree, but it is """ +
                  (if (labelled) "given a name" else "focusable")
              ).at(describe(e))
                .withHint(
                  "the role is ignored, so the element is announced anyway"
                )
            )
          else Nil
        }
    },
    Rule("accesskey-unique", "no two elements answer to the same access key") {
      page =>
        page.document
          .select("[accesskey]")
          .asScala
          .toSeq
          .groupBy(_.attr("accesskey").toLowerCase(Locale.ROOT))
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
    }
  )

  private def attributesAndStructureRules: Seq[Rule] = Seq(
    Rule(
      "autocomplete-is-valid",
      "an autocomplete attribute uses tokens the specification defines"
    ) { page =>
      page.document.select("[autocomplete]").asScala.toSeq.flatMap { e =>
        val raw = e.attr("autocomplete").toLowerCase(Locale.ROOT).trim
        if (raw.isEmpty || autocompleteIsValid(e, raw)) Nil
        else
          Seq(
            Violation(
              "autocomplete-is-valid",
              s"""autocomplete="$raw" is not made of tokens the specification defines""",
              actual = Some(raw)
            ).at(describe(e))
              .withHint(
                "WCAG 1.3.5 — a browser can only fill a field it recognises"
              )
          )
      }
    },
    Rule(
      "no-meta-refresh",
      "the page does not redirect or refresh itself on a timer"
    ) { page =>
      page.document.select("meta[http-equiv=refresh]").asScala.toSeq.map { e =>
        Violation(
          "no-meta-refresh",
          s"""the page refreshes itself after ${e.attr("content")}"""
        )
          .at("meta[http-equiv=refresh]")
          .withHint("WCAG 2.2.1 — a reader cannot ask for more time")
      }
    },
    Rule(
      "no-deprecated-effects",
      "nothing on the page blinks or scrolls by itself"
    ) { page =>
      page.document.select("blink, marquee").asScala.toSeq.map { e =>
        Violation(
          "no-deprecated-effects",
          s"<${e.tagName}> moves without being asked to"
        )
          .withHint("WCAG 2.2.2 — movement must be stoppable")
      }
    },
    Rule(
      "embedded-content-has-name",
      "an object, embedded image or svg carries a name",
      severity = Warning
    ) { page =>
      page.document
        .select("object, input[type=image], svg[role]")
        .asScala
        .toSeq
        .filter(e => !e.is("svg") || Attrs.is(e, "role", "img"))
        .flatMap { e =>
          val named =
            e.hasAttr("aria-label") || e.hasAttr("aria-labelledby") || e
              .hasAttr("title") ||
              e.attr("alt").trim.nonEmpty || e
                .select("> title")
                .asScala
                .exists(_.text().trim.nonEmpty)
          if (named) Nil
          else
            Seq(
              Violation(
                "embedded-content-has-name",
                s"<${e.tagName}> has nothing to announce it by"
              )
                .at(describe(e))
                .withHint(
                  "WCAG 1.1.1 — give it alt, aria-label, or a <title> child"
                )
            )
        }
    },
    Rule(
      "table-headers-resolve",
      "every headers attribute points at a header on the same table"
    ) { page =>
      page.document.select("table").asScala.toSeq.flatMap { table =>
        // the table's own headers and cells, not those of a table inside it
        def own(selector: String) =
          table
            .select(selector)
            .asScala
            .toSeq
            .filter(_.closest("table") eq table)
        val ids = own("th[id]").map(_.id()).toSet
        own("td[headers], th[headers]").flatMap { cell =>
          cell.attr("headers").split("\\s+").filter(_.nonEmpty).toSeq.collect {
            case target if !ids.contains(target) =>
              Violation(
                "table-headers-resolve",
                s"headers names $target, but no th on this table has that id",
                expected = Some(
                  if (ids.isEmpty) "a th with an id"
                  else ids.toList.sorted.mkString(", ")
                ),
                actual = Some(target)
              ).at(describe(cell))
          }
        }
      }
    },
    Rule(
      "definition-list-structure",
      "a definition list contains only terms and descriptions"
    ) { page =>
      page.document.select("dl").asScala.toSeq.flatMap { dl =>
        val stray = dl.children.asScala.filterNot(c =>
          Set("dt", "dd", "div", "script", "template").contains(c.tagName)
        )
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
    Rule(
      "landmarks-are-distinguishable",
      "two landmarks of the same kind are told apart by name, unless they are exact copies, holding no form control, under one name"
    ) { page =>
      val landmarkRoles =
        Set(
          "banner",
          "complementary",
          "contentinfo",
          "form",
          "main",
          "navigation",
          "region",
          "search"
        )
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
          val names = group.map { case (e, _) => landmarkName(e) }
          // Copies of one landmark, such as pagination above and below a list, may share a
          // name: the W3C's ARIA practices advise one name where each holds the same.
          val clash = group.map(_._1).zip(names).groupBy(_._2).exists {
            case (name, same) =>
              def copies = same.forall(c => holdsNoControl(c._1)) &&
                same.map(c => shape(c._1)).distinct.size == 1
              same.size > 1 && (name.isEmpty || !copies)
          }
          if (clash)
            Seq(
              Violation(
                "landmarks-are-distinguishable",
                s"${group.size} $role landmarks share a name",
                expected = Some(
                  "a distinct aria-label on each, or one shared by exact copies"
                ),
                actual = Some(
                  names
                    .map(n => if (n.isEmpty) "(unnamed)" else n)
                    .mkString(", ")
                )
              )
            )
          else Nil
        }
    }
  )

  /** Whether a copy of this landmark can be told by its HTML alone. A form
    * control takes meaning from outside it, from a label around it, a disabled
    * fieldset or the form it is sent with, so a landmark holding one is never
    * taken for a copy.
    */
  private def holdsNoControl(landmark: Element): Boolean =
    !Tree
      .withDescendants(landmark)
      .exists(
        _.is(
          "button, fieldset, input, meter, object, output, progress, select, textarea"
        )
      )

  /** An element as a structure, a step at a time, so that no two different ones
    * can come out the same: each element and where it ends, its attributes, and
    * every other node as it is. Two landmarks are copies when theirs are equal,
    * as pagination a component renders twice is; anything else that differs,
    * down to an id, a space or a comment, sets them apart.
    */
  private def shape(e: Element): Seq[(String, String, String)] =
    (("element", e.tagName, "") +: e.attributes.asList.asScala.toSeq
      .map(a => ("attribute", a.getKey, a.getValue))
      .sorted) ++ e.childNodes.asScala.toSeq.flatMap {
      case child: Element => shape(child)
      case text: TextNode => Seq(("text", text.getWholeText, ""))
      case other          => Seq((other.nodeName, other.outerHtml, ""))
    } :+ ("end", e.tagName, "")

  /** The autofill grammar: `on` or `off` alone; or an optional section, an
    * optional `shipping` or `billing`, one field, which a contact kind such as
    * `home` may come before where the field is a way of contacting someone, and
    * an optional `webauthn`, on an input or a textarea. A hidden input cannot
    * say `on` or `off`, having no user to ask.
    */
  private def autocompleteIsValid(e: Element, raw: String): Boolean = {
    val tokens = raw.split("\\s+").toList
    val afterSection = tokens match {
      case section :: rest if section.startsWith("section-") => rest
      case all                                               => all
    }
    val afterAddress = afterSection match {
      case ("shipping" | "billing") :: rest => rest
      case all                              => all
    }
    def ends(rest: List[String]) =
      rest.isEmpty || rest == List("webauthn") && e.is("input, textarea")
    def isField(name: String) =
      AriaVocabulary.autocompleteTokens(name) && name != "on" && name != "off"
    afterAddress match {
      case List("on" | "off") =>
        tokens.size == 1 && !e.is("input[type=hidden]")
      case kind :: field :: rest
          if AriaVocabulary.autocompleteContactKinds(kind) =>
        AriaVocabulary.isContactField(field) && ends(rest)
      case field :: rest => isField(field) && ends(rest)
      case Nil           => false
    }
  }

  /** HTML that supplies a role's required state itself: a heading's level is in
    * its tag, and a native checkbox has a checked state whatever its
    * attributes.
    */
  private val NativeState = Map(
    "heading" -> "h1, h2, h3, h4, h5, h6",
    "checkbox" -> "input[type=checkbox]",
    "switch" -> "input[type=checkbox]",
    "radio" -> "input[type=radio]",
    "option" -> "option",
    "slider" -> "input[type=range]",
    "meter" -> "meter",
    "combobox" -> "select"
  )

  /** What can take focus, by a script if not by the tab key, unless disabled:
    * these, and what is editable.
    */
  private val Focusable =
    "a[href], button, input, select, textarea, [tabindex]"

  private val Editable = Pattern.compile("(?i)^(true|plaintext-only)?$")

  private def focusable(e: Element): Boolean =
    e.is(Focusable) || Attrs.matches(e, "contenteditable", Editable)

  /** What disabling takes the focus from. */
  private val Control = "button, input, select, textarea"

  /** What `aria-owns` makes this element's own, wherever it sits in the
    * document. Only the one step is followed, so a cycle cannot trap this.
    */
  private def ownedBy(e: Element): Seq[Element] =
    e.attr("aria-owns")
      .split("\\s+")
      .filter(_.nonEmpty)
      .toSeq
      .flatMap(id => Option(e.ownerDocument().getElementById(id)))

  /** The elements whose `aria-owns` names this one or anything it sits in,
    * since what an owned element holds is owned too.
    */
  private def ownersOf(page: Page, e: Element): Seq[Element] = {
    val ids = (e +: e.parents.asScala.toSeq).map(_.id()).filter(_.nonEmpty)
    page.document
      .select("[aria-owns]")
      .asScala
      .toSeq
      .filter(_.attr("aria-owns").split("\\s+").exists(ids.contains))
  }

  /** What a landmark is called, from the first of these that says anything. */
  private def landmarkName(e: Element): String = {
    val labelledBy = e
      .attr("aria-labelledby")
      .split("\\s+")
      .filter(_.nonEmpty)
      .flatMap(id =>
        Option(e.ownerDocument().getElementById(id)).map(_.text().trim)
      )
      .mkString(" ")
      .trim
    Seq(labelledBy, e.attr("aria-label").trim, e.attr("title").trim)
      .find(_.nonEmpty)
      .getOrElse("")
  }

  private def elementsWith(page: Page, p: Element => Boolean): Seq[Element] =
    page.document.getAllElements.asScala.toSeq.filter(p)

  private def rolesOn(page: Page): Seq[(Element, String)] =
    page.document.getAllElements.asScala.toSeq.flatMap(e =>
      roleOf(e).map(r => (e, r))
    )

  /** The explicit role, or the one the tag implies for the landmarks that have
    * one.
    */
  private def roleOf(e: Element): Option[String] = {
    val explicit =
      e.attr("role")
        .split("\\s+")
        .find(_.nonEmpty)
        .map(_.toLowerCase(Locale.ROOT))
        // A browser ignores presentation on what it has to expose anyway.
        .filterNot(role => Presentational(role) && exposed(e))
    explicit.orElse(implicitRole(e))
  }

  /** A role as a container counts it among its children: what [[roleOf]] gives,
    * or the role a native option, option group or radio carries, which `roleOf`
    * leaves out so that no rule asks them for state their HTML gives.
    */
  private def childRole(e: Element): Option[String] =
    roleOf(e).orElse(e.tagName match {
      case "option" if e.closest("select, datalist") != null => Some("option")
      case "optgroup" if e.closest("select") != null         => Some("group")
      case "input" if Attrs.is(e, "type", "radio")           => Some("radio")
      // a native table's row groups; its rows and cells have their roles already
      case "thead" | "tbody" | "tfoot" => Some("rowgroup")
      case _                           => None
    })

  private val Presentational = Set("presentation", "none")

  /** What a browser exposes with its own role whatever it is told, as ARIA's
    * conflict resolution says: what can take focus, and what carries a global
    * state or property. `aria-hidden` counts unless it is true, which hides the
    * element altogether.
    */
  private def exposed(e: Element): Boolean =
    focusable(e) || e.attributes.asScala.exists { a =>
      val key = a.getKey.toLowerCase(Locale.ROOT)
      AriaVocabulary.globalAttributes(key) ||
      key == "aria-hidden" && !a.getValue.trim.equalsIgnoreCase("true")
    }

  /** A header or footer is the page's own only when nothing sectioning encloses
    * it.
    */
  private def sectioning(e: Element): Boolean =
    asideBelongsTo(e) || e.is("main") || Attrs.is(e, "role", "main")

  /** An aside is the page's own in `<main>` too, and only a name makes one
    * anywhere else a landmark.
    */
  private def asideBelongsTo(e: Element): Boolean =
    e.is("article, aside, nav, section") ||
      Seq("article", "complementary", "navigation", "region").exists(
        Attrs.is(e, "role", _)
      )

  private def implicitRole(e: Element): Option[String] = e.tagName match {
    case "nav"   => Some("navigation")
    case "aside" =>
      Option.when(
        landmarkName(e).nonEmpty ||
          Tree.closest(e.parent())(asideBelongsTo).isEmpty
      )("complementary")
    case "main" => Some("main")
    // A form is a landmark only once it has a name to be told apart by.
    case "form"   => Option.when(landmarkName(e).nonEmpty)("form")
    case "header" => Option.when(Tree.closest(e)(sectioning).isEmpty)("banner")
    case "footer" =>
      Option.when(Tree.closest(e)(sectioning).isEmpty)("contentinfo")
    case "ul" | "ol" => Some("list")
    case "li"        => Option.unless(inPresentational(e, "ul, ol"))("listitem")
    case "table"     => Some("table")
    case "tr"        => Option.unless(inPresentational(e, "table"))("row")
    case "td"        => Option.unless(inPresentational(e, "table"))("gridcell")
    case "th" => Option.unless(inPresentational(e, "table"))("columnheader")
    case _    => None
  }

  /** Whether the list or table an item, row or cell belongs to was made
    * presentational, which takes their roles with it, unless the item is one a
    * browser exposes anyway.
    */
  private def inPresentational(e: Element, owner: String): Boolean =
    !exposed(e) && Option(e.closest(owner))
      .flatMap(roleOf)
      .exists(Presentational)

  private def ancestorRoles(e: Element): Seq[String] =
    e.parents.asScala.toSeq.flatMap(roleOf)

  private def describe(e: Element): String = {
    val id = e.id()
    val classes = e
      .className()
      .split("\\s+")
      .filter(_.nonEmpty)
      .headOption
      .map("." + _)
      .getOrElse("")
    if (id.nonEmpty) s"#$id" else s"<${e.tagName}>$classes"
  }

}
