#!/usr/bin/env bash
#
# Pins release-plan.sh's decisions, in throwaway repositories. A version on
# Maven Central can never be withdrawn, so the logic that picks it is tested
# rather than trusted.
set -euo pipefail

plan="$(cd "$(dirname "$0")" && pwd)/release-plan.sh"
failures=0

repo() {
  cd "$(mktemp -d)"
  git init -q -b main
  git config user.name test
  git config user.email test@example.com
  git config commit.gpgsign false
  git config tag.gpgsign false
}

commit() { git commit -q --allow-empty -m "$1"; }

expect() {
  local name=$1 want=$2 got
  got="$("$plan" | tr '\n' ' ')"
  if [[ "$got" == *"$want"* ]]; then
    echo "ok   $name"
  else
    echo "FAIL $name: wanted [$want] in [$got]"
    failures=$((failures + 1))
  fi
}

repo
commit "Start"
expect "no tag yet: the first release is v1.0.0" "last= bump=patch next=v1.0.0 skip=false commits=1"

repo
commit "Release #minor"
git tag v1.4.2
expect "nothing since the last release: skip" "last=v1.4.2 bump=patch next=v1.4.3 skip=true commits=0"

repo
commit "Release"
git tag v1.4.2
commit "Fix a rule"
expect "an unmarked commit: patch" "bump=patch next=v1.4.3 skip=false commits=1"

repo
commit "Release"
git tag v1.4.2
commit "Add a rule #minor"
commit "Correct a typo in the docs"
expect "a marker on an earlier commit still counts" "bump=minor next=v1.5.0 skip=false commits=2"

repo
commit "Release"
git tag v1.4.2
commit "Add a rule #minor"
commit "Remove a method #major"
commit "Tidy up"
expect "the highest bump wins" "bump=major next=v2.0.0"

repo
commit "Release"
git tag v1.4.2
commit "Update the workflow [skip release]"
commit "Update the docs [SKIP RELEASE]"
expect "every commit skipping: skip" "skip=true commits=2"

repo
commit "Release"
git tag v1.4.2
commit "Fix a rule"
commit "Update the docs [skip release]"
expect "one commit that needs releasing: release, even under a skip" "bump=patch next=v1.4.3 skip=false commits=2"

repo
commit "Release"
git tag v1.4.2
git switch -q -c feature
commit "Update a plugin [skip release]"
git switch -q main
git merge -q --no-ff feature -m "Merge pull request #1 from someone/feature"
expect "a merge commit's own message is not read" "skip=true commits=1"

repo
commit "Release"
git tag v1.4.2
commit "$(printf 'Explain the release process\n\nA commit that says #major bumps the major, and [skip release] skips.')"
expect "a marker mentioned in a body does not count" "bump=patch next=v1.4.3 skip=false"

repo
commit "Release"
git tag v1.4.2
commit "Count the #majority of cases"
expect "a marker has to be a whole word" "bump=patch"

repo
commit "One"
git tag v1.9.0
commit "Two"
git tag v1.10.0
commit "Three"
expect "the latest tag by version, not by name" "last=v1.10.0 bump=patch next=v1.10.1"

if [ "$failures" -gt 0 ]; then
  echo "$failures failure(s)"
  exit 1
fi
echo "release-plan.sh holds"
