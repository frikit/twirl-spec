# Security

Report a vulnerability privately, through GitHub's
[private vulnerability reporting](https://github.com/frikit/twirl-spec/security/advisories/new),
and not in a public issue.

twirl-spec runs in a project's tests, never in its production code, so what
matters is what a test dependency can do: what its artifacts contain, how they
are signed and published, and what they pull into a build. Every release is
signed, published to Maven Central from the release workflow, and built by
actions pinned to a commit.

Fixes go to the latest release of the current major line. The 1.x line
receives none.
