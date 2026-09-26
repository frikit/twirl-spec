# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/) from 1.0.0 — see *Versioning* in the
README for what a minor release may and may not do.

Every push to `main` is a release, and each one has a GitHub Release whose
notes are its section here. Every release on the current major line is
recorded in full; earlier lines are kept to one entry each, so the file stays
readable.

## [2.2.0] - 2026-09-26

What a spec renders changes in this release, so it is a minor version: a
bilingual service's Welsh specs now render in Welsh, and a form now renders
its CSRF token field. A spec that passed on the English page, or on a form
without its token, can fail after it.

### Added

- `GovukServiceChecks`, in `twirl-spec-all`: `AllChecks` for a GOV.UK
  transactional service, which leaves out the three rules about search
  results. `has-meta-description` fires on every page the GOV.UK template
  renders, since the template writes no description; `title-is-concise` on
  most titles in the Design System's "Page - Service - GOV.UK" pattern; and
  `not-noindex` on a service that keeps itself out of search on purpose. Its
  `scripts-are-deferred` also leaves alone the tracking-consent script
  play-frontend-hmrc puts in the head wherever `tracking-consent-frontend` is
  configured, which has to run first, and still reports the service's own
  head scripts. `AllChecks` is unchanged.
- `MetadataStandards.searchRules`, those three rules, for a spec base that
  picks its modules by hand and wants to leave them out too.
- `PerformanceStandards.scriptsAreDeferredExcept(selectors*)`:
  `scripts-are-deferred`, leaving alone the head scripts the selectors match.
- `page.formSubmission`: every name and value a browser would submit, in
  document order, with a repeated name listed once per value.

### Fixed

- `TwirlSpec` no longer overrides the service's languages.
  `SharedApplication.viewTestDefaults` set `play.i18n.langs` to `en`, which
  replaced the service's own `conf/application.conf`: a bilingual service's
  `inLanguage(Lang("cy"))` rendered English, and every Welsh assertion passed
  against the English page. The languages are now whatever the service
  declares, and `applicationConfig` can still change them for the specs.
- A language the application is not configured for is refused. `inLanguage`
  and `renderIn` fail, naming the configured languages, instead of letting
  Play render the first configured language in its place. Regions follow
  Play's own lookup, which narrows `en-GB` to a configured `en`; a language
  asked for without a region is also found in its configured regional form
  (`cy` in `cy-GB`), which that lookup alone would miss. The page, its
  messages and the request's language cookie then all use the configured
  form, and `languages` puts a regional English such as `en-GB` first, as it
  did `en`.
- `TwirlSpec`'s request carries a signed CSRF token. A view that renders
  Play's `@helper.CSRF.formField` threw "No CSRF token was generated for this
  request", and play-frontend-hmrc's `formWithCSRF` left the token field out,
  so a form rendered unlike the one a citizen sees. The token comes from
  `play-filters-helpers`, now a `Provided` dependency of the core, which a
  Play application built with the Play sbt plugin already has; without it on
  the classpath the request carries no token, as before.
- The message-file check no longer says a file is missing when Play simply
  did not load it. Play reads `conf/messages.<lang>` only for the languages
  `play.i18n.langs` declares, and the hint now says so.
- A name taken from content reads an image's alt text where the image stands.
  `<a><img alt="Download"> PDF</a>` was named "PDF", because the alt text was
  consulted only when there was no text at all; it is now "Download PDF", as a
  screen reader announces it.
- `formValues` reads a form the way a browser submits it. A select with no
  option marked submitted nothing and now submits its first enabled option; a
  disabled control, or an option in a disabled group, submitted its value and
  now submits nothing; a checked box without a value submitted an empty string
  and now submits `on`; and of two ticked boxes sharing a name only the last
  was seen, where now `formValues("tick" -> "a")` passes when `a` is among the
  values. Reset and image inputs are no longer counted.
- A control inside the first legend of a disabled fieldset is enabled, as HTML
  has it, for `disabled`, `enabled` and the form values alike.
- Whether markup is a whole page, which decides if page-level rules run, is
  read from how the document opens. A page whose doctype came after two
  thousand characters of whitespace or comments was taken for a fragment and
  skipped those rules, and a fragment that merely mentioned `<html` was taken
  for a page.

## [2.1.0] - 2026-09-22

Rule verdicts change in this release. A page that passes today can fail after
it — `no-password-in-get` in particular now sees a shape it used to miss — so
this is a minor version rather than a patch, for the reason set out under
*Versioning* in the README.

### Added

- A documentation site at
  [frikit.github.io/twirl-spec](https://frikit.github.io/twirl-spec/), built
  from the repository itself, with the Scaladoc of every module rendered as one
  site under `/api`.

### Fixed

- `no-password-in-get` sees a form that names no method. HTML submits such a
  form by GET, and that is the shape the mistake ships in; the rule matched
  only an explicit `method="get"`.
- `no-aria-hidden-focusable` accepts the fix its own hint prescribes. An
  element taken out of the tab order with `tabindex="-1"` no longer counts as
  focusable, so following the advice clears the rule instead of leaving the
  test red.
- `zoom-not-blocked` reads `maximum-scale` as a number rather than matching it
  as text. `maximum-scale=10`, which allows ten times the size, was reported as
  blocking zoom because it contains `maximum-scale=1`. A cap below 2 is still
  flagged: that is the 200% WCAG 1.4.4 asks for. The value is read the way a
  browser reads it — the leading number if there is one, so `1e-1` is a tenth
  and `10junk` is ten; then the words it knows, `yes` being 1 and
  `device-width` 10; and then 0 for `no`, for a word it does not know, and for
  no value at all, each of which is a page that will not zoom. Only a negative
  number caps nothing, because it translates to auto. Where the directive
  appears more than once the last one applies whatever it says, so a trailing
  `maximum-scale=-1` lifts the cap an earlier one set. The content is read as
  directives rather than having its whitespace stripped out, so
  `maximum-scale=1 0` is a cap of 1 and not of 10, and the violation now
  quotes the content as the page wrote it. Whitespace separates one directive
  from the next as a comma does, so a cap in
  `width=device-width maximum-scale=1` is no longer missed.
- `user-scalable` is read by the same translation rather than matched as the
  literal `no`: `yes`, `device-width`, `device-height` and a number at 1 or
  beyond in either direction leave scaling on, while a number between -1 and 1
  — and any value a browser does not know, including no value at all — turn it
  off. `user-scalable=nope` and `user-scalable=0` are caught.
- `target-blank-is-safe` accepts `rel="noreferrer"`, which severs
  `window.opener` just as `rel="noopener"` does.
- `link-has-name` no longer reads a decorative image as a link's name: an
  `<img alt="">` inside an otherwise empty link named it, because the rule
  asked whether an `alt` attribute was present rather than whether it said
  anything. Neither does an image hidden from assistive technology with
  `aria-hidden` or `role="presentation"`, which is announced to nobody.
- Content hidden from assistive technology is no longer part of an element's
  name. `<a>Next<span aria-hidden="true"> →</span></a>` is named "Next", and a
  link whose only content is hidden is named by nothing, because that is what
  a screen reader announces. Whether the element *itself* carries
  `aria-hidden` is a separate question, and `no-aria-hidden-focusable`
  answers it.
- An id containing a quote no longer throws. The label lookup built the id
  into a selector string, where `label[for="a"b"]` does not parse, and an
  exception from a check is worse than a wrong answer from it.
- A label wrapped around a `<select>` or `<textarea>` no longer borrows the
  control's own content as its text, so a country picker is named "Country"
  rather than by whichever option happens to be selected.
- A control is associated only with a `<label>`, and only on an exact id.
  `for` on anything else — `<output for="total">` — labels nothing, and an id
  reference is case-sensitive, so `for="Total"` does not name `id="total"`.
- Hidden content is left out of a name taken from elsewhere, not only one
  taken from the element's own content: a label, a legend or the target of an
  `aria-labelledby` is read the way it is announced, so a label reading only
  `<span aria-hidden="true">Required</span>` names nothing. Where the
  referenced element is itself hidden its whole subtree still counts, which is
  what ACCNAME exempts and what makes pointing `aria-labelledby` at a hidden
  element work at all.
- A `tabindex` padded with spaces is read as the number HTML says it is, for
  both `no-positive-tabindex` and `no-aria-hidden-focusable`.

### Changed

- One answer to "what is this element called". `link-has-name`,
  `submit-has-name` and `labelled-controls` each computed an accessible name
  their own way and disagreed with each other; all three now ask
  `AccessibleName`, which is what `page.accessibleName` and role-and-name
  matching already used. So a link or submit control named by
  `aria-labelledby` is accepted, and an `aria-labelledby` that resolves to
  nothing no longer names a control.
- `AccessibleName` follows the name computation more closely: an `<input>`'s
  `value` names it only where the value is the name (`button`, `submit` and
  `reset`), a `<select>`'s options and a `<textarea>`'s content are no longer
  read as its name, `title` is used when nothing else names the element, and so
  is the alt text of an image standing in for a link's content.
- `main-landmark` counts the landmark `single-main` counts — `<main>` or
  `role="main"` — so a page can no longer be told at once that it has no main
  landmark and too many. `page.main` is unchanged and still answers to GOV.UK's
  `#main-content`, which scopes an assertion but is not itself a landmark.

## [2.0.1] - 2026-09-13

### Added

- Scaladoc on every public member, so the published javadoc jars and an
  IDE's quick documentation say what each accessor and builder does.
- A `docs` folder: getting started, writing view specs, rules and standards,
  languages and message files, coverage and entry points, failure messages,
  adopting the library in an existing service, and troubleshooting.

### Fixed

- The `display` matcher was described as holding a page to "the GOV.UK
  standards"; it holds it to whatever `standardsRules` resolves to.

## [2.0.0] - 2026-09-13

A major version because the Scala 2.13 artifacts are gone; the library is
otherwise source-compatible with 1.0.x.

### Added

- `page.errorSummaryDanglingLinks`: the error summary entries whose link lands
  on no element. The `errorSummary` expectation and the `error-summary-targets`
  rule both read it, rather than each working it out.

### Changed

- `renderPage` and `renderInEachLanguage` group coverage by the spec, as
  `render` already did, so `assertEverything` counts assertions made through
  them.
- `submitButton()` records only the button it resolves to as asserted, rather
  than every button on the page.
- `CoverageChecks` and `I18nChecks` extend `TwirlSpecDsl` like the other check
  traits, instead of requiring it through a self-type. Nothing changes for a
  spec that already mixes in `TwirlSpec` or `TwirlSpecDsl`.
- Every rule set is built once rather than on each `display(...)`.
- Built against Play 3.0.0 rather than 3.0.11, so a consumer on any 3.0.x
  resolves it without an upgrade.
- `new HtmlStandards(...)` takes its attribute prefixes explicitly; the
  `HtmlStandards` object remains the set with none.

### Removed

- The Scala 2.13 artifacts. The library is built for Scala 3 only, on the 3.3
  LTS line, and a consumer on 2.13 can no longer resolve it.

### Fixed

- The README reference described eleven members with the doc comment of the
  trait or class enclosing them, listed a private helper, left out the form
  control expectations, and cut the GOV.UK row of the rule-set table short.
  The rule tables are now held to the code by `ReadmeSpec`.
- The modules' test tasks no longer run concurrently in one JVM while sharing
  an application that one suite stops. CI also no longer tests Scala 2.13
  twice.

## [1.x]

One release, 1.0.0 on 2026-09-03: the first version published, cross-built for
Scala 2.13 and 3 against Play 3.0.11. It shipped the nine modules 2.x still
has: the page model, expectation DSL and matchers in `twirl-spec-core`; the
WCAG, rendering and safety rules in `twirl-spec-wcag`; the GOV.UK error
conventions in `twirl-spec-govuk`; ARIA correctness in `twirl-spec-aria`; HTML
validity in `twirl-spec-html`; semantics, page weight, metadata, coverage and
the Twirl entry points in `twirl-spec-quality`; language parity in
`twirl-spec-i18n`; message-file integrity in `twirl-spec-messages`; and all of
them in `twirl-spec-all`. The renames a service built against a 0.x snapshot
would meet are listed in [the adoption guide](docs/adopting.md).

[Unreleased]: https://github.com/frikit/twirl-spec/compare/v2.0.1...HEAD
[2.0.1]: https://github.com/frikit/twirl-spec/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/frikit/twirl-spec/compare/v1.0.0...v2.0.0
[1.x]: https://github.com/frikit/twirl-spec/releases/tag/v1.0.0
