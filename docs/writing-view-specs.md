# Writing view specs

Everything here is called inside `display(...)`, the matcher that takes any
number of expectations and checks them all:

```scala
render(view(form)) must display(
  title("x.title"),
  heading("x.heading"),
  submitButton()
)
```

`render` parses the view's output into a `Page`. The expectations are plain
values, so they can be kept in a `val`, put in a list, or bundled with
`expectations(...)` and reused across specs.

## Message keys and exact words

Every expectation that takes text takes a **message key** by default, because
that is what template content is. The key is resolved in the page's language,
so the same spec holds in English and Welsh. Arguments follow the key:

```scala
title("livesInUk.title", "Ada")            // messages("livesInUk.title", "Ada")
```

Where you mean exact words, use the `…Text` form, or wrap the words:

```scala
titleText("What is your name?")
heading(literal("What is your name?"))
```

Two more `Expected` values exist for the expectations that take one:
`anyText`, for "there is a heading, its wording is asserted elsewhere", and
`matching("Sign\\s+up".r)`, for a regular expression.

Page text is normalised before comparison: whitespace collapsed, non-breaking
spaces made plain, soft hyphens dropped, curly quotes straightened. The
expected side is normalised the same way, so a curly apostrophe in a message
file never fails against a straight one on the page. When you compare page text
yourself, `normalised(...)` and `messageText(key)` do the same to your side.

## Page framing

The parts of a page the layout is responsible for.

```scala
title("x.title")            // the browser title, ignoring " - Service - GOV.UK" and an "Error:" prefix
exactTitle("Full - Service - GOV.UK")
heading("x.heading")        // the single <h1>, ignoring a caption rendered inside it
caption("x.caption")        // the caption above or inside the h1
subheading("x.section")     // an h2 with this text
headingAtLevel(3, literal("Details"))
serviceName()               // "service.name" by default
backLink                    // a GOV.UK back link is present
backLink.to("/where")       // ... and points here
noBackLink                  // a page a citizen must not reverse out of
languageToggle              // a language switcher, however it is rendered
phaseBanner
timeoutDialog               // the HMRC timeout dialog is wired up
signOutLink
```

## Content

```scala
content("x.p1")                          // somewhere in the visible text
noContent("x.gone")                      // nowhere in the visible text
paragraph("x.p1")                        // inside a paragraph
warning("x.warning")                     // a GOV.UK warning callout
insetText("x.inset")
notificationBanner("x.banner")
panelTitle("x.panel.title")
panelBody("x.panel.body")
detailsSummary("x.details")              // the visible summary of a details block
bullets("x.b1", "x.b2")                  // exactly these items, in order
numberedItems("x.step1", "x.step2")
```

Links can be found by text or by id, and pinned to a destination:

```scala
link("x.guidance").to("/guidance")
linkText("Read the guidance").to("/guidance")
linkWithId("guidance-link").to("/guidance").saying("x.guidance")
```

Check-your-answers pages have a summary list; assert the whole list or one
row:

```scala
summaryList("x.name" -> "Ada", "x.dob" -> "27 March 1993")   // exactly these rows, in order
summaryRow("x.name").withValue("Ada").withChangeLinkTo("/change/name")
summaryRow("x.name").withActions("site.change")
```

Tables:

```scala
tableHeaders("x.col1", "x.col2")         // the header cells, in order
tableRow("Ada", "1815")                  // a row whose cells read exactly like this
```

## Structure

For anything the named expectations do not cover, fall back to ids and
selectors:

```scala
element("submit")                        // exactly one element with this id
noElement("warning")
elementWithText("submit", "site.continue")
elementHasClass("tag", "govuk-tag--green")
cssSelector(".govuk-panel")              // at least one match
noCssSelector(".govuk-error-summary")
elementCount(".govuk-summary-list__row", 4)
appearsBefore(".govuk-error-summary", "form")   // document order
```

`appearsBefore` is worth knowing: reading order is not cosmetic, and an error
summary announced after the form it describes is announced too late.

## Finding things the way a screen reader does

Borrowed from Testing Library: if a control cannot be found by its role and
the name it announces, a screen reader cannot find it either, so the query is
itself an accessibility check.

```scala
role("button").named("site.continue")
role("textbox").namedText("Email address")
role("heading").namedMatching("Sign\\s+up".r)
role("radio").occurring(2)
```

Roles are resolved the way HTML-AAM defines them, implicit or explicit, and an
element with an explicit `role` is matched only by that role, so
`<a role="button">` is a button and not a link. The accessible name comes from
`aria-labelledby`, `aria-label`, an associated `<label>`, a `<legend>`, `alt`,
the `value` of a `button`, `submit` or `reset` input, the element's own text
(or the `alt` of an image standing in for it), and finally `title` — in that
order. A `<select>`, `<textarea>` or `<input>` is never named by its own
content, and neither is an image hidden from assistive technology. This is a
working subset of the accessible name computation: one hop of
`aria-labelledby`, and nothing that depends on CSS or JavaScript.

## Forms

Every control expectation finds its control by `name` or `id`, then checks
what the builders ask for.

```scala
textInput("email")
  .labelled("x.email")                   // label text, from a key; .labelledText for words
  .hinted("x.email.hint")                // hint text, and that the field announces it
  .withValue("ada@example.com")
  .withAutocomplete("email")
  .ofType("email")

textArea("notes").labelled("x.notes")

dropdown("country").labelled("x.country").withOptionValues("GB", "FR").withOptionCount(3)

radioGroup("value")                      // "value" is the default name, as Play names it
  .legendIs("x.legend")
  .hinted("x.hint")
  .withOptions("yes" -> "site.yes", "no" -> "site.no")   // exactly these, labels checked
  .selectedIs("yes")                     // or .nothingSelected

checkboxGroup("colours").withOptionValues("red", "blue").selectedIs("red", "blue")
checkboxGroup("terms").containingOption("agree", "x.agree")   // among possibly others

dateInput("dob").legendIs("x.dob").hinted("x.dob.hint")       // day, month, year
dateInput("dob").withParts("month", "year")

fileUpload("evidence")
hiddenInput("csrfToken", token)

submitButton()                           // says "site.continue" by default
submitButton("site.saveAndContinue")
submitButtonText("Continue")
hasSubmitButton                          // present, whatever it says
button("save-for-later", "x.saveForLater")

formPostsTo("/register/name")
formGetsFrom("/search")
```

A control that is legitimately labelled by a legend rather than a label, as
the single field of a fieldset sometimes is, says so with `labelledByLegend`.

Labels are checked the way an assistive technology reads them: a `<label>`
whose `for` matches the control's id. Hints are checked for their words and
for being announced, that is, referenced from the control's
`aria-describedby`, or from the fieldset's for a group. The GOV.UK components
wire both up when you pass `hint`; the checks catch a hand-written template
that does not.

## Control state, form values and reading order

Borrowed from jest-dom, keeping what means something for server-rendered
HTML:

```scala
formValues("email" -> "ada@example.com", "country" -> "GB", "contact" -> "email")

disabled("locked")                       // on itself, or through an enclosing fieldset
enabled("email")
required("email")                        // required attribute or aria-required
invalid("email")                         // aria-invalid
describedAs("email", "x.email.hint")     // what is read after the control's name
```

## Errors

```scala
noErrors                                 // no summary, no inline errors, no "Error:" in the title
errorTitlePrefix                         // the title carries the error prefix
errorSummaryTitle()                      // "error.summary.title" by default
errorSummary("email" -> "x.error.email.required")           // exactly these entries, in order
errorSummaryContaining("email", "x.error.email.required")   // this one, whatever else
fieldError("email", "x.error.email.required")               // the inline message against the field
```

`errorSummary` also checks that every entry links to an element that exists,
because a link to `#firstName` when the input is `id="value"` leaves a
keyboard user stranded.

## One match, or a failure

An expectation that names a single element fails when the page has more than
one match, as well as when it has none:

```
x input(email) — 2 elements matched, so this assertion is ambiguous
      expected  "exactly one match"
      actual    "<input#email[name=email]> | <input#email2[name=email]>"
      hint      name the one you mean, or assert on the group with cssSelector and elementCount
```

This follows Testing Library's `getBy`: quietly taking the first of several
is how a test ends up asserting against something other than the thing it
names. Expectations that are plural by nature, such as `radioGroup`, `bullets`,
`link` and `tableRow`, are unaffected.

## House rules

A service usually has a few expectations every page must meet. Bundle them
once:

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {
  val standardFurniture: Expectation = expectations(serviceName(), languageToggle, signOutLink)
}

render(view(form)) must display(standardFurniture, title("x.title"), heading("x.heading"))
```

An expectation can also be softened with `.asWarning`, so it is reported
without failing, or made conditional with `.when(page => ...)`.

## Raw Jsoup, one call away

The page model is there when the DSL is not enough:

```scala
val page = render(view(form))
page.doc                                 // the Jsoup Document
page.css(".govuk-summary-list__row").size
page.byId("value").attr("aria-describedby")
page.summaryRows                         // (key, value, action texts)
page.fieldErrors                         // field -> inline message
page.errorSummaryLinks                   // (target id, text)
page.formValues
page.accessibleName(page.byId("submit")(0))
page.outline                             // the skeleton, printable and stable enough to snapshot
```

Assertions made through the page model count towards coverage the same way
DSL expectations do; see [Coverage and entry points](coverage-and-entry-points.md).

## Checking outside a matcher

`checkPage(page, Seq(...))` runs expectations and returns a `CheckReport`
with `passed`, `errors`, `warnings` and `message`, for a spec that wants to
report rather than fail, or to assert on the result itself.
