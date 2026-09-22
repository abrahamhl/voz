"""Prints test counts per module from Gradle JUnit XML reports (used by CI)."""
import glob
import sys
import xml.etree.ElementTree as ET

for module in sys.argv[1:]:
    tests = failures = errors = skipped = 0
    for path in glob.glob(f"{module}/build/test-results/**/*.xml", recursive=True):
        root = ET.parse(path).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    print(f"- `{module}`: {tests} tests, {failures} failures, {errors} errors, {skipped} skipped")
