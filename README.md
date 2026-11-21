# TapKey

**Six apps. One widget. Two digits.**

TapKey puts a single 2×1 widget on your home screen with the digits `1 │ 2`. Tap them in one of six
sequences — `1`, `2`, `11`, `22`, `12`, `21` — and the matching app opens.

Native Android. Java and framework views only: **no dependencies, no permissions, no background
services, no network access, no analytics.** The signed release APK is **51,414 bytes (50.2 KB)**.

---

## Contents

- [For users](#for-users)
  - [Requirements](#requirements)
  - [Install](#install)
  - [Add the widget](#add-the-widget)
  - [Assign your apps](#assign-your-apps)
  - [Themes](#themes)
  - [Appearance](#appearance)
  - [How taps are resolved](#how-taps-are-resolved)
  - [Backup and restore](#backup-and-restore)
- [For developers](#for-developers)
  - [Build](#build)
  - [Project layout](#project-layout)
  - [Architecture](#architecture)
  - [Testing](#testing)
  - [Signing and releases](#signing-and-releases)
  - [Design constraints](#design-constraints)

---

# For users

## Requirements

Android 6.0 (API 23) or newer. Built and verified against Android 16 / One UI 8 on a Galaxy A53 5G,
and tested with both Samsung One UI Home and Smart Launcher.

## Install

Download `TapKey-1.0.apk` from the [latest release](https://github.com/Devhoss/tapkey/releases/latest)
and open it on the phone, or sideload it:

```bash
adb install -r TapKey-1.0.apk
```

Android may ask you to allow installing from this source. The APK is signed with TapKey's release key,
so later builds update in place — no uninstall needed.

## Add the widget

1. Long-press an empty area of the home screen.
2. Open **Widgets** and find **TapKey** (Samsung: *Widgets* in the home-screen editor; Smart Launcher:
   search "TapKey" in its widget list).
3. Tap or drag the 2×1 widget onto the home screen. It shows `1 │ 2`.

The widget is resizable. Both halves stay centred and the divider stays dead centre at any size.

## Assign your apps

Open **TapKey** and tap a shortcut row to choose the app for that sequence. Long-press a row to clear it.

Tapping a sequence that has no app assigned yet opens TapKey straight on that assignment screen, so the
widget is never a dead end.

Assignments are stored as package names, so an app that gets renamed keeps its slot. If an assigned app
is later uninstalled or disabled, the row shows *App unavailable* and the tap opens the picker instead of
failing.

## Themes

Ten widget themes, all free, all included:

| Theme | Look |
|---|---|
| Gold | Warm gold, dark digits |
| Black | Opaque charcoal |
| Ivory | Soft cream, dark digits |
| **Clear** | Transparent glass — digits and divider float on your wallpaper behind a faint sheen and hairline border |
| Sapphire | Deep blue |
| **Invisible** | Nothing at all; the tap targets still work |
| Slate | Opaque grey |
| Coral | Warm terracotta |
| Ruby | Deep red |
| Champagne | Light metallic beige |

The theme applies to every widget instance immediately. Each theme's digits are tuned for its
background, and Clear adds a soft shadow so white digits stay readable on bright wallpapers.

## Appearance

The app's own screens support **System**, **Light** and **Dark**. *System* follows the phone's dark-mode
setting. The widget themes are independent of this setting — a widget can be Gold while the app is dark.

## How taps are resolved

The first digit is held for **350 ms**:

- a second tap inside that window resolves `11`, `22`, `12` or `21` immediately;
- otherwise the held digit resolves on its own as `1` or `2`.

A sequence never produces both a single and a double launch, and the state resets after every resolution.

## Backup and restore

TapKey participates in Android Auto Backup: the six app assignments, the theme and the appearance
setting are included in cloud backup and device-to-device transfer. The app itself uploads nothing.
A full `bmgr` backup → uninstall → reinstall → restore round trip was verified on device; Google's cloud
transport and device-to-device transfer were not.

---

# For developers

## Build

A standard Gradle/Android Studio project: `compileSdk 35`, `minSdk 23`, `targetSdk 35`, Java 17, AGP 8.7.3.
No Gradle wrapper is checked in, so use an installed Gradle 8.11 (Android Studio uses its own):

```bash
gradle :app:assembleRelease   # R8 + resource shrinking, signed when keystore.properties exists
gradle :app:assembleDebug
```

`local.properties` points at the SDK on the build machine; adjust it for another setup.

On this machine a Gradle 8.11 distribution is unpacked into `.tooling/` (git-ignored) for command-line
builds:

```bash
export PATH="$JAVA_HOME/bin:$PATH"
./.tooling/gradle-8.11.1/bin/gradle --no-daemon :app:clean :app:assembleRelease
```

The release artifact is named from `versionName`:

```
app/build/outputs/apk/release/TapKey-1.0.apk
```

## Project layout

```
app/src/main/java/com/tapkey/app/
  MainActivity.java          shortcut rows, theme gallery, appearance, share, how-it-works
  ChooseAppActivity.java     app picker: installed launcher apps + search
  TapActivity.java           transparent tap surface and the sequence engine
  TapKeyWidgetProvider.java  AppWidgetProvider, RemoteViews rendering, click PendingIntents
  SequenceLauncher.java      sequence → launch app, or → assignment screen
  ThemeStyle.java            the 10 widget themes (background, digit, divider) for widget and previews
  Appearance.java            System / Light / Dark → theme resource
  Prefs.java                 six package names + theme + appearance in one SharedPreferences file
  Shortcuts.java             canonical sequence list and preference keys
  Ui.java                    system-bar insets and palette attribute lookup

app/src/main/res/
  layout/widget_tapkey.xml        the widget, also used for previews and the picker
  layout/widget_tapkey_clear.xml  same layout with digit shadows — used only by Clear
  layout/item_theme.xml           one gallery tile
  drawable/widget_bg_*.xml        ten backgrounds, one per theme
  drawable/theme_ring_*.xml       gallery tile outlines (Clear gets a dark backdrop variant)
  values/themes.xml               light palette, values-night/themes.xml → dark palette
  xml/tapkey_widget_info.xml      resizable 2×1 provider, updatePeriodMillis = 0
  xml/backup_rules.xml            Auto Backup (API ≤ 30)
  xml/data_extraction_rules.xml   backup + device transfer (API 31+)

docs/TECHNICAL-REPORT.md          audit, measurements, test matrix, known gaps
```

## Architecture

```
widget tap ── PendingIntent(getActivity) ──> TapActivity (transparent, singleTop, own task)
                                               │
                           first digit ────────┤ 350 ms window
                                               │
                        second digit ── resolve "12" / "21" / "11" / "22" immediately
                        timeout ────── resolve "1" / "2"
                                               │
                               SequenceLauncher.launch()
                                 ├─ assigned + installed ─> startActivity(launch intent)
                                 └─ empty / uninstalled ──> MainActivity + ChooseAppActivity(sequence)
```

Key decisions, each of which exists for a reason:

- **A transparent activity handles the tap.** Widgets cannot wait 350 ms inside a broadcast receiver
  without being killed, and Android 10+ refuses background activity starts. Starting `TapActivity`
  first means the app launch after the window is an ordinary foreground launch. Verified on Android 16.
- **One layout, weighted halves.** Two weighted `TextView`s and a 1 dp divider keep the digits centred
  and the divider in the middle at every resize. RemoteViews cannot inflate a plain `View`, so the
  divider is a `TextView` too.
- **A theme is three values.** Background drawable, digit colour, divider colour — applied by the same
  code to the real widget and to the in-app previews, so the preview cannot drift from the home screen.
- **Clear has its own layout.** Only Clear needs a digit shadow; putting it in the shared layout made
  Invisible render ghost glyphs, because a text shadow is drawn even for fully transparent text and
  RemoteViews cannot set a shadow per widget update.
- **No dependencies.** No AppCompat: appearance modes use framework theme attributes and `values-night`.
  No FileProvider: sharing is text-only.

## Testing

`docs/TECHNICAL-REPORT.md` holds the full audit: 39 device tests against the signed release build, the
APK size breakdown, permission and signature checks, edge cases (missing app, resize, process death,
reboot, two instances), and everything that was *not* verified (Google cloud backup, device-to-device
transfer, other Android versions, the exact 350 ms boundary).

Manual checks worth repeating after a change:

1. All six sequences launch the right apps, and a single tap never also fires a double.
2. Every theme paints correctly, including Clear and Invisible, in light and dark app mode.
3. Resize the widget horizontally and vertically; both halves still hit their own region.
4. Change the theme with two widget instances placed; both must repaint.
5. `am force-stop` the app and relaunch: assignments, theme and appearance must survive.

## Signing and releases

The release build is signed when `keystore.properties` exists in the project root; without it the build
falls back to an unsigned release. Signing material is git-ignored (`*.jks`, `*.keystore`,
`keystore.properties`).

```bash
gradle :app:assembleRelease
gh release create v1.1 app/build/outputs/apk/release/TapKey-1.1.apk \
  --target master --title "TapKey 1.1" --notes "…"
```

**Back up the keystore.** `tapkey-release.jks` is the only key that can update an installed TapKey.
Losing it makes every future build a different app to Android, and users have to uninstall first.

## Design constraints

These are deliberate, and pull requests that break them will be declined:

| Constraint | Why |
|---|---|
| No third-party dependencies | Keeps the APK at ~50 KB and the attack surface at zero |
| No permissions, no `INTERNET` | Nothing to request, nothing to leak; package visibility uses `<queries>` for `MAIN`/`LAUNCHER` |
| No services, alarms or wake locks | The widget updates only on placement, resize, theme change and assignment change |
| Exported components: `MainActivity` and the widget provider only | Everything else is `exported="false"` |
| `FLAG_IMMUTABLE \| FLAG_UPDATE_CURRENT`, unique request code and data URI per half | The two halves can never collide or be hijacked |

Release APK for v1.0: `TapKey-1.0.apk` — 51,414 bytes, zero permissions, minSdk 23, targetSdk 35,
SHA-256 `156ca72736407feffb1d7ddf07ba3ef35a3407ed4ac5a324de7d210a4c95c2bc`.
