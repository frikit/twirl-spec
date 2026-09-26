#!/usr/bin/env python3
"""Hold the published POMs to the library's dependency decisions.

Run after `sbt makePom` with the version the POMs were made for:

    python3 .github/scripts/check-poms.py 0.0.0-CONSUMER

Each module's POM must ask a consumer for Play as `provided`, so the library
never moves a service's Play; for twirl-api as a normal dependency at 2.0.1,
the one Play 3.0.0 ships; for Scala on the 3.3 LTS line, which every later
Scala 3 can read; and for nothing that was only needed to test the library.
"""

import glob
import sys
import xml.etree.ElementTree as ET

MODULES = 9
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PLAY = "3.0.0"
PROVIDED = {"play_3", "play-test_3", "play-guice_3", "play-filters-helpers_3"}

# What every module's POM must declare, and what the core's must declare as
# well: (group, artifact) -> (scope, version, or None for any version).
EVERY_MODULE = {
    ("org.playframework", "play_3"): ("provided", PLAY),
    ("org.playframework", "play-test_3"): ("provided", PLAY),
    ("org.playframework", "play-guice_3"): ("provided", PLAY),
    ("org.scalatest", "scalatest_3"): ("compile", None),
}
CORE_ONLY = {
    ("org.playframework", "play-filters-helpers_3"): ("provided", PLAY),
    ("org.playframework.twirl", "twirl-api_3"): ("compile", "2.0.1"),
    ("org.jsoup", "jsoup"): ("compile", None),
}


def problems_in(path):
    found = []
    root = ET.parse(path).getroot()
    declared = {}
    for dep in root.findall("m:dependencies/m:dependency", NS):
        group = dep.findtext("m:groupId", "", NS)
        artifact = dep.findtext("m:artifactId", "", NS)
        version = dep.findtext("m:version", "", NS)
        scope = dep.findtext("m:scope", "compile", NS)
        declared[(group, artifact)] = (scope, version)
        name = f"{group}:{artifact}:{version} ({scope})"
        if group == "org.playframework" and artifact in PROVIDED and (scope != "provided" or version != PLAY):
            found.append(f"{name} should be provided at {PLAY}")
        if group == "org.scala-lang" and artifact == "scala3-library_3" and not version.startswith("3.3."):
            found.append(f"{name} should be on the 3.3 LTS line")
        if scope == "test":
            found.append(f"{name} is a test dependency in a published POM")
    required = dict(EVERY_MODULE)
    if "/twirl-spec-core_3-" in path.replace("\\", "/"):
        required.update(CORE_ONLY)
    for (group, artifact), (scope, version) in required.items():
        got = declared.get((group, artifact))
        if got is None:
            found.append(f"{group}:{artifact} is missing")
        elif got[0] != scope or (version is not None and got[1] != version):
            found.append(f"{group}:{artifact} is {got[1]} ({got[0]}), should be {version or 'any'} ({scope})")
    return found


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: check-poms.py <version>")
    version = sys.argv[1]
    poms = sorted(glob.glob(f"*/target/scala-*/*-{version}.pom"))
    problems = [] if len(poms) == MODULES else [f"expected {MODULES} module POMs for {version}, found {len(poms)}"]
    for path in poms:
        problems += [f"{path}: {p}" for p in problems_in(path)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    print(f"{len(poms)} POMs hold to the dependency decisions")


if __name__ == "__main__":
    main()
