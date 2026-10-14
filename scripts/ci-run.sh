#!/usr/bin/env bash
# Preserve the real command status and expose a bounded diagnostic through the
# Checks API as well as downloadable logs (useful on restricted development hosts).
set -uo pipefail
mkdir -p .cache/ci
log="$(mktemp .cache/ci/command-XXXXXX.log)"
"$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  python3 - "$log" "$status" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text(errors="replace")
# Individual Checks API annotation messages are truncated at 4096 characters.
# Keep a short beginning plus the diagnostic tail and use bounded chunks.
if len(text) > 27000:
    text = text[:3000] + "\n… middle omitted; see full workflow log …\n" + text[-23500:]
chunks = [text[i:i + 3000] for i in range(0, len(text), 3000)]
for index, chunk in enumerate(chunks, 1):
    chunk = chunk.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f"::error title=Command failed (exit {sys.argv[2]}) part {index}/{len(chunks)}::{chunk}")
PY
fi
exit "$status"
