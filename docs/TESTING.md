# Immortal World verification and device acceptance

## Current living-story build — 2026-10-15

[Run 38650787173](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38650787173) completed successfully for code commit
`eb0bd5dde2903e23e855f7e4641932e5886f985e`. [Download the tested APK artifact](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38650787173/artifacts/11934273712).

| Check | Actual result |
|---|---|
| Android compilation / debug APK | Passed, uploaded |
| Core foundation | 34 passed |
| Retained adventure mechanics | 30 passed |
| Grounded scenes / dialogue / context / interpretation | 15 passed |
| Save codec / v1-v2 migrations / story bounds | 27 passed |
| Compatible provider / pacing / failure recovery | 10 passed, mock server |
| Total unit tests | **116 passed; 0 failures/errors/skips** |
| API 26 device tests | **19 passed; 0 failures/errors/skips** |
| API 35 device tests | **19 passed; 0 failures/errors/skips** |
| Android lint | 0 errors; 14 non-error findings |
| Room schema | v1, unchanged |

Each device suite has 17 disk-backed persistence tests and two Compose flows. The
expanded creation flow submits cultivation and dialogue, reads the actual notice,
opens/closes surroundings and recent events, navigates the map, travels, and checks
that drafts survive navigation. A separate **320dp-wide, 130%-font** composition
exercises the actual keyboard/Submit control, a long draft, inventory navigation,
and a conditional offline request that must leave time and the draft unchanged.
V2 upgrade/reopen/backup restore is tested on both Android JSON implementations.

The story suite covers observed NPC presence, exhausted stock, patrols/supplies,
duel continuity, defeated guardians, deterministic cosmetic weather without RNG
mutation, arrivals/revisits, historical snapshots, custom text, private-context
exclusion, authoritative NPC speech, notice gates, seeded spiritual sense and
short-line consolidation. The mock provider suite checks scene/ordinary token
budgets and grounded narrator instructions alongside retained network safety tests.

Intermediate runs `38649025037` (116 units, 17 device tests per API) and
`38649853198` (116 units, 19 per API) passed. Final review additionally closed the
conditional-command offline fallback so an unparsed prohibition cannot silently
spend a generic attempt turn. The final run above includes its UI regression.

Local verification ran 34 foundation + 30 adventure + 15 story checks and
save-codec type checking. Android builds and instrumentation ran on GitHub Actions
because local Android dependency hosts are blocked. No tests were disabled.
Machine-readable evidence: `verification-living-story-2026-10-15.json`.

**Not claimed:** physical-phone visual/SAF/rotation/TalkBack certification, arbitrary
accessibility scaling beyond the tested viewport, or live paid-provider/model
behavior. NPC crowds/weather are bounded presentation rules, not verification of
a new autonomous population simulator. Retained earlier build evidence follows.

## Previously verified active-adventure build — 2026-10-14

[Run 38643323537](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38643323537) completed successfully for code commit
`ae34852f90fd3e13510b077543c4afd49c6543cd`. The APK is
[available as a workflow artifact](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38643323537/artifacts/11931333038).

| Check | Actual result |
|---|---|
| Android build / debug APK | Passed, uploaded |
| Core foundation checks | 34 passed |
| Adventure engine / interpreter / context checks | 30 passed |
| Save codec / version-1 migration / encounter round-trip | 22 passed |
| Compatible-provider and failure/cancellation tests | 8 passed (mock server) |
| Total unit tests | **94 passed; 0 failures/errors/skips** |
| API 26 device tests | **17 passed; 0 failures/errors/skips** |
| API 35 device tests | **17 passed; 0 failures/errors/skips** |
| Android lint | 0 errors; 14 non-error findings |
| Live paid-provider/model invocation | Not tested; no real credentials supplied |
| Updated APK on a physical phone / SAF / accessibility | Still manual acceptance |

Each device suite runs 16 Room/persistence cases plus a Compose end-to-end flow.
The new device cases preserve an old v1 backup while initializing v2 gameplay, and
close/reopen a campaign during combat with NPC memories and deterministic replay.
The UI flow creates a character, types and submits an actual cultivation action,
verifies elapsed world time, saves a journal entry, inspects the character and
returns to an unfinished action draft without losing it.

The core suite covers travel validation, actual stock/currency transfer, NPC memory,
one-time quests, faction requirements, activity estimate equality and costs,
explicit breakthroughs, deterministic combat, energy/technique checks, defense/
dodge/movement/retreat, paused/normal/accelerated schedules, active-duel NPC location
consistency, remembered duel endings, opt-in bounded/non-repeatable offline claims,
idempotent action IDs, rejection atomicity, bounded history, and no state grants
from creative prose. Context tests exclude hidden traits/private journals.

Provider tests use MockWebServer to exercise valid requests, authorization headers,
malformed and oversized responses, unsupported action/state patches, HTTP 401/403/
404/429/500, socket failure, cancellation without retry, unsupported narration
numbers/player-decision phrases, and rejected unsafe endpoint configuration. These
are real adapter tests, **not** claims of verification against a live model.

The initial new build (`38640854904`) passed 90 unit tests and all API 26 tests,
but its API 35 UI test timed out after tapping Submit with the keyboard active.
The fix sets explicit resize behavior, consumes Scaffold insets correctly, compacts
the composer/header while the keyboard is visible, hides the bottom navigation
while typing, and clears focus on submission. The same touch-based submission
assertions now pass on both APIs; tests were not disabled. Run `38642128165` passed
92 unit tests and both device jobs. The final two NPC consistency regressions are
included in the verified 94-test run above.

Machine-readable evidence: `verification-active-adventure-2026-10-14.json`.
Previous Phase 1 evidence is retained below rather than overwritten.

## Verified on GitHub Actions — 2026-10-14

[Run 38632996109](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38632996109)
completed successfully for code commit `89266cb399c0ccb88fcd776771b3ce05d405794d`.
All three jobs (`build`, `persistence-tests (26)`, `persistence-tests (35)`) passed.
Counts below come from the JUnit XML reports published through GitHub checks.

| Check | Status |
|---|---|
| Android compilation and debug APK | Passed; artifact uploaded |
| Core + portable-save unit tests | 52 passed; 0 failed/errored/skipped |
| API 26 Room + Compose instrumentation | 15 passed; 0 failed/errored/skipped |
| API 35 Room + Compose instrumentation | 15 passed; 0 failed/errored/skipped |
| Android lint | 0 errors; 14 non-error findings |
| Generated Room v1 schema | Retrieved, integrity-checked, reviewed, committed |
| Physical-device UI / actual file picker / accessibility / rotation | Not yet verified |

The 52 unit tests comprise 34 parameterized core checks and 18 save codec tests.
Each emulator ran 14 database tests and one end-to-end Compose creation, autosaved
journal, and character-inspection test. Tests reopen a disk database, not just an
in-memory database. This does not substitute for the manual force-stop/picker checks.

[Download the tested debug APK artifact](https://github.com/Stlucifer15t/World-of-Xianxia/actions/runs/38632996109/artifacts/11927596311)
(the ZIP contains `app-debug.apk`; this is not a production release).
Machine-readable provenance is in `verification-2026-10-14.json`.

### Failures found and addressed

1. Run `38629739276`: the extra Android setup action failed before compilation.
   CI now uses the SDK installed on the pinned Ubuntu 24.04 GitHub runner.
2. Runs `38630033310` / `38631531419`: the build, unit tests, and API 26 tests passed.
   API 35's UI test failed at the first creator lookup. The next test step ran
   without ensuring the home button was visible and navigation had completed.
3. The UI test now explicitly scrolls the new-life button into view, waits for the
   creator/journey/status destinations, and waits for async operations to finish.
   No tests, assertions, or API levels were removed. Run `38632996109` passed all jobs.

### Local versus remote execution

The local sandbox compiled the Kotlin core and passed its 34 checks, and typechecked
`SaveCodec` against upstream JSON-java source. It cannot download Gradle/Google's SDK,
or access the separate cloud domains hosting workflow logs/artifacts. These limits
are **not a reason to stop Android verification**: GitHub-hosted runners now do it.

`scripts/ci-run.sh` preserves failing exit codes and emits bounded diagnostics in
4096-character-safe annotation chunks. `scripts/ci-report.py` publishes JUnit counts,
failed-test details, lint counts, and initial generated schemas through the Checks
API. Full reports and APKs remain downloadable from GitHub's workflow UI. Branch
build caches are enabled; pull-request runs use read-only caches.

Do not equate automated emulator success with full physical-device acceptance or a
complete RPG. The remaining manual checks are below.

## Automated suites

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

- Core: deterministic/diverse generation, background modifiers, weighted rarity,
  custom identity and realms, RNG restore/extremes, structural validation/bounds.
- Codec: complete equality after round trip; Unicode, escapes, null equipment,
  hidden traits, history, extreme RNG values, sandbox flag; invalid/missing/unknown
  fields, future versions, coercion, numeric overflow, trailing payload, file/depth limits.
- Room: close/reopen from a disk database, independent slots, duplicate/import
  isolation, failed writes/imports, optimistic concurrency, retention/restore,
  corrupted payload recovery, metadata consistency, cascading/stale deletion.
- Compose: creation, journal, character/draft continuity, dialogue, notice, travel,
  menu/recent events and 320dp/130%-font keyboard/conditional-action checks.

## Additional story-first manual checks

- Read the initial scene, travel/return, advance into night, deplete Mei's herbs and
  revisit the defeated guardian's courtyard. Descriptions must match actual state.
- Open Current surroundings and Recent events while reading an older receipt;
  close the sheet and confirm your place/draft remain. Expand facts/calculations.
- Compare raw NPC speech against optional AI prose. Check no added player dialogue,
  NPC consent, hidden knowledge, forced next action or unearned reward is presented.
- Repeat 320dp-like narrow phone layouts and larger font sizes, with your physical
  keyboard/IME. The automated 130% regression is not a blanket accessibility claim.

## Additional active-adventure manual checks

Before installing, export an old `.xianxia.json` on the phone. If the new debug APK
has a different signing key, only uninstall after confirming that external backup.
Import in 0.3 and verify character/journal/adventure values and independent slot identity.

- Type and submit with the keyboard open in portrait, landscape and large-font mode.
  Inspect every destination and return to the same unfinished action draft.
- Try a quest/trade/travel/ruins encounter; kill and relaunch the app mid-encounter.
  Confirm HP, energy, enemy, RNG-derived continuation, stock and memories persist.
- Compare activity estimates to costs/results, capped progression, explicit
  breakthroughs, background modes, and real-time claim cap/checkpoint behavior.
- Configure your own HTTPS-compatible provider in Settings (do not put keys in
  chat). Test a real request, disable connectivity, cancel a request and retry
  narration. Confirm no action, purchase or combat turn executes twice.
- Inspect provider prose versus engine facts. Report contradictions; prose must
  never be treated as a stat change or an NPC promise not recorded by the engine.
- Repeat actual system-file-picker export/import and backup restore on the phone.
  Keystore storage, accessibility and real-provider integration need device review
  beyond these emulator/mock-server suites.

## Manual Android acceptance checklist

Record device, Android API, APK revision, result, and reproduction details. Test
at least API 26 and 35, portrait/landscape, large font, and a narrow phone.

1. **Fresh install**: Continue disabled; new game, saves, settings and back all work.
2. **Creator**: exercise all preset/select/text fields, custom species, randomize,
   generated/manual root, age boundaries, empty fields, overlong names. Repeated
   inspection must not reroll traits. Confirm preview matches the persisted record.
3. **Persistence**: create two differently named characters; kill/relaunch the
   process (`adb shell am force-stop com.immortalworld`); continue and switch
   between slots. Verify all stats, traits, equipment, journal, and rules.
4. **Autosave**: add a journal entry and rename the character; wait for completion,
   kill/relaunch, and verify both persist. Turn autosave off, edit, verify dirty
   warning and replacement guard, then manual save. Repeat with explicit discard.
5. **Slots**: rename, duplicate, modify a copy, and confirm the original remains
   unchanged. Cancel deletion, then delete only the copy; verify other campaigns.
6. **Backups**: make six+ saves, verify five backups, restore an older one, then
   restore the before-restore snapshot. Test failures with a corrupt internal
   payload using instrumentation rather than modifying production databases.
7. **Export/import**: choose local Documents and a supported cloud provider if
   available. Export; inspect UTF-8 JSON; import; compare all gameplay fields.
   Only campaign ID/title/timestamps should differ. Import twice; original intact.
8. **Picker failure/cancellation**: cancel both pickers, reject access from a
   provider, run out of external space if practical; internal saves must survive.
9. **Invalid files**: truncated JSON, >2 MiB, nesting >32, version 999, negative
   health, duplicate IDs/equipment, unknown fields/realms, integer overflow, invalid
   UTF-8. Expect an understandable error and no existing-slot mutation.
10. **Lifecycle**: rotate during creation/journal/status; navigate away and back;
    verify no unintended save or reroll. Stored data must survive process death;
    unsaved edits with autosave off are explicitly not guaranteed.
11. **Accessibility**: TalkBack labels, large fonts, text wrapping, keyboard visibility,
    scrolling dialogs, touch targets, no cut-off actions at minimum supported sizes.
12. **Scope**: implemented adventure actions must work. No control should imply
    a completed graphical God Mode editor, autonomous NPC plots or unrestricted
    generated mechanics. An imported God Mode flag must show the explicit banner.

Automated build success does not replace these physical-device acceptance checks.
