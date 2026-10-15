#!/usr/bin/env python3
"""Bounded emulator screenshots in Checks, for environments unable to fetch artifact CDN URLs.
Only generated instrumented-test screens are captured; never a user's device or real key.
"""
import base64
import json
import re
import sys
from pathlib import Path

directory = Path('app/build/outputs/ui-previews')
log = directory / 'capture.log'
if log.exists():
    images = {}
    for line in log.read_text(errors='replace').splitlines():
        match = re.search(r'IW_UI_PREVIEW\s*: (\{.*\})', line)
        if not match:
            continue
        try:
            item = json.loads(match.group(1))
            name = item['file']
            if not re.fullmatch(r'[a-z-]+\.jpg', name) or not 1 <= item['part'] <= item['parts'] <= 100:
                continue
            images.setdefault(name, {})[item['part']] = item
        except (ValueError, KeyError, TypeError):
            continue
    for name, chunks in images.items():
        parts = next(iter(chunks.values()))['parts']
        if set(chunks) != set(range(1, parts + 1)):
            print(f'::warning::Incomplete native preview: {name}')
            continue
        data = base64.b64decode(''.join(chunks[i]['base64'] for i in range(1, parts + 1)), validate=True)
        if len(data) <= 160_000:
            (directory / name).write_bytes(data)
    if not images:
        print('::warning::No native preview frames were captured.')
if '--decode-only' in sys.argv:
    sys.exit(0)

if '--batch' not in sys.argv:
    print(f"::notice title=Native UI previews::{len(list(directory.glob('*.jpg')))} emulator screenshots are included in the device-test artifact.")
    sys.exit(0)
batch = int(sys.argv[sys.argv.index('--batch') + 1])
annotation_index = 0
for path in sorted(Path('app/build/outputs/ui-previews').glob('*.jpg'))[:6]:
    data = path.read_bytes()
    if len(data) > 160_000:
        continue
    encoded = base64.b64encode(data).decode('ascii')
    chunks = [encoded[i:i+3800] for i in range(0, len(encoded), 3800)]
    for i, chunk in enumerate(chunks, 1):
        message = json.dumps(dict(file=path.name, part=i, parts=len(chunks), base64=chunk))
        if annotation_index // 10 == batch:
            print(f'::notice title=UI preview {path.stem} {i}/{len(chunks)}::{message}')
        annotation_index += 1
