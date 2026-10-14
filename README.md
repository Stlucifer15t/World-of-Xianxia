# Immortal World

Native Kotlin/Jetpack Compose xianxia RPG with an **active, player-controlled text
adventure**, an authoritative offline game engine, optional AI narration, and
portable saves. This extends the tested Phase 1 app rather than replacing it.

**Verified build:** 0.2.0 active adventure, code commit `ae34852`.

[Download the tested APK (ZIP)](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38643323537/artifacts/11931333038) · [Successful build and tests](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38643323537)

94 unit tests and 17 device tests on **each** of API 26/API 35 passed. Lint has
zero errors and 14 non-error findings. Extract `app-debug.apk` from the ZIP.
GitHub may require sign-in. **Read the save-export warning below before installing.**

## Protect your existing save before upgrading

**Export your campaign from the old app's Saves screen first.** CI debug APKs can
have different signing keys. If Android requires uninstalling the old version,
that deletes private saves. Export first, install the new APK, then import the
`.xianxia.json`. Version-1 saves migrate without rerolling the player or deleting
the journal. Do not uninstall without an external save copy.

## Current increment: active adventure (0.2)

- Persistent action input: type actions, intentions or your own NPC dialogue.
  Convenience buttons are optional. Drafts survive character/journal inspection.
- Engine-validated travel, inspection, investigations, finite searches, NPC
  dialogue/memory, bargaining, trading, quests and sect entry in a small connected
  world. NPCs and faction resources have scheduled activity and a persistent log.
- Turn-based NPC duels and guarded-ruins encounters: attacks, techniques, targets,
  defense, dodge, cover/high ground, items, negotiation and retreat. Hit results,
  energy, damage, equipment, resistance, injury and RNG are actual game data.
- Optional Cultivation, Meditation, Technique Practice, Body Training, Alchemy and
  Crafting: preview outcomes/costs, then simulate 1–24 in-game hours immediately.
  Breakthroughs require a separate choice. No forced real-time waiting.
- Optional, explicitly armed real-time activity claims (24-hour cap). Paused,
  normal and accelerated background schedules never choose player actions.
- Replaceable OpenAI-compatible provider interface with configurable HTTPS endpoint,
  model, temperature and encrypted API key. Connection test, cancellation, safe
  network errors and narration-only retry. Offline play does not require a key.
- Existing creator, character records, private journal, independent save slots,
  manual snapshots, backups, duplication and Android file-picker import/export
  retained. Mechanics and action receipts save before AI narration runs.
- Portable format **v2**, with **v1 migration** and unchanged Room schema v1.

This is a bounded first world (four connected locations, three NPCs, two quests,
one sect and a guarded ruins encounter), **not** the complete master-spec game.
Large-scale/procedural worlds, advanced sect management, group combat, arbitrary
mechanical content generation, multiple provider profiles, the graphical God Mode
editor and production release signing remain future work. The existing explicit
God Mode save flag remains compatible with the engine; AI cannot enable it.

See [active adventure rules and limitations](docs/ACTIVE_ADVENTURE.md),
[save format](docs/SAVE_FORMAT.md), and [verification](docs/TESTING.md).

## Start playing

1. Continue or create a campaign. Import an exported old campaign if upgrading.
2. Try `Ask Mei about Moonfall Ruins`, `Buy herb`, `Accept dewleaf`,
   `Deliver dewleaf`, or `Travel to Jade River Crossing`.
3. Investigate the crossing, travel to Silent Pine Mountain, and search for herbs
   and ore. The map shows adjacent roads and discovered locations.
4. Use **Cultivation** to preview and simulate an activity, or type
   `Cultivate for 4 hours`. Your character does not need to idle for real hours.
5. Challenge a nearby NPC or search the guarded ruins to enter combat. Try
   `Attack`, `Defend`, `Use Flowing Stream Palm`, `Move to high ground`, or `Retreat`.
6. For AI, open **Settings**, enter your provider's complete HTTPS
   **chat/completions** endpoint, model and key, then save and test it. Provider
   calls may incur charges. No API key is included with the app.

Unknown wording uses AI interpretation when configured, otherwise a bounded
creative-attempt check. It never creates a promised victory or new power merely
because the player described one. Consequential AI interpretations are reviewed
before execution; authoritative engine facts are shown separately from prose.

All adventure actions save immediately, even if private-journal autosave is off.
This also commits pending journal/name edits. Failed narration does not undo or
repeat an action. Journal/Notes text is still private and never executes commands.

## Build and test

Requirements: Android Studio Meerkat (or newer compatible IDE), **JDK 17**, Android
SDK **35**, build tools **35.0.0**, internet access to Gradle/Google Maven/Maven
Central. Minimum phone version: **Android 8.0 / API 26**.

Open this repository in Android Studio, select JDK 17 for Gradle, install SDK 35
and accept its licenses. Let Studio create `local.properties`, or set `ANDROID_HOME`.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# With an emulator or phone connected:
./gradlew :app:connectedDebugAndroidTest
```

On Windows use `gradlew.bat`. APK output:
`app/build/outputs/apk/debug/app-debug.apk`. Install through Android Studio, open
the APK on your phone, or use `adb install -r` when the signing key matches.

[GitHub Actions](.github/workflows/android.yml) builds the APK, runs unit tests and
lint, and tests on API 26 and API 35 emulators. Download
`immortal-world-debug-apk` from a successful workflow run; extract the ZIP to obtain
`app-debug.apk`. Verify both emulator jobs as well as the build. Artifacts expire;
new runs create new downloads. A debug APK is not a production-signed release.

A local Android download restriction is not a blocker: GitHub runners perform the
build and publish bounded diagnostics through the Checks API, plus full reports
and APK artifacts. Core tests can also run locally with Kotlin 2.1.20 / Java 17:

```sh
./scripts/check-core.sh
```

## Architecture

```text
core/    Character generation, immutable state, validation, action interpreter,
         engine/combat/activity/world rules, bounded AI context and provider port
app/     Existing Compose shell and ViewModel, Room repository, DataStore,
         v1/v2 codec, Android file picker, compatible AI adapter and Keystore
scripts/ Dependency-free core checks and bounded CI diagnostics
docs/    Rules, save contract, architecture, phase gates and test evidence
```

Tests cover deterministic outcomes, resource bounds, agency/command restrictions,
world schedules, active NPC consistency, idempotent receipts, old-save migration,
combat/memory persistence, malformed/rate-limited/failed/cancelled AI requests,
and the actual Android create → text action → journal → inspect → draft flow.
Live paid-provider behavior and physical-device/file-picker/accessibility checks
remain separately identified; mock-server tests are not represented as live-model
verification. Network permission is used only for explicitly enabled/tested AI.
