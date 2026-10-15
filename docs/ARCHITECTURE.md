# Architecture and authority boundaries

## AI opening and prose-only regeneration (0.6)

`GameViewModel.create()` waits for encrypted provider preferences to finish loading,
uses the existing seeded generator, and atomically persists/activates the character
with `OpeningScene.attach()` before calling the real compatible storyteller. There
is no synthetic OBSERVE command: the opening consumes no time, RNG or resources,
and does not advance the engine sequence, NPCs, quests or relationships.

`OpeningScene` records bounded visible creation facts and an observed-scene/local
fallback snapshot in the existing v3 `ActionRecord` fields. A reserved `opening:`
ID prefix with empty action kind identifies this receipt across import/duplicate;
recognition does not depend on the new slot ID. No Room or portable schema migration
is needed. History keeps the opening plus the latest 99 ordinary receipts within
its existing 100-record limit. Existing event/memory retention is unchanged.

The opening context is built solely from that receipt, not the mutable campaign.
It contains no hidden traits, private journal entries, NPC goals or unspoken
knowledge. `pace=opening` asks for a substantial original beginning grounded in
selections rather than a mechanical recap. The actual saved mechanics and player
agency remain authoritative. The existing provider adapter, endpoint resolver,
streaming transport, bounds and optional reference lookup are reused.

Regeneration targets only the latest accepted receipt. Before an action this is
**Regenerate opening**; afterwards it is **Regenerate response**. A legacy untouched
start may explicitly attach its missing opening before requesting prose; loading
alone never makes a paid request. Dirty edits require save/discard first. Busy
requests cannot be submitted twice. Streaming previews are transient and guarded
by the request job; cancelled jobs cannot inject late tokens into another attempt.
Only a completed, validated response replaces narration through revision-checked
repository persistence. Errors/cancellation leave the last good response untouched.

The one-Application ViewModel constructor remains available for Android's factory.
A defaulted narrator dependency permits deterministic lifecycle/UI tests; production
still calls `CompatibleStoryteller.narrateStreaming`. Wire tests independently
exercise actual HTTP payloads, SSE completion, authentication and response guards.

## Custom API URL correction (0.5.1)

`AiEndpoints` is the shared, pure resolver used by validation, `ModelDiscovery`
and every `CompatibleStoryteller` request. It accepts base URLs or full chat URLs,
preserves origin/port/nested API prefixes, and derives sibling models/chat routes.
Stored configuration stays unchanged. Gemini retains its bounded native catalog
route/header; generation remains compatible chat. URL safety checks still reject
credentials, query parameters, fragments and production plaintext HTTP. There is
no endpoint probing or fallback paid request. This fixes the previous mismatch
where discovery required a full chat path and generation POSTed to a bare base URL.

## Jade-records UI refinement (0.5)

`CultivationDesign.kt` draws static formation ornaments and provides settings cards,
expandable creation records and a realm/qi tablet sourced directly from `GameSave`.
Bundled Noto fonts separate practical controls from serif story text; UI scaling
and the persistent adventure composer remain native Compose. The creator still
calls the original seeded generator and never changes its seed on section toggles.

Settings adds an internal hub/section route and local AI tabs. Credentials stay
in `remember`, not saveable Bundles. Provider drafts survive tab switches, while
saved profiles keep their existing DataStore/Keystore contract. `testAndSaveAi`
awaits the existing short connection test before persisting to the captured profile
ID. Preset selection only fills a supported endpoint; origin changes clear draft
credentials. `ModelDiscovery` adds a Gemini-only catalog mapping with explicit
metadata and generation-method filtering; chat generation still uses the same
compatible adapter. No save schema or engine state was changed by this refinement.

See `JADE_RECORDS.md` for visual behavior and `TESTING.md` for actual evidence.

## Connected-AI extension (0.4)

The existing adapter and device DataStore remain the integration points. No
campaign table or portable-save version changed. `AiPreferences` now stores a
versioned set of named profiles in that same DataStore and migrates legacy values
only after a successful atomic write. Android Keystore AES-GCM protects each key;
unreadable ciphertext is preserved unless its own profile is explicitly repaired
or deleted. Saves are bound to the edited profile ID.

`AiTransport` shares an OkHttp pool/dispatcher, uses request-local credentials and
cancellable asynchronous callbacks, and bounds response sizes/deadlines.
`ModelDiscovery` reads compatible catalogs; explicit metadata is tied to endpoint,
credential and selected model. Unknown capacity retains an editable manual fallback.
`ContextBudget` checks a conservative byte-based estimate, not an exact tokenizer.
`ChatStream` handles bounded SSE and throttled transient previews. Only complete,
validated narration follows the existing persistence path; no streaming fragment
can execute an action or become an accepted result.

`GameViewModel` restores configuration at startup, manages cancellable metadata
checks with epoch guards, and observes validated network transitions for bounded
reconnection. It does not retry paid actions on network recovery. The native
settings panel reuses existing state, controls and theme, adding model selection,
independent context/output boxes, named profiles and opt-in source controls.

`KnowledgeService` uses a separate unauthenticated public-document client with
HTTPS/address guards and bounded targeted retrieval. It produces ephemeral,
untrusted reference notes for narration, never database patches or player/NPC
knowledge. Search queries exclude private action text unless the user explicitly
enters a test query. Source previews allow inspection; references are not new
campaign canon. See `AI_SETTINGS.md` for precise limits and provider boundaries.

## Living-story extension (0.3)

`SceneNarrator` is a platform-independent, read-only presentation of the existing
save and schedules. It upgrades exact stock location descriptions, derives cosmetic
weather from world ID/time, and presents current NPC work/stock/encounters without
consuming engine RNG. It does not spawn another simulation or storage system.

`ActionRecord` now keeps offline prose, resolved dialogue and an observed-scene
snapshot alongside the original facts/calculations. `ContextBuilder` uses that
snapshot for historical narration, excluding private goals/unspoken knowledge and
present-day targets. `StoryText` consolidates short fragmented output. The provider
receives explicit pacing and bounded longer scene budgets but still cannot mutate
mechanics. A seeded `SENSE` action and public-notice inspection are normal validated
engine operations, not narrator-created rewards.

`AdventureScreen` uses native scalable prose and a persistent adaptive composer;
secondary controls move into a standard Material bottom sheet. Historical entries
are progressively revealed, never deleted for layout. Existing screens, keyboard
inset handling, action commit ordering and save repository are retained.

Portable v3 adds receipt presentation fields and visits; v1/v2 decode migrations
preserve mechanical state and custom text. The next overwrite keeps the original
raw JSON as a backup. SQL schema v1 is unchanged. See `LIVING_STORY.md` for behavior,
`SAVE_FORMAT.md` for exact bounds, and `TESTING.md` for verified builds.

## Active-adventure extension (0.2, retained)

The Phase 1 storage/shell described below is retained. `AdventureEngine` now
implements `GameEngine`, using the original character models, generator, RNG and
validator. Combat, activities, NPC schedules and receipts are data in the same
campaign aggregate, not a separate game or parallel persistence system.

`GameSave` portable version 2 adds nullable `AdventureState`; legacy initialization
uses the existing backed-up repository write. SQL schema v1 is unchanged. The
ViewModel commits engine results and receipt IDs in a non-cancellable transaction
boundary before optional AI narration. Rejections produce receipts but do not
advance RNG/time or spend resources. Narration retries cannot replay mechanics.

`CompatibleStoryteller` implements the original `Storyteller` port using a bounded,
cancellable OpenAI-compatible request. It receives only `NarrativeContext`, never
Room, a complete save or a mutator. Local ordinary commands do not need AI. Unknown
wording can request an allowlisted proposal; consequential proposals require player
review. Provider output cannot set stats, time settings or God Mode. Keys are
AES-GCM encrypted with Android Keystore in a separate provider DataStore.

NPCs are generated once through the shared generator and persisted. The new world
is a small authored topology with generated character variation, not the full
procedural-world system. Background schedules skip active duel participants and
remember duel endings. Activity claims are explicitly armed and explicitly claimed;
there is no background worker or forced real-time gate.

See `ACTIVE_ADVENTURE.md` for the exact current mechanics and limitations. The
remaining sections document the original foundation decisions; statements about
future systems are historical Phase 1 scope, superseded by this section.

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

## Original foundation contracts — historical Phase 1 scope

`GameEngine.apply` must be the sole normal-play mechanical authority. Every action
must validate context, costs, targets, time bounds, and resulting state.
`CombatEngine` must produce calculations/facts, not ask a model to select winners.
`Storyteller` may propose a typed `GameAction` and narrate bounded authoritative
facts. The provider must never receive database access or a raw state-mutation
function. Even a syntactically valid proposed action is an untrusted request.
`WorldGenerator` will persist locations, factions, and NPC identities once, rather
than inventing them on every inspection.

At Phase 1 these were interfaces only. The retained 0.2–0.4 extensions above now
implement bounded combat/world mechanics and compatible AI with profiles, secure
credentials, context limits, cancellation and output validation. INTERNET and
ACCESS_NETWORK_STATE are declared for opt-in network features. The larger
procedural-world milestone is still unfinished; no inactive controls pretend it exists.

God Mode later uses a distinct, explicitly enabled command path through the
same validation/persistence boundary, records real state edits, and displays a
persistent indicator. A v1 imported save with `godMode: true` already displays
that indicator, but no God Mode editor is implemented yet. Structural limits
remain enforced even when God Mode is enabled.
