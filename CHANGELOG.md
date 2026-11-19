# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/) from 1.0.0 — see *Versioning* in the
README for what a minor release may and may not do.

Every push to `main` that changes something a user could depend on is a
release, and each one has a GitHub Release whose notes are its section here. Every release on the current major line is
recorded in full; earlier lines are kept to one entry each, so the file stays
readable.

## [3.2.1] - 2026-11-19

No change to the library's code or behaviour.

### Added

- [For coding agents](docs/agents.md), a guide that takes adoption as a
  procedure: the facts to collect from a service, the base and rule traits to
  choose, one spec per view through the catalogue, what to do when a check
  fails and what never to do to make a run green, a definition of done, and a
  block of instructions and a skill file to keep in the service. Its examples
  are the consumer build's specs, compiled and run in CI.
- `llms.txt` at the site's root, an index of every page for tools that read one.
- `ARCHITECTURE.md` in the repository: a map of the modules, where each
  behaviour lives, and the constraints the code relies on. `AGENTS.md` points
  to it and names the constraints easiest to break.

### Changed

- The consumer service's ScalaTest spec puts its view through `cataloguePage`,
  as the guide recommends, so CI runs a ScalaTest catalogue on every row.
- `twirl-spec-munit` is held to binary compatibility with 3.2.0 by MiMa, like
  every other module.

## [3.2.0] - 2026-11-15

### Added

- `twirl-spec-munit`, a tenth module: `TwirlSuite`, the library for an MUnit
  suite.
  - It has `TwirlSpec`'s shared application, `inject`, languages, rendering
    and the expectation DSL, and mixes in the same rule traits.
  - Its `catalogue` and `cataloguePage` give a test for each check, named and
    tagged as in ScalaTest, with a check a fixture cancels or leaves pending
    skipped.
  - `assertDisplays`, `assertMeetsStandards`, `assertTranslatesConsistently`
    and `assertMatches`, for any other matcher, fail with the same messages.
  - Nothing else depends on the module, so a ScalaTest project never gets
    MUnit. ScalaTest stays on an MUnit suite's class path, as the rules are
    built on it, and `TwirlSpecReporter` does not report an MUnit run.
- CI's consumer service runs an MUnit suite beside its ScalaTest spec, on every
  row. `checkPoms` holds MUnit to the MUnit module's POM.

## [3.1.0] - 2026-11-15

### Changed

- The library is compiled for Java 17 where it was compiled for Java 21, so a
  service on Java 17 can use it as well. Its classes use nothing newer than
  Java 17 and are Java 17 class files, which Java 21 and later still run, so
  nothing changes for a service already on Java 21. Scala 3.3.0, the first of
  the 3.3 LTS line, stays the oldest Scala it supports.
- CI runs a service's view specs at the floor as well: Java 17, Scala 3.3.0 and
  Play 3.0.0, with the library itself built on Java 17.

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
  tokenizer does. The rules read a page alike in jsoup's two query modes, but
  for one difference jsoup intends: Web mode keeps a class name's case, so a
  GOV.UK or HMRC class written in capitals is found only in the legacy mode. A
  spec's own CSS selector is read in whichever mode jsoup is in.
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

[Unreleased]: https://github.com/frikit/twirl-spec/compare/v3.2.1...HEAD
[3.2.1]: https://github.com/frikit/twirl-spec/compare/v3.2.0...v3.2.1
[3.2.0]: https://github.com/frikit/twirl-spec/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/frikit/twirl-spec/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/frikit/twirl-spec/compare/v2.15.0...v3.0.0
[2.x]: https://github.com/frikit/twirl-spec/compare/v1.0.0...v2.15.0
[1.x]: https://github.com/frikit/twirl-spec/releases/tag/v1.0.0
