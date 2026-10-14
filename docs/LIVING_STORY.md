# Living story increment — 0.3

This refines the existing native Compose game. There is one engine, campaign
aggregate, history, repository and provider pipeline—not a second game or a web UI.
Build/install instructions remain in the README; verification evidence is in
`TESTING.md`. The previous active-adventure mechanics remain available.

## Reading and directing the journey

- Narrative is **19sp / 30sp line height**; NPC speech **18sp / 28sp**; player
  input/history is 17–18sp. Android font scaling is respected. Prose is not shrunk
  to accommodate buttons. Short sentence-per-paragraph output is consolidated.
- Location, time, HP, Qi and cultivation stage stay above the story. The current
  encounter's HP, turn and position also remain visible. The jade/gold theme is
  unchanged. Journey omits the redundant app bar and bottom bar; other screens
  retain the existing navigation shell.
- The latest resolved action leads the reading area, with two earlier receipts
  initially available. **Read earlier entries** reveals more without deleting
  anything. A new action scrolls to its result; a narration retry does not replay
  or scroll through the campaign. Offline prose appears immediately after the
  durable mechanical commit; successful optional AI prose replaces its presentation.
- Player words, narrative, NPC dialogue and important resolutions use light
  typographic/color distinctions rather than a card around every result.
  **Action details · facts & calculations** exposes the authoritative result,
  including numeric gains, costs, rolls and any provider disagreement.
- **Menu → Current surroundings** reads the present scene without leaving the
  Journey or replacing a historical receipt. **Recent events** shows the last 20
  existing world events in a sheet; the full log remains accessible. Closing a
  sheet preserves the underlying scroll and unsent draft.
- The same menu contains optional action shortcuts, a manual snapshot, Character,
  Cultivation, Inventory, Techniques, Quests, Map, NPCs, Events, Journal, Saves,
  Settings/AI, and Home. **Notes** remains next to the input.
- The padded input normally starts at three lines, with 18sp text. Keyboard-height
  and enlarged-font constraints reduce its line count (not font size) and permit
  internal scrolling so Submit remains reachable. Drafts survive screen navigation;
  an unsent draft is UI state, not a durable campaign event or a promise of recovery
  after process death.

## Observable, state-aware scenes

`SceneNarrator` is a pure read-only view of the existing save. It never advances
RNG, time, inventory, relationships, quests or cultivation. First visits/arrival
use two to four consolidated paragraphs, ordinary actions shorter passages.

The four authored locations now have architectural/sensory descriptions rather
than a fixed sentence naming NPCs who might no longer be there. Actual nearby
NPCs are introduced by their work. Rendering accounts for:

- saved hour/light and village/ford/trail/ruins activity;
- present NPC locations, role, generated personality, recorded relationship,
  low health and whether the character is engaged in a duel;
- remaining merchant inventory (including exhausted herbs/empty stock);
- the existing scout patrol, faction supply changes and sect membership;
- completed guardian encounters and depleted local search opportunities;
- persisted visit counts, with shorter familiar-place openings on return.

Weather is a **new cosmetic rule**, not a pre-existing weather simulator. Clear
weather, breeze, drizzle and mist are derived from the saved world ID, day and
six-hour clock band:

```
floorMod(world.id.hashCode().toLong() + day * 31 + (hour / 6) * 17, 4)
```

It therefore survives reload/export/import without spending mechanical RNG.
It has no combat, harvest or cultivation modifier. Unnamed porters, travellers,
workers and disciples are contextual background activity, not spawned persistent
quest-givers or extra invented combatants. The same saved scene renders identically
until relevant state changes; it does not reroll on every screen opening.

The important NPCs remain the original three persistent generated characters, with
actual stock, cultivation, goals, relationships and memories. Their simulation is
still bounded (patrols, supplies, restocking, duel continuity), not autonomous
long-term plotting, a new economy, or a huge procedural population.

## Dialogue, interpretation and agency

Examples supported offline:

- `Ask Mei about cultivation resources` — established herb knowledge, actual NPC
  reply, an interaction memory, and the existing one-hour conversation cost.
- `Inspect the recruitment notice` — reads the village's actual stage/reputation
  gate without enrolling or charging time. A notice is not invented elsewhere.
- `Examine Elder Wen with spiritual sense` — a seeded examination, **3 energy and
  one hour**, not automatic access to hidden abilities/thoughts. See below.
- `Travel to Jade River Crossing` — validates discovery/adjacency, then spends the
  existing two in-game hours and describes the recorded road and arrival.
- `Negotiate medicinal herbs` — the existing validated bargaining check, not
  automatic agreement or an invented purchase.
- `Sit beneath a tree and cultivate for 2 hours` — the existing optional activity
  with normal resource/progress limits; no automatic breakthrough.

The local parser is a bounded convenience interpreter, not unrestricted NLP.
Unknown offline approaches still use the existing bounded creative check. Explicit
conditional/negated wording is not silently promoted to an unconditional command
or offline attempt. It asks for a clear immediate action without spending time;
with AI enabled, any proposed interpretation requires review. Confirmation means
choosing the displayed operation now, not assuming NPC consent or that an unmodeled
condition became true. Dialogue questions are exempt from this command guard.

NPC replies use stored knowledge, distinct role-based delivery and an unwilling
response when the recorded relationship is strongly hostile. Unknown topics do
not dump the NPC's private goal. Spoken dialogue is persisted separately and shown
verbatim alongside narrative. Merely looking shows appearance/activity, not a free
spiritual scan. The optional NPC **Mechanical record · out of character** preserves
access to system stats without presenting them as character knowledge.

Spiritual-sense chance:

```
clamp(45 + (player.spiritualSense - npc.soulStrength) / 2
      + player.comprehension / 10 - realmTierGap * 12, 5, 90)
```

A persisted RNG roll below the chance reveals only the outward realm/stage. If the
NPC's spiritual sense is at least the player's, the probe is noticed, opinion falls
by one and an NPC memory is stored. Insufficient energy/absent NPC/active combat
rejects the action atomically. Explicit God Mode bypasses the energy charge; it
does not expose hidden traits or force knowledge. Existing ordinary mechanics are
otherwise unchanged.

## Travel, history and AI

Journey prose shares the engine's authored connections and two-hour travel rule.
It adds no unrecorded ambush, shortcuts, kilometre distances or rewards. Return
journeys have direction-aware descriptions; arrival reflects the post-travel
clock and NPC schedule. Valid custom routes without authored terrain are identified
without inventing geography. Non-adjacent sandbox travel is explicitly labeled.

Each new action receipt stores its action kind, location, offline story, resolved
NPC dialogue and observed-scene snapshot. This is part of the existing history,
not an independent log. Historical AI requests use that snapshot, never current
inventory/NPC targets as a reconstruction of the past. Older receipts without a
snapshot remain intact and are narrated only from their original facts.

AI context excludes private goals, unspoken NPC knowledge, hidden character traits
and the private journal. The narrator receives authoritative resolved facts,
observed surroundings, pacing and a small sample of recent openings to avoid.
The prompt asks for 2–4 substantial scene paragraphs, concise ordinary actions,
no poetry, repetitive introductions, omniscience, automatic reverence, additional
NPC speech, player thoughts/dialogue or invented mechanics. Resolved speech stays
outside the AI-generated prose.

Narration budgets are 1,200 tokens for scene/major events and 650 for brief/dialogue
results, with a 6,000-character validation cap. Action interpretation/connection
checks retain a 500-token budget; existing response, timeout, redirect and retry
safety limits remain. Numeric/agency guards cannot prove semantic truth: engine
facts remain authoritative and visible. Live paid-provider output was not certified
by the mock-server tests. Cancellation/failure leaves the saved offline story intact.

## Saves and honest limits

Portable saves are now **v3**, with v1/v2 migration and unchanged Room schema v1.
No character rerolls, NPC resets, changed God Mode flag or overwritten imported
slots are part of this update. Exact original stock descriptions are upgraded;
custom descriptions and all legacy facts/narration are preserved. See
`SAVE_FORMAT.md` for bounds and migration details.

Collapsing the UI never prunes history. Existing rolling storage limits are still
100 receipts, 200 events, 20 memories per NPC and five local backup revisions—not
an unlimited campaign archive. Completed quests, membership, defeated guardians,
character progress and inventory persist independently of what is expanded.
Export snapshots to retain older narrative beyond those established limits.

Full God Mode editing, arbitrary generated mechanics, large procedural worlds,
advanced sect management and autonomous NPC plots remain future work. Physical
phone/keyboard combinations, very large accessibility fonts, file providers and
production signing still need device acceptance beyond emulator regression tests.
