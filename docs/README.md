# twirl-spec documentation

`twirl-spec` is a ScalaTest toolkit for testing [Twirl](https://github.com/playframework/twirl)
views: a page model with named accessors, an expectation DSL that reads like
the page, rule sets that check accessibility and markup without a browser, and
failure messages that show what the template actually rendered.

The [README](../README.md) is the reference: every expectation, matcher and
rule is listed there, and the rule tables are held to the code by a test. The
[API documentation](https://frikit.github.io/twirl-spec/api/) is the Scaladoc
of all nine modules as one site. These pages are the guide.

| Page | Read it when |
|---|---|
| [Getting started](getting-started.md) | you have never used the library: install it, write a first spec, read a failure |
| [Writing view specs](writing-view-specs.md) | you are writing specs and want the DSL explained control by control |
| [Rules and standards](rules-and-standards.md) | you want to know what the check traits enforce, how to select or exclude rules, and how to write your own |
| [Languages and message files](languages-and-messages.md) | your service renders in more than one language, or you want the message files themselves checked |
| [Coverage and entry points](coverage-and-entry-points.md) | you want to know what a spec never looked at, and to reach the Twirl entry points nothing calls |
| [Failure messages](failure-messages.md) | a test is red and you want to read its output quickly |
| [Adopting in an existing service](adopting.md) | you have a home-grown spec base to replace, or are moving from 1.x |
| [Troubleshooting](troubleshooting.md) | something does not behave as you expected |

## Requirements

| | |
|---|---|
| Scala | 3.3.8 or any later Scala 3 |
| Java | 21 or later |
| Play | any 3.0.x; your project supplies it |
| ScalaTest | 3.2.20 or later |

## The shortest possible start

```scala
libraryDependencies += "io.github.frikit" %% "twirl-spec-all" % "2.0.1" % Test
```

```scala
import io.github.frikit.twirlspec.{AllChecks, TwirlSpec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SignUpViewSpec extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {

  private val view = inject[SignUpView]

  "SignUpView" should {
    "render the question" in {
      render(view(form)) must display(
        title("signUp.title"),
        heading("signUp.heading"),
        textInput("email").labelled("signUp.email"),
        submitButton(),
        noErrors
      )
    }
  }
}
```

That one `display` checks what it names and, because `AllChecks` is mixed in,
runs every rule in the library over the page as well. [Getting
started](getting-started.md) takes it from there.
