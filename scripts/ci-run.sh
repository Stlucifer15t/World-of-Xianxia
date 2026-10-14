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
if len(text) > 28000:
    text = text[:12000] + "\n… middle of log omitted; see full workflow log …\n" + text[-16000:]
text = text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f"::error title=Command failed (exit {sys.argv[2]})::{text}")
PY
fi
exit "$status"
