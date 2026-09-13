# Coverage and entry points

Rules answer "is this page sound". The two checks here answer the other
question: "did the spec actually look at it". A view can pass every rule while
half of it goes unasserted, because rendering a page is not the same as
testing it. Both come from `CoverageChecks` in `twirl-spec-quality`, which
`AllChecks` includes.

## What the spec never asserted

Every id, link and tracked element the page shows has to have been asserted
by some test in the spec. Put the check in the last test, because it can only
see the assertions that have already run:

```scala
"everything this page shows" must {
  "have been asserted by one of these tests" in {
    render(view(form)) must assertEverything
  }
}
```

When something was missed:

```
7 things on this page were never asserted:
  id (3): #sub-header, #sub-header-link, #upload-file
  link (1): "upload a new file or download one that is waiting."
  tracking (1): data-journey-click=link - click:File not available:Choose something else to do
```

Assertions are recorded by what they touch, not by the selector they used, so
asserting `#action-list > *` does not count as asserting `#action-list`.
Renders of the same view in different states add up: a spec that checks a
valid form in one test and an invalid one in the next is measured as a whole.
Assertions made through the page model, `page.byId`, `page.css` and the
component accessors, count the same as DSL expectations. Rule sets do not
count: they sweep the whole page, and what they touch says nothing about what
the spec asserted.

Three things are adjustable, usually once on a spec base:

```scala
override def coverageScope: String          = "main, #main-content"   // the layout is not the view spec's problem
override def coverageIgnored: Set[String]   = Set("#content")         // what every page inherits
override def trackedAttributes: Set[String] = Set("data-journey-click")
```

Ignoring a block ignores what it holds: disclaiming the layout's
`#report-technical-issue` wrapper does not then hold the spec to the link
inside it. `assertEverythingExcept("#id")` covers the one-off case, and
`unassertedContent(page)` returns the list without failing.

## The Twirl entry points nothing calls

Twirl generates `render`, `f` and `ref` beside `apply`. Nothing in a normal
service calls them, so a change to a template's parameters can break them with
no test noticing, and they sit in coverage reports as permanently unreached
lines. One line exercises all three and checks they agree with `apply`:

```scala
"the generated entry points" in {
  view must entryPointsAgree(
    view(form, edit = false)(request, messages, appConfig),
    form, false, request, messages, appConfig
  )
}
```

The arguments after the expected HTML are the ones `render` takes: every
parameter including the implicit ones, flattened, in order. The check reaches
the methods by reflection, so a spec does not have to spell out each
template's arity by hand, and a template whose parameters changed fails here
with the entry point named.
