# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/) from 1.0.0 — see *Versioning* in the
README for what a minor release may and may not do.

Every push to `main` that changes something a user could depend on is a
release, and each one has a GitHub Release whose notes are its section here. Every release on the current major line is
recorded in full; earlier lines are kept to one entry each, so the file stays
readable.

## [3.0.0] - 2026-11-13

### Changed

- The 2.x line closes, and 3.x begins as the stable line, from 2.15.0's code. The
  public API, the rule ids, the published dependencies and every verdict are
  those of 2.15.0, so a 2.x service moves by its version alone: see
  [Moving from 2.x to 3.x](docs/adopting.md#moving-from-2x-to-3x). Binary
  compatibility is held against 3.0.0 from here, and a minor version may change
  only what the README's Versioning section says it may.
- The changelog keeps the 2.x line to one entry, as it keeps 1.x. Each 2.x
  release's notes stay on its GitHub Release.

## [2.x]

Nineteen releases, from 2.0.0 on 2026-09-13 to 2.15.0 on 2026-11-13, for Scala
3 only, built against Play 3.0.0 and Twirl 2.0.1, with jsoup 1.24.1 from 2.7.0.

- **Rules:** the line added `GovukServiceChecks` and `HmrcServiceChecks`. Among
  its new rules are `i18n-same-submissions`, `button-has-name`,
  `post-form-has-csrf-token` and `messages.malformed`.
- **Expectations:** among the new ones are `within`, `columnHeaders`,
  `summaryList` keys with arguments, and `matchOutlineSnapshot`.
- **Reading markup:** `tags-are-balanced` reads it in one pass, as HTML's
  tokenizer does, and the rules read a page alike in jsoup's two query modes.
- **Catalogue and report:** `PageCatalogue` became a table of a service's pages,
  with a test for each state, rule and language. `cataloguePage` puts a view's
  own tests under its label, and `TwirlSpecReporter` reports a run as a page.

Each release's notes are on
[its GitHub Release](https://github.com/frikit/twirl-spec/releases).

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

[Unreleased]: https://github.com/frikit/twirl-spec/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/frikit/twirl-spec/compare/v2.15.0...v3.0.0
[2.x]: https://github.com/frikit/twirl-spec/compare/v1.0.0...v2.15.0
[1.x]: https://github.com/frikit/twirl-spec/releases/tag/v1.0.0
