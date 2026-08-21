#!/usr/bin/env bash
set -euo pipefail

# `clean` first, deliberately. sbt caches scalafmt results, so a check run over a
# warm target can report success on a file it never looked at — which is how an
# unformatted file reaches CI while the local run is green.
#
# `coverage` and `+` cannot share an invocation either: the setting scoverage
# applies does not survive the reload that cross-building does.
sbt clean scalafmtSbt scalafmtAll
sbt scalafmtCheckAll scalafmtSbtCheck +test
sbt coverage test coverageOff coverageReport dependencyUpdates
