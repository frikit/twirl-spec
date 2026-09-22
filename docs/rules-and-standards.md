# Rules and standards

A rule is a check that runs over the whole page without the spec naming a
selector: every form control has an accessible name, no two elements share an
id, no message key reached the citizen unresolved. The README lists every rule
with its id and, for the accessibility rules, the WCAG success criterion it
enforces; those tables are generated from the rule sets and held to them by a
test, so they are current.

## Turning rules on

Each module ships a trait. Mix it in and every `display(...)` runs its rules:

| Trait | Module | Runs |
|---|---|---|
| `WcagChecks` | `twirl-spec-wcag` | `WcagStandards`, `TwirlStandards`, `SecurityStandards` |
| `GovukChecks` | `twirl-spec-govuk` | `GovukStandards` |
| `QualityChecks` | `twirl-spec-quality` | `SemanticStandards`, `PerformanceStandards`, `MetadataStandards` |
| `AriaChecks` | `twirl-spec-aria` | `AriaStandards` |
| `HtmlChecks` | `twirl-spec-html` | `HtmlStandards` |
| `AllChecks` | `twirl-spec-all` | all of the above, plus `CoverageChecks`, `I18nChecks` and `MessagesMatchers` |

The traits compose; `with WcagChecks with GovukChecks` runs both sets. They
all work by extending `standardsRules`, so a spec can see or replace the
active set:

```scala
standardsRules                                   // the rules every display runs
override def standardsRules = WcagStandards.conformingTo(Level.AA)   // exactly these instead
```

## Blocking rules and warnings

A rule is blocking unless it is marked a warning. Warnings are reported on a
green run too, through ScalaTest's alert mechanism, so a service can see it is
one fix away from being able to promote them:

```scala
override def failOnWarnings: Boolean = true      // warnings fail the page
override def reportWarnings: Boolean = false     // passing tests stay quiet
```

The two AAA accessibility rules, and the performance rules, ship as warnings
for that reason.

## Running rules alone, or without them

```scala
page must meetStandards                          // only the active rules
page must meetStandardsExcept("one-h1")          // the active rules, minus these
page must displayOnly(title("x.title"))          // your expectations, no rules
```

`meetStandardsExcept` is for a page that has to break a rule, or a legacy view
being brought up to standard; prefer fixing the page, because an exclusion
here hides the rule on every page it is applied to. `standardsExpectation` is
the active set as one expectation, for asserting on the result rather than
failing.

## Page-level rules and fragments

Jsoup wraps every fragment in `<html><head><body>`, so a component rendered
without a layout would otherwise fail every rule about titles and landmarks.
Rules that only make sense for a whole page are marked page-level and stay
silent unless the rendered markup starts with a doctype or `<html>`: `one-h1`,
`title-present`, `html-lang`, `main-landmark`, `single-main`, `zoom-not-blocked`,
`error-title-prefix`, `tags-are-balanced` and the metadata rules. A component
spec therefore gets the rules that apply to it and no noise from the rest.

## Selecting accessibility rules by conformance level

Every rule in `WcagStandards` that enforces a success criterion carries its
number, title, level and the WCAG version it first appeared in:

```scala
import io.github.frikit.twirlspec.standards.{Level, WcagVersion}
import io.github.frikit.twirlspec.wcag.WcagStandards

WcagStandards.conformingTo(Level.AA)                     // A and AA, all versions
WcagStandards.conformingTo(Level.AA, WcagVersion.V2_1)   // A and AA, up to WCAG 2.1
WcagStandards.atLevel(Level.AAA)                         // exactly AAA
WcagStandards.introducedIn(WcagVersion.V2_1)             // what 2.1 added
WcagStandards.conventions                                // the rules that are not WCAG at all
WcagStandards.criteria                                   // which criteria this set covers
```

Both selections are cumulative, because that is what they mean in WCAG. A green
run means these mistakes are absent; it is not a conformance claim, because a
static check over rendered markup cannot see colour contrast, focus order,
motion, or anything that depends on CSS or JavaScript.

## What each set is for

**`WcagStandards`** is accessibility, 21 rules each tied to a success
criterion plus `one-h1`, a structural convention almost everyone wants.

**`TwirlStandards`** is Play rendering mistakes: an unresolved message key
shown to a citizen, markup the parser had to repair, two controls submitting
under one name, and a Scala value such as `Some(...)` reaching the page.

**`SecurityStandards`** is ways a page can leak or be turned against its
reader: a password in a form that submits by GET — including one that names no
method, which submits by GET too — a `javascript:` link, and a new-tab link
carrying neither `rel="noopener"` nor `rel="noreferrer"`.

**`GovukStandards`** is the Design System's error conventions: the title
prefix, the visually hidden "Error:" on inline messages, the summary links
landing on their fields. They key off Design System markup, so they stay
silent on a page that does not use it.

**`AriaStandards`** is the static half of what an automated accessibility
tool reports: whether an `aria-` attribute or a role is real, whether its
value is allowed, whether roles that only mean something together appear
together. None of it needs a browser.

**`HtmlStandards`** is basic HTML validity read from the source rather than
the repaired tree, so an unclosed element is named with the line it opened
on, and unknown elements and attributes are caught. A project with its own
attribute prefixes beyond `data-` and `aria-` declares them:

```scala
override def attributePrefixes: Set[String] = Set("hx-")   // on HtmlChecks
```

**`SemanticStandards`**, **`PerformanceStandards`** and **`MetadataStandards`**
are markup that parses but says the wrong thing, page weight, and what a
browser tab or a search result makes of the page.

## Scanning templates without rendering them

`tags-are-balanced` also reads a Twirl template directly, with no application
and no render, so it can run over a whole codebase:

```scala
import io.github.frikit.twirlspec.html.TagBalance
import java.nio.file.{Files, Path}

val problems = TagBalance.checkTemplate(Files.readString(Path.of("app/views/page.scala.html")))
problems.foreach(p => println(s"line ${p.line}: ${p.message}"))
```

The Scala is taken out first, so `@if(page < total)` is a comparison and not
the start of an element. Two things it cannot know without evaluating the
template: markup that arrives from a helper, and an element opened in one
branch and closed in another.

## Writing your own rules

A rule is an id, a description, and a function from a page to the violations
found:

```scala
import io.github.frikit.twirlspec.expect.Violation
import io.github.frikit.twirlspec.standards.{Rule, RuleSet}

object HouseRules extends RuleSet {

  lazy val all: Seq[Rule] = Seq(
    Rule("footer-has-accessibility-link", "the footer links to the accessibility statement", pageLevel = true) { page =>
      if (page.links.attrs("href").exists(_.contains("/accessibility-statement"))) Nil
      else Seq(Violation("footer-has-accessibility-link", "no link to the accessibility statement").withHint("the layout's footer should carry it"))
    },
    Rule("no-inline-styles", "styling lives in the stylesheet", severity = Rule.Warning) { page =>
      page.css("[style]").elements.map(e => Violation("no-inline-styles", s"<${e.tagName}> carries a style attribute"))
    }
  )
}
```

`pageLevel = true` keeps a rule quiet on fragments. `severity = Rule.Warning`
reports without failing. A `criterion` ties a rule to a WCAG success criterion
so it takes part in `conformingTo`. Wire the set in the same way the library's
own traits do:

```scala
trait HouseChecks extends TwirlSpecDsl {
  override def standardsRules: Seq[Rule] = super.standardsRules ++ HouseRules.all
}
```

Violations carry `expected`, `actual`, `where` and a `hint`, and render in
the same shape as the library's own, so a house rule reads like the rest of
the output.
