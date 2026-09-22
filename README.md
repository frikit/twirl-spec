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

With the `twirl-spec-wcag` module mixed in, that block also runs 29
accessibility, rendering and safety rules over the page. You do not list them, switch
them on, or maintain them.

[![Release](https://github.com/frikit/twirl-spec/actions/workflows/release.yml/badge.svg)](https://github.com/frikit/twirl-spec/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.frikit/twirl-spec-core_3)](https://central.sonatype.com/artifact/io.github.frikit/twirl-spec-core_3)
[![Scala 3.3 LTS](https://img.shields.io/badge/scala-3.3%20LTS-red)](https://github.com/frikit/twirl-spec/blob/main/build.sbt)
[![Apache 2.0](https://img.shields.io/badge/licence-Apache%202.0-blue)](https://github.com/frikit/twirl-spec/blob/main/LICENSE)
[![Documentation](https://img.shields.io/badge/docs-frikit.github.io-brightgreen)](https://frikit.github.io/twirl-spec/)

## Documentation

Everything below is published at
[frikit.github.io/twirl-spec](https://frikit.github.io/twirl-spec/), together
with the [API documentation](https://frikit.github.io/twirl-spec/api/) — the
Scaladoc of all nine modules as one searchable site.

This README is the reference: every expectation, matcher and rule. The
[`docs`](docs/README.md) folder is the guide, from a first spec to adopting the
library across a service:

- [Getting started](docs/getting-started.md) — install, a first spec, reading a failure
- [Writing view specs](docs/writing-view-specs.md) — the DSL, control by control
- [Rules and standards](docs/rules-and-standards.md) — what the check traits enforce, and how to tune them
- [Languages and message files](docs/languages-and-messages.md) — Welsh, or any language, and the files behind it
- [Coverage and entry points](docs/coverage-and-entry-points.md) — what the spec never looked at
- [Failure messages](docs/failure-messages.md) — how to read what a red test says
- [Adopting in an existing service](docs/adopting.md) — replacing a home-grown spec base, and moving from 1.x
- [Troubleshooting](docs/troubleshooting.md) — the questions that come up

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
| `twirl-spec-wcag` | core | 29 rules: 21 tagged with a WCAG success criterion, 1 structural convention, 4 for Twirl rendering, 3 for safety |
| `twirl-spec-govuk` | core, wcag | 5 GOV.UK Design System conventions |
| `twirl-spec-quality` | core | 12 rules: semantics, page weight and metadata, plus the coverage and entry-point checks |
| `twirl-spec-aria` | core | 16 ARIA correctness rules, the static half of what an automated tool reports |
| `twirl-spec-html` | core | 3 HTML validity rules: unclosed elements named with their line, unknown elements and attributes |
| `twirl-spec-i18n` | core | Language parity: every language renders the same page as the base |
| `twirl-spec-messages` | core | Message-file integrity checks |
| `twirl-spec-all` | all of the above | One dependency that pulls in everything, and `AllChecks` |

```scala
libraryDependencies ++= Seq(
  "io.github.frikit" %% "twirl-spec-core"     % twirlSpecVersion % Test,
  "io.github.frikit" %% "twirl-spec-wcag"     % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-govuk"    % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-quality"  % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-aria"     % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-html"     % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-i18n"     % twirlSpecVersion % Test,  // optional
  "io.github.frikit" %% "twirl-spec-messages" % twirlSpecVersion % Test   // optional
)
```

Or take the lot in one line:

```scala
libraryDependencies += "io.github.frikit" %% "twirl-spec-all" % twirlSpecVersion % Test
```

Rule modules ship a trait that wires their rules into every `display(...)`. The
traits compose, so mixing in two runs both sets:

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec
  with WcagChecks     // accessibility, rendering and safety rules
  with GovukChecks    // + GOV.UK Design System conventions
  with QualityChecks  // + semantics, page weight and metadata
  with AriaChecks     // + ARIA correctness
  with HtmlChecks     // + HTML validity
```

Without a rule module, `display(...)` checks exactly what you asked it to and
nothing else.

## Compatibility

Each release is built and tested against exactly these. The POM of a given
release is the authority for that release; this table is kept for the current
line.

| twirl-spec | Play | Twirl | Scala | Java | ScalaTest | jsoup |
|---|---|---|---|---|---|---|
| 2.0.x | 3.0.0 | 2.0.1 | 3.3.8 | 21 | 3.2.20 | 1.23.2 |

What that means for a project on something close but not identical:

**Play** is `Provided`: your project supplies `play`, `play-test` and
`play-guice`, and this library never moves your Play version. Built against
3.0.0, the first 3.0 release, so any 3.0.x works without an upgrade. **Play 2.9
and earlier are not supported** — they predate the Pekko move and are not
tested.

**Scala.** Published for Scala 3 only, built with 3.3.8, the LTS line. Scala 3
is forward-compatible, so a project on 3.3.8 or anything later uses the
artifact. A project below 3.3.8, or on Scala 2.13, cannot.

**Java.** Compiled with `-release 21`, so 21 is the floor and later JDKs are fine.

**ScalaTest** is a normal dependency, not `Provided`, so it resolves upward
against whatever your project already has; 3.2.20 is the floor.

**jsoup** is this library's own dependency and is not something your project
needs to align with.

Anything outside this table is untested rather than known-broken. If you get a
combination working, a note in an issue is welcome.

## Versioning

Semantic versioning from 1.0.0, with one wrinkle that matters for a library of
checks.

Within a major version the public API is stable: DSL methods and matchers, rule
ids, package names, and the fields of every `Config`. Deprecations stand for at
least one minor version before removal.

**A minor version may add rules.** A new rule can turn a page that passed red,
which is the point of it, but it is not what "minor" usually promises. Pin to a
minor version if a green build matters more to you than the newest checks, and
read the changelog before moving. Rule wording, hints and failure-message layout
may change in any release; assert on rule ids, not on message text.

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
the `value` of a `button`, `submit` or `reset` input, the element's own text
(or the `alt` of an image standing in for it), and finally `title` — in that
order. A `<select>`, `<textarea>` or `<input>` is never named by its own
content: that content is what the user entered, not what the control is
called.

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

### One match, or a failure

An expectation that names a single element — `textInput("email")`,
`element("submit")`, `backLink`, `dropdown("country")`, `summaryRow(...)` —
fails when the page has **more than one** match, as well as when it has none:

```
x input(email) — 2 elements matched, so this assertion is ambiguous
      expected  "exactly one match"
      actual    "<input#email[name=email]> | <input#email2[name=email]>"
      hint      name the one you mean, or assert on the group with cssSelector and elementCount
```

This follows [Testing Library's](https://testing-library.com/docs/queries/about/)
`getBy`, and it exists because the alternative is worse than a failure: quietly
taking the first of several is how a test ends up asserting against something
other than the thing it names, and passing while it does so.

Where several matches are legitimate, say so — `cssSelector` and
`elementCount` are the plural form, and the accessors on `page` return
everything without complaint. Expectations that are plural by nature —
`radioGroup`, `checkboxGroup`, `bullets`, `link`, `tableRow` — are unaffected.

### Reference

Every expectation and matcher the DSL exposes. Expectations take a message
key by default; the `…Text` variants take the exact words instead. The builders
on a returned expectation, such as `labelled` on `textInput` or
`withChangeLinkTo` on `summaryRow`, are shown in the tour above rather than
listed again here.

**Rendering**

| | |
|---|---|
| `render` | Parse rendered HTML into a page that can be asked questions. |
| `renderIn` | Render the same view in a given language. |
| `literal` | Text expectations take message keys by default; wrap a string in `literal` when you really do mean the exact words. |
| `anyText` | For "there is a heading, its wording is asserted elsewhere". |
| `matching` | Match text against a regular expression rather than an exact string. |
| `role` | Find an element the way an assistive technology does: by role, then by the name it announces. |
| `normalised` | Page text has its quotes, spaces and soft hyphens normalised before comparison, and a raw `messages(...)` value has not — so `page.text must include(messages("x"))` fails on a curly apostrophe that looks identical. |
| `messageText` | A message, resolved and normalised, ready to compare against page text. |
| `expectations` | Bundle expectations so a service can name its own house rules once. |

**Application and languages** (`TwirlSpec` only)

| | |
|---|---|
| `applicationConfig` | Extra configuration for this suite's application, over the view-test defaults. |
| `app` | The shared application for that configuration. |
| `inject` | An instance from the application's injector, usually a view. |
| `languages` | Every language the application is configured for, English first. |
| `currentLang` | The language the current block is running in. |
| `inLanguage` | Run a block with `messages` and a request for the given language in scope. |
| `inEnglish` | `inLanguage(english)`. |
| `inEachLanguage` | Run the same block once per configured language. |
| `renderPage` | Render a view in the current language; `render` with the language made explicit. |
| `renderInEachLanguage` | Render the same view in every configured language, for `translateConsistently`. |

**Matchers**

| | |
|---|---|
| `standardsRules` | Which rules run alongside every `display(...)`: none in the core, and each rule module adds its own. |
| `failOnWarnings` | Whether warnings fail the test. |
| `reportWarnings` | Whether passing tests still surface their warnings in the test output. |
| `display` | The page shows all of this, and holds to every rule `standardsRules` resolves to. |
| `displayOnly` | As `display`, but without the standards — for the rare page that has to break a rule, or while a legacy view is being brought up to standard. |
| `meetStandards` | Whatever `standardsRules` resolves to, for a spec that has its own assertions already. |
| `meetStandardsExcept` | The standards, minus the named rules. |
| `standardsExpectation` | The active rule set as one expectation, for asserting on the result. |
| `checkPage` | Assert against a page directly, outside a matcher. |

**Page framing**

| | |
|---|---|
| `title` | The browser title, ignoring the " - Service name - GOV.UK" suffix the layout appends and the translated "Error:" prefix an error state adds. |
| `titleText` | The browser title, given as the exact words rather than a message key. |
| `exactTitle` | The browser title in full, including service name and " - GOV.UK". |
| `heading` | The single `<h1>`. |
| `headingText` | The `<h1>`, given as the exact words rather than a message key. |
| `caption` | The caption rendered above (or inside) the h1. |
| `captionText` | The caption, given as the exact words rather than a message key. |
| `serviceName` | The service or site name in the header. |
| `backLink` | A GOV.UK back link is present. |
| `noBackLink` | There is no back link, for a page a citizen must not reverse out of. |
| `languageToggle` | A language switcher, however it is rendered. |
| `timeoutDialog` | The session-timeout dialog is wired up. |
| `signOutLink` | A sign out link is present, and points where it should. |
| `phaseBanner` | The alpha or beta phase banner is present, with the phase it names. |
| `subheading` | An `h2` with the given message key. |
| `headingAtLevel` | A heading at a given level says this. |

**Content**

| | |
|---|---|
| `content` | The resolved message appears somewhere in the page's visible text. |
| `contentText` | Somewhere in the page body, given as the exact words rather than a message key. |
| `noContent` | The resolved message appears nowhere in the page's visible text. |
| `paragraph` | The message appears inside a paragraph. |
| `warning` | The text of a GOV.UK warning callout. |
| `insetText` | The text of an inset text block. |
| `notificationBanner` | The text of a notification banner. |
| `panelTitle` | The title of a confirmation panel. |
| `panelBody` | The body of a confirmation panel. |
| `detailsSummary` | The visible summary of a collapsed details block. |
| `bullets` | The bullet list contains exactly these items, in order. |
| `numberedItems` | The numbered list contains exactly these items, in order. |
| `link` | A link with the given text pointing at the given URL. |
| `linkText` | A link, given as the exact words rather than a message key. |
| `linkWithId` | A link found by id, whatever it says. |
| `summaryRow` | A row of a `govukSummaryList`, by its key. |
| `summaryList` | The summary list holds exactly these `key -> value` rows, in order. |
| `tableHeaders` | The table header cells, in order. |
| `tableRow` | A row whose cells read exactly like this. |
| `appearsBefore` | One selector's first match comes before another's in document order. |
| `element` | An element with this id exists. |
| `noElement` | No element with this id exists. |
| `elementWithText` | The element with this id says this. |
| `cssSelector` | At least one element matches this selector. |
| `noCssSelector` | Nothing matches this selector. |
| `elementCount` | Exactly this many elements match. |
| `elementHasClass` | The element with this id carries this class. |

**Forms**

| | |
|---|---|
| `textInput` | A text input, by name or id; its label, hint, value, autocomplete and type come through the builders. |
| `textArea` | As `textInput`, for a `<textarea>`. |
| `dropdown` | A `<select>`, with its label and options. |
| `radioGroup` | A radio group: its legend, options, hint and what is selected. |
| `checkboxGroup` | As `radioGroup`, for checkboxes. |
| `dateInput` | The GOV.UK date input: three fields under one legend. |
| `fileUpload` | A file input is present. |
| `hiddenInput` | A hidden input carries this value. |
| `submitButton` | The submit control says this, `site.continue` by default. |
| `submitButtonText` | The submit control, given as the exact words rather than a message key. |
| `hasSubmitButton` | A submit control is present, whatever it says. |
| `button` | The button with this id says this. |
| `formPostsTo` | The form is a POST to this action. |
| `formGetsFrom` | The form is a GET to this action. |
| `formValues` | Every named control holds these values, as a browser would submit them. |
| `disabled` | The control is disabled, on itself or through an enclosing fieldset. |
| `enabled` | This control is not disabled. |
| `required` | This control is marked required. |
| `invalid` | The control is marked invalid for an assistive technology. |
| `describedAs` | What an assistive technology reads after the control's name. |
| `noErrors` | No error summary and no inline error messages anywhere on the page. |
| `errorTitlePrefix` | GOV.UK requires the browser title of a page in an error state to be prefixed, so screen reader users hear that something went wrong before the page name. |
| `errorSummaryTitle` | The heading above the error summary. |
| `errorSummary` | The error summary lists exactly these `field -> message key` entries, in order, and every entry links to an element that exists on the page. |
| `errorSummaryContaining` | The error summary mentions this field, whatever else it lists. |
| `fieldError` | The inline error message rendered against a specific field. |
| `labelledByLegend` | For the rare text input that is legitimately labelled by a legend rather than a label. |

**Coverage and entry points**

| | |
|---|---|
| `trackedAttributes` | Attributes that mark an element as worth asserting, beyond ids and links. |
| `coverageScope` | Which part of the page a spec answers for. |
| `coverageIgnored` | Ids, links or tracking values every page in this project inherits from its layout. |
| `assertEverything` | Every id, link and tracked element on the page was asserted by some test in this spec. |
| `assertEverythingExcept` | As `assertEverything`, but these ids, links or tracking values are deliberately not asserted. |
| `unassertedContent` | What this spec has asserted so far, for a spec that wants to report rather than fail. |
| `entryPointsAgree` | Twirl's generated `render`, `f` and `ref` all agree with `apply`. |

**Languages**

| | |
|---|---|
| `translationConfig` | What a page may legitimately keep the same between languages. |
| `translateConsistently` | Every language renders the same page as the base language, differing only in words. |
| `translateConsistentlyExcept` | As `translateConsistently`, without the named rules. |
| `basedOn` | Measure the other languages against this one rather than against the first page given. |
| `translationDifferences` | The differences, for a spec that would rather report than fail. |

## The rules

65 rules in nine sets across five optional modules, kept apart so a
project is only judged against what it actually uses.

| Set | Module | Rules | Covers |
|---|---|---|---|
| `WcagStandards` | `twirl-spec-wcag` | 22 | Accessibility |
| `TwirlStandards` | `twirl-spec-wcag` | 4 | Play rendering mistakes |
| `SecurityStandards` | `twirl-spec-wcag` | 3 | Ways a page can leak or be turned against its reader |
| `GovukStandards` | `twirl-spec-govuk` | 5 | GOV.UK Design System error conventions |
| `AriaStandards` | `twirl-spec-aria` | 16 | ARIA correctness |
| `HtmlStandards` | `twirl-spec-html` | 3 | Basic HTML validity, read from the source rather than the repaired tree, so an unclosed element is named along with the line it opened on |
| `SemanticStandards` | `twirl-spec-quality` | 4 | Markup that parses but does not mean what it looks like |
| `PerformanceStandards` | `twirl-spec-quality` | 4 | Page weight and rendering cost |
| `MetadataStandards` | `twirl-spec-quality` | 4 | What a browser tab, a search result and a share preview make of the page |

A rule is blocking unless marked a warning; warnings are reported on a green
run too, and `failOnWarnings` promotes them. See [Failure output](#failure-output).

### `WcagStandards`

Accessibility: 21 rules each enforce a WCAG success criterion, and `one-h1` is a structural convention WCAG does not require but almost everyone wants.

| Rule | Checks | Criterion |
|---|---|---|
| `one-h1` | a page has exactly one <h1> | convention |
| `title-present` | a page has a non-empty <title> | 2.4.2 Page Titled · A · WCAG 2.0 |
| `html-lang` | the <html> element declares the rendered language | 3.1.1 Language of Page · A · WCAG 2.0 |
| `main-landmark` | a page has a main landmark *(warning)* | 1.3.1 Info and Relationships · A · WCAG 2.0 |
| `heading-order` | heading levels are not skipped | 1.3.1 Info and Relationships · A · WCAG 2.0 |
| `no-empty-headings` | headings have text | 2.4.6 Headings and Labels · AA · WCAG 2.0 |
| `unique-ids` | element ids are unique | 4.1.2 Name, Role, Value · A · WCAG 2.0 |
| `labelled-controls` | every form control has an accessible name | 3.3.2 Labels or Instructions · A · WCAG 2.0 |
| `grouped-choices` | radios and checkboxes sit in a fieldset with a legend | 1.3.1 Info and Relationships · A · WCAG 2.0 |
| `submit-has-name` | the submit control has an accessible name | 4.1.2 Name, Role, Value · A · WCAG 2.0 |
| `table-header-scope` | table headers declare a scope *(warning)* | 1.3.1 Info and Relationships · A · WCAG 2.0 |
| `link-has-name` | every link has an accessible name | 2.4.4 Link Purpose (In Context) · A · WCAG 2.0 |
| `link-text-is-meaningful` | link text makes sense out of context *(warning)* | 2.4.9 Link Purpose (Link Only) · AAA · WCAG 2.0 |
| `new-tab-is-announced` | links opening a new tab say so *(warning)* | 3.2.5 Change on Request · AAA · WCAG 2.0 |
| `input-purpose-autocomplete` | inputs collecting information about the user declare an autocomplete purpose *(warning)* | 1.3.5 Identify Input Purpose · AA · WCAG 2.1 |
| `aria-references-resolve` | every aria reference points at an element that exists | 1.3.1 Info and Relationships · A · WCAG 2.0 |
| `no-aria-hidden-focusable` | nothing hidden from assistive technology can still take focus | 4.1.2 Name, Role, Value · A · WCAG 2.0 |
| `no-positive-tabindex` | focus order follows the document | 2.4.3 Focus Order · A · WCAG 2.0 |
| `zoom-not-blocked` | the page can be zoomed | 1.4.4 Resize Text · AA · WCAG 2.0 |
| `label-for-resolves` | every label points at a control that exists | 3.3.2 Labels or Instructions · A · WCAG 2.0 |
| `single-main` | a page has one main landmark | 1.3.1 Info and Relationships · A · WCAG 2.0 |
| `image-alt` | every image has an alt attribute | 1.1.1 Non-text Content · A · WCAG 2.0 |

### `TwirlStandards`

Play rendering mistakes: an unresolved message key, markup the parser had to repair, two controls sharing a name, and a Scala value reaching the page.

| Rule | Checks |
|---|---|
| `no-raw-message-keys` | no unresolved message key is shown to a citizen |
| `well-formed-html` | the template produced markup a browser does not have to repair |
| `unambiguous-field-names` | no two controls submit under the same name |
| `no-scala-leakage` | no Scala value leaks into the rendered page |

### `SecurityStandards`

Ways a page can leak or be turned against its reader.

| Rule | Checks |
|---|---|
| `no-password-in-get` | a password is never submitted in a URL |
| `no-javascript-href` | links do not carry javascript: URLs |
| `target-blank-is-safe` | a link opening a new tab cannot reach back *(warning)* |

### `GovukStandards`

[GOV.UK Design System](https://design-system.service.gov.uk/) error conventions. They key off Design System markup, so they stay silent on a page that does not use it.

| Rule | Checks |
|---|---|
| `error-title-prefix` | an error state prefixes the browser title |
| `error-aria-describedby` | an inline error is announced with its field |
| `error-hidden-prefix` | an inline error carries a visually hidden prefix |
| `error-summary-targets` | every error summary link lands on an element |
| `error-summary-focusable` | the error summary can take focus *(warning)* |

### `AriaStandards`

ARIA correctness: whether a name is real, whether its value is allowed, and whether roles that only mean something together appear together.

| Rule | Checks |
|---|---|
| `aria-attr-is-real` | every aria- attribute is one the specification defines |
| `aria-attr-value-is-allowed` | an aria- attribute taking a fixed set of values carries one of them |
| `aria-role-is-real` | every role is one the specification defines, and not an abstract one |
| `aria-required-attr` | a role that depends on state declares it |
| `aria-required-parent` | a role that only means something inside another sits inside one |
| `aria-required-children` | a role that must contain something is not empty |
| `aria-hidden-not-on-body` | the whole page is not hidden from assistive technology |
| `no-role-conflict` | an element made presentational is not also announced |
| `accesskey-unique` | no two elements answer to the same access key |
| `autocomplete-is-valid` | an autocomplete attribute uses tokens the specification defines |
| `no-meta-refresh` | the page does not redirect or refresh itself on a timer |
| `no-deprecated-effects` | nothing on the page blinks or scrolls by itself |
| `embedded-content-has-name` | an object, embedded image or svg carries a name *(warning)* |
| `table-headers-resolve` | every headers attribute points at a header on the same table |
| `definition-list-structure` | a definition list contains only terms and descriptions |
| `landmarks-are-distinguishable` | two landmarks of the same kind are told apart by name |

### `HtmlStandards`

Basic HTML validity, read from the source rather than the repaired tree, so an unclosed element is named along with the line it opened on.

| Rule | Checks |
|---|---|
| `tags-are-balanced` | every element that is opened is closed |
| `known-elements` | every element is one the HTML specification defines |
| `known-attributes` | every attribute is one the HTML specification defines |

### Scanning templates instead of pages

`tags-are-balanced` runs against a rendered page, which is where it belongs: by
then the template has taken one branch and the markup is either balanced or it
is not. The same check will also read a Twirl template directly, which needs no
application, no injector and no render:

```scala
TagBalance.checkTemplate(Files.readString(template))
```

A template is two languages at once, and only one of them writes tags, so the
Scala is taken out first — `@if(page < total)` is a comparison, not the start of
an element. Expressions, comments, imports, argument lists, `.field` chains,
bare `} else if (…) {` continuations and `@name = { … }` fragment definitions
are all blanked, keeping newlines so the line numbers still point at the source.

Two things it cannot know, because they need the conditions evaluated:

- markup that arrives from a helper rather than being written literally
- an element opened in one branch and closed in another, which really is
  unbalanced on any single render

Run over a few thousand templates it reports a handful, most of them real.

### `SemanticStandards`

Markup that parses but does not mean what it looks like.

| Rule | Checks |
|---|---|
| `no-nested-interactive` | no control contains another control |
| `lists-contain-list-items` | a list contains only list items |
| `no-presentational-markup` | meaning is carried by markup, not by looks *(warning)* |
| `no-br-for-layout` | spacing comes from styling, not from line breaks *(warning)* |

### `PerformanceStandards`

Page weight and rendering cost. All four are warnings.

| Rule | Checks |
|---|---|
| `images-have-dimensions` | images reserve their space before they load *(warning)* |
| `scripts-are-deferred` | scripts in the head do not block rendering *(warning)* |
| `no-oversized-data-uri` | large assets are files, not attributes *(warning)* |
| `no-large-inline-style` | styling lives in a stylesheet *(warning)* |

### `MetadataStandards`

What a browser tab, a search result and a share preview make of the page.

| Rule | Checks |
|---|---|
| `has-charset` | the page declares its character encoding |
| `not-noindex` | the page is not accidentally hidden from search *(warning)* |
| `has-meta-description` | the page describes itself for a search result *(warning)* |
| `title-is-concise` | the title survives being truncated *(warning)* |

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
| 1.3.1 Info and Relationships | A | 2.0 | `main-landmark`, `heading-order`, `grouped-choices`, `table-header-scope`, `aria-references-resolve`, `single-main` |
| 1.4.4 Resize Text | AA | 2.0 | `zoom-not-blocked` |
| 2.4.2 Page Titled | A | 2.0 | `title-present` |
| 2.4.3 Focus Order | A | 2.0 | `no-positive-tabindex` |
| 2.4.4 Link Purpose (In Context) | A | 2.0 | `link-has-name` |
| 2.4.6 Headings and Labels | AA | 2.0 | `no-empty-headings` |
| 2.4.9 Link Purpose (Link Only) | AAA | 2.0 | `link-text-is-meaningful` |
| 3.1.1 Language of Page | A | 2.0 | `html-lang` |
| 3.2.5 Change on Request | AAA | 2.0 | `new-tab-is-announced` |
| 3.3.2 Labels or Instructions | A | 2.0 | `labelled-controls`, `label-for-resolves` |
| 4.1.2 Name, Role, Value | A | 2.0 | `unique-ids`, `submit-has-name`, `no-aria-hidden-focusable` |
| 1.3.5 Identify Input Purpose | AA | 2.1 | `input-purpose-autocomplete` |

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

## What the spec missed

Rules answer "is this page sound". These two answer the other question: "did the
spec actually look at it". A view can pass every rule while half of it goes
unasserted, because rendering a page is not the same as testing it.

### Coverage

Every id, link and tracked element the page shows has to be asserted by some
test in the spec. Put it in the last test — it can only see assertions that have
already run.

```scala
"everything this page shows" must {
  "have been asserted by one of these tests" in {
    page must assertEverything
  }
}
```

```
7 things on this page were never asserted:
  id (3): #sub-header, #sub-header-link, #upload-file
  link (1): "upload a new file or download one that is waiting."
  tracking (1): data-journey-click=link - click:File not available:Choose something else to do
```

Assertions are recorded by what they touch, not by the selector they used, so
asserting `#action-list > *` does not count as asserting `#action-list`. Renders
of the same view in different states add up, so a spec that checks a valid form
in one test and an invalid one in the next is measured as a whole.

Three things are adjustable, usually once on a project's spec base:

```scala
override def coverageScope: String        = "main, #main-content"   // the layout is not the view spec's problem
override def coverageIgnored: Set[String] = Set("#content")         // what every page inherits
override def trackedAttributes: Set[String] = Set("data-journey-click")
```

Ignoring a block ignores what it holds: disclaiming the layout's
`#report-technical-issue` wrapper does not then hold the spec to the link
inside it.

`assertEverythingExcept("#id")` covers the one-off case, and
`unassertedContent(page)` reports without failing.

### Entry points

Twirl generates `render`, `f` and `ref` beside `apply`. Nothing in a normal
service calls them, so a change to a template's parameters can break them with
no test noticing, and they sit in coverage reports as permanently unreached
lines. One line exercises all three:

```scala
memberNameView must entryPointsAgree(
  memberNameView(form, edit = false)(request, messages, appConfig),
  form, false, request, messages, appConfig
)
```

The arguments are the ones `render` takes: every parameter including the
implicit ones, flattened, in order.

## Languages

```scala
"render in every language" in {
  inEachLanguage { _ =>
    render(view(form)) must display(title("x.title"), heading("x.heading"))
  }
}
```

`inLanguage(lang) { … }` swaps the implicit `messages` and request for a block.
A key that is not defined in that language is reported as missing, rather than
silently compared against itself — which is what a plain
`doc.title mustBe messages("x.y")` does, passing happily while the page shows a
raw key.

### Comparing languages

`twirl-spec-messages` compares the message *files*. `twirl-spec-i18n` compares
the *pages* those files produce, which is where a translation that parses but
renders differently shows up: a control that vanished, a link that kept its
original href, a heading level that moved.

```scala
"say the same thing in every language" in {
  renderInEachLanguage(memberNameView(form, edit = false)) must translateConsistently
}
```

The first page given is the base; nominate a different one with
`basedOn(Lang("fr"))`. Nothing here knows about any particular language pair —
whichever languages the application is configured for are the ones compared.

| Rule | Checks |
|---|---|
| `i18n-lang-attribute` | the page declares the language it was rendered in |
| `i18n-same-ids` | every language renders the same elements |
| `i18n-same-links` | every language links to the same places |
| `i18n-same-controls` | every language collects the same fields |
| `i18n-same-headings` | every language has the same heading structure |
| `i18n-nothing-lost` | no text present in the base language goes missing |
| `i18n-actually-translated` | the page is translated, not copied *(warning)* |

Text that is meant to read the same everywhere — a product name, a unit — would
otherwise be reported by `i18n-actually-translated`. Say so once:

```scala
override def translationConfig: TranslationConfig =
  TranslationConfig(sameTextIsFine = Set("GOV.UK", "Example Ltd", "ISBN"))
```

`translateConsistentlyExcept("i18n-same-links")` drops a rule, and
`translationDifferences(pages)` reports without failing.

A single configured language passes: there is nothing to compare it against.

## Message files

```scala
messagesApi must beConsistentAcrossLanguages()
Seq(new File("conf/messages"), new File("conf/messages.cy")) must haveNoDuplicateKeys
```

Every language the application is configured for is measured against a base
language, English unless `Config(baseLanguage = …)` says otherwise. Checks key
parity both ways, empty values, placeholder parity, translation coverage,
unpaired apostrophes (Play runs every message through `MessageFormat`, so a
lone `'` swallows the rest of the sentence), and keys defined twice in one file
— which Play resolves by silently keeping the last.

A service with one language sets `requireTranslations = false`, or the parity
check reports that there is nothing to compare against.

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

Its defaults also switch off `metrics.enabled` and `auditing.enabled`, which are
settings of the HMRC bootstrap library; a plain Play application ignores them.

## Contributing

`./run_all_tests.sh` formats, tests and holds the build to the coverage gate. [CONTRIBUTING.md](https://github.com/frikit/twirl-spec/blob/main/CONTRIBUTING.md) covers the pre-push hook,
the licence headers, how the rule tables above are kept true, dependency
updates, and how a push to `main` becomes a release.

## Licence

Apache 2.0.
