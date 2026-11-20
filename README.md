# TapKey

Two digits on one tiny home-screen widget. Tap `1`, `2`, `11`, `22`, `12` or `21` to open six different apps.

Native Android, Java + framework Views only. **No dependencies, no permissions, no services, no network.**
Release APK: **48.1 KB** (49,226 bytes).

---

## Install

| Artifact | Path |
|---|---|
| Release APK (signed, sideload) | `app/build/outputs/apk/release/app-release.apk` |
| Debug APK (testing) | `app/build/outputs/apk/debug/app-debug.apk` |

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Over USB (or copy the APK to the phone and tap it). The release APK is signed with `tapkey-release.jks`
in the project root; the credentials live in `keystore.properties` so builds stay reproducible.
Uninstalling the debug build is required before installing the release build on a device that already
has the debug one (different signing keys).

## Widget setup

1. Long-press an empty spot on the home screen.
2. Open **Widgets**, find **TapKey** (Samsung: the Widgets button in the home editor).
3. Drag the 2×1 widget onto the home screen. It shows `1 │ 2`.
4. Open the TapKey app and tap a shortcut row to assign an app to that sequence.
   Long-press a row to clear it. Pick a theme at the bottom of the same screen.

Tapping the widget on a sequence with no app opens TapKey straight on the assignment screen for that
sequence.

## Build

The project is a standard Gradle/Android Studio project (`compileSdk 35`, `minSdk 23`, `targetSdk 35`).
`local.properties` points at the local SDK; adjust it for another machine.

```bash
./gradlew :app:assembleRelease     # R8 + resource shrinking, signed
./gradlew :app:assembleDebug
```

A Gradle 8.11 distribution is unpacked in `.tooling/` (git-ignored) for command-line builds on this
machine: `.tooling/gradle-8.11.1/bin/gradle :app:assembleRelease`.

## Where things are

```
app/src/main/java/com/tapkey/app/
  MainActivity.java             six shortcut rows, theme gallery, share, how-it-works
  ChooseAppActivity.java        "Choose App" picker: installed launcher apps + search
  TapActivity.java              invisible tap surface: single/double tap sequence engine
  TapKeyWidgetProvider.java     AppWidgetProvider + RemoteViews rendering + click PendingIntents
  SequenceLauncher.java         sequence -> app launch, or -> assignment screen
  Prefs.java                    6 package names + theme in SharedPreferences
  Shortcuts.java                the six sequences and their preference keys
  ThemeStyle.java               the 10 themes, shared by widget and in-app previews
  Ui.java                       system-bar insets for edge-to-edge Android 15+

app/src/main/res/
  layout/widget_tapkey.xml      the widget itself (also used for the picker & theme previews)
  xml/tapkey_widget_info.xml    resizable 2x1 provider info, updatePeriodMillis=0
  xml/backup_rules.xml          Auto Backup (API <= 30)
  xml/data_extraction_rules.xml Auto Backup + device transfer (API 31+)
  drawable/widget_bg_*.xml      10 rounded backgrounds, one per theme
```

Sequence timing is one constant: `TapActivity.SEQUENCE_TIMEOUT_MS` (350 ms).

## Behaviour notes

- **Sequence model.** The first digit is held for 350 ms. A second tap inside that window resolves
  `11`, `22`, `12` or `21` immediately; otherwise the held digit resolves on its own. A sequence never
  produces both a single-tap and a double-tap launch, and the state resets after every resolution.
- **Foreground launch.** Widget taps start `TapActivity`, a transparent `singleTop` activity with its
  own task. The 350 ms wait therefore happens while TapKey is on screen, so the follow-up app launch is
  an ordinary foreground launch and is not blocked by Android's background-activity-start rules
  (verified on Android 16 / One UI 8).
- **Package visibility.** Android 11+ sees installed launcher apps through a `<queries>` declaration for
  `MAIN`/`LAUNCHER`. `QUERY_ALL_PACKAGES` is not used and no permission is declared.
- **Storage.** Package names (not labels) are stored, so renamed apps keep their assignment. A restored
  assignment whose app is gone is kept and shown as "App unavailable" until replaced.
- **Widget updates.** Only on provider update, resize, theme change and assignment change. No periodic
  updates, no services, no alarms, no wake locks.
- **Appearance.** System / Light / Dark for the app screens, stored in the same preferences file.
  System follows the phone's dark-mode setting. The widget keeps its own ten themes, independent of this.
- **Backup.** `allowBackup="true"` with SharedPreferences included in both cloud backup and
  device-to-device transfer. Nothing is uploaded by the app itself. A full `bmgr` backup/restore round
  trip was verified on device; Google cloud backup was not.

See `docs/TECHNICAL-REPORT.md` for the audit, architecture decisions, APK size breakdown, device test
results and known gaps.
