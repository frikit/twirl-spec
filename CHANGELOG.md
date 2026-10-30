# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/) from 1.0.0 — see *Versioning* in the
README for what a minor release may and may not do.

Every push to `main` that changes something a user could depend on is a
release, and each one has a GitHub Release whose notes are its section here. Every release on the current major line is
recorded in full; earlier lines are kept to one entry each, so the file stays
readable.

## [2.4.0] - 2026-10-30

Verdicts change in this release, so it is a minor version. `assertEverything`
can fail where it passed before, because it no longer counts what an expectation
merely looked through as asserted. A Welsh assertion can fail where it passed
before, because a message defined only in English no longer satisfies it, and
`translateConsistently` can fail on a page it passed, because it now reads text
that has no id.

### Rules

- `i18n-same-submissions`, new, as a warning: every language offers the same
  choices, by what they submit rather than their labels or their order, and each
  form submits to the same place. A service that loads a separate Welsh list of
  countries can drop one from it without any other check noticing.
- `messages.malformed`, new, as a warning: a message MessageFormat cannot parse, such
  as `Hello {0` or `Hello {name}`, throws when it is shown, however the rest of the
  checks read it.
- `button-has-name`, new, as a warning: a button that does not submit has an
  accessible name, as `submit-has-name` asks of one that does. An icon-only button
  with no label is called nothing at all.
- `iframe-has-name`, new, as a warning: a frame has an accessible name, for WCAG
  4.1.2. A frame the markup hides, such as a tag manager's, is left alone, and what
  is inside an `<iframe>` no longer counts as its name.
- `error-in-summary`, `error-summary-has-inline` and `error-summary-wording`, new,
  as warnings: an inline error has an entry in the error summary, an entry has an
  inline error beside its field, and the two read the same, as the GOV.UK Design
  System asks.
- `skip-link-target`, new, as a warning: the skip link lands on an element. On a
  page whose main content has another id, it silently does nothing.
- `link-names-distinct`, new, as a warning: links called the same go to the same
  place. It goes by the name announced, so a summary list's "Change" links pass with
  their hidden context, and fail without it.

### Added

- The library traces what it does at `DEBUG` through Play's logger, under
  `io.github.frikit.twirlspec`: the application it builds and the settings it
  builds it with, each page rendered, each check and the rules it failed, and
  each coverage and language comparison. A service turns it on with one
  logger line in `logback-test.xml`; nothing is written at any other level.
- `PageCatalogue`, in `twirl-spec-all`: a table of a service's pages, with two tests
  per page, the standards in every language and the comparison across them, over
  each of its states. `staticPage` has one state; `formPage` is blank, with the
  errors of an empty submission, and with those of each submission named, and fails
  on one that gives no errors. A page can be excused from rules it names. Every state
  runs in every language, and a failure reports each that went wrong. `PageCases`
  offers the same pages and checks to any other test style.
- `HmrcServiceChecks`: `GovukServiceChecks` with what an HMRC service would
  otherwise write on its spec base. The report-a-problem link every page inherits
  is left out of coverage in whichever language it reads, and "HMRC" may read the
  same in every language.
- `coverageIgnoredSelectors` leaves out a block by CSS selector, with what it
  holds, for one with no id, and `ContentCoverage.unassertedOutside` beside
  `unasserted`.
- `Path.of("app/views") must haveBalancedTemplates` checks every template under a
  directory, names each problem by its file and line, and fails on a directory
  with no templates. `TagBalance.templatesUnder` lists them.

### Changed

- Arguments straight after `inject[MyView]` apply the view, where they used to bind
  to `inject`'s own `ClassTag` and fail to compile. `inject[MyView].apply(...)`
  still works and compiled code is unaffected. The one source change is passing a
  `ClassTag` explicitly, which now needs `using`.

### Fixed

- `role(...)` and `page.byRole` find what HTML-AAM says, as the README claimed:
  `complementary`, `rowheader`, `searchbox`, `spinbutton`, `slider`, `listbox`,
  `option`, `rowgroup`, `region`, `dialog`, `progressbar`, `meter`, `status`, `term`,
  `definition` and `figure`, which matched nothing. `role("textbox")` no longer finds
  a search input, `role("columnheader")` a row's `<th>`, or `role("combobox")` a
  `<select>` showing several rows. `banner`, `contentinfo` and `complementary` go by
  where the element sits, so a `<header>` in an article is no banner.
- `landmarks-are-distinguishable` no longer counts an unnamed `<aside>` in an article or
  a section as a landmark, so two of them there pass, and treats an element with a
  sectioning role as it does the tag, so a `<header>` in `<div role="main">` is not a
  second banner. A landmark's name goes by `aria-labelledby` first, as everywhere else,
  so two with different `aria-label`s and one `aria-labelledby` target can now fail.
- `aria-required-attr` asks a `meter` for `aria-valuenow`, and a `separator` for it
  when it can take focus. `aria-attr-value-is-allowed` fails `aria-checked="mixed"` on
  `switch`, `radio` and `menuitemradio`, which read it as false.
- `textArea(...).withValue` compares what the textarea holds, as `formValues` does. It
  squashed the page's side only, so `withValue("a\nb")` failed on a textarea holding
  those two lines, and `withValue("a b")` passed.
- `basedOn` fails when no page is in the language it names. It measured against the
  first page in silence, and took that for a pass.
- The page outline leaves a CSRF token out of a form's action, as it differs on every
  render and the outline is meant to be stable enough to commit.
- The release plan reads only `vX.Y.Z` tags in the branch's own history. A `v9.0.0` on
  another branch was taken for the last release, which could skip a release or jump a
  version, and a `v2.4.0-rc1` aborted the plan.
- `heading` fails on a page with more than one `<h1>`, as its documentation said. Two
  headings read as one, so `<h1>A</h1><h1>B</h1>` passed `headingText("A B")`.
- Words in a `hidden` element no longer name what they sit in, as words under
  `aria-hidden` already did, so `<a href="/next"><span hidden>Continue</span></a>` now
  fails `link-has-name`.
- `grouped-choices` reads every member of a group. It read only the first, so a
  fieldset that closed before the second radio passed. What the group needs is a
  fieldset in common with an accessible name, so an empty legend no longer counts, an
  `aria-labelledby` on the fieldset does, and two forms can each have a group of one
  name.
- A fieldset is named by ARIA and then its own legend. It could borrow the legend of
  one nested in it, or fall back on all the text it holds.
- `label-for-resolves` fails a label pointing at what no label can name, such as a
  `<div>`, which clicking the label does nothing for. A custom element is left alone,
  as a script can make it labelable.
- `contentText`, `noContent`, `page.text` and `page.contains` read the page's body. They
  read the `<title>` too, so `contentText` could pass on words only in the tab.
  `no-scala-leakage` still reads the title.
- `no-scala-leakage` reports an object with no `toString`, a lambda, and a case class
  holding `None` in the middle.
- `dateInput(...).withParts()` with no parts fails, where it threw
  `NoSuchElementException`.
- The adopting guide's spec base ignored `#report-technical-issue` and
  `#hmrc-timeout`, which play-frontend-hmrc does not render, so a base copied from
  it left out nothing. It now mixes in `HmrcServiceChecks`. Other guides had drifted
  from the code too: which expectations take a regular expression, what `signOutLink`
  and `phaseBanner` check, and when two specs share an application.
- `assertEverything` credits what an assertion found, not what it looked
  through. One `link(...)` counted every link on the page as asserted, and one
  `role(...).named(...)` every element with the role; `paragraph`, `tableRow`,
  `formPostsTo`, `hiddenInput`, `appearsBefore`, `caption`, the component text
  expectations and the caption read by `heading` did the same on a smaller scale.
  Each now counts only the elements it matched, and `link`, `formPostsTo` and
  `appearsBefore` count nothing when they fail.
- A link's identity for coverage is its words and where it goes, so of two links
  reading "Change" and going to different places, asserting one no longer covers
  the other. The report names the destination, and `coverageIgnored` still takes
  `link "Change"` for every link with those words.
- `trackedAttributes` works beyond the default: an attribute it adds could never
  be covered, because what an assertion touched was recorded with the default
  list.
- `summaryRow`, `summaryList`, `dateInput`, `subheading`, `headingAtLevel`,
  `fieldError`, `errorSummary`, `formValues`, `tableRow`, `legendIs` and
  `describedAs` now count as asserting what they check: the row, its key and any
  value or action named; the date input's container; the heading; the inline
  error; the summary links they name and where those go; the controls behind a
  submitted value; a row's cells; the legend; the elements describing a control.
  `assertEverything` reported all of these as unasserted.
- `paragraph`, `caption`, `warning`, `insetText`, `notificationBanner`,
  `panelTitle`, `panelBody` and `detailsSummary` find the words within one
  element. They searched every match's text run together, so words spanning two
  of them passed.
- `headingAtLevel` honours `matching(...)` and `anyText`, which it compared as
  literal words.
- A message key resolves in a language only when that language defines it. Play's
  `isDefinedAt` also answers from the default files, so a key only in
  `conf/messages` satisfied a Welsh assertion on a page showing English, against
  what the README promised. The default files still count for English, in any
  region, and for the first language `play.i18n.langs` names.
- `i18n-nothing-lost` and `i18n-actually-translated` read text with no id, and the
  whole of a block of text with one, such as a paragraph or a heading. They compared
  only what sat directly in an element with an id, which on most pages is hardly any
  of the content: a Welsh page left entirely in English, or with a paragraph emptied,
  reported nothing. An id on a wrapper such as a `<section>` still means what sits
  directly in it, so `i18n-nothing-lost` can miss text lost from a plain `<div>`
  inside one. `i18n-actually-translated` also reads the title, and leaves out what
  reads as data.
- `i18n-same-links` notices two links with ids swapping their destinations. It
  compared only the set of places, which is unchanged by a swap.
- Warnings follow one policy everywhere. `translateConsistently` and the
  message-file matchers ignored `failOnWarnings` and dropped their warnings on a
  passing run, so `i18n-actually-translated` could not be seen at all unless
  another rule failed. A spec with `failOnWarnings` on can now fail where it
  passed.
- With `TwirlSpecDsl` alone, the route for a spec base with its own application,
  a passing check's warnings go to standard output. They went nowhere, against
  what the docs said. `alertHook` routes them to ScalaTest's alert in one line.
- A failure caused only by warnings says so, rather than "page failed 0 checks".
- A page with two forms no longer fails. `unambiguous-field-names` read the whole
  document as one submission, so two forms each with a CSRF token failed it, and
  `landmarks-are-distinguishable` counted every form as a landmark, which a form
  is only once it has a name. The first now goes by the form each control belongs
  to, leaves out what submits nothing, and accepts a name ending in `[]`.
- An id or a name is compared exactly. Jsoup's attribute selector ignores case, so
  `element("name")` passed on `id="Name"` and a reference to `Hint` resolved to
  `hint`, and it throws on a value with a quote in it, so such an id ended a rule
  with a `SelectorParseException` rather than a verdict.
- `no-raw-message-keys` reads everything in the main content rather than a list of
  elements, which left out buttons, hints, inset text, panels, details and options,
  and also the title, `aria-label`, `alt`, `placeholder` and a button's value. A
  key may have a hyphen. It also stops failing what is not a key: a version such as
  `v1.2`, and a plain pair of words such as `john.smith`, which could be someone's
  answer, unless the service has keys beginning with the first of them.
- `no-aria-hidden-focusable` leaves alone what takes no focus: a disabled control,
  a hidden input, and anything inside `hidden` or `inert`.
- `labelled-controls` reads an image button's `alt` as its name, and `role("button")`
  finds one.
- `input-purpose-autocomplete` does not ask it of a control it does not apply to,
  such as a radio, and reads `tel` and `phone` as whole words, so `hotelName` is not
  a telephone.
- `autocomplete-is-valid` follows the autofill grammar. A valid
  `section-person1 given-name` failed it, and an invalid `given-name family-name`
  passed.
- `html-lang` and `i18n-lang-attribute` accept any region of the page's language,
  in any case, and do not take `english` for `en`.
- `errorSummary(field -> key)` accepts a link to any control of the field, which is
  where a GOV.UK radio group's and date input's links go, to the first radio and
  the day. It needed a link to `#field`, so it could not pass on those. It also
  holds the entries to the order the summary lists them in, as it always said it
  did, so a spec naming them in another order can fail where it passed. Each entry
  now answers for one link, so a summary listing one field twice has a link to spare.
- `fieldError(field, key)` follows the control or its fieldset to the error they
  announce, where the error's id is not `field-error`.
- `timeoutDialog` finds the `meta[name=hmrc-timeout-dialog]` play-frontend-hmrc
  renders. It looked only for a `data-module` no current page has, so it failed on
  every HMRC page.
- `new-tab-is-announced` knows the Welsh "yn agor tab neu ffenestr newydd" and "yn
  agor ffenestr neu dab newydd", and reads `aria-label` for all of its phrases.
- `tags-are-balanced` and `TagBalance.check` do not read a `>` inside a quoted
  attribute value as the end of the tag, so an attribute holding markup, such as
  `srcdoc`, no longer reports stray tags.
- `TagBalance.checkTemplate` is not thrown by an apostrophe in a named fragment,
  which hid every problem from there to the end of the template.
- `known-elements` and `known-attributes` know more of SVG, including `desc`, with
  which a graphic describes itself, and `pathLength`.
- `aria-required-parent` and `aria-required-children` read `aria-owns`, so a child
  owned from elsewhere in the document no longer fails both.
- `aria-required-attr` does not ask an element for state its HTML already gives it,
  such as `aria-level` of `<h2 role="heading">`.
- `entryPointsAgree` handles a template with more than 22 parameters in one list,
  whose `f` is a `FunctionXXL`. It reported that `f` produced different HTML.
- `haveNoDuplicateKeys` reports a file that is not there, or being given none,
  rather than passing on a wrong path. It no longer reads a line of a value carrying
  on past a backslash as a key of its own.

## [2.3.0] - 2026-10-14

Two verdicts change in this release, so it is a minor version.
`translateConsistently` now passes a page whose only difference between
languages is the language switch, and `assertEverything` can fail where it
passed before, because the translation rules no longer count what they read
as asserted.

### Fixed

- `i18n-same-links` accepts a link that differs only by the language it
  names. The language switch points at the other language on each page, and a
  link that carries the page's own language carries a different one on each,
  so every bilingual page rendered with play-frontend-hmrc's language select
  failed the rule, and the only remedy was to drop it. A whole path segment or
  query value naming either language of the pair now reads as the same, a name
  inside a longer segment does not, and a real difference is still reported as
  the page wrote it.
- The translation rules no longer count what they read as asserted.
  `translateConsistently` and `translationDifferences` read every link on both
  pages through the recording accessor, so a spec that ran them before
  `assertEverything` passed coverage for links it never asserted. They now run
  with recording paused, as the standards rules do.

### Changed

- The troubleshooting guide says how to boot a service whose Mongo component
  pings a database as the application starts, without a database: swap the
  component for a stub through `applicationConfig`, or through the builder of
  a spec base with an application of its own.
- CI's consumer build tests the newest Play through the consumer build's own
  default, which Scala Steward keeps current, so the newest row no longer
  names a version that goes stale; the row for a service today stays pinned
  at Scala 3.3.7 and Play 3.0.10. Play 3.0.12 is the newest tested.

## [2.2.0] - 2026-09-26

What a spec renders, and what coverage and exclusions accept, change in this
release, so it is a minor version. A bilingual service's Welsh specs now
render in Welsh, a form renders its CSRF token field, names and form values
follow the browser, `assertEverything` counts assertions for the page they
were made on, and `meetStandardsExcept` refuses an id it does not know. A spec
that passed on any of the old behaviour can fail after it; the Changed and
Fixed sections say how.

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
- `page.coveredAs(name)`: count the assertions on several pages of a spec as
  one record, for the states of one view that should be measured as a whole.
- `meetStandardsExcept(ruleIds, because = "...")`: an exclusion with its
  reason, which a failure reports.
- `unusedExclusions`: the rules a spec has excluded that found nothing on any
  page they were excluded from, for a last test that keeps exclusions honest.

### Changed

- Coverage counts assertions for the page they were made on: every render of
  exactly that markup, in any test of the spec. It used to pool every page a
  spec rendered, so one view's assertions could pass `assertEverything` on
  another view that shared its ids, or on a page the spec never asserted at
  all. The states of one view are now separate records unless the spec names
  them together with `coveredAs`, which a spec that relied on the pooling
  needs to add.
- `TwirlSpec` clears a spec's coverage and exclusion records when its suite
  starts and when it finishes, so a spec run again in the same JVM cannot pass
  on what an earlier run asserted. It does so through `SuiteLifecycle`, which a
  spec base built on `TwirlSpecDsl` mixes in itself.
- The CSRF token is signed once per test JVM, so a view renders the same
  markup each time it is rendered with the same arguments.
- A page built directly with `Page.fromString` keeps a coverage record of its
  own instead of sharing one, across specs, with any page of the same markup;
  and `withLang` keeps a page's record, so the same page read in another
  language counts as the same page.
- `meetStandardsExcept` fails on an id that names no rule the spec runs, and
  `RuleSet.only` and `allExcept` throw on an id the set does not have. Each
  used to exclude or select nothing without saying so.
- The README says what is supported: any Scala 3.3.0 or later, tested on
  3.3.0, 3.3.6, 3.3.7 and 3.9.0. It used to say a project below 3.3.8 could
  not use the library, which was never so.
- Every release within a major line is held to binary compatibility with the
  one before it, and CI runs a Play service's view specs against the published
  artifacts, on Scala 3.3.7 with Play 3.0.10 and on Scala 3.9.0 with Play
  3.0.11. New rules will arrive as warnings first, and be made blocking in a
  later minor.

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
- A name read from content reads an image's alt text where the image stands.
  `<a><img alt="Download"> PDF</a>` was named "PDF", because the alt text was
  consulted only when there was no text at all; it is now "Download PDF", as a
  screen reader announces it. The same holds for a name taken from a label, a
  legend or the target of an `aria-labelledby`, so a label holding only an
  image names its control, and an image that is itself the target names by
  its alt text.
- `formValues` reads a form the way a browser would submit it as its markup
  stands. A select with no option marked submitted nothing and now submits
  its first enabled option; a disabled control, or an option in a disabled
  group, submitted its value and now submits nothing; a checked box without a
  value submitted an empty string and now submits `on`; of two radios marked
  checked in one group only the last now counts; and of two ticked boxes
  sharing a name only the last was seen, where now `formValues("tick" -> "a")`
  passes when `a` is among the values. A textarea's value is its text as
  written, a file input's an empty file name, and reset and image inputs are
  no longer counted. Every form on the page is still read together, with no
  button as the submitter.
- A control inside the first legend of a disabled fieldset is enabled, as HTML
  has it, for `disabled`, `enabled` and the form values alike.
- Whether markup is a whole page, which decides if page-level rules run, is
  read from how the document opens. A page whose doctype came after two
  thousand characters of whitespace or comments was taken for a fragment and
  skipped those rules, and a fragment that merely mentioned `<html` was taken
  for a page. The opening has to be the whole name, so `<htmlish>` is not
  `<html>`.

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

[Unreleased]: https://github.com/frikit/twirl-spec/compare/v2.4.0...HEAD
[2.4.0]: https://github.com/frikit/twirl-spec/compare/v2.3.0...v2.4.0
[2.3.0]: https://github.com/frikit/twirl-spec/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/frikit/twirl-spec/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/frikit/twirl-spec/compare/v2.0.1...v2.1.0
[2.0.1]: https://github.com/frikit/twirl-spec/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/frikit/twirl-spec/compare/v1.0.0...v2.0.0
[1.x]: https://github.com/frikit/twirl-spec/releases/tag/v1.0.0
