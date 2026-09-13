# Failure messages

Every check runs, so one failing run shows every problem on the page rather
than one per re-run. This is what a red `display` prints, and how to read it.

```
page failed 2 checks:

  x title — text did not start with the expected value
        expected  "What is your name?"
        actual    "What is you name?"

  x input(lastName) label — field has no associated label
        expected  label[for="lastName"]
        hint      WCAG 3.3.2 — every input needs a label whose `for` matches the input id

  warnings:
    x link-text-is-meaningful: link text "click here" does not describe its destination

  --- page outline (en) ---
page
  title        What is you name? - Example Service - GOV.UK
  lang         en
  h1           What is you name?
  back link    /back
form POST /register/name
  text     firstName    label "First name"
  text     lastName
  button   submit       "Continue"
```

## The findings

Each finding starts with `x`, the name of the expectation or the id of the
rule, and one line saying what went wrong. Under it:

- `expected` and `actual` show the two sides of a comparison, quoted, with
  long values cut at 300 characters. An empty actual value is shown as
  `(empty)`, and where there was nothing at all to compare, the message says
  what was absent: `(no links on the page)`, `(no summary list rows)`,
  `(nothing selected)`.
- `at` locates a rule finding: an element by id, or `<tag>.class` when it has
  no id, or a line number for the source-level HTML checks.
- `hint` is advice on the fix, with the WCAG success criterion where one
  applies.

A message key that does not resolve is reported as its own finding rather
than compared as text:

```
  x title — message key `x.tittle` is not defined for lang `en`
        expected  "x.tittle"
        hint      add `x.tittle` to conf/messages
```

## Warnings

Findings from warning rules, and from expectations softened with
`.asWarning`, appear under `warnings:` and do not fail the page unless
`failOnWarnings` is on. A passing test with warnings still surfaces them,
through ScalaTest's alert output, unless `reportWarnings` is off.

## The outline

The block under `--- page outline ---` is the page's skeleton as the library
sees it: the framing, the heading tree when there is more than one heading,
each form with its controls and their labels, hints, values and checked
state, the components present, and the errors. It is built only when a check
fails, because building it walks the whole page.

It is also available on its own, `page.outline`, and is worth printing while
building a view. It is stable enough to commit as a structural snapshot of a
page.

## Names to assert on

Rule wording, hints and the layout of this output may change in any release.
A spec that inspects findings should assert on rule ids, which are stable
within a major version:

```scala
val report = checkPage(page, Seq(standardsExpectation))
report.errors.map(_.rule) must contain("labelled-controls")
```

## Reporting without failing

Three matchers have a reporting twin for a spec that wants the findings as
data: `checkPage(page, expectations)` for a `CheckReport`,
`unassertedContent(page)` for the coverage list, and
`translationDifferences(pages)` for the language comparison.
