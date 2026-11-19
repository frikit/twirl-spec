# Architecture

A map of twirl-spec for anyone changing it, written so a coding agent can find
its way without reading every file first. [AGENTS.md](AGENTS.md) says how to
work here (checks, release cycle); [CONTRIBUTING.md](CONTRIBUTING.md) says what
the project promises (compatibility, dependency pins, coverage). This file says
where things are and which constraints the code relies on.

Paths are under `<module>/src/main/scala/io/github/frikit/twirlspec/` unless
written in full. Symbols are named rather than lines numbered, so search for
them.

## The idea in one paragraph

A spec renders a Twirl view in a real Play application, parses the HTML with
jsoup into a `Page`, and checks it two ways at once: the expectations the spec
names (`title("x.title")`, `textInput("value")`) and the rules the spec has
mixed in (accessibility, GOV.UK conventions, markup). Every check returns
`Violation`s rather than throwing, so one failure message can list everything
wrong with the page and end with the page's outline. Rules come from stackable
traits; the page catalogue turns a page into a test for each rule in each
language.

## Modules

Ten published modules; the root aggregate publishes nothing.

| Module | Depends on | Holds |
|---|---|---|
| `core` | — | the page model, the expectation DSL, the matchers, the rule infrastructure, the shared Play application |
| `wcag` | core | `WcagChecks`; `WcagStandards`, `TwirlStandards`, `SecurityStandards` |
| `govuk` | core, wcag | `GovukChecks`; `GovukStandards` |
| `quality` | core | `QualityChecks`, `CoverageChecks`; semantic, performance and metadata rules; entry points |
| `messages` | core | `MessagesMatchers`, `MessagesIntegrity`: checks of the message files themselves |
| `i18n` | core | `I18nChecks`, `TranslationStandards`: comparing a page across languages |
| `aria` | core | `AriaChecks`; `AriaStandards` |
| `html` | core | `HtmlChecks`; `HtmlStandards`, `TagBalance` (template scanning) |
| `all` | all of the above | `AllChecks`, `GovukServiceChecks`, `HmrcServiceChecks`, the page catalogue, `TwirlSpecReporter` |
| `munit` | all | `TwirlSuite`: the library for an MUnit suite |

Dependency sets are in `project/LibDependencies.scala`. Play (`play`,
`play-test`, `play-guice`, and `play-filters-helpers` for core) is `Provided`
at 3.0.0, the oldest supported, so a service's own Play is used; because
`Provided` is not transitive, every module declares it. ScalaTest is a compile
dependency of every module. MUnit belongs to `munit` alone: nothing depends on
that module, so a ScalaTest project never gets MUnit, and `checkPoms` enforces
it.

## core

### Entry traits

- `TwirlSpecDsl` (`TwirlSpecDsl.scala`): the whole DSL and the matchers, with
  no implicits and no application, so it can sit beside a service's existing
  spec base. Renders through `render(html)(implicit Messages)`. Two hooks a
  framework adapter overrides: `alertHook` (prints by default) and
  `templateInstance` (throws by default; needed for entry-point checks).
- `TwirlSpec` (`TwirlSpec.scala`): `TwirlSpecDsl with SuiteLifecycle`, self-typed
  to a ScalaTest `Suite with Alerting`. Adds the shared application (`app`,
  `inject`), languages (`languages` from `play.i18n.langs`, English first;
  `currentLang` in a `DynamicVariable`; `inLanguage`, `inEachLanguage`), the
  implicit `messages` and `request` (language cookie, and a CSRF token where
  `play-filters-helpers` is on the class path), and `renderPage`.
- `SuiteLifecycle` (`SuiteLifecycle.scala`): a `SuiteMixin` whose `run` forgets
  the spec's coverage and exclusion records before and after a run, and turns
  `SuiteRuns`, unless the suite runs each test in a new instance.

### The page

`page/Page.scala`: `final class Page` wraps the jsoup document with the
language, the `Messages` it rendered with, the raw source and parse errors.
Accessors (`css`, `named`, `byRole`, `byId`, the GOV.UK component accessors)
record what they touch for coverage, unless recording is paused
(`withRecordingPaused`), which rules, translation comparisons and `outline` do
so they never count as the spec having asserted something. `Page.fromString`
parses with error tracking on. `page/CoverageRegistry.scala` holds the touched
elements per spec and group.

### Expectations

`expect/`: an `Expectation` has a `description` and
`check(page): Seq[Violation]`. `Violation` carries the rule or expectation,
message, `expected`, `actual`, `where`, severity and `hint`, and renders the
lines a failure shows. `CheckReport` gathers violations into the failure
message, outline included. `Expected` is what a text expectation compares with:
a message key (the default), a literal, a pattern or anything. The DSL methods
live in `FramingExpectations`, `FormExpectations` and `ContentExpectations`,
which `TwirlSpecDsl` extends.

### Matchers

`TwirlMatchers.scala`: `display` (the named expectations plus every active
rule), `displayOnly`, `meetStandards`, `meetStandardsExcept` (an unknown id
fails the match; excluded rules still run, silently, and record whether they
fired), `unusedExclusions`, outline snapshots, and the knobs `standardsRules`,
`failOnWarnings` and `reportWarnings`. `translateConsistently` lives in
`I18nChecks` (i18n), `assertEverything` in `CoverageChecks` (quality).

### Rules

`standards/Rule.scala`: `Rule(id, description, pageLevel, severity, criterion)(run)`.
A page-level rule returns nothing for a fragment, decided from the raw source
(a doctype or `<html>`), because jsoup wraps every fragment in a document. A
rule with `severity = Rule.Warning` reports without failing. A `Criterion`
(number, title, `Level`, `WcagVersion`) ties a rule to WCAG, which
`conformingTo` and the README tables use. `RuleSet` (`all`, `only`,
`allExcept`, which refuse unknown ids) is what each rule module's object
extends. `standards/ExclusionRegistry.scala` records exclusions per spec.

### Rendering

`render/SharedApplication.scala` builds one Play application per distinct
configuration and shares it across the JVM, with metrics, auditing and the CSP
nonce off and deliberately no languages (a value there would override the
service's and let Welsh specs pass in English). `render/CsrfToken.scala` signs
one token per JVM, so two renders of one view are identical, and only
references Play's helper where it is on the class path.

## How the rules are assembled

`standardsRules` starts as `Nil` in `TwirlMatchers` and each rule trait stacks
its set on with `override def standardsRules = super.standardsRules ++ X`
(`WcagChecks`, `GovukChecks`, `QualityChecks`, `AriaChecks`, `HtmlChecks`).
`AllChecks` is the linearisation of all of them, plus `CoverageChecks`,
`I18nChecks` and `MessagesMatchers`, which add no rules.
`GovukServiceChecks` filters `super.standardsRules`, dropping
`MetadataStandards.searchRules` and replacing `scripts-are-deferred` with a
version that allows GOV.UK's tracking-consent script, which is why it must be
mixed in after any rule trait whose rules it should filter.
`HmrcServiceChecks` changes coverage and translation settings, not rules.

A rule set is an object (`HtmlStandards` is a class, for its
`attributePrefixes`, with a default companion) whose `lazy val all` joins
private blocks of rules. The blocks exist because Scala 3.3.9 silently stops
measuring coverage in a block over 3000 tree nodes; `build.sbt` turns that
warning into an error, so the build fails rather than losing coverage.

The README's rule tables, counts and module rows are checked against the rule
sets by `all/src/test/.../ReadmeSpec.scala`, which prints the exact block to
paste when they differ.

## The page catalogue

`all/.../PageCatalogue.scala`:

- `PageCase` is a labelled page: one or more `PageState`s (a name and a render
  thunk) with the rules it is excused from (`exceptStandards`,
  `exceptTranslation`). `PageCheck` is a named check; `CheckCells` remembers
  each check's `CheckCell` (page, kind, check, language, excused), from which
  its tags come.
- `PageCases` (needs `TwirlSpec`): `staticPage` and `formPage` build cases;
  `formPage` renders blank, with the errors of the form submitted untouched,
  and with each named submission. `checksOf(page)` names a check per state and
  language (render), per rule and language (standards) and per rule and
  language against the base (translation); `checkStandards` and
  `checkTranslation` run them all in one test.
- Renders are memoised per state and language, and dropped when
  `SuiteRuns.current` for the suite changes, which is how a second run of the
  same suite instance renders again.
- `PageCatalogue` (`AnyWordSpecLike with PageCases`): `catalogue(pages*)` and
  `cataloguePage(page)(tests)` register each check as a tagged WordSpec test
  under the page's label, then the page's own tests.
- `recordKey` is whose exclusion records an excused rule writes: the spec's own,
  or the MUnit suite's when `TwirlSuite`'s host runs the checks.

## Reporting

`all/.../TwirlSpecReporter.scala` is a ScalaTest `Reporter` that writes
`index.html` and `report.md` (to `target/twirl-spec-report`, or the
`twirlspec.report.dir` property or `TWIRLSPEC_REPORT_DIR`). It knows a catalogue
test by parsing its name with three regexes (`Render`, `Translation`,
`Standard`) rather than by a registry, because sbt can load the reporter in a
different class loader from the tests. `project/TestSummary.scala` (`sbt
testSummary`) turns JUnit XML and `report.md` into CI's job summary.

## MUnit

`munit/.../TwirlSuite.scala`: `TwirlSuite` is an MUnit `FunSuite` with
`TwirlSpecDsl` and `I18nChecks`, repeating `TwirlSpec`'s application and
language surface, with `assertDisplays`, `assertMeetsStandards`,
`assertTranslatesConsistently` and `assertMatches` bridging any ScalaTest
matcher to an MUnit failure. Its catalogue runs on a private `CatalogueHost`
(`AnyFunSuite with PageCases`, never run itself) that forwards the
application, language, rules, warnings, translation settings and `recordKey`
to the suite, so pages render in the suite's language. A fixture's
`TestCanceledException`, `TestPendingException` or MUnit `assume` becomes an
MUnit skip. `beforeAll` and `afterAll` do what `SuiteLifecycle` does.

## Constraints the code relies on

Each of these has broken something before, or would.

- **Test names are a contract.** The catalogue's names (`render <state>, in
  <lang>`, `meet <id> in <lang>: <description>`, `be excused from …`,
  `translate consistently by <id>, <lang> against <base>: …`) are parsed by
  `TwirlSpecReporter` and reproduced by `TwirlSuite`. Change them in all three
  places, with tests, or not at all.
- **Binary compatibility.** MiMa checks every module against the previous
  stable release. In a trait, a `super` call compiles to a super accessor and a
  new `lazy val` or `val` to a new field, both of which break a class compiled
  against the old trait; per-suite state therefore lives in objects keyed by the
  suite (`SuiteRuns`), and new behaviour goes in new members. A deliberate
  break is a `#major` release.
- **Initialisation order.** The catalogue names its checks when
  `catalogue(...)` runs in the constructor, so `standardsRules`,
  `applicationConfig` and anything they read must be a `def`, a `lazy val` or a
  `val` declared above the call. `HtmlChecks` holds its rule set in a
  `lazy val` for the same reason.
- **Coverage is 100% statements and branches,** with no `$COVERAGE-OFF$`
  anywhere. After a compiler bump, compare each module's statement count with
  the last run: a drop with 100% still reported means the compiler skipped a
  block.
- **Rules and comparisons never count as coverage.** Anything that reads a page
  on the library's behalf pauses recording, or `assertEverything` would pass on
  the library's account rather than the spec's.
- **Scala 3.3 LTS.** The library compiles with Scala 3.3.x so its TASTy is
  readable by every later Scala 3; Scala Steward is pinned to 3.3.
  `-release 17` keeps the class files on Java 17.
- **Play is the service's.** Never make Play, twirl-api or the Play test
  helpers a normal dependency; `checkPoms` fails a POM that does.
- **No Python or other extra languages** in the build. Build and CI helpers
  are sbt tasks in `project/` (`testSummary`, `checkPoms`) or shell scripts in
  `.github/scripts`.
- **Suites run one at a time** (`Test / parallelExecution := false`, one test
  task across modules), because they share one Play application and one suite
  exercises its shutdown hook.

## Tests

Each module's tests are in `<module>/src/test/scala/io/github/frikit/twirlspec/`.
Modules depend on `core`'s tests (`test->test`) for the shared fixtures:

- `core/src/test/scala/.../Bilingual.scala`: a `TwirlSpec` with English and
  Welsh;
- `core/src/test/resources`: `application.conf`, `messages`, `messages.cy`, and
  `captured/govuk-components-*.html`, real play-frontend-hmrc output;
- `core/src/test/twirl/testviews/`: Twirl views compiled for the tests.

The documentation is tested too, in `all/src/test`:

| Spec | Holds |
|---|---|
| `ReadmeSpec` | the README's rule tables, criteria table, translation table and rule counts against the rule sets |
| `DocsContentSpec` | every install line in `README.md` and `docs/*.md` against the newest `CHANGELOG.md` section; each `<!-- compiled: path -->` snippet against the file it names |
| `DocsLinksSpec` | relative links resolve; a published page links to a repository-only file by its GitHub URL; `_config.yml`'s header pages exist |

`consumer/` is a separate build of two Play applications that depend on the
artifacts `publishLocal` made, run in CI on the oldest and newest supported
Scala, Java and Play combinations. Its specs are quoted in the docs as compiled
snippets.

## Build, CI and release

- `build.sbt`: Scala 3.3.9; `-release 17`; unused imports and skipped coverage
  are errors; MiMa against `previousStableVersion`; scalafmt and licence headers
  per project; a pre-push hook in `.githooks/pre-push`, installed when sbt loads,
  that runs the format and header checks and the tests.
- `project/CheckPoms.scala` (`sbt checkPoms`): ten POMs, Play `provided` at
  3.0.0, Scala on 3.3, no test dependencies, MUnit only in `twirl-spec-munit`.
- `.github/workflows/ci.yml`: format and headers, release-plan tests, MiMa
  (skipped for a planned major), coverage, the test summary, a run with jsoup's
  Web query mode, and the consumer matrix.
- `.github/workflows/release.yml`: on every push to `main`, runs CI, then
  `.github/scripts/release-plan.sh` decides the version from tags and commit
  subjects (`#minor`, `#major`, `[skip release]`), checks the changelog names
  it, publishes to Maven Central, pushes the tag and creates the GitHub
  Release.
- `.github/workflows/docs.yml`: after a release, builds the Jekyll site from
  the newest tag and the Scaladoc of every module (`sbt unidoc`) under `/api`.

## Where to start for common changes

| Change | Start at | Also update |
|---|---|---|
| a new rule | the rule module's `…Standards` object, in a block under 3000 tree nodes | its tests; the README table `ReadmeSpec` prints; the rule count in prose |
| a new expectation | `core/.../expect/` (the matching `…Expectations` trait) | `ExpectationDslSpec`; the README reference; `writing-view-specs.md` |
| a catalogue behaviour | `all/.../PageCatalogue.scala` | `PageCatalogueSpec`; the reporter and `TwirlSuite` if names or tags change |
| the MUnit adapter | `munit/.../TwirlSuite.scala` | `TwirlSuiteSpec`; `consumer/service/test/views/NameViewSuite.scala` |
| a dependency | `project/LibDependencies.scala` | `checkPoms` if a scope changes; the README compatibility table |
| a guide | `README.md`, `docs/*.md` | the three docs specs; `docs/README.md`'s table and `llms.txt` for a new page |
