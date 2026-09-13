#!/usr/bin/env bash
set -euo pipefail

# Reported whether the run passes or fails: a failing run is the one whose cost
# you most want to know, and `set -e` would otherwise skip straight past it.
SECONDS=0
report() {
  local status=$?
  if [ "$status" -eq 0 ]; then
    printf '\nall checks passed in %ds\n' "$SECONDS"
  else
    printf '\nfailed after %ds (exit %d)\n' "$SECONDS" "$status"
  fi
}
trap report EXIT

# `clean` first, deliberately. sbt caches scalafmt results, so a check run over a
# warm target can report success on a file it never looked at — which is how an
# unformatted file reaches CI while the local run is green. The same cache
# stops headerCreateAll rewriting an existing header after the licence changes.
sbt clean headerCreateAll scalafmtSbt scalafmtAll
sbt scalafmtCheckAll scalafmtSbtCheck headerCheckAll
sbt coverage test coverageOff coverageReport dependencyUpdates
