# Contributing

## Building

```sh
./run_all_tests.sh
```

Adds any missing licence header, formats, tests under coverage against a 100%
statement and branch gate, and lists dependency updates. The library is built
for Scala 3 only, on the 3.3 LTS line, so there is no cross-build.

An unused import is a compile error, through `-Wunused:imports` and `-Wconf`
in `build.sbt`. The Twirl test fixtures therefore declare exactly the imports
they use in place of Twirl's defaults, and a template that needs more, such as
`nameView` with `Form`, imports it at the top of the template, before `@this`.

Play, Twirl and Scala are pinned deliberately: Play 3.0.0 and its Twirl 2.0.1
are the oldest 3.0.x, so any 3.0.x consumer resolves the artifacts without an
upgrade, and Scala 3.3 is the LTS line. `dependencyUpdates` is told not to
propose newer ones.

Every module is fully covered, with no `$COVERAGE-OFF$` exclusions anywhere in
the source. That is a deliberate constraint rather than a trophy: a new branch
has to arrive with a test, be excluded with a marker and a stated reason, or
lower the gate in a commit someone can see. Getting there also deleted three
pieces of unreachable code — a `getOrElse` on a key Play always defines, a
not-found branch in a helper only called for values already found, and a
`catch` that the tests written to justify it showed had never caught
anything.

Tests are not forked. Suites share one Play application, and one suite
exercises the shutdown hook that stops it, so `build.sbt` runs suites one at a
time within a module and test tasks one module at a time.

`src/test/resources/captured/` holds markup captured verbatim from a real GOV.UK
Design System implementation, so the Design System rules are checked against
genuine output without this library depending on any component package.

## Licence headers

Every Scala source carries the Apache 2.0 header. `sbt headerCreateAll` adds it
to a new file and `sbt headerCheckAll` fails when one is missing; the pre-push
hook and CI both run the check.

`headerCreateAll` is incremental and keyed on file content, so after changing
the licence text in `build.sbt` it leaves every unchanged file alone while
`headerCheckAll` reports all of them. Run `sbt clean headerCreateAll` to rewrite
them, which is what `run_all_tests.sh` does anyway.

## The README's rule tables

The tables under *The rules* and the criteria table are derived from the rule
sets, and `ReadmeSpec` in the `all` module holds the README to them. A rule
added, renamed, reworded or re-graded fails that spec, and its message carries
the table to paste in.

## Dependencies

Scala Steward runs on Mondays through `.github/workflows/scala-steward.yml` and
opens a pull request per update; Dependabot does not understand sbt. The
repository allows Actions to create pull requests, a setting under Settings,
Actions, General that the workflow needs. With the workflow's own token, GitHub
does not start CI on the pull requests it opens; a fine-grained personal access
token with contents and pull-requests write access, stored as the
`SCALA_STEWARD_TOKEN` secret, lifts that.

## The pre-push hook

`.githooks/pre-push` runs the same formatting and header checks and tests that
CI runs, and `build.sbt` points `core.hooksPath` at it on load, so a fresh clone
gets it by running sbt once rather than by remembering a setup step. It takes a
few seconds; `git push --no-verify` skips it.

It exists because formatting broke CI twice, each time for a different reason
and each time invisible locally: `.scalafmt.conf` was skipping untracked files,
and then sbt's scalafmt cache reported success on a file it had not looked at.
The hook clears just the scalafmt caches — not the whole `target` — so being
careful does not cost a full recompile on every push.

## Releasing

Every push to `main` is a release. `release.yml` runs the same checks as a
pull request and, if they pass, tags the commit with the next version, publishes
every module to Maven Central through the Sonatype Central Portal, and only then
pushes the tag and creates a GitHub Release whose notes are generated from the
commits since the last one. A publish that fails leaves no tag behind, so the
next attempt gets the same version. There are no snapshots.

The bump is a patch unless the commit message asks for more: a message
containing `#minor` bumps the minor version, `#major` the major. The first
release, with no tag yet in the repository, is `v1.0.0`.

A commit message containing `[skip release]` is verified but not published, for
a change to the workflows or the documentation that no user could depend on. A
version on Maven Central can never be withdrawn or altered, so it is worth not
spending one on a change that alters no artifact.

The version is the tag, read by `sbt-ci-release` through `sbt-dynver`, so
`build.sbt` does not carry one. Locally, `sbt version` gives a derived value such
as `1.0.0+3-1a2b3c4d-SNAPSHOT`. `sbt-ci-release` also wants to see the tag in
`GITHUB_REF` before it will publish a stable release, and the runner sets that to
the branch, so the workflow hands the sbt process the tag it has just made.

### Secrets

Four repository secrets, named as in `frikit/krandom` so the same values serve
both. Until all four are present, a push is verified but not released, with a
notice rather than a failure.

| Secret | What it is |
|---|---|
| `CENTRAL_PORTAL_USERNAME` | the username half of a Central Portal user token |
| `CENTRAL_PORTAL_PASSWORD` | the password half of that token |
| `GPG_SIGNING_KEY` | the armoured private key — raw or base64, either is accepted |
| `GPG_SIGNING_PASSWORD` | the passphrase of that key |

The signing key is accepted in either form because Gradle's `useInMemoryPgpKeys`
wants the raw armoured text and `sbt-ci-release` wants it base64-encoded; the
workflow looks at what it was given and converts if it has to.

The `io.github.frikit` namespace is already verified in the Portal for krandom,
and a namespace covers every artifact under it.

Unlike krandom's release, which uploads and waits for a manual **Publish** in the
Portal, this one publishes as soon as the Portal has validated the upload. There
is no human step, because there is no human in a push to `main`.

To make a fresh signing key, should you ever need one:

```sh
gpg --gen-key                                                  # RSA, 4096, no expiry is fine
gpg --list-secret-keys --keyid-format LONG                     # note the key id
gpg --armor --export-secret-keys <KEY_ID> | pbcopy             # GPG_SIGNING_KEY, either form works
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>      # Central checks the public half
```

A release can also be started by hand from the Actions tab (`workflow_dispatch`)
without a code change.
