#!/usr/bin/env python3
"""Publish small, readable test diagnostics through the Checks API, not only logs."""
from pathlib import Path
import base64
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET


def annotate(level, title, message):
    def escape(value):
        return str(value).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f"::{level} title={escape(title).replace(',', '%2C')}::{escape(message)}")


patterns = (
    'core/build/test-results/test/TEST-*.xml',
    'app/build/test-results/testDebugUnitTest/TEST-*.xml',
    'app/build/outputs/androidTest-results/connected/**/TEST-*.xml',
)
files = sorted({p for pattern in patterns for p in Path('.').glob(pattern)})
counts = dict(tests=0, failures=0, errors=0, skipped=0)
for path in files:
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
    for suite in suites:
        for key in counts:
            counts[key] += int(suite.attrib.get(key, 0))
        for case in suite.findall('testcase'):
            for failure in list(case.findall('failure')) + list(case.findall('error')):
                text = failure.attrib.get('message', '') + '\n' + (failure.text or '')
                annotate('error', f"Test failure: {case.attrib.get('classname')}.{case.attrib.get('name')}", text[:3400])
annotate('notice', 'JUnit results', json.dumps(dict(reports=len(files), **counts)))

lint = Path('app/build/reports/lint-results-debug.xml')
if lint.exists():
    issues = ET.parse(lint).getroot().findall('issue')
    errors = [issue for issue in issues if issue.attrib['severity'] in ('Error', 'Fatal')]
    annotate('notice', 'Android lint', f"{len(errors)} errors; {len(issues) - len(errors)} other findings")
    for issue in errors:
        annotate('error', 'Android lint: ' + issue.attrib['id'], issue.attrib['message'][:3400])

# Export the first real KSP-generated schema for review and checking into Git.
# Subsequent builds verify their existing schema via the normal KSP/Room tooling.
if '--schema' in sys.argv:
    for path in Path('app/schemas').rglob('*.json'):
        tracked = subprocess.run(['git', 'ls-files', '--error-unmatch', str(path)], capture_output=True).returncode == 0
        if tracked:
            continue
        data = path.read_bytes()
        encoded = base64.b64encode(data).decode('ascii')
        chunks = [encoded[i:i + 2800] for i in range(0, len(encoded), 2800)]
        for index, chunk in enumerate(chunks, 1):
            annotate('notice', f'Room schema export {index}/{len(chunks)}', json.dumps(dict(
                path=str(path), part=index, parts=len(chunks), sha256=hashlib.sha256(data).hexdigest(), base64=chunk)))
