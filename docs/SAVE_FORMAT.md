# Portable save contract — version 1

Extension: `.xianxia.json`. MIME: `application/json`. Encoding: UTF-8. The actual
executable contract is `SaveCodec` plus `SaveValidator`; the specification's short
illustrative JSON is **not** a complete, importable version-1 save.

## Root object

All fields are required. Unknown keys are rejected to prevent silent data loss.
All IDs are opaque, nonblank strings, at most 100 UTF-16 code units (realm IDs: 80).

| Field | Type | Meaning |
|---|---|---|
| `format` | string | Exactly `xianxia-save` |
| `version` | integer | Exactly `1` |
| `id` | string | Source campaign ID; replaced on import |
| `name` | string | Campaign title, 1–80 code units |
| `createdAt`, `updatedAt` | integer | Nonnegative Unix epoch milliseconds, updated ≥ created |
| `randomState` | signed 64-bit integer | Engine RNG state; preserve all integer digits |
| `player` | object | Complete current character record |
| `world` | object | Persistent world metadata and journal |
| `rules` | object | Per-campaign gameplay settings |
| `realms` | array | Configured ordered hierarchy, 1–64 unique entries |

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
`lightning_damage`, `heal`, or `defend`). These are stored mechanical definitions;
Phase 1 does not execute them.

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

This phase stores a starting location, **not** a generated navigable world.
NPC/faction/encounter tables are not part of v1 because those systems do not exist
in this build. Future phases must version and migrate their additional state.

### `rules`

`difficulty`: `Gentle`, `Standard`, or `Harsh`.
`cultivationSpeed`: finite 0.1–100. `breakthroughDifficulty`: finite 0.1–10.
Booleans: `lifespanEnabled`, `skipTribulations`, `passiveCultivation`, `godMode`.
All are required. Autosave is a **device** preference, not a game rule. Difficulty
and progression rules are preserved for future engines, not currently simulated.

### `realms`

Each entry: `id`, `name` (≤80), `stages` (integer 1–99), `baseLifespan` (integer
1–1,000,000,000). The default hierarchy spans Qi Refining through Immortal Emperor.
Edited hierarchies can be imported if all references and stage bounds validate.

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
The dedicated graphical God Mode editor is Phase 5, not part of this increment.

## Migration policy

v1 is the first implemented version; there is no real older format to migrate.
Unknown versions fail explicitly. For each future format: keep fixture exports,
add a pure version-to-version transformation, validate the transformed state,
write to a new slot or transactional revision, and retain the old snapshot.
Never guess a schema, silently drop fields, or reset a campaign on migration failure.
