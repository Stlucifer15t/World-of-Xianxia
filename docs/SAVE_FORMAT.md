# Portable save contract — version 2 (version 1 imports supported)

Extension: `.xianxia.json`. MIME: `application/json`. Encoding: UTF-8. The actual
executable contract is `SaveCodec` plus `SaveValidator`; the specification's short
illustrative JSON is **not** a complete, importable save.

## Root object

All fields are required. Unknown keys are rejected to prevent silent data loss.
All IDs are opaque, nonblank strings, at most 100 UTF-16 code units (realm IDs: 80).

| Field | Type | Meaning |
|---|---|---|
| `format` | string | Exactly `xianxia-save` |
| `version` | integer | Exports use `2`; imports accept `1` and `2` |
| `id` | string | Source campaign ID; replaced on import |
| `name` | string | Campaign title, 1–80 code units |
| `createdAt`, `updatedAt` | integer | Nonnegative Unix epoch milliseconds, updated ≥ created |
| `randomState` | signed 64-bit integer | Engine RNG state; preserve all integer digits |
| `player` | object | Complete current character record |
| `world` | object | Persistent world metadata and journal |
| `rules` | object | Per-campaign gameplay settings |
| `realms` | array | Configured ordered hierarchy, 1–64 unique entries |
| `adventure` | object or null | Required in v2; absent in v1; active world/encounter state below |

Do not round `randomState` through JavaScript's floating-point number type. Use a
JSON editor that preserves signed 64-bit integers. Save files are for personal
single-player worlds and are not encrypted or tamper-proof.

### `player`

- Identity strings: `id`, `name` (1–60), `gender`, `appearance`, `race`,
  `birthplace`, `background`, `personality` (other strings up to 500).
- `age`: integer 0–1,000,000,000. Normal new-character UI restricts 16–100.
- Foundation strings: `spiritualRoot`, `constitution`, `bloodline`, `potential`.
- `talents`, `hiddenTraits`: arrays of at most 100 nonblank strings, each ≤500.
- `attributes`: eleven integer keys, each 0–1,000,000:
  `strength`, `constitution`, `agility`, `vitality`, `spiritualPower`,
  `spiritualSense`, `comprehension`, `talent`, `luck`, `soulStrength`,
  `techniqueMastery`.
- `cultivation`: `realmId`, `substage`, `progress` (finite 0–100),
  `maximumLifespan` (1–1,000,000,000), `method`, `breakthroughs`, `tribulations`.
  The last two are bounded string arrays. Realm ID and substage must exist.
- `health`, `maxHealth`, `energy`, `maxEnergy`: integers. Maxima are
  1–1,000,000,000; current values are 0–maximum.
- `spiritStones`: integer 0–1,000,000,000.
- `techniques`: up to 200 unique records, described below.
- `inventory`: up to 500 unique records, described below.

Technique record: `id`, `name` (≤100), `description` (≤1000), `rank` (≤80),
`energyCost` and `power` (integers 0–1,000,000), `mastery` (0–100),
`requiredRealmId` (valid reference), `effect` (`physical_damage`,
`lightning_damage`, `heal`, or `defend`). These are mechanical definitions executed by the turn-based engine.

Item record: `id`, `name` (≤100), `description` (≤1000), `kind`, `rank` (≤80),
`quantity` (1–maxStack), `maxStack` (1–9999), `power` (0–1,000,000),
`equippedSlot` (`null`, `weapon`, `armor`, or `artifact`). Equipped items must have
quantity 1; each equipment slot may be occupied only once. Item IDs are unique
within a campaign, not across duplicated campaigns.

### `world`

`id`, `name` (≤100), `day` (integer 1–1,000,000,000), `currentLocation`,
`startingCircumstances` (≤500), `journal`. Journal entries contain `id`, `day`
(not later than world day), and `text` (1–2000). Up to 200 entries with unique IDs.
Adding a new entry retains only the most recent 200; older versions may still
exist in local backups. Export snapshots to retain long-running archives.

The original world metadata stays unchanged in shape. Version 2 stores navigable
locations, NPCs, factions, quests, and encounters in the separate adventure aggregate.

### `rules`

`difficulty`: `Gentle`, `Standard`, or `Harsh`.
`cultivationSpeed`: finite 0.1–100. `breakthroughDifficulty`: finite 0.1–10.
Booleans: `lifespanEnabled`, `skipTribulations`, `passiveCultivation`, `godMode`.
All are required. Autosave is a **device** preference, not a game rule. Difficulty,
cultivation speed, breakthrough difficulty, skipped tribulations and God Mode are
used by the engine. Passive cultivation only advances through explicitly armed
claims; lifespan aging exists but natural death/succession does not yet.

### `realms`

Each entry: `id`, `name` (≤80), `stages` (integer 1–99), `baseLifespan` (integer
1–1,000,000,000). The default hierarchy spans Qi Refining through Immortal Emperor.
Edited hierarchies can be imported if all references and stage bounds validate.

## Version 2 adventure aggregate

`adventure` is nullable for uninitialized campaigns. Activation initializes missing
state through a backed-up repository update. All object fields are required;
unknown fields fail closed. Limits are enforced in `AdventureValidator`.

- `locationId` must refer to a location whose name matches `world.currentLocation`.
- `hour` (0–23), `simulationHour` (0–100 billion), `simulationMode`
  (`PAUSED`, `NORMAL`, `ACCELERATED`), `sequence` (0–1 billion), `injury` (0–80).
- `locations` (≤128): id, name, description, danger (0–10), discovered boolean.
- `npcs` (≤100): complete `character` using the player record schema; locationId,
  role, goal, knowledge (≤20 strings), relationship (-100–100), memories
  (≤20 strings, 1,000 characters each), negotiated boolean. IDs are distinct.
- `factions` (≤100): id, name, resources (0–1 million), reputation (-100–100), member.
- `quests` (≤100): id, name, giverId, objective, status
  (`AVAILABLE`, `ACTIVE`, `READY`, `COMPLETE`), reward (0–1 million).
- `events` (≤200): id, day, hour, text. Dates must not exceed current world day.
- `history` (≤100): id, input, day, hour, facts (1–30), calculations (≤30),
  narration (≤3,000 characters), accepted boolean. Input/fact/calculation strings
  are bounded to 2,000 characters. Receipt IDs prevent mechanical replay.
- `combat` (nullable): complete enemy character, nullable NPC id, turn (1–1 million),
  position (`close`, `cover`, `high_ground`). Active enemies must have positive HP.
- `idlePlan` (nullable): supported activity, method, useHerb boolean, checkpointMillis.
  A plan requires `rules.passiveCultivation`; timestamps are bounded nonnegative longs.
- `searchCounts`: location-ID map of integer counts (0–3); `defeated`: ≤100 unique IDs.

Provider configuration/API keys are never part of this object or any campaign save.
See `ACTIVE_ADVENTURE.md` for actual formulas, behavior and limitations.

## Protection, editing, and recovery

- Maximum input/output size: **2 MiB**; input is read through a bounded stream.
- UTF-8 errors, oversized/deeply nested input (>32), missing fields, unsupported
  keys, invalid primitives, duplicate entity IDs, out-of-range values, and bad
  references reject the entire import before any database write.
- JSON numerical strings and boolean strings are not coerced. Integer fields
  cannot be fractional or exceed their Kotlin integer bounds.
- Imports use new local IDs and new slot timestamps, and append `(imported)` to
  the title. Character/world IDs, RNG state, and gameplay data are retained.
- Importing is never an overwrite operation. Cancellation in the picker changes
  nothing. Export uses the stored revision, not unsaved working changes.
- Before every local overwrite, the current snapshot is backed up in the same
  database transaction. Five revisions per slot are retained.
- A corrupt current payload can be restored from a valid backup. Invalid backups
  are refused. Missing/corrupted entire database files cannot be recovered without
  an external export; local backups are not a substitute for off-device backups.
- SAF writes depend on the selected document provider. A provider failure may
  leave a partial external document; it cannot corrupt the internal campaign.
- Deleted campaigns lose local backups too. Deletion asks for confirmation.

Export → edit supported values → import creates an independent experimental
campaign. If enabling God Mode in a file, set the explicit boolean so the app
shows its banner. Import validation protects structure, not competitive fairness.
The dedicated graphical God Mode editor remains future work, not part of this increment.

## Migration policy

The implemented v1 → v2 migration preserves every v1 game field and replaces only
the format version, initially leaving `adventure` null. Activating that campaign
seeds its adventure and uses the existing transactional overwrite/backup path.
The old v1 snapshot remains in the backup until normal retention prunes it.

Database schema v1 does not change: adventure data is part of the JSON aggregate.
Future schema/format changes still require explicit migrations and fixtures; never
use destructive Room fallback or silently discard unsupported fields. The original
v1 example/fixture shape is generated from real codec output in automated tests.
