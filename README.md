# twirl-spec

A ScalaTest toolkit for testing [Twirl](https://github.com/playframework/twirl) views.

```scala
class SignUpViewSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val view = inject[SignUpView]
  private val form = inject[SignUpFormProvider].apply()

  "SignUpView" should {
    "render the question" in {
      render(view(form)) must display(
        title("signUp.title"),
        heading("signUp.heading"),
        textInput("email").labelled("signUp.email").hinted("signUp.email.hint"),
        submitButton(),
        noErrors
      )
    }
  }
}
```

With the `twirl-spec-wcag` module mixed in, that block also runs 19
accessibility and rendering rules over the page. You do not list them, switch
them on, or maintain them.

[![CI](https://github.com/frikit/twirl-spec/actions/workflows/ci.yml/badge.svg)](https://github.com/frikit/twirl-spec/actions/workflows/ci.yml)
[![Scala 2.13 and 3](https://img.shields.io/badge/scala-2.13%20%7C%203.3-red)](build.sbt)
[![Apache 2.0](https://img.shields.io/badge/licence-Apache%202.0-blue)](LICENSE)

## Why

Testing a rendered template usually means one of two things: asserting on raw
HTML strings, which breaks whenever the markup moves, or hand-rolling a pile of
Jsoup selectors, which every project rewrites slightly differently and which
nobody enjoys maintaining.

`twirl-spec` gives you a page model with named accessors, an expectation DSL
that reads like the page, and failure messages that tell you what the template
actually rendered.

## Modules

Pick only what you need. The core carries no rules of its own, so it never
judges a page against a design system you are not using.

| Artifact | Depends on | What it adds |
|---|---|---|
| `twirl-spec-core` | — | Page model, expectation DSL, ScalaTest matchers, `Rule` infrastructure |
| `twirl-spec-wcag` | core | 19 rules: 15 tagged with a WCAG success criterion, 1 structural convention, 3 for Twirl rendering |
| `twirl-spec-govuk` | core, wcag | 5 GOV.UK Design System conventions |
| `twirl-spec-messages` | core | Message-file integrity checks |

```scala
libraryDependencies ++= Seq(
  "io.github.frikit" %% "twirl-spec-core"     % twirlSpecVersion % Test,
  "io.github.frikit" %% "twirl-spec-wcag"     % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-govuk"    % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-messages" % twirlSpecVersion % Test   // optional
)
```

Rule modules ship a trait that wires their rules into every `display(...)`. The
traits compose, so mixing in two runs both sets:

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec
  with WcagChecks     // accessibility + Twirl rendering rules
  with GovukChecks    // + GOV.UK Design System rules
```

Without a rule module, `display(...)` checks exactly what you asked it to and
nothing else.

## Compatibility

| twirl-spec | Play | Scala | Java | Twirl | ScalaTest |
|---|---|---|---|---|---|
| 0.1.x | 3.0.x | 2.13.18, 3.3.7 | 21+ | 2.0.x | 3.2.x |

**Scala.** Published for 2.13 and 3 from a single source tree. The 3.x build
targets 3.3 LTS, which is binary-compatible with every later 3.x release, so a
project on 3.4 through 3.7 uses the same artifact.

**Play.** `play`, `play-test` and `play-guice` are `Provided`: your project
supplies them, and this library never moves your Play version. Compiled against
the oldest Play in the supported range, so anything newer in the same major line
works. **Play 2.9 and earlier are not supported** — they predate the Pekko move
and are not tested here.

**Java.** Compiled with `-release 21`, so 21 is the floor. Later JDKs are fine.

**ScalaTest.** A normal dependency, not `Provided`, so the version resolves
upward against whatever your project already has.

Anything outside this table is untested rather than known-broken. If you get a
combination working, a note in an issue is welcome.

## Two ways in

**You already have a spec base.** Mix in `TwirlSpecDsl`. It declares no
implicits and builds no application, so it cannot clash with the `messages`,
`fakeRequest` or application you already have.

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with TwirlSpecDsl
```

**Greenfield.** Mix in `TwirlSpec` for the application, the implicits and
language switching:

```scala
class MyViewSpec extends AnyWordSpec with Matchers with TwirlSpec {
  private val view = inject[MyView]
}
```

## The DSL

Everything takes a **message key** by default, because that is what template
content usually is. Wrap a string in `literal("...")` when you mean exact words.

```scala
// framing
title("x.title"), exactTitle("Full - Service"), heading("x.heading"), subheading("x.section")
caption("x.caption"), serviceName(), backLink, backLink.to("/where"), noBackLink
languageToggle, phaseBanner, timeoutDialog, signOutLink

// form controls
textInput("email").labelled("x.email").hinted("x.hint").withValue("a@b.c")
                  .withAutocomplete("email").ofType("email")
textArea("notes"), dropdown("country").withOptionValues("GB", "FR")
radioGroup("value").legendIs("x.legend").withOptions("yes" -> "site.yes").selectedIs("yes")
checkboxGroup("colours"), dateInput("dob"), fileUpload("evidence")
submitButton(), button("save", "x.save"), formPostsTo("/submit"), hiddenInput("csrf", "…")

// errors
noErrors, errorTitlePrefix, errorSummaryTitle()
errorSummary("email" -> "x.error.email.required")
errorSummaryContaining("email", "x.error.email.required")
fieldError("email", "x.error.email.required")

// content
content("x.p1"), noContent("x.gone"), paragraph("x.p1"), bullets("x.b1", "x.b2")
link("x.link").to("/guidance"), warning("x.warning"), insetText("x.inset")
summaryList("x.name" -> "Ada"), summaryRow("x.name").withChangeLinkTo("/change")
tableHeaders("x.col1"), tableRow("Ada", "1815")

// structure
element("submit"), noElement("warning"), elementHasClass("tag", "highlight")
cssSelector(".panel"), elementCount(".row", 4)
```

### Finding things the way a screen reader does

Borrowed from [Testing Library](https://testing-library.com/docs/queries/about/), whose
insight is that if you cannot find a control by its role and the name it
announces, neither can an assistive technology — so the query itself is the
accessibility test.

```scala
role("button").named("site.continue")
role("textbox").namedText("Email address")
role("heading").namedMatching("Sign\\s+up".r)
role("radio").occurring(2)
```

Roles are resolved the way HTML-AAM defines them, implicit or explicit, and an
element carrying an explicit `role` is matched only by that role — so
`<a role="button">` is a button and not a link. The accessible name comes from
`aria-labelledby`, `aria-label`, an associated `<label>`, a `<legend>`, `alt`,
or the element's text, in that order.

When it fails it lists the names that *are* announced, which is usually enough
to see the problem:

```
x role(button, messages(site.continue)) — no element with role `button` announces this name
      expected  "Continue"
      actual    "Save and come back later | Cancel"
```

This is a working subset of the accessible name computation, not the whole
specification: it resolves one hop of `aria-labelledby` and knows nothing that
depends on CSS or JavaScript.

Text expectations also take a regular expression anywhere a string is accepted:

```scala
heading(matching("Sign\\s+up".r))
```

### Control state, form values and reading order

Borrowed from [jest-dom](https://github.com/testing-library/jest-dom), keeping the
matchers that mean something for server-rendered HTML and dropping the ones that
need a browser.

```scala
formValues("email" -> "ada@example.com", "country" -> "GB", "contact" -> "email")

disabled("locked")     // including a control disabled by an ancestor fieldset
enabled("email")
required("email")      // required attribute or aria-required
invalid("email")       // aria-invalid
describedAs("email", "signUp.email.hint")

appearsBefore(".govuk-error-summary", "form")
```

`appearsBefore` is the one worth calling out: reading order is not cosmetic. An
error summary announced after the form it describes is announced too late to be
useful, and nothing else here could express that.

Raw Jsoup is always one call away: `page.doc`, `page.summaryRows`,
`page.fieldErrors`, `page.errorSummaryLinks`, `page.outline`.

## The rules

Three sets in two optional modules, kept apart so a project is only judged
against what it actually uses.

**`WcagStandards`** (`twirl-spec-wcag`) — 16 rules: 15 enforce a WCAG success
criterion, and 1 is a structural convention WCAG does not require but almost
everyone wants (exactly one `<h1>`).

**`TwirlStandards`** (`twirl-spec-wcag`) — 2 rules for Play rendering mistakes:
a message key rendered raw because it is missing from the messages file, and a
`Some(...)` reaching the page because a value was never unwrapped. Neither is an
accessibility rule.

**`GovukStandards`** (`twirl-spec-govuk`) — 5 conventions of the [GOV.UK Design
System](https://design-system.service.gov.uk/): the error summary, the inline
error message, and the `Error:` browser-title prefix. These key off Design
System markup, so they stay silent on a page that does not use it.

### Selecting by conformance level and WCAG version

Every accessibility rule carries the success criterion it enforces — number,
title, level and the WCAG version it first appeared in — so a project can run
exactly the rules that bear on the claim it is making.

```scala
WcagStandards.conformingTo(Level.AA)                     // A and AA, all versions
WcagStandards.conformingTo(Level.AA, WcagVersion.V2_1)   // A and AA, up to WCAG 2.1
WcagStandards.atLevel(Level.AAA)                         // exactly AAA
WcagStandards.introducedIn(WcagVersion.V2_1)             // what 2.1 added
WcagStandards.conventions                                // the rules that are not WCAG at all
WcagStandards.criteria                                   // which criteria this set covers
```

Both selections are cumulative, because that is what they mean in WCAG: an AA
claim includes A, and 2.2 includes everything in 2.1. `conformingTo` excludes
the structural conventions, so what comes back is exactly the WCAG surface this
library covers — nothing that would inflate a conformance claim.

Criteria currently covered:

| Criterion | Level | Since | Rules |
|---|---|---|---|
| 1.1.1 Non-text Content | A | 2.0 | `image-alt` |
| 1.3.1 Info and Relationships | A | 2.0 | `main-landmark`, `heading-order`, `grouped-choices`, `table-header-scope` |
| 1.3.5 Identify Input Purpose | AA | 2.1 | `input-purpose-autocomplete` |
| 2.4.2 Page Titled | A | 2.0 | `title-present` |
| 2.4.4 Link Purpose (In Context) | A | 2.0 | `link-has-name` |
| 2.4.6 Headings and Labels | AA | 2.0 | `no-empty-headings` |
| 2.4.9 Link Purpose (Link Only) | AAA | 2.0 | `link-text-is-meaningful` |
| 3.1.1 Language of Page | A | 2.0 | `html-lang` |
| 3.2.5 Change on Request | AAA | 2.0 | `new-tab-is-announced` |
| 3.3.2 Labels or Instructions | A | 2.0 | `labelled-controls` |
| 4.1.2 Name, Role, Value | A | 2.0 | `unique-ids`, `submit-has-name` |

This is a useful subset, not full WCAG coverage. A static check over rendered
markup cannot see colour contrast, focus order, motion or anything that depends
on CSS or JavaScript. Treat a green run as "these mistakes are absent", not as a
conformance claim.

```scala
page must display(...)                    // your expectations + whatever standardsRules resolves to
page must meetStandards                   // the active rule set, alone
page must meetStandardsExcept("one-h1")

override def standardsRules = WcagStandards.conformingTo(Level.AA)
override def failOnWarnings = true
```

Warnings are reported but never fail a build until you ask them to. The two AAA
rules ship as warnings for that reason.

## Languages

```scala
"render in every language" in {
  inEachLanguage { _ =>
    render(view(form)) must display(title("x.title"), heading("x.heading"))
  }
}
```

`inWelsh { … }` and `inLanguage(lang) { … }` swap the implicit `messages` and
request for a block. A key that is not defined in that language is reported as
missing, rather than silently compared against itself — which is what a plain
`doc.title mustBe messages("x.y")` does, passing happily while the page shows a
raw key.

## Message files

```scala
messagesApi must beConsistentAcrossLanguages()
Seq(new File("conf/messages"), new File("conf/messages.cy")) must haveNoDuplicateKeys
```

Checks key parity both ways, empty values, placeholder parity, translation
coverage, unpaired apostrophes (Play runs every message through
`MessageFormat`, so a lone `'` swallows the rest of the sentence), and keys
defined twice in one file — which Play resolves by silently keeping the last.

## Failure output

```
page failed 2 checks:

  x title — text did not start with the expected value
        expected  "What is your name?"
        actual    "What is you name?"

  x input(lastName) label — field has no associated label
        expected  label[for="lastName"]
        hint      WCAG 3.3.2 — every input needs a label whose `for` matches the input id

  --- page outline (en) ---
page
  title        What is you name? - Example Service - GOV.UK
  lang         en
  h1           What is you name?
  back link    /back
form POST /register/name
  text     firstName    label "First name"
  text     lastName
  button   submit       "Continue"
```

Every check runs, so one failing run shows every problem rather than one per
re-run. The outline underneath is the page's skeleton — worth printing on its
own while building a view, and stable enough to commit as a structural snapshot.

## Speed

`SharedApplication` keeps one application per distinct configuration for the
whole JVM. Views are stateless and only read from the application, so building
one per view — a common pattern — costs a great deal and buys nothing.

## Building

```sh
./run_all_tests.sh
```

Formats, cross-compiles both Scala versions, tests every module, and measures
coverage against a 100% statement and branch gate.

Every module is fully covered, with no `$COVERAGE-OFF$` exclusions anywhere in
the source. That is a deliberate constraint rather than a trophy: a new branch
has to arrive with a test, be excluded with a marker and a stated reason, or
lower the gate in a commit someone can see. Getting there also deleted three
pieces of unreachable code — a `getOrElse` on a key Play always defines, a
not-found branch in a helper only called for values already found, and a
`catch` that the tests written to justify it showed had never caught
anything.

`src/test/resources/captured/` holds markup captured verbatim from a real GOV.UK
Design System implementation, so the Design System rules are checked against
genuine output without this library depending on any component package.

## Licence

Apache 2.0.
