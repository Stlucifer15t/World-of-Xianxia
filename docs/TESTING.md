# Phase 1 verification and device acceptance

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
- Compose: actual character creation, autosaved journal entry, character inspection.

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
12. **Scope**: no button implies playable cultivation/combat/AI/God Mode editing in
    Phase 1. An imported God Mode flag must show the explicit banner.

Do not declare Phase 1 complete until these gates pass and an APK is installed.
