#!/usr/bin/env bash
# Always preserve the real Gradle/test exit status; screenshot export is diagnostic only.
set -u
adb logcat -G 8M
./scripts/ci-run.sh ./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain
result=$?
mkdir -p app/build/outputs/ui-previews
adb logcat -d -s IW_UI_PREVIEW:I > app/build/outputs/ui-previews/capture.log
python3 scripts/ci-ui-previews.py --decode-only
exit "$result"
