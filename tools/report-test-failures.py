#!/usr/bin/env python3
"""Print failed JUnit cases as GitHub Actions annotations.

The release workflow runs unit tests before building. Job logs are not readable without
repository admin rights, so failures are surfaced as check annotations as well.
"""
import glob
import sys
import xml.etree.ElementTree as ET

pattern = "app/build/test-results/testReleaseUnitTest/*.xml"
found = False
for path in glob.glob(pattern):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        print(f"::error title=TestResults::Cannot parse {path}: {error}")
        continue
    for case in root.iter("testcase"):
        for node in list(case.findall("failure")) + list(case.findall("error")):
            found = True
            raw = (node.get("message") or node.text or "failed").strip()
            first = raw.splitlines()[0] if raw else "failed"
            detail = first.replace("%", "%25").replace("\r", "").replace("\n", " ")
            print(
                f"::error title={case.get('classname')}.{case.get('name')}::{detail}"
            )

if not found:
    print("::warning title=TestResults::No failed test cases found in XML reports")
sys.exit(0)
