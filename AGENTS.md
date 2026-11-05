# Working on twirl-spec

Read [CONTRIBUTING.md](CONTRIBUTING.md) before changing the build or release process.
It describes the compatibility policy, dependency pins, checks and publishing secrets.

## Documentation

- Edit the root `README.md` and `docs/*.md` directly. Jekyll publishes those files;
  there is no separate copy of the public guides to maintain.
- Check rule counts against the actual rule sets and the README's rule tables.
  `ReadmeSpec` checks those tables, but a count in introductory prose can still drift.
- A starter spec must define the view, form and other values it uses. Keep it
  consistent with the complete example in `docs/getting-started.md`.
- A snippet marked `<!-- compiled: path -->` must match its source file exactly.
- Keep every literal installation version aligned with the newest changelog section;
  `DocsContentSpec` enforces that agreement. Preserve historical changelog entries.
- Use relative links between published pages. Link to repository-only files through
  their GitHub URL from a public page; `DocsLinksSpec` checks this distinction.
- Keep `AGENTS.md` and temporary development plans out of the public site through
  `_config.yml`.

## Validation

Use Java 21 or later. Run the existing documentation checks after a docs change:

```sh
sbt 'twirl-spec-all/testOnly *ReadmeSpec *DocsContentSpec *DocsLinksSpec'
```

Before pushing a patch or minor release change, run the same gates as CI:

```sh
.github/scripts/test-release-plan.sh
sbt scalafmtCheckAll scalafmtSbtCheck headerCheckAll mimaReportBinaryIssues
sbt coverage test coverageOff coverageReport
```

For an intentional major API break, follow CI's release-plan-based MiMa exemption.

The pre-push hook also checks formatting, headers and tests. Do not lower coverage
gates or disable tests to make a release pass. CI additionally exercises the consumer
build and POMs; check its results before merging.

## Full release cycle

1. Read `.github/scripts/release-plan.sh` and the latest release tag. Versions come
   from Git tags through sbt-dynver, not a version field to edit in `build.sbt`.
   The default bump is patch; `#minor` and `#major` in commit subjects change it.
   Merge commits are ignored, so preserve the intended subjects when merging.
2. Add a dated changelog section for the planned version and update all installation
   lines. Check `.github/scripts/release-plan.sh` after committing: `next` must name
   that version and `skip` must be `false` when a release is intended.
3. Commit on a branch, push, open a PR to `main`, inspect its complete diff and wait
   for CI. Merge only after the required checks pass. Do not include unrelated work.
4. Watch the `Release` workflow on the resulting `main` commit. It verifies, publishes
   all nine modules to Maven Central, pushes the tag and creates the GitHub Release.
   Let the workflow create the tag; do not tag or publish the same version manually.
5. Verify all nine `io.github.frikit:twirl-spec-<module>_3` coordinates on Maven Central:
   `core`, `wcag`, `govuk`, `quality`, `messages`, `i18n`, `aria`, `html`, and `all`.
   Check the exact version's POM, main jar, sources, javadoc and signatures, as well
   as the GitHub release tag and notes. The root aggregate is not published.
6. Wait for `Docs`, which runs after a successful `Release` and builds from the newest
   release tag. Verify the live guides and `/api/` version before calling the cycle
   complete. Remove the merged remote branch and report the PR and release links.

The website changes only with a release. `[skip release]` is appropriate for work
that can wait for the next release, but not when the user asks to publish corrected
documentation now. A green skipped release does not deploy new guides.

If publishing fails, inspect the failed job and Central before retrying. Published
coordinates are immutable; use the workflow's documented recovery behavior instead
of uploading the same version again. Never expose publishing tokens or signing keys.
