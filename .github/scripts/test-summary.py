#!/usr/bin/env python3
"""Writes a CI job's summary of a test run.

Each module's results, from the JUnit XML sbt writes to
<module>/target/test-reports, as a table with the failures beneath it, then the
report TwirlSpecReporter wrote of the all module's tests, if there is one.

Usage: test-summary.py [report.md]
Writes to the file $GITHUB_STEP_SUMMARY names, or to standard output.
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET

KEYS = ("tests", "failures", "errors", "skipped")


def cell(text):
    """Text that cannot break a Markdown table row."""
    return text.replace("|", "\\|").replace("\n", " ")


def seconds(time):
    return f"{time:.1f} s"


def summary(report):
    modules = sorted({path.split("/")[0] for path in glob.glob("*/target/test-reports/*.xml")})
    rows, failures = [], []
    total = dict.fromkeys(KEYS, 0)
    total_time = 0.0
    for module in modules:
        counts = dict.fromkeys(KEYS, 0)
        time = 0.0
        for path in sorted(glob.glob(f"{module}/target/test-reports/*.xml")):
            suite = ET.parse(path).getroot()
            for key in KEYS:
                counts[key] += int(suite.get(key, 0))
            time += float(suite.get("time", 0))
            for case in suite.iter("testcase"):
                for bad in case.findall("failure") + case.findall("error"):
                    message = (bad.get("message") or "").strip().splitlines()
                    first = message[0][:200] if message else ""
                    failures.append(
                        f"- **{module}** `{cell(suite.get('name', ''))}`: "
                        f"{cell(case.get('name', ''))}: {cell(first)}"
                    )
        for key in KEYS:
            total[key] += counts[key]
        total_time += time
        rows.append(
            f"| {module} | {counts['tests']} | {counts['failures']} | {counts['errors']} "
            f"| {counts['skipped']} | {seconds(time)} |"
        )

    bad = total["failures"] + total["errors"]
    passed = total["tests"] - bad - total["skipped"]
    head = f"✗ {bad} of {total['tests']} tests failed" if bad else f"✓ {passed} tests passed"
    lines = [
        f"## {head}",
        "",
        "| Module | Tests | Failed | Errors | Skipped | Time |",
        "|---|---:|---:|---:|---:|---:|",
        *rows,
        f"| **all modules** | **{total['tests']}** | **{total['failures']}** | **{total['errors']}** "
        f"| **{total['skipped']}** | **{seconds(total_time)}** |",
    ]
    if failures:
        lines += ["", "### Failures", "", *failures[:50]]
        if len(failures) > 50:
            lines.append(f"- and {len(failures) - 50} more")
    if report and os.path.isfile(report):
        with open(report, encoding="utf-8") as f:
            lines += [
                "",
                "<details><summary>The report of the all module's tests, by suite and page</summary>",
                "",
                f.read(),
                "</details>",
            ]
    return "\n".join(lines) + "\n"


def main():
    report = sys.argv[1] if len(sys.argv) > 1 else None
    text = summary(report)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
