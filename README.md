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

That block also runs 17 accessibility and rendering rules over the page. You do
not list them, switch them on, or maintain them.

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

## Install

```scala
libraryDependencies += "io.github.frikit" %% "twirl-spec" % "x.y.z" % Test
```

Cross-built for Scala 2.13 and Scala 3, on Java 21 and Play 3.0. It depends on
`jsoup` and `scalatest` only; Play is `provided`, so it never moves your Play
version, and there is no dependency on any UI component library.

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

Raw Jsoup is always one call away: `page.doc`, `page.summaryRows`,
`page.fieldErrors`, `page.errorSummaryLinks`, `page.outline`.

## The rules

Three sets, kept separate so a project is only judged against what it actually
uses.

**`WcagStandards`** — 15 rules that hold for any HTML page: exactly one `<h1>`,
non-empty `<title>`, `lang` on `<html>`, a `<main>` landmark, no skipped heading
levels, no empty headings, unique ids, every control labelled, radios and
checkboxes in a fieldset with a legend, submit controls and links with an
accessible name, `alt` on images, `scope` on table headers, meaningful link
text, new tabs announced.

**`TwirlStandards`** — 2 rules for Play rendering mistakes: a message key
rendered raw because it is missing from the messages file, and a `Some(...)`
reaching the page because a value was never unwrapped.

**`GovukStandards`** — 5 conventions of the [GOV.UK Design
System](https://design-system.service.gov.uk/): the error summary, the inline
error message, and the `Error:` browser-title prefix. These key off Design
System markup, so they stay silent on a page that does not use it.

```scala
page must display(...)          // WcagStandards ++ TwirlStandards
page must meetWcagStandards     // those two, alone
page must meetGovukStandards    // all three
page must meetStandardsExcept("main-landmark")

// or set it once, in your own base spec
override def standardsRules = WcagStandards.all ++ TwirlStandards.all ++ GovukStandards.all
override def failOnWarnings = true
```

Warnings are reported but never fail a build until you ask them to.

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

Formats, cross-compiles both Scala versions, tests, and measures coverage
against a 97% statement gate.

`src/test/resources/captured/` holds markup captured verbatim from a real GOV.UK
Design System implementation, so the Design System rules are checked against
genuine output without this library depending on any component package.

## Licence

Apache 2.0.
