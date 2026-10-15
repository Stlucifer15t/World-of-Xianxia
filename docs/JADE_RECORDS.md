# Jade records: native cultivation UI (0.5)

This is an incremental Compose redesign. The Kotlin engine, generated character
rules, Room v1 database, v1/v2→v3 imports and portable v3 exports are unchanged.
No web frontend, duplicated game state, new decorative stats or image-generation
service is needed.

## Settings: progressive disclosure

Settings opens on three large navigation cards:

- **Provider & model** → Setup, Saved, Limits, Knowledge.
- **Saving & backups** → journal autosave and the existing save manager.
- **About this world** → offline/privacy boundaries and honest implemented scope.

Top Back and system Back return from a section to the hub. Provider drafts stay
in memory while switching AI tabs; they are not written into Android saved-state
bundles. Leaving the provider page discards unsaved changes. Saved settings and
profile selection remain persistent. Switching profiles with unsaved edits still
requires confirmation.

Setup shows a provider card, password field with Show/Hide, model ID, Load models,
searchable Browse picker, an AI enable switch and an explicit test-and-save action.
Provider URLs are hidden for presets and editable for other compatible APIs.
Changing origin clears the key/model. The short generation test runs once and
saves only after success. Save without testing remains available. No game command
is submitted; failure leaves the previous saved configuration intact.

Detailed privacy/connection explanations are in a separate help dialog rather
than long paragraphs between every control. See `AI_SETTINGS.md` for exact API,
metadata, encryption, streaming and source behavior.

## Creation: a cultivation record

A static seal introduces the journey. The top foundation card displays the actual
seeded preview's root, potential, constitution, bloodline and starting realm.
Campaign and character names stay directly editable above the records. The six
expandable records retain the rest of the original creation inputs:

1. Identity and Appearance (gender, age, appearance, personality).
2. Spiritual Root and Constitution (root choice or generated; generated constitution/potential).
3. Race and Bloodline (preset/custom race; generated bloodline).
4. Innate Talents (actual aptitude, comprehension, vitality and revealed talents).
5. Background and Starting Scenario (distinct preset cards, birthplace/background,
   location/circumstances, difficulty).
6. Starting Cultivation and Abilities (actual method, lifespan and starting arts).

Opening/closing records does not reroll the seed. Randomize remains an explicit
button. Background cards describe existing stat modifiers: herbalist vitality,
scholar comprehension, heir spiritual power, hunter agility. Constitution,
bloodline and potential remain generated; hidden traits are not exposed. Normal
starts remain Qi Refining Stage 1, not a fake higher-realm selector.

## Gameplay and visual system

`CultivationDesign.kt` contains reusable static vector formation seals, navigation
cards, expandable records and the state-backed realm tablet. Ink backgrounds,
jade surfaces and antique-gold accents replace default purple selection colors.
Ornaments are decorative and excluded from accessibility announcements.

Cultivation and the character sheet show the current realm/stage, method,
breakthrough progress, spiritual root and qi reserves. Existing activities,
previews, costs, optional offline claims and breakthrough choices stay functional.
A full progress circle does not guarantee a breakthrough.

Technique cards use manual/rank labels and mastery bars. Inventory categories
come from actual item kinds, not inferred fantasy bonuses. Spirit stones remain
the real balance. Quests/sects and map/NPC/event records retain their actions and
get the shared visual hierarchy. Story still leads the adventure screen, with
secondary controls in Menu and an accessible persistent composer.

Bundled Noto Sans serves controls and forms; Noto Serif serves prose/titles. This
avoids reliance on an OEM's decorative system font. Story is 19sp/30sp line height,
dialogue 18sp/28sp, with native font scaling. Font binaries came from the Noto
project's `hinted/ttf/NotoSans` and `hinted/ttf/NotoSerif` directories. The SIL OFL
license is included in `app/src/main/assets/licenses/Noto-OFL.txt` and `docs/licenses`.

## Verification and boundaries

See `TESTING.md` for the actual verified code commit, CI run and APK, rather than
assuming all current documentation is itself a completed device acceptance claim.
Tests retain the prior creation/adventure/provider/profile/persistence assertions;
new UI checks exercise expandable records, root selection without a reroll,
created-character equality, settings cards and inner-world navigation at 320dp
with 130% font scaling. CI also publishes real emulator screenshots, not mockups.

Physical-device keyboards, TalkBack and wider font/landscape combinations still
need acceptance. Live Gemini/provider access and model prose/latency are not
certified by fixtures. There is no animated breakthrough effect, independent
root-quality mechanic, higher-realm creator, local GGUF runtime or native
Cloudflare account integration masquerading as functionality.
