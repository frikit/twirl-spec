# For coding agents

This page is a procedure for a coding agent, or a person working the same way,
that has been asked to add twirl-spec to a Play service or to extend the view
specs a service already has. Follow the steps in order. Each step ends with a
command whose output proves it is done, so an agent can check its own work
rather than assume it.

The other pages explain the library; this one says what to do with it, and
links to them for detail. A plain-text index of every page, for tools that read
one, is at
[frikit.github.io/twirl-spec/llms.txt](https://frikit.github.io/twirl-spec/llms.txt).

Agents changing twirl-spec itself want the repository's
[AGENTS.md](https://github.com/frikit/twirl-spec/blob/main/AGENTS.md) and
[ARCHITECTURE.md](https://github.com/frikit/twirl-spec/blob/main/ARCHITECTURE.md)
instead.

## The library in six facts

1. A spec renders a Twirl view inside a real Play application, which every spec
   in the JVM with the same configuration shares, and parses it into a `Page`.
2. `page must display(...)` checks the expectations it names **and** every rule
   the spec has mixed in. One red test lists every problem on the page, followed
   by the page's outline.
3. Rules come from traits: mix one in and every `display` runs its rules. With
   no rule trait, `display` checks only what it names.
4. Text expectations take **message keys**, not words: `title("name.title")`
   resolves the key in the page's language, and an undefined key is a failure.
5. An expectation that names one element fails when it finds more than one.
6. A `PageCatalogue` turns a page into a test for each rule in each language, so
   the run reports what was checked, where.

## Step 0: collect the facts that decide the setup

Read these from the service before writing anything:

| Question | Where to look | What it decides |
|---|---|---|
| Scala version | `scalaVersion` in `build.sbt` | Scala 3.3.0 or later is required. On Scala 2.13 the library cannot be used: stop and report. |
| Java version | the JDK the build runs on | A JDK of 17 or later. `-release` in `scalacOptions` may still name an older Java, such as 11; only Scala 3.3.0 on a JDK of 21 or later needs it set. |
| Play version | `project/plugins.sbt` | Any Play 3.0.x. Play 2.x cannot use it: stop and report. |
| Test framework | test dependencies in `build.sbt` or `project/AppDependencies.scala` | MUnit if the build declares it; otherwise ScalaTest, which twirl-spec brings with it. |
| An existing spec base | `test/**/ViewSpecBase*`, `GuiceOneAppPerSuite`, a home-grown `ViewBehaviours` | `TwirlSpec`, or `TwirlSpecDsl` beside the existing application. |
| The layout | a `play-frontend-hmrc` dependency, GOV.UK Frontend classes in the templates | `HmrcServiceChecks`, `GovukServiceChecks` or `AllChecks`. |
| Languages | `play.i18n.langs` in `conf/application.conf`, `conf/messages.*` | How many languages every check runs in; a service that declares none has Play's one default language. Nothing to configure, except that a one-language service turns off the translation requirement (Step 2). |
| What the application needs to start | modules enabled in `conf/application.conf`, such as Mongo | Whether `applicationConfig` has to switch something off. |

The full requirements are in the [documentation index](README.md#requirements).

## Step 1: add the dependency

ScalaTest:

```scala
libraryDependencies += "io.github.frikit" %% "twirl-spec-all" % "3.2.2" % Test
```

MUnit (it brings `twirl-spec-all` with it):

```scala
libraryDependencies += "io.github.frikit" %% "twirl-spec-munit" % "3.2.2" % Test
```

Play is `provided`: the service's own Play 3.0.x is used. On a service that keeps
its dependencies in `project/AppDependencies.scala`, add the line to the test
dependencies there.

**Done when** `sbt Test/compile` succeeds.

## Step 2: choose the base

| The service has | Mix in |
|---|---|
| no view specs, or none that build an application | `TwirlSpec` |
| a spec base on `GuiceOneAppPerSuite` that every spec relies on for `app`, `messages` or `fakeRequest` | `TwirlSpecDsl with SuiteLifecycle`, keeping its own application and its CSRF-signed request ([details](adopting.md#keeping-an-existing-application)) |
| MUnit suites | `TwirlSuite` (see [MUnit](#munit)) |

Then the rules:

| The service is | Rule trait |
|---|---|
| on play-frontend-hmrc | `HmrcServiceChecks` |
| a GOV.UK transactional service on another layout | `GovukServiceChecks` |
| anything else | `AllChecks`, or the module traits it wants ([table](rules-and-standards.md#turning-rules-on)) |

All of these, and `TwirlSpec`, `TwirlSpecDsl`, `TwirlSuite` and
`PageCatalogue`, are imported from `io.github.frikit.twirlspec`; the module
traits come from its sub-packages, such as `io.github.frikit.twirlspec.wcag.WcagChecks`.

Put both on one spec base, which every view spec extends. For most services it
is no more than this, with the configuration the specs need to differ from the
service's:

<!-- compiled: all/src/test/scala/snippets/GettingStartedSpecBase.scala -->
```scala
import io.github.frikit.twirlspec.{GovukServiceChecks, TwirlSpec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with GovukServiceChecks {
  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("some.feature.enabled" -> true)
}
```

The message-file checks require a translation of every key by default. A
service with one language turns that off on its spec base:

```scala
override def messagesIntegrityConfig = MessagesIntegrity.Config(requireTranslations = false)
```

with `import io.github.frikit.twirlspec.messages.MessagesIntegrity`. A
bilingual service on play-frontend-hmrc keeps the requirement, and its base can
also gather the furniture every page carries:

<!-- compiled: all/src/test/scala/snippets/AdoptingSpecBase.scala -->
```scala
package views

import io.github.frikit.twirlspec.{HmrcServiceChecks, TwirlSpec}
import io.github.frikit.twirlspec.messages.MessagesIntegrity
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

// every rule, less the search-result ones a GOV.UK service has no use for,
// with what every service on play-frontend-hmrc would otherwise write here
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with HmrcServiceChecks {

  // the languages come from play.i18n.langs in conf/application.conf

  // the service is bilingual, so the message files must be too
  override def messagesIntegrityConfig = MessagesIntegrity.Config(requireTranslations = true)

  // the furniture every page must carry
  val furniture = expectations(serviceName(), languageToggle, signOutLink, timeoutDialog)
}
```

If the application cannot start in a test, for example because it needs a
database, see [Troubleshooting](troubleshooting.md#the-application-needs-a-database-it-cannot-reach)
before going on.

**Done when** `sbt Test/compile` succeeds with the spec base in place.

## Step 3: one spec per view

Write one spec file per view and per component, named after it and mirroring
`app/views`: `app/views/NameView.scala.html` gets
`test/views/NameViewSpec.scala`. Each spec puts the view through the catalogue
with `cataloguePage`, then adds the tests of what that view in particular shows.
This one is compiled and run on every change to the library:

<!-- compiled: consumer/service/test/views/NameViewSpec.scala -->
```scala
package views

import io.github.frikit.twirlspec.{GovukServiceChecks, PageCatalogue, TwirlSpec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.i18n.Lang

/** The path a service takes: TwirlSpec for the application and implicits,
  * GovukServiceChecks for the rules, the service's own configuration throughout,
  * and one spec for the view, with a test for each check and its own tests after.
  */
class NameViewSpec extends AnyWordSpec with Matchers with TwirlSpec with GovukServiceChecks with PageCatalogue {

  private val view = inject[views.html.NameView]
  private val form: Form[String] = Form(single("value" -> text))

  "the application" should {
    "be built from the service's own configuration" in {
      app.configuration.get[String]("consumer.marker") mustBe "from-the-service"
    }
  }

  // a test for each rule in each language, then the view's own tests
  cataloguePage(staticPage("NameView")(view(form))) {

    "render its CSRF field, as it does in production" in {
      render(view(form)).css("input[type=hidden][name=csrfToken]").elements must have size 1
    }

    "render in Welsh from the languages the service declares" in {
      inLanguage(Lang("cy"))(renderPage(view(form)).h1.text) mustBe "Beth yw eich enw?"
    }

    "say what it should" in {
      render(view(form)) must display(
        heading("name.heading"),
        textInput("value").labelled("name.heading"),
        submitButton("site.continue")
      )
    }

    "have been asserted in full" in {
      render(view(form)) must assertEverything
    }
  }

  "the message files" should {
    "agree across the service's languages" in {
      messagesApi must beConsistentAcrossLanguages()
    }
  }
}
```

Three of its tests belong to this example rather than to the pattern: the
configuration marker, the CSRF field and the Welsh heading prove things about
the library in this build. A service's spec keeps the catalogue, the tests of
what the view shows, `assertEverything` and, in one spec only, the message
files.

How to write each part:

- **The page case.** A view without a form is `staticPage("Label")(view(...))`.
  A view with a form is `formPage("Label", form)(view(_, ...))`: it renders
  blank, with the errors of the form submitted untouched, and with those of
  each named submission, such as `"part of a date" -> Map("value.day" -> "1")`.
  A named submission that gives no errors fails its test.
- **The arguments.** Take views and their dependencies from the application:
  `inject[NameView]`, `inject[AppConfig]`, `inject[NameFormProvider].apply()`.
  Build the model a view takes from the service's own test fixtures where it
  has them.
- **The expectations.** Read the template's `@messages("...")` calls to find
  the keys, and name each thing the view shows by its key: `title`, `heading`,
  `paragraph`, `textInput(...).labelled(...)`, `submitButton(...)`, `backLink`.
  [Writing view specs](writing-view-specs.md) covers every control, and the
  [README reference](../README.md#reference) lists every expectation.
- **What an expectation looks at.** The GOV.UK-shaped expectations find GOV.UK
  markup: `paragraph` reads GOV.UK body text (`p.govuk-body` and its sizes) and
  any `<p>` inside `<main>`, so it finds nothing in a component rendered without
  a layout. For an element of any shape, name it by its id:
  `elementWithText("hello", "hello.key")`.
- **Completeness.** `assertEverything`, last, fails while anything the page
  shows has not been asserted by an earlier test of the same spec. It counts
  ids, links and tracked elements, so `content("key")`, which finds text
  anywhere, does not count for the element with an id that shows it;
  `elementWithText(id, key)` does. [Coverage](coverage-and-entry-points.md)
  explains what counts.
- **Components.** A component rendered without a layout has no `<html>`, so
  the rules about whole pages (one `<h1>`, a title, a declared language) stay
  silent on it. The catalogue still lists them as met; they have nothing to
  find, not nothing wrong.
- **The message files.** Check them once, in one spec, as above, not in every
  view spec.

**Done when** `sbt "testOnly views.NameViewSpec"` passes. Repeat for each view.

## Step 4: run it, and read the run

```sh
sbt "testOnly views.*"                              # every view spec
sbt "testOnly views.NameViewSpec"                   # one view
sbt "testOnly views.* -- -n twirlspec.lang.cy"      # only the Welsh checks
sbt "testOnly views.* -- -n twirlspec.rule.one-h1"  # one rule on every page
```

A failing test prints every finding on the page, each with what was expected,
what was found and, where the library knows one, a hint, followed by the page's
outline as the library saw it. [Failure messages](failure-messages.md) reads one
line by line.

For a report of the whole run, as HTML and as Markdown for a CI summary, add the
reporter to `build.sbt`:

```scala
Test / testOptions += Tests.Argument(
  TestFrameworks.ScalaTest, "-C", "io.github.frikit.twirlspec.TwirlSpecReporter"
)
```

It writes `target/twirl-spec-report/index.html` and `report.md`.

## When a check fails, decide in this order

1. **The template is wrong.** This is the usual case, and the fix belongs in
   `app/views`, not in the spec: a label whose `for` misses its input, an error
   summary link to an id that does not exist, two elements with one id, a
   message key with no Welsh translation.
2. **The spec is wrong.** A key the template does not use, or an expectation
   that matches more than one element: narrow it, with `cssSelector` and
   `elementCount` for groups.
3. **The rule cannot apply to this page.** Excuse that page alone, from that
   rule alone, and say why:
   - in a catalogue, by the failing test's name: a test named `meet <id> in
     <lang>` is excused with `staticPage(...)(...).exceptStandards("<id>")`, one
     named `translate consistently by <id>` with `.exceptTranslation("<id>")`.
     A page case takes no reason, so give it in a comment beside the call;
   - in a single test, `meetStandardsExcept(Seq("rule-id"), because = "...")`.

   Then add a last test that checks no exclusion has outlived its reason:
   `unusedExclusions mustBe empty`.
4. **The layout puts it there.** Content the layout gives every page, which no
   view spec should assert, is left out of `assertEverything` through
   `coverageIgnored` (ids) or `coverageIgnoredSelectors` on the spec base.

Never make a run green by:

- removing a rule trait, or overriding `standardsRules` to drop a rule, for
  every page;
- switching `display` to `displayOnly`, or deleting the failing test;
- writing a Welsh, or any other, translation the service did not supply. A
  missing translation is a finding to report, not text to invent.

Ask the person you work for before going on when one rule fails on most pages,
when the fix would change wording or behaviour a user sees, or when the fix is
a design decision rather than a markup one.

## Done means

- [ ] Every view and component has a spec, named after it, and every spec passes
      in every configured language.
- [ ] Every exclusion names one rule on one page and carries its reason, and
      `unusedExclusions` is empty.
- [ ] The message files are checked once, with `beConsistentAcrossLanguages()`.
- [ ] Specs that end in `assertEverything` pass it.
- [ ] `sbt test` passes as a whole, not only `testOnly`.

## MUnit

A service on MUnit extends `TwirlSuite` instead, which gives the same
application, DSL, rule traits and catalogue. This suite is compiled and run on
every change to the library:

<!-- compiled: consumer/service/test/views/NameViewSuite.scala -->
```scala
package views

import io.github.frikit.twirlspec.{GovukServiceChecks, TwirlSuite}
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.i18n.Lang

/** The same view, from an MUnit suite: TwirlSuite for the application and
  * implicits, the page catalogue as a test for each check, and assertions over
  * the same expectations.
  */
class NameViewSuite extends TwirlSuite with GovukServiceChecks {

  private val view = inject[views.html.NameView]
  private val form: Form[String] = Form(single("value" -> text))

  cataloguePage(staticPage("NameView")(view(form))) {

    test("NameView renders in Welsh from the languages the service declares") {
      assertEquals(
        inLanguage(Lang("cy"))(renderPage(view(form)).h1.text),
        "Beth yw eich enw?"
      )
    }

    test("NameView says what it should") {
      assertDisplays(
        renderPage(view(form)),
        heading("name.heading"),
        textInput("value").labelled("name.heading"),
        submitButton("site.continue")
      )
    }
  }

}
```

Assertions are `assertDisplays`, `assertMeetsStandards`,
`assertTranslatesConsistently`, and `assertMatches(page, matcher)` for any other
matcher, such as `assertMatches(page, assertEverything)`. Tags select part of a
run with `-- --include-tags=twirlspec.lang.cy`. A suite that overrides
`beforeAll` or `afterAll` must call `super`. `TwirlSpecReporter` is
ScalaTest-only.

## Instructions to keep in the service

So the next agent working on the service follows the same conventions, add a
block like this to the service's own `AGENTS.md`, or whatever file its agents
read:

```markdown
## View specs (twirl-spec)

- Every Twirl view and component in `app/views` has one spec, named after it,
  in the mirrored package under `test/views`, extending `ViewSpecBase`.
- Each spec puts its view through `cataloguePage(staticPage(...))` or
  `cataloguePage(formPage(...))`, then tests what the view shows with
  `display(...)`, naming text by message key.
- A failing rule is fixed in the template. An exclusion names one rule on one
  page, with its reason, and `unusedExclusions` stays empty.
- Never invent a translation; report a missing one.
- Run `sbt "testOnly views.*"` after changing a view or a message file.
- Guide: https://frikit.github.io/twirl-spec/docs/agents.html
```

For agents that load skills, the same as a `SKILL.md`:

```markdown
---
name: twirl-spec
description: Write, fix and run twirl-spec view specs for this Play service's Twirl templates. Use when adding or changing a view, a component or a message file, or when a view spec fails.
---

# twirl-spec view specs

1. Find the view's spec under `test/views`, named after the view; create it,
   extending `ViewSpecBase`, if it is missing.
2. Put the view through `cataloguePage(staticPage(...))`, or `formPage(...)` for
   a view with a form, and assert what it shows with `display(...)`, naming text
   by the message keys the template uses.
3. Run `sbt "testOnly views.<View>Spec"`. Read every finding and the outline
   under it.
4. Fix the template first. Excuse a rule only for one page, with its reason.
   Never invent a translation.
5. Finish with `sbt "testOnly views.*"`.

Procedure and decisions: https://frikit.github.io/twirl-spec/docs/agents.html
Every page, as an index: https://frikit.github.io/twirl-spec/llms.txt
```

## Where a problem is explained

| Symptom | Page |
|---|---|
| a message key "is not defined for lang `cy`" | [Troubleshooting](troubleshooting.md) |
| "2 elements matched, so this assertion is ambiguous" | [Troubleshooting](troubleshooting.md) |
| the application does not start in a test | [Troubleshooting](troubleshooting.md#the-application-will-not-start) |
| a rule fires on a component rendered without a layout | [Rules and standards](rules-and-standards.md#page-level-rules-and-fragments) |
| warnings print but nothing fails | [Rules and standards](rules-and-standards.md#blocking-rules-and-warnings) |
| `assertEverything` reports what the layout put there | [Coverage and entry points](coverage-and-entry-points.md) |
| a bilingual page says different things in each language | [Languages and message files](languages-and-messages.md) |
| replacing a home-grown spec base, or moving from 1.x or 2.x | [Adopting in an existing service](adopting.md) |
