# Delivery phases and acceptance gates

## Cultivation identity and simpler settings (0.5)

Refines the existing native screens, not the engine: a card-based settings hub;
short provider setup with searchable model selection and explicit test-and-save;
secondary Saved/Limits/Knowledge tabs; expandable cultivation creation records;
static formation seals; bundled, licensed Noto typography; a realm/progress/qi
inner-world tablet; and distinct technique manuals and inventory categories.
Google Gemini is offered through its compatible chat API, with a bounded,
text-generation-filtered native model catalog. Unsupported local-model/Cloudflare
runtimes are not presented as working integrations.

All creation fields and generated traits retain their existing mechanics. Normal
starts remain Qi Refining Stage 1; no decorative root quality or realm selector
invents powers. Save v3 and Room v1 remain unchanged. See `JADE_RECORDS.md` and
`TESTING.md` for scope and actual verification, including unresolved manual checks.

## Connected AI and request-profile continuation (0.4)

The requested AI improvements extend Phase 3 in the existing architecture:
saved configuration restoration and optional startup/network-return metadata
checks, pooled asynchronous transport, streamed narration previews, model catalogs,
explicit detected/manual context budgets, separate output limits, and opt-in
bounded cultivation web references. See `AI_SETTINGS.md` for supported protocols,
privacy, setup and failure behavior, and `TESTING.md` for verified build evidence.

After that increment, the next unfinished provider feature—**named request
profiles**—was continued: up to eight independent configurations/credentials,
saved selection, copy-as-new, rename, and confirmed switching/deletion. Existing
single-provider settings migrate without touching campaign saves. This removes
multiple profiles from the unfinished-feature list, not the rest of Phase 3's
ongoing adversarial/live-provider acceptance or the larger game backlog.

Remaining next work includes wider provider/manual-device acceptance and the
unfinished Phase 4 generated-world/social systems, followed by the dedicated
Phase 5 editor. Existing bounded world mechanics must be extended, not rebuilt.

## Story-first refinement (0.3)

The existing adventure now has larger native prose/dialogue, a compact controls
sheet, contextual read-only scenes, visit-aware arrivals, persistent story snapshots,
state-grounded NPC replies, notice inspection and seeded spiritual sense. V1/v2
saves migrate to v3 without resetting mechanics. See `LIVING_STORY.md` and the
current verification record in `TESTING.md`. This is not completion of the larger
procedural-world, autonomous-NPC or full God Mode editor milestones.


The full Immortal World specification is the direction, not a statement of current
completion. Do not ship buttons for systems whose mechanics do not exist.

## Added prerequisite — active player-controlled gameplay (0.2)

The new user-directed adventure model is implemented before continuing the wider
roadmap. It reuses all Phase 1 foundations and brings forward a bounded subset of
Phases 2–4: free-text action receipts, a provider adapter, single-opponent combat,
optional instant/claimed activities, a small persistent world, NPC memories/trade,
quests, faction entry and scheduled simulation.

This is not a claim that the full later phases are complete. In particular, full
procedural generation, advanced factions/sects/combat, arbitrary content creation,
the graphical God Mode editor remain outstanding. Named AI profiles were added in 0.4. See
`ACTIVE_ADVENTURE.md` for implemented behavior and `TESTING.md` for current evidence.

## Phase 1 — foundation (automated verification passed; manual acceptance open)

Native application shell; character creation and generation; complete initial
character records; status; independent persistent slots; manual/autosaving;
validated portable JSON; backups; real journal edits to exercise persistence.

Before considering this phase complete:

- [x] Platform-free core compiles and local checks pass.
- [x] Gradle Android debug build and lint pass (zero errors; 14 non-error findings).
- [x] JSON round-trip and invalid-file unit tests pass (18 codec tests).
- [x] Room persistence tests pass on API 26 and 35 (14 per API).
- [x] Compose creation/journal/inspection smoke test passes on both APIs.
- [ ] File-picker round-trip, force-stop/relaunch, accessibility, small-screen,
      large-font, and rotation acceptance checks pass on Android.
- [x] Generate/review/commit Room schema export from a successful Android build.
- [x] Produce a debug APK and record automated device/API test evidence.

Evidence: [run 38632996109](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38632996109),
code commit `89266cb399c0ccb88fcd776771b3ce05d405794d`.

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
