#!/usr/bin/env bash
set -euo pipefail

# Cross-compile and test on every supported Scala version, then measure coverage
# on the default one. `coverage` and `+` cannot share an invocation: the setting
# scoverage applies does not survive the reload that cross-building does.
sbt clean scalafmtSbt scalafmtAll +test
sbt coverage test coverageOff coverageReport dependencyUpdates
