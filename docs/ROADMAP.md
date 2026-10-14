# Delivery phases and acceptance gates

The full Immortal World specification is the direction, not a statement of current
completion. Do not ship buttons for systems whose mechanics do not exist.

## Phase 1 — foundation (source implemented; acceptance blocked)

Native application shell; character creation and generation; complete initial
character records; status; independent persistent slots; manual/autosaving;
validated portable JSON; backups; real journal edits to exercise persistence.

Before considering this phase complete:

- [x] Platform-free core compiles and local checks pass.
- [ ] Gradle Android debug build and lint pass.
- [ ] JSON round-trip and invalid-file unit tests pass.
- [ ] Room persistence tests pass on API 26 and 35.
- [ ] Compose creation/journal/inspection smoke test passes.
- [ ] File-picker round-trip, force-stop/relaunch, accessibility, small-screen,
      large-font, and rotation acceptance checks pass on Android.
- [ ] Generate/review/commit Room schema export from a successful Android build.
- [ ] Produce a working debug APK and record device/API test evidence.

## Phase 2 — authoritative gameplay

Add typed engine commands and receipts, configurable cultivation/substages,
resource use, progression and breakthroughs, lifespan/tribulations, passive
cultivation opt-in, deterministic combat/RNG logs, equipment and item effects,
technique costs/mastery, normal loot, and offline encounters. Normalize/query
inventory and encounters when needed, with tested migrations.

Gate: reproduce combat from a saved RNG state; players and NPCs obey the same
rules; costs and equipment affect outcomes; death/retreat/failure behave correctly;
no normal action bypasses progression or silently enables God Mode; older saves
migrate without loss. No AI dependency for mechanical gameplay.

## Phase 3 — provider-independent AI

Configurable endpoint/model/request profiles, secure credentials separate from
saves, provider connection tests, async/cancellable requests, retry/error handling,
bounded relevant context, allowlisted action proposals, engine validation,
authoritative result narration, and persistent NPC memory groundwork.

Gate: adversarial model output cannot mutate state directly or fabricate mechanical
outcomes. Invalid operations fail closed. Cancellation cannot apply an action twice.
Network failure never disables local play/saving. No full-save or unrelated private
data transmission. Never choose player speech, thoughts, or decisions for them.

## Phase 4 — persistent world and social simulation

Generate persistent world maps/locations, travel, discoveries, quests, loot tables,
factions, unique NPCs with shared generation rules and weighted rarity, identities,
personalities, goals, relationships, possessions, and memories. Add scheduled
background simulation instead of continuously running every NPC through an AI.
Build sect membership, contribution, missions, leadership and resource groundwork.

Gate: IDs/relationships survive restart, world time and selected goals progress
consistently, geography and faction power are coherent, simulation remains bounded
on low-memory phones, and quests cannot reward twice.

## Phase 5 — God Mode and extensibility

Dedicated clearly marked God Mode console with real data editors for player,
attributes, realm/lifespan, roots/bloodlines/races, techniques, items, NPCs/memories,
relationships, factions, locations, bosses, quests, time, combat setup, rules,
reputation, teleportation, tribulations, and snapshots. Add unrestricted sandbox
creation and validated custom world/realm/content configuration.

Basic import/export/backup protection was deliberately pulled into Phase 1; extend
it here for larger worlds and per-system migrations. Do not remove it or tie it to
an AI provider. Finish player-facing cultivation rule customization.

Gate: every console command updates persistent engine data, not narration; turning
God Mode off stops bypasses; structural safety remains enforced; normal play never
enables it automatically. Imported/duplicated worlds stay independent.

## Phase 6 — stabilization and release

Profile memory/queries/world simulation, expand UI and migration tests, harden file
and network error recovery, refine touch targets/accessibility/layouts, instrument
crash recovery, document privacy, and configure a release APK signing process using
external secrets. Deliver a tested release APK only after actual build/device runs.

## Full-spec backlog (not implemented or removed from scope)

- Phase 2/4: group battles, tournaments, sect wars and large conflicts; crafting,
  alchemy, forging/upgrades; advanced techniques/combinations and inheritances.
- Phase 4: full sect recruitment, disciples/elders, facilities, ranks, resources,
  territorial development and diplomacy; varied NPC role/realm distributions,
  independent personalities and long-term social memories.
- Phase 5: custom factions/races/content bundles, procedural higher paths,
  unrestricted world creation, and every listed God Mode editor operation.
- Optional later social features: companions, romance, marriage, descendants,
  succession/inheritance, with explicit opt-ins and persistent character agency.

Multiple independent campaigns are part of Phase 1, but multiple generated worlds
and mod support require the later generation/schema work. These features need
explicit rules, migrations, and tests, not narrative-only approximations. A full
specification release must account for all required backlog, not just the smaller
Phase 1 slice.
