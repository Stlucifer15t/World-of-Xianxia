# Immortal World

Native Kotlin/Jetpack Compose xianxia RPG with a **story-first, player-controlled text
adventure**, an authoritative offline game engine, optional AI narration, and
portable saves. This extends the tested Phase 1 app rather than replacing it.

**Verified build:** 0.5.0 Jade Records, code commit `35c9501`.

[Download the tested APK (ZIP)](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38666930895/artifacts/11943050164) · [Successful build and tests](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38666930895)

136 unit tests and 27 device tests on **each** of API 26/API 35 passed. Lint has
zero errors and 14 non-error findings. Extract `app-debug.apk` from the ZIP.
GitHub may require sign-in. **Read the save-export warning below before installing.**

## Protect your existing save before upgrading

**Export your campaign from the old app's Saves screen first.** CI debug APKs can
have different signing keys. If Android requires uninstalling the old version,
that deletes private saves. Export first, install the new APK, then import the
`.xianxia.json`. Version-1 and version-2 saves migrate to v3 without rerolling the player,
resetting the adventure, or deleting the journal. Keep the original export if
you may need to return to an older app; older apps do not read v3 exports. Do not uninstall without an external save copy.

## Cultivation identity & simpler settings (0.5)

- **Settings are now a hub**, not a wall of text: Provider & model, Saving & backups,
  and About this world. Back returns to the hub before leaving Settings.
- **Provider setup:** choose a service, enter your key, load/browse models, then
  **Test connection & save**. Saved profiles, limits and optional knowledge have
  their own tabs. Key visibility is controllable; detailed help stays out of the form.
- **Google Gemini** preset uses Google's compatible chat API, with text-generation
  model filtering and explicit input/output limits. No fake local-model options.
- **Cultivation records:** see your spiritual foundation first; expand identity,
  root/constitution, bloodline, talent, background and starting-abilities sections.
  Real generated traits remain authoritative; normal starting realm is unchanged.
- **Inner world:** realm, stage, breakthrough progress, qi reserves and method
  lead the cultivation screen. Techniques read as manuals; inventory categories
  distinguish pills, materials, weapons, armor and spirit treasures.
- Static formation seals, ink/jade/gold surfaces and bundled Noto fonts strengthen
  the identity without animation overhead. Narrative stays **19sp**, dialogue **18sp**.

See [design and behavior notes](docs/JADE_RECORDS.md).

## Retained connected AI settings (0.4)

- Restore the selected encrypted provider configuration on startup; optional
  asynchronous model/access checks on startup and network restoration.
- Shared connections, cancellable requests and streamed **unverified** narration
  previews. Only completed, validated prose is saved; failures never replay actions.
- Fetch compatible model lists when supported, with manual model entry always
  available. Use explicit provider context metadata or an editable manual budget,
  including **1,000,000 tokens**. **Maximum output tokens remain separate.**
- Optional targeted cultivation references from accessible Fandom/MediaWiki pages,
  custom HTTPS sources and a user-configured SearXNG search service. Bounded excerpts,
  no crawling or whole-site downloads; external text cannot change game mechanics.
- Roadmap continuation: up to **eight named provider profiles**, with independent
  encrypted credentials/settings, saved selection, rename, confirmed deletion and
  protection against accidentally discarding unsaved edits when switching.

See [AI setup, profiles, context limits and knowledge-source guide](docs/AI_SETTINGS.md).
Compatible APIs vary: metadata and SSE are not universally supported. Protected
wiki pages may be inaccessible. No paid-provider latency or prose-quality guarantee
is implied; offline gameplay remains independent of these optional services.

## Retained living story (0.3)

- **19sp narrative and 18sp dialogue**, comfortable line spacing, consolidated
  paragraphs and a larger padded action field. Native font scaling and keyboard
  constraints are respected without shrinking story text to fit buttons.
- State-aware offline scenes and arrival prose: architecture, light, cosmetic
  weather, background activity and the actual nearby NPCs' work, stock, health,
  relationships and encounters. Revisits use persisted visit counts.
- Narrative/results lead the Journey screen. **Menu** holds secondary screens and
  shortcuts; **Current surroundings** and **Recent events** open without replacing
  the reading position. Mechanical facts/calculations remain expandable.
- Persistent observed-scene snapshots and separate resolved NPC speech. AI gets
  longer scene budgets and pacing instructions, not private NPC goals/unspoken
  knowledge or a license to invent player decisions and outcomes.
- Natural-language recruitment-notice reading, resource questions, seeded spiritual
  sense and seated cultivation. Conditional requests do not silently execute an
  unconditional action or spend a generic offline turn.
- **Portable save v3** with v1/v2 migration; Room schema stays at v1. Character,
  NPC, inventory, cultivation, combat, God Mode and save-slot mechanics are retained.

See [living-story behavior, boundaries and examples](docs/LIVING_STORY.md).

## Retained active-adventure mechanics

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
- Portable format **v3**, with **v1/v2 migration** and unchanged Room schema v1.

This is a bounded first world (four connected locations, three NPCs, two quests,
one sect and a guarded ruins encounter), **not** the complete master-spec game.
Large-scale/procedural worlds, advanced sect management, group combat, arbitrary
mechanical content generation, the graphical God Mode
editor and production release signing remain future work. The existing explicit
God Mode save flag remains compatible with the engine; AI cannot enable it.

See [active adventure rules and limitations](docs/ACTIVE_ADVENTURE.md),
[save format](docs/SAVE_FORMAT.md), and [verification](docs/TESTING.md).

## Start playing

1. Continue or create a campaign. Import an exported old campaign if upgrading.
2. Open **Menu** for the existing screens, shortcuts, surroundings and recent events.
   Try `Ask Mei about cultivation resources`, `Inspect the recruitment notice`,
   `Examine Elder Wen with spiritual sense`, `Buy herb`, `Accept dewleaf`,
   `Deliver dewleaf`, or `Travel to Jade River Crossing`.
3. Investigate the crossing, travel to Silent Pine Mountain, and search for herbs
   and ore. The map shows adjacent roads and discovered locations.
4. Use **Cultivation** to preview and simulate an activity, or type
   `Cultivate for 4 hours`. Your character does not need to idle for real hours.
5. Challenge a nearby NPC or search the guarded ruins to enter combat. Try
   `Attack`, `Defend`, `Use Flowing Stream Palm`, `Move to high ground`, or `Retreat`.
6. For AI, open **Settings → Provider & model**, choose a provider and enter its key.
   Load/browse models or type an ID. Enable **Use AI storytelling**, then test and
   save (may incur provider charges), or save without testing. **Limits** holds
   context/output settings. The selected saved profile restores automatically.
   No API key is included with the app.

Unknown wording uses AI interpretation when configured, otherwise a bounded
creative-attempt check. Conditional/negated commands need a clear immediate action
offline; AI interpretations of those requests always require review. It never creates a promised victory or new power merely
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
         engine/combat/activity/world rules, read-only scene presentation, bounded AI context
app/     Existing Compose shell and ViewModel, Room repository, DataStore,
         v1/v2/v3 codec, Android file picker, compatible AI adapter and Keystore,
         pooled async transport/SSE, model discovery and bounded public references
scripts/ Dependency-free core checks and bounded CI diagnostics
docs/    Rules, save contract, architecture, phase gates and test evidence
```

Tests cover deterministic outcomes, resource bounds, agency/command restrictions,
world schedules, active NPC consistency, idempotent receipts, old-save migration,
combat/memory persistence, malformed/rate-limited/failed/cancelled AI requests,
state-grounded story snapshots, and actual Android create → action → dialogue →
notice → menu → travel flows, plus a 320dp/130%-font keyboard/draft regression.
Live paid-provider behavior and physical-device/file-picker/accessibility checks
remain separately identified; mock-server tests are not represented as live-model
verification. AI tests additionally cover pool reuse, streaming/cancellation,
metadata, million-token budgets, targeted references and encrypted named profiles.
Network use is limited to explicitly enabled/tested provider and reference features.
