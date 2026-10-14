# Immortal World

A native, offline-first Android xianxia RPG foundation. Kotlin + Jetpack Compose,
with a provider-independent game core, Room persistence, and portable JSON saves.

**Status: Phase 1 implemented in source, not yet Android-build/device verified.**
This is not the complete RPG. No APK has been produced in this workspace. Do not
advance to Phase 2 until the Android build and device acceptance gates pass.

## Implemented in this increment

- Native Compose home, new-character flow, journey journal, character inspection,
  save manager, and preferences. No web wrapper or chatbot stand-in.
- Editable identity, custom species/backgrounds, appearance, age, personality,
  starting circumstances/location, difficulty, and spiritual-root selection.
- Seeded random generation and background presets; eleven attributes, potential,
  weighted rare traits, cultivation record, talents, starter techniques/items,
  equipment, currencies, and resources persist as actual data.
- Independent save slots; continue, manual snapshots, autosave on edits, load,
  rename, duplicate, delete with confirmation, and five pre-overwrite backups.
- Human-readable `.xianxia.json` import/export through Android's file picker.
  Validated imports **always create a new slot**. Failed validation never writes
  game data. Unsupported fields/versions fail rather than silently losing data.
- Personal journal entries and character renaming are the state-changing actions
  available in Phase 1. Journal text does **not** execute gameplay actions.
- Interfaces, not pretend implementations, for future engine, combat, world
  generation, and AI storytelling systems.

Not yet implemented: cultivation actions, combat, item use/trading/crafting,
NPCs, simulation/travel, quests/sects, AI APIs, God Mode editor, sandbox, and
release signing. Their data/contracts do not constitute working features.

## Build an Android APK

Requirements: Android Studio Meerkat (or newer compatible IDE), **JDK 17**,
Android SDK platform **35**, build tools **35.0.0**, and internet access to
Google Maven, Maven Central, and Gradle's distribution service. Minimum device:
**Android 8.0 / API 26**.

1. Open the repository root in Android Studio and select JDK 17 for Gradle.
2. Install Android SDK 35 and accept SDK licenses in SDK Manager.
3. Let Studio create `local.properties`, or set `ANDROID_HOME` to your SDK path.
4. Run:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# On Windows, use gradlew.bat instead of ./gradlew.
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, for testing).
Install through Android Studio, a downloaded APK on your phone, or:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

With a device/emulator connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The checked-in [GitHub Actions workflow](.github/workflows/android.yml) builds the
APK, runs unit tests/lint, and runs instrumentation tests on APIs 26 and 35.
When the branch is pushed and the workflow succeeds, download
`immortal-world-phase1-debug-apk` from the workflow's artifacts. This is **not** a
claim that CI has already run. A debug artifact alone does not prove the device
test job passed; verify both jobs.

## Try the foundation

1. **Begin a new life** → edit or randomize → **Begin journey & create save**.
2. Inspect your cultivation, attributes, techniques, and inventory in **Character**.
3. Write a journal entry. Autosave is on by default; wait for the saved message.
4. In **Saves**, create a duplicate, rename a campaign, or export a snapshot.
5. Import that export: a separate slot is created, even if the file has an existing ID.
6. Open **Backups** to restore a prior revision. Restoring also backs up the replaced revision.
7. Turn autosave off in **Settings** to test manual saving and explicit discard.

Save before force-stopping when autosave is disabled. Drafts that have not been
committed are not guaranteed across process death. Uninstalling clears private
saves and local backups; export to a document provider to preserve them.

## Verification recorded here

- **Passed:** compilation of the pure Kotlin core using Kotlin 2.1.20 / Java 17.
- **Passed:** 34 core checks, including 10,000-character rarity sampling, RNG
  restoration, custom characters, and invalid-state rejection.
- **Blocked:** Gradle/Android compilation, JSON unit tests, Room/device tests,
  Compose UI tests, lint, APK generation. This sandbox cannot download Gradle
  or Google's Android SDK; the attempted wrapper bootstrap failed with an SSL
  connection error. No Android test success is implied.

Core checks can also run without Android or Gradle, with Java 17 and Kotlin 2.1.20
on PATH:

```sh
./scripts/check-core.sh
```

## Project layout

```text
core/         Immutable engine models, seeded generation, validation, future ports
app/          Compose UI, ViewModel orchestration, Room, DataStore, JSON & SAF
  src/test/             Portable-save unit tests
  src/androidTest/      Database/recovery and Compose flow tests
scripts/      Dependency-free core test runner
docs/         Architecture, save contract, roadmap, acceptance checklist
```

Read [architecture](docs/ARCHITECTURE.md), [save format](docs/SAVE_FORMAT.md),
[phase gates](docs/ROADMAP.md), and [device acceptance tests](docs/TESTING.md).

No API keys, network permission, external storage permission, telemetry, or AI
provider dependency are included in this phase.
