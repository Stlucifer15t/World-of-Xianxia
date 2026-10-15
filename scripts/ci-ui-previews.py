#!/usr/bin/env python3
"""Bounded emulator screenshots in Checks, for environments unable to fetch artifact CDN URLs.
Only generated instrumented-test screens are captured; never a user's device or real key.
"""
import base64
import json
from pathlib import Path

for path in sorted(Path('app/build/outputs/ui-previews').glob('*.jpg'))[:6]:
    data = path.read_bytes()
    if len(data) > 160_000:
        continue
    encoded = base64.b64encode(data).decode('ascii')
    chunks = [encoded[i:i+2800] for i in range(0, len(encoded), 2800)]
    for i, chunk in enumerate(chunks, 1):
        message = json.dumps(dict(file=path.name, part=i, parts=len(chunks), base64=chunk))
        print(f'::notice title=UI preview {path.stem} {i}/{len(chunks)}::{message}')
