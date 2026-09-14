# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/) from 1.0.0 — see *Versioning* in the
README for what a minor release may and may not do.

Every push to `main` is a release, and each one has a GitHub Release whose
notes are its section here. Every release on the current major line is
recorded in full; earlier lines are kept to one entry each, so the file stays
readable.

## [Unreleased]

### Added

- A documentation site at
  [frikit.github.io/twirl-spec](https://frikit.github.io/twirl-spec/), built
  from the repository itself, with the Scaladoc of every module rendered as one
  site under `/api`.

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
