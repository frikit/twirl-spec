# Getting started

This page takes a Play service with Twirl views from nothing to a first
passing spec, and shows what a failing one looks like.

## 1. Add the dependency

Everything in one line, which is right for most services:

```scala
libraryDependencies += "io.github.frikit" %% "twirl-spec-all" % "2.0.1" % Test
```

Or pick modules. The core carries the page model, the DSL and the matchers and
no rules at all; each other module adds a set of rules and the trait that wires
them in:

```scala
libraryDependencies ++= Seq(
  "io.github.frikit" %% "twirl-spec-core"     % "2.0.1" % Test,
  "io.github.frikit" %% "twirl-spec-wcag"     % "2.0.1" % Test, // accessibility, rendering, safety
  "io.github.frikit" %% "twirl-spec-govuk"    % "2.0.1" % Test, // GOV.UK Design System error conventions
  "io.github.frikit" %% "twirl-spec-quality"  % "2.0.1" % Test, // semantics, page weight, metadata, coverage
  "io.github.frikit" %% "twirl-spec-aria"     % "2.0.1" % Test, // ARIA correctness
  "io.github.frikit" %% "twirl-spec-html"     % "2.0.1" % Test, // HTML validity
  "io.github.frikit" %% "twirl-spec-i18n"     % "2.0.1" % Test, // language parity
  "io.github.frikit" %% "twirl-spec-messages" % "2.0.1" % Test  // message-file integrity
)
```

Play is a `Provided` dependency: the library is compiled against Play 3.0.0
and uses whatever 3.0.x your project already has. Your test classpath needs
`play`, `play-test` and `play-guice`; a Play application built with the Play
sbt plugin and `guice` has all three.

## 2. Choose a way in

There are two entry traits, and the choice depends on what your specs already
have.

**Nothing yet.** Mix in `TwirlSpec`. It builds a Play application, shares it
with every other spec in the JVM that uses the same configuration, and
provides the implicits a view needs: `messages`, `request` and `messagesApi`.

```scala
class MyViewSpec extends AnyWordSpec with Matchers with TwirlSpec {
  private val view = inject[MyView]
}
```

**An existing spec base** with its own application, `messages` and
`fakeRequest`, typically through `GuiceOneAppPerSuite`. Mix in
`TwirlSpecDsl` instead. It declares no implicits and builds no application, so
it cannot clash with yours; you pass your own `Messages` where the DSL needs
them.

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with TwirlSpecDsl
```

Both work with ScalaTest's `must` and `should` matchers. The examples here use
`must`.

## 3. Add the rules you want

Each rule module ships a trait. Mixing one in makes every `display(...)` run
its rules over the page as well as your own expectations, and the traits
compose:

```scala
import io.github.frikit.twirlspec.wcag.WcagChecks
import io.github.frikit.twirlspec.govuk.GovukChecks

class MyViewSpec extends AnyWordSpec with Matchers with TwirlSpec with WcagChecks with GovukChecks
```

`AllChecks`, from `twirl-spec-all`, is every trait at once. Without a rule
trait, `display` checks exactly what you asked and nothing else.

## 4. Write the first spec

A GOV.UK question page with one text input:

```scala
import io.github.frikit.twirlspec.{AllChecks, TwirlSpec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import views.html.WhatIsYourNameView

class WhatIsYourNameViewSpec extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {

  private val view = inject[WhatIsYourNameView]
  private val form = inject[WhatIsYourNameFormProvider].apply()

  "WhatIsYourNameView" should {

    "render the question" in {
      render(view(form)) must display(
        title("whatIsYourName.title"),
        heading("whatIsYourName.heading"),
        backLink,
        textInput("value").labelled("whatIsYourName.heading").hinted("whatIsYourName.hint"),
        submitButton(),
        noErrors
      )
    }

    "show the error state" in {
      val invalid = form.bind(Map("value" -> ""))
      render(view(invalid)) must display(
        errorTitlePrefix,
        errorSummary("value" -> "whatIsYourName.error.required"),
        fieldError("value", "whatIsYourName.error.required")
      )
    }
  }
}
```

Three things to notice:

- **Text expectations take message keys.** `title("whatIsYourName.title")`
  resolves the key in the page's language and compares the result with what
  the page shows. A key that is not defined is reported, rather than silently
  compared against itself. When you really mean exact words, wrap them:
  `titleText("What is your name?")`, or `literal("...")` where an expectation
  takes an `Expected`.
- **Expectations that name one element fail when they find more than one.**
  `textInput("value")` with two matching inputs is a failure, not a silent
  pick of the first. Use `cssSelector` and `elementCount` for groups.
- **Every check runs.** One red test shows every problem on the page, followed
  by the page's outline, so you fix them in one round.

## 5. Run it

```sh
sbt "testOnly *WhatIsYourNameViewSpec"
```

The first spec in a JVM boots one Play application, which takes a second or
two; every later spec with the same `applicationConfig` reuses it.

## 6. Read a failure

Break the template so the label points at the wrong id, and the output is:

```
page failed 1 check:

  x input(value) label — field has no associated label
        expected  label[for="value"]
        hint      WCAG 3.3.2 — every input needs a label whose `for` matches the input id

  --- page outline (en) ---
page
  title        What is your name? - Example Service - GOV.UK
  lang         en
  h1           What is your name?
  back link    /back
form POST /register/name
  text     value
  button   submit       "Continue"
```

The first line counts the failures. Each finding names the expectation, says
what went wrong, and where the library knows a fix, gives a hint. The outline
underneath is the page as the library sees it. [Failure
messages](failure-messages.md) goes through it in detail.

## 7. Configure the application once

`TwirlSpec` builds its application with a few defaults: metrics and auditing
off, one language, `en`, and no CSP nonce. A bilingual service declares its
languages by overriding `applicationConfig`, usually once on a spec base:

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {
  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq("en", "cy"))
}
```

Anything else the views need at construction, such as an `AppConfig`, comes
from `inject[AppConfig]` and is passed to the view, or declared implicit in the
spec base if the view takes it implicitly.

## Where next

- [Writing view specs](writing-view-specs.md) for every expectation the DSL
  offers, control by control.
- [Rules and standards](rules-and-standards.md) for what the traits check and
  how to tune them.
- [Adopting in an existing service](adopting.md) if you have specs already.
