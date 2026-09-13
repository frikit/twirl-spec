# Languages and message files

Nothing in the library is tied to a particular language. A service declares
the languages it renders, and every expectation, rule and comparison works in
each of them.

## Declaring the languages

`TwirlSpec` builds its application with one language, `en`. A service that
renders in more declares them, usually once on a spec base:

```scala
trait ViewSpecBase extends AnyWordSpec with Matchers with TwirlSpec with AllChecks {
  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq("en", "cy"))
}
```

`languages` is then every configured language, English first.

## Rendering in a language

```scala
"render in every language" in {
  inEachLanguage { _ =>
    render(view(form)) must display(title("x.title"), heading("x.heading"))
  }
}

"say it in Welsh" in {
  inLanguage(Lang("cy")) {
    render(view(form)) must display(title("x.title"))
  }
}
```

`inLanguage` swaps the implicit `messages` and `request` for the block, so
the view renders in that language and every message key in the expectations
resolves in it too. A key that is not defined in that language is reported as
missing, rather than silently compared against itself, which is what a plain
`doc.title mustBe messages("x.y")` does, passing while the page shows a raw key.

With `TwirlSpecDsl`, where the spec owns its `Messages`, the same thing is
done explicitly:

```scala
val welsh = messagesApi.preferred(Seq(Lang("cy")))
render(view(form)(welsh), Lang("cy"), welsh) must display(title("x.title"))

renderIn(Lang("cy"))(m => view(form)(m)) must display(title("x.title"))
```

## Comparing the languages

Two modules look at translation from two sides. `twirl-spec-messages`
compares the message **files**. `twirl-spec-i18n` compares the **pages** those
files produce, which is where a translation that parses but renders
differently shows up: a control that vanished, a link that kept its English
href, a heading level that moved.

```scala
"say the same thing in every language" in {
  renderInEachLanguage(view(form)) must translateConsistently
}
```

The first page given is the base; nominate another with
`basedOn(Lang("cy"))`. The rules:

| Rule | Checks |
|---|---|
| `i18n-lang-attribute` | the page declares the language it was rendered in |
| `i18n-same-ids` | every language renders the same elements |
| `i18n-same-links` | every language links to the same places |
| `i18n-same-controls` | every language collects the same fields |
| `i18n-same-headings` | every language has the same heading structure |
| `i18n-nothing-lost` | no text present in the base language goes missing |
| `i18n-actually-translated` | the page is translated, not copied *(warning)* |

Text that is meant to read the same everywhere, a product name or a unit,
would otherwise be reported by the last rule. Say so once:

```scala
override def translationConfig: TranslationConfig =
  TranslationConfig(sameTextIsFine = Set("GOV.UK", "Example Ltd", "ISBN"))
```

`translateConsistentlyExcept("i18n-same-links")` drops a rule, and
`translationDifferences(pages)` returns the findings without failing. A single
configured language passes: there is nothing to compare it against.

## Checking the message files

```scala
"the message files" should {

  "be consistent across languages" in {
    messagesApi must beConsistentAcrossLanguages()
  }

  "define no key twice" in {
    Seq(new File("conf/messages"), new File("conf/messages.cy")) must haveNoDuplicateKeys
  }
}
```

Every language the application is configured for is measured against the base
language, English unless told otherwise. The checks, with their rule ids:

| Rule | Finds |
|---|---|
| `messages.translation-parity` | a key in the base language missing from a translation, or no translation file at all |
| `messages.base-parity` | a key in a translation missing from the base |
| `messages.empty-value` | a key with an empty value |
| `messages.unescaped-quote` | an unpaired apostrophe, which `MessageFormat` swallows along with everything after it |
| `messages.quoted-placeholder` | a `{0}` inside a quoted section, shown literally |
| `messages.quoted-literal` | a deliberately quoted section *(warning)* |
| `messages.placeholder-parity` | a translation using different placeholders from the base |
| `messages.translation-coverage` | too much of a translation identical to the base *(warning)* |
| `messages.duplicate-key` | a key defined twice in one file, of which Play silently keeps the last |

The apostrophe rule matters more than it looks: Play runs every message
through `MessageFormat`, whether or not it takes arguments, so `don't` in a
message file renders as `dont` and stops any later placeholder substituting.
Write `don''t`.

Tune the checks with a `Config`, once on the spec base:

```scala
override def messagesIntegrityConfig: MessagesIntegrity.Config =
  MessagesIntegrity.Config(
    baseLanguage = "en",
    requireTranslations = true,        // false for a single-language service
    maxUntranslatedRatio = 0.06,       // how much may be identical before it counts as untranslated
    ignoreKeys = Set("footer.copyright"),
    ignoreKeyPrefixes = Set("internal."),
    urlKeySuffixes = Set(".url", ".href", ".link.url")   // identical in every language by nature
  )
```

Play's own framework messages, under `default.play`, are not a language and
are left out of the comparison.
