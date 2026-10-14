# Immortal World

A native, offline-first Android xianxia RPG foundation. Kotlin + Jetpack Compose,
with a provider-independent game core, Room persistence, and portable JSON saves.

**Status: Phase 1 builds successfully and passes automated Android verification.**
This is not the complete RPG. A debug APK is available from GitHub Actions;
physical-device, file-picker, and accessibility acceptance checks remain open.

[Successful build and tests](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38632996109)
· [Download the tested APK artifact (ZIP)](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38632996109/artifacts/11927596311)

Extract `app-debug.apk` from the ZIP and install it on an Android 8.0+ device.
GitHub may require sign-in to download artifacts. Artifacts expire; future builds
produce new downloads. This is a debug/test build, not a signed production release.

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
The workflow has passed for commit `89266cb399c0ccb88fcd776771b3ce05d405794d`.
For newer builds, download `immortal-world-phase1-debug-apk` from their workflow
artifacts and verify both emulator jobs, not just the APK build job.

CI debug signing keys may differ between runners/builds. Export your campaigns
before replacing a debug installation; if Android rejects an update's signature,
do not uninstall until your saves are exported. Stable release signing is Phase 6.

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

GitHub Actions run [38632996109](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38632996109)
on **2026-10-14** passed:

- **52 unit tests:** 34 core checks and 18 portable-save codec tests.
- **15 instrumentation tests on API 26**, and the same **15 on API 35**:
  14 Room/persistence tests and one Compose creation/journal/status flow per device.
- **Android compilation and debug APK generation.**
- **Android lint:** zero errors; 14 non-error findings remain.
- Real KSP-generated Room schema v1 was retrieved, reviewed, and checked in.

An earlier API 35 UI test failed because it attempted to use the creator before
confirming navigation. The test now scrolls the home button into view and waits
for screen transitions and async operations; assertions were not removed.

The local sandbox still cannot download Gradle/Google SDKs or direct workflow
log/artifact storage. **That is no longer a build blocker:** GitHub-hosted runners
perform the builds, and bounded diagnostics are available through the Checks API.
Physical-device/file-picker/accessibility checks are still pending; see
[testing evidence and remaining acceptance checks](docs/TESTING.md).

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
