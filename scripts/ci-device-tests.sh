#!/usr/bin/env bash
# Always preserve the real Gradle/test exit status; screenshot export is diagnostic only.
set -u
./scripts/ci-run.sh ./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain
result=$?
mkdir -p app/build/outputs/ui-previews
if ! ./scripts/ci-run.sh adb pull /data/local/tmp/immortal-world-previews/. app/build/outputs/ui-previews/; then
  echo '::warning::Native UI previews could not be collected; inspect the instrumentation results.'
fi
exit "$result"
