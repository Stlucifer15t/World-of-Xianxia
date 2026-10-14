# Phase 1 verification and device acceptance

## Recorded in this workspace

On 2026-10-14, Kotlin 2.1.20 and Java 17 compiled the core; all **34** cases in
`CoreChecks` passed. A toolchain was bootstrapped from the permitted npm/PyPI
registries into ignored `.cache/`; no toolchain binary is committed.

`./gradlew --version` was attempted with Java 17. Gradle's distribution download
failed with `SSLHandshakeException`; the sandbox only permits a limited set of
hosts. Google's Android SDK download host is also inaccessible. Consequently:

| Check | Status |
|---|---|
| Core compilation | Passed |
| Core checks (generation, RNG, rarity, validation) | 34 passed |
| Save codec Kotlin typecheck against upstream JSON-java 20240303 source | Passed (not a runtime or Android build test) |
| Android Gradle compilation / APK | Not run; blocked |
| JSON codec unit tests | Authored, not yet run |
| Room persistence instrumentation | Authored, not yet run |
| Compose creation/journal/status flow | Authored, not yet run |
| Lint / real-device UI / SAF | Not run |
| CI workflow | Configured, not executed from this workspace |

Do not substitute static review or core success for a working Android APK.

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
