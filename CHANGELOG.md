# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/) from 1.0.0 — see *Versioning* in the
README for what a minor release may and may not do.

## [Unreleased]

### 1.0.0 — first stable release

Nothing before 1.0.0 was published, so this section describes the library as it
ships rather than a delta. For anyone who built a 0.x snapshot locally, the
changes since then that would break a spec are listed under *Changed*.

#### Added

- `twirl-spec-core` — the page model, the expectation DSL and the ScalaTest
  matchers, with no rules of its own.
- `twirl-spec-wcag` — 29 rules: 21 each tied to a WCAG success criterion, one
  structural convention, four for Twirl rendering mistakes, three for safety.
- `twirl-spec-govuk` — five GOV.UK Design System error conventions.
- `twirl-spec-aria` — 16 ARIA correctness rules, the static half of what an
  automated accessibility tool reports, needing no browser.
- `twirl-spec-html` — basic HTML validity: an unclosed element is named with
  the line it opened on; unknown elements and attributes. `TagBalance.checkTemplate`
  reads a Twirl template directly, with the Scala taken out first.
- `twirl-spec-quality` — semantics, page weight and metadata rules, plus
  `assertEverything`, which fails when a page shows something no test asserted,
  and `entryPointsAgree`, which reaches the `render`, `f` and `ref` Twirl
  generates beside `apply`.
- `twirl-spec-i18n` — the same view rendered in several languages compared
  against a base: structure, links, controls, headings, and whether anything
  was translated at all.
- `twirl-spec-messages` — message-file integrity, every configured language
  measured against a base.
- `twirl-spec-all` — everything in one dependency, and `AllChecks`.
- `normalised` and `messageText` on the DSL, so page text and a message value
  compare the same way.

#### Changed

- **Packages.** Rule sets moved out of `io.github.frikit.twirlspec.standards`
  into the package of the module that owns them: `.wcag`, `.govuk`, `.quality`.
  `.standards` keeps `Rule`, `RuleSet`, `Criterion`, `Level` and `WcagVersion`.
- **Languages are configuration, not API.** `TwirlSpec.welsh` and `inWelsh`
  are gone; use `inLanguage(Lang("cy"))`. The default application configures
  `play.i18n.langs = ["en"]`; a bilingual service declares its languages
  through `applicationConfig`.
- **Messages module generalised** from English-and-Welsh to base-and-others.
  `Config.requireWelsh` is now `requireTranslations`, `Config.baseLanguage`
  is new (default `"en"`), `englishMessages` is now `baseMessages`, and the
  rule ids `messages.welsh-parity` and `messages.english-parity` are now
  `messages.translation-parity` and `messages.base-parity`.
- Failure messages are built only when a check fails. They include the page
  outline, which walks the whole document, and that work was being done on
  every passing assertion.
- Scala 3 artifacts are built with 3.3.8; sbt 1.13.0; Play 3.0.11;
  jsoup 1.23.2; scalafmt 3.11.5.

[Unreleased]: https://github.com/frikit/twirl-spec/compare/main...HEAD
