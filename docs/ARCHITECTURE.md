# Architecture and authority boundaries

## Scope and current decisions

This repository originally contained only a README. Phase 1 creates a native
Android foundation rather than replacing an existing engine or implementing a
web prototype. Android compilation, unit tests, and emulator tests on APIs 26/35
have now passed on GitHub Actions. Manual file-picker/device acceptance is still
open; see `TESTING.md` for the exact scope and evidence.

### `:core` — platform-free state

Immutable Kotlin records represent identity, eleven attributes, cultivation,
techniques, inventory/equipment, game rules, world metadata, journal, and a
configurable realm hierarchy. `SaveValidator` is shared by creation, UI edits,
and every persistence boundary. Save versions, references, uniqueness, resource
bounds, finite values, collection limits, and equipment constraints are checked.

`WorldRandom` uses a documented 64-bit LCG, with normal JVM two's-complement
wraparound:

```
state = state * 6364136223846793005 + 1442695040888963407
result = (state unsigned-shift-right 1) mod bound
```

This is reproducible, not cryptographically secure. Do not change the algorithm
without migrating its saved state or introducing an RNG version. Mechanical
randomness comes from the persisted state; UI creation chooses a fresh seed only
when explicitly randomized. Cosmetic IDs use UUIDs and do not consume engine RNG.
The core itself compiles without Android or an AI SDK.

Generation rarity is 0.5% Heaven-touched, 4.5% Exceptional, 20% Gifted, 75%
Ordinary. Constitutions/bloodlines are a subset of those rolls. Backgrounds apply
explicit stat modifiers. Derived maximum health and energy use vitality and
spiritual power. Race is identity data in Phase 1, not an undocumented power
multiplier. All normal starts are Qi Refining stage 1, with God Mode off. Full
NPC role/realm distributions and unrestricted sandbox starts belong to later phases.

### `:app` — lifecycle and user interaction

A single Compose activity has a retained `AndroidViewModel`. Navigation does not
regenerate or replace a character. Six working destinations cover the current
scope. All screens derive from the same active `GameSave`.

The ViewModel gates operations while busy. A dirty working copy is explicit;
creating/loading/importing/restoring another campaign is refused until the user
saves or discards it. Changing tabs does not discard it. Recreated activities
retain the ViewModel; after process death the home screen offers the last stored
slot. Uncommitted edits with autosave disabled are intentionally memory-only.

New games always commit. Journal/name mutations autosave immediately when enabled;
there is no unreliable background timer. A failed autosave leaves the working
copy marked dirty. File and database operations run on IO dispatchers; character
creation runs on Default. Small creation previews are deterministic and cheap.
UI data is collected with lifecycle awareness. Lists are lazy and journals bounded.

### Storage choice

Room stores three tables:

- `slots`: indexed campaign metadata and monotonic revision.
- `states`: one validated complete JSON aggregate per slot, FK cascade.
- `backups`: up to five prior aggregates per slot, FK cascade.

Phase 1 deliberately uses **snapshot-oriented Room persistence**, not a fully
normalized NPC/inventory simulation schema. A campaign is currently small and
loaded as one aggregate; transactional metadata + payload eliminates torn saves
and portable/internal model drift. This is a foundation trade-off, not a claim
that all future world data already has relational tables. As Phase 2/4 require
queries over inventories/NPCs, introduce child tables and explicit Room migrations,
keeping the portable `GameSave` contract independent of SQL layout. Never use
`fallbackToDestructiveMigration` for user saves.

`app/schemas/com.immortalworld.data.GameDatabase/1.json` is the actual KSP-generated
schema from a successful GitHub Android build, retrieved through Checks API
annotations with a SHA-256 integrity check, reviewed, and committed. Add explicit
migration tests before changing schema versions; do not hand-invent schema exports.

All overwrites validate before writing, check the expected revision, insert a
backup, update metadata/state, and prune backups within one transaction. Stale
writes/deletions fail. Restore validates a chosen backup without requiring the
current corrupted payload to decode; the old payload is still preserved before
replacement. Import always forks to a fresh UUID. IDs inside each campaign are
campaign-scoped so duplication can preserve entity relationships unchanged.

DataStore holds device preferences (autosave and last slot); game rules live
inside the campaign. Portable saves never include device URI grants or secrets.
Android OS auto-backup is disabled: explicit user exports are the supported
cross-device backup route in this phase. SAF creates/opens documents without
broad filesystem permissions.

## Future modules — interfaces only today

`GameEngine.apply` must be the sole normal-play mechanical authority. Every action
must validate context, costs, targets, time bounds, and resulting state.
`CombatEngine` must produce calculations/facts, not ask a model to select winners.
`Storyteller` may propose a typed `GameAction` and narrate bounded authoritative
facts. The provider must never receive database access or a raw state-mutation
function. Even a syntactically valid proposed action is an untrusted request.
`WorldGenerator` will persist locations, factions, and NPC identities once, rather
than inventing them on every inspection.

The current contracts do not make AI, combat, or world simulation functional.
There are deliberately no inactive controls for them. Phase 3 must implement
HTTP profile storage, secure credential handling, bounded/relevant context,
cancellation/retry, errors, and output validation before enabling provider UI.
No network permission is currently needed or declared.

God Mode later uses a distinct, explicitly enabled command path through the
same validation/persistence boundary, records real state edits, and displays a
persistent indicator. A v1 imported save with `godMode: true` already displays
that indicator, but no God Mode editor is implemented yet. Structural limits
remain enforced even when God Mode is enabled.
