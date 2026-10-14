#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Requires Java 17+ and Kotlin 2.1.20. No Android SDK or external libraries.
KOTLINC="${KOTLINC:-kotlinc}"
mkdir -p .cache/checks
"$KOTLINC" core/src/main/kotlin/com/immortalworld/core/*.kt \
  core/src/test/kotlin/com/immortalworld/core/CoreChecks.kt \
  core/src/test/kotlin/com/immortalworld/core/AdventureChecks.kt \
  -include-runtime -d .cache/checks/core-checks.jar
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp .cache/checks/core-checks.jar com.immortalworld.core.CoreChecks

"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp .cache/checks/core-checks.jar com.immortalworld.core.AdventureChecks
