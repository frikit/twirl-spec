# Adopting in an existing service

Most Play services already have view specs, and most of those share the same
shape: a `ViewSpecBase` that boots an application and provides `messages`, a
`ViewBehaviours` trait with `normalPage`, `pageWithBackLink`,
`pageWithSubmitButton` and friends, and a spec per view that calls them. The
library replaces that scaffolding, and can do so gradually.

## A spec base to replace the old one

```scala
package views

import io.github.frikit.twirlspec.{AllChecks, TwirlSpec}
import io.github.frikit.twirlspec.messages.MessagesIntegrity
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {

  // the languages the service renders in
  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq("en", "cy"))

  // what every page inherits from the layout, so coverage does not ask each spec for it
  override def coverageIgnored: Set[String] = Set("#report-technical-issue", "#hmrc-timeout")

  // text that is the same in every language by design
  override def translationConfig = TranslationConfig(sameTextIsFine = Set("GOV.UK", "HMRC"))

  // the service is bilingual, so the message files must be too
  override def messagesIntegrityConfig = MessagesIntegrity.Config(requireTranslations = true)

  // the furniture every page must carry
  val furniture = expectations(serviceName(), languageToggle, signOutLink, timeoutDialog)
}
```

A view spec then reads like the page:

```scala
class WhatIsYourNameViewSpec extends ViewSpecBase {

  private val view = inject[WhatIsYourNameView]
  private val form = inject[WhatIsYourNameFormProvider].apply()

  "WhatIsYourNameView" should {

    "render the question" in {
      render(view(form)) must display(
        furniture,
        title("whatIsYourName.title"),
        heading("whatIsYourName.heading"),
        backLink,
        textInput("value").labelled("whatIsYourName.heading"),
        submitButton(),
        noErrors
      )
    }

    "render in Welsh" in inLanguage(Lang("cy")) {
      render(view(form)) must display(title("whatIsYourName.title"), heading("whatIsYourName.heading"))
    }

    "have been asserted in full" in {
      render(view(form)) must assertEverything
    }
  }
}
```

## Keeping an existing application

If the old base already builds an application through `GuiceOneAppPerSuite`
and every spec depends on its `messages`, `fakeRequest` or `app`, keep it and
mix in `TwirlSpecDsl` rather than `TwirlSpec`. The DSL declares no implicits
and builds no application, so nothing clashes:

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with TwirlSpecDsl with AllChecks {
  implicit val messages: Messages = ...   // yours, as before
}
```

`render(html)` takes the implicit `Messages` in scope, and `render(html, lang,
messages)` takes explicit ones for another language. The one thing
`TwirlSpecDsl` cannot offer is `inLanguage` and `renderInEachLanguage`, which
need the application; build the pages for each language yourself and pass
them to `translateConsistently`.

## Adopting one view at a time

Nothing requires a big bang. Old specs and new ones can share a test source
tree, and a new spec can start loose and tighten:

1. Mix in `TwirlSpec` alone, with no rule trait, and port the old assertions
   to `display(...)`. The old `pageWithBackLink` becomes `backLink`,
   `pageWithSubmitButton` becomes `submitButton()`, and `normalPage(...)`
   becomes `title`, `heading` and `serviceName()`.
2. Add `WcagChecks`. Fix what it finds in the templates; it is usually labels
   and error-summary links. `meetStandardsExcept` and `displayOnly` are there
   for the page that genuinely cannot comply yet.
3. Add the rest of the traits, or `AllChecks`.
4. Add `assertEverything` as the last test, and delete the assertions the
   coverage report shows to be redundant.
5. Add the message-file checks once, in one spec, and
   `translateConsistently` to the pages that render in every language.

## What changes in a spec base

| Home-grown | twirl-spec |
|---|---|
| a copy of `ViewSpecBase` per service, drifting | `TwirlSpec` or `TwirlSpecDsl`, versioned |
| `normalPage(view, "x")` | `title("x.title")`, `heading("x.heading")` |
| `pageWithBackLink(view)` | `backLink`, or `backLink.to(url)` |
| `pageWithSubmitButton(view, "Continue")` | `submitButton()` |
| `pageWithHint(view, "x.hint")` | `textInput("value").hinted("x.hint")`, which also checks it is announced |
| `doc.select("#value").attr("value")` | `formValues("value" -> "Ada")` or `textInput("value").withValue("Ada")` |
| `doc.title mustBe messages("x.title")` | `title("x.title")`, which reports an undefined key instead of passing on it |
| nothing | `noErrors`, `errorSummary(...)`, `assertEverything`, every rule |

## Moving from 1.x to 2.x

2.0.0 is published for Scala 3 only, on the 3.3 LTS line. A service on
Scala 2.13 stays on 1.0.x. On Scala 3, the API is source-compatible with
1.0.x, with one exception: `new HtmlStandards(...)` takes its attribute
prefixes explicitly, and the `HtmlStandards` object remains the set with none.
Play 3.0.0 is now the floor rather than 3.0.11, so a service on any 3.0.x
resolves the artifacts without upgrading Play.

## Moving from a 0.x snapshot

Nothing before 1.0.0 was published, but a service that built a snapshot
locally will find three renames: the rule sets moved from the `standards`
package into the package of the module that owns them (`wcag`, `govuk`,
`quality`); `welsh` and `inWelsh` became configuration, `inLanguage(Lang("cy"))`
with `play.i18n.langs` declared; and the messages module's `Config` speaks of
a base language and translations rather than English and Welsh
(`requireWelsh` is `requireTranslations`, `englishMessages` is
`baseMessages`).
