# Persistent frontier — 0.7

This is the first generated-geography increment of Phase 4, built on the existing
engine, saves, AI narrator and native Map screen. It is not completion of the
large-world, autonomous-NPC or sect-management milestones.

## Play it

**New campaigns:** creation adds six wilderness sites in three two-stop branches
around the established starter region. The chosen starting location, character,
Mei, Wen, Ren, existing quests and factions remain intact. The opening is still
sent to the saved enabled AI provider immediately after saving the character.

Open **Journey → Menu → Map**. The regional atlas shows known locations and saved
footpaths, not a decorative or simulated terrain map. Uncharted destinations do
not expose their names through road labels or AI targets.

1. **Survey nearby paths · 1 hour** uses the existing Investigate action. It charts
   directly connected destinations without moving you or granting loot. Existing
   ruins investigation/quest behavior is retained.
2. Travel to a charted adjacent destination. The button shows the actual foot-route
   duration; the generated routes take 1–4 game hours. Starter roads still take two.
3. Survey again at the next site to chart its deeper branch. There are no free
   shortcuts or automatic multi-stop journeys.
4. **Search / Gather** resolves the site's saved gathering table: two Dewleaf herbs,
   two iron ore, or both. Each site has three opportunities, persisted across
   visits and reloads. Gathering takes one hour and does not reroll rewards.
5. Return along the same road. The clock, visit count and scheduled simulation
   advance once, using the recorded duration. Repeat visits use shorter scene openings.

Normal travel requires discovery, an actual connecting road and no active combat.
Only an already explicitly enabled God Mode save can bypass normal road access;
it does not become enabled because a road is missing. Unsupported travel is rejected
without spending time/resources. Imported route durations are validated at 1–24h.

## Existing campaigns are opt-in

Loading an old campaign reconstructs its established starter/refuge road records;
it **does not generate a frontier, reroll a character or make a paid narration call**.
On Map, choose **Extend this world's frontier**, then confirm **Add frontier**.
Cancel keeps the world unchanged.

Expansion adds the six sites once, saves a generation seed and the full geometry,
and writes an engine receipt/normal revision backup. It does not move the player,
consume resources, advance the clock or change existing NPC/quest/player records.
It makes no AI request, even when AI storytelling is enabled. The next normal action
still uses the existing narration pipeline. Pending unsaved edits must be saved or
discarded first. Combat, capacity limits and reserved-ID collisions reject expansion.

Already expanded worlds cannot reroll or add another free frontier. Imported and
duplicated campaigns keep the same generated sites, roads, discoveries and depletion
but remain independent slots. A disconnected custom map gets the new branches only
in the player's current connected component; existing islands are not silently joined.

## Persistence and authority

`WorldGeography` generates a bounded region using an isolated `WorldRandom`, seeded
from existing world identity and RNG state at generation. The seed and complete
locations/routes are saved. Generation never consumes the player's ongoing RNG.
Loading, rendering, travelling and regenerating AI prose never regenerate geography.

`TravelRoute` stores a unique ID, two existing distinct endpoint IDs, duration and
observable description. Edges are undirected; duplicate endpoint pairs, invalid
references, loops and invalid durations fail validation. Travel validates against
these stored edges, not hardcoded adjacency or AI suggestions. Journey descriptions
and exact elapsed time become authoritative facts for the AI, as well as offline
prose. Hidden destination names and private journal/NPC information are excluded.

Each location's `forage` enum controls real inventory grants. The existing search
count, combat checks, time simulation, history bounds and transactional save path
are reused. No new parallel inventory, movement or save system was introduced.

Portable exports now use **v4**; imports support **v1–v4**. The v3→v4 migration retains
characters, encounters, RNG, notes, AI openings, story receipts, discoveries and
custom scenery. Older versions default their missing road/resource fields from
the established rules. Decoding is read-only; the first overwrite backs up the
original raw payload. Room's database schema remains v1.

**Keep an external export from the previous app before upgrading. Older apps cannot
read v4 exports.** Debug signing differences may require uninstalling, which erases
private saves and device-only keys. Local backups retain five revisions, not a
permanent archive.

## Still unfinished

This region contains wilderness exploration and finite gathering, not a new set of
fully simulated settlements. Frontier NPC populations, new quest chains, advanced
loot/encounters, sect contributions/missions/leadership, diplomacy, larger maps and
the God Mode editor remain roadmap work. Existing three NPCs, starter quests,
combat and faction systems are preserved. No AI output may invent executable map
changes, resources or rewards. Physical-phone and live-provider acceptance remains
separate from emulator/mock-server verification; see `TESTING.md`.
