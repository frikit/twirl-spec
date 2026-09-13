# Troubleshooting

The questions that come up, with what is going on and what to do.

## "message key `x.title` is not defined for lang `cy`"

The expectation resolved its key in the page's language and the language has
no such key. Either the translation is missing, which is the finding, or the
words were never meant to be a message key, in which case use the `…Text`
form or `literal("...")`.

## "2 elements matched, so this assertion is ambiguous"

An expectation that names one element found several. The page really has two
inputs with that name, or two elements with that id, which is itself a
defect the `unique-ids` rule would report. Where several matches are
legitimate, assert on the group: `cssSelector(".govuk-summary-list__row")`
and `elementCount(".govuk-summary-list__row", 4)`.

## "no element with role `button` announces this name"

The element exists but its accessible name is not what the spec expects. The
finding lists the names that *are* announced, which usually shows the problem:
a `<button>` whose text comes from a message key the template did not
resolve, or a name that comes from an `aria-label` you had forgotten about.
An element with an explicit `role` is matched only by that role, so
`<a role="button">` is not found by `role("link")`.

## "field has no associated label"

The control has no `<label for="...">` matching its id, no `aria-label` and no
`aria-labelledby`. The GOV.UK components produce the label; a hand-written
`<input>` needs one. The one field of a fieldset labelled by its legend can say
so with `labelledByLegend`.

## "the hint is not announced with the field"

The hint text is right but the control's `aria-describedby` does not reference
the hint's id, so a screen reader never reads it. `govukInput` and
`govukRadios` wire this up when you pass `hint`; a hand-rolled hint does not.

## A rule fires on a component that has no layout

Rules about titles, landmarks and metadata are page-level and stay silent for
a fragment, which is anything that does not start with a doctype or `<html>`.
If a component spec sees them, the component is being rendered inside a
layout, or the fragment starts with `<html>` itself.

## The application will not start

`TwirlSpec` builds a `GuiceApplicationBuilder` application with metrics,
auditing and the CSP nonce switched off. A service whose modules need more
configuration at start-up provides it through `applicationConfig`, the same
way it declares its languages. A `NoClassDefFoundError` for a Play class means
one of `play`, `play-test` or `play-guice` is missing from the test
classpath: the library declares them `Provided` so that it never moves your
Play version, and your project supplies them.

## Specs are slow to start

The first spec in a JVM boots a Play application. Every later spec with the
same `applicationConfig` shares it, so a spec base that overrides
`applicationConfig` should do so with a stable value; a fresh map per suite is
a fresh application per suite. `SharedApplication.instanceCount` says how
many have been built, and a service that suspects it is still booting per
spec can assert on it.

## Specs run in parallel

They can. The shared application is built once per configuration and never
stopped until the JVM exits, and the coverage record is keyed by spec, so
suites running side by side do not interfere.

## The Welsh page compares equal to the English one

`inLanguage(Lang("cy"))` renders in Welsh only when `cy` is among the
configured languages; otherwise Play falls back to the first configured
language. Declare the languages through `applicationConfig` on the spec base.

## A view needs an implicit the spec does not have

`TwirlSpec` provides `messages`, `request` and `messagesApi`. Anything else a
view takes implicitly, such as an `AppConfig`, the spec provides:

```scala
implicit val appConfig: AppConfig = inject[AppConfig]
```

## Warnings appear in the test output but nothing fails

That is by design. Warning rules and `.asWarning` expectations are reported
through ScalaTest's alerts on a green run, so a service can see what
`failOnWarnings = true` would start failing. `reportWarnings = false` silences
them on passing tests.

## `assertEverything` reports things the layout put there

Set `coverageScope` to the part of the page the view spec answers for,
`"main, #main-content"` by default, and list what every page inherits in
`coverageIgnored`. Ignoring a wrapper ignores what it holds.

## The template scan reports an element that is fine

`TagBalance.checkTemplate` reads the template's literal markup. An element
opened in one branch of an `@if` and closed in the other looks unbalanced,
because on any single render it is; and markup that arrives from a helper is
invisible to it. Both are limits of scanning without rendering, and the
rendered-page rule `tags-are-balanced` sees the truth.

## Something else

The README lists every expectation, matcher and rule. If the library does
something surprising that these pages do not explain, an issue with the
template and the spec that shows it is welcome.
