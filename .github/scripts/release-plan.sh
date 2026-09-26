#!/usr/bin/env bash
#
# What a push to main would release, read from every commit since the last
# release rather than from the last commit alone.
#
# Prints key=value lines, ready for $GITHUB_OUTPUT:
#
#   last=v2.1.0     the latest release tag, or empty before the first release
#   bump=minor      major if any commit says #major, else minor if any says
#                   #minor, else patch
#   next=v2.2.0     the version that bump makes of last (v1.0.0 with no tag)
#   skip=false      true when there is nothing to release: no commits since
#                   last, or every one of them says [skip release]
#   commits=7       how many commits were read
#
# The markers are read from each commit's subject line, and only as whole
# words, so a body that explains the release process ("a commit that says
# #major...") cannot release a major by describing one. Merge commits are not
# read: their messages are GitHub's, not the author's, and the commits they
# bring in are read in their place.
#
# release.yml uses it to tag and publish, ci.yml and run_all_tests.sh to know
# whether the API may change (only a major may break binary compatibility).
set -euo pipefail

last="$(git tag --list 'v*' --sort=-v:refname | head -n 1)"
range="HEAD"
[ -n "$last" ] && range="$last..HEAD"

word() { grep -qiE "(^|[^[:alnum:]_])$1([^[:alnum:]_]|$)" <<< "$2"; }

commits=0
skipping=0
bump=patch
while IFS= read -r -d '' subject; do
  commits=$((commits + 1))
  if grep -qiF '[skip release]' <<< "$subject"; then skipping=$((skipping + 1)); fi
  if word '#major' "$subject"; then
    bump=major
  elif word '#minor' "$subject" && [ "$bump" = patch ]; then
    bump=minor
  fi
done < <(git log --no-merges --format='%s%x00' "$range")

skip=false
if [ "$commits" -eq 0 ] || [ "$skipping" -eq "$commits" ]; then skip=true; fi

if [ -z "$last" ]; then
  next=v1.0.0
else
  IFS=. read -r major minor patch <<< "${last#v}"
  case "$bump" in
    major) next="v$((major + 1)).0.0" ;;
    minor) next="v$major.$((minor + 1)).0" ;;
    *) next="v$major.$minor.$((patch + 1))" ;;
  esac
fi

echo "last=$last"
echo "bump=$bump"
echo "next=$next"
echo "skip=$skip"
echo "commits=$commits"
