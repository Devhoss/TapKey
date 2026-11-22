<p align="center">
  <img src="docs/icon.png" width="120" height="120" alt="TapKey app icon">
</p>

# TapKey

**Six apps. One widget. Two digits.**

TapKey puts a single 2×1 widget on your home screen with the digits `1 │ 2`. Tap them in one of six
sequences — `1`, `2`, `11`, `22`, `12`, `21` — and the matching app opens.

Native Android. Java and framework views only: **no dependencies, no permissions, no background
services, no network access, no analytics.** The signed release APK is **55,961 bytes (54.6 KB)**.

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

Download `TapKey-1.2.1.apk` from the [latest release](https://github.com/Devhoss/tapkey/releases/latest)
and open it on the phone, or sideload it:

```bash
adb install -r TapKey-1.2.1.apk
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
| **Clear** | Nothing but the two digits — no panel, no divider, no border; white digits float on your wallpaper with a soft shadow |
| Sapphire | Deep blue |
| **Invisible** | Nothing at all; the tap targets still work |
| Slate | Opaque grey |
| Coral | Warm terracotta |
| Ruby | Deep red |
| Champagne | Light metallic beige |

The theme applies to every widget instance immediately. Each theme's digits are tuned for its
background, and Clear adds a soft shadow so white digits stay readable on bright wallpapers.

## Settings

The gear icon in the top right of the main screen opens **Settings**, which holds everything that is not
a shortcut assignment:

- **Appearance** — System, Light or Dark for the app's own screens; *System* follows the phone's
dark-mode setting. The widget themes are independent, so a widget can be Gold while the app is dark.
- **Widget theme** — the ten themes above.
- **Tap timeout** — the window between the first and second tap, in milliseconds, adjustable from 100
  to 2000 with 350 ms as the default. It is also how long a lone digit waits before it launches.
- **Share** — the share icon in the Settings header, which sends a short text-only description of TapKey
  with a link to this repository.

## How taps are resolved

The first digit is held for the **tap timeout** — 350 ms by default, adjustable in Settings from 100 to
2000 ms. The timeout is the gap allowed *between the two digits*, not a rhythm you have to keep:

- a second tap inside that window resolves `11`, `22`, `12` or `21` immediately;
- otherwise the held digit resolves on its own as `1` or `2`.

So with 900 ms set, tapping `1` and then `1` anywhere from 0 to 900 ms later gives `11`, and the window
restarts on every first tap. Deliveries are measured from the first tap only — there is no second
deadline after the pair resolves.

A sequence never produces both a single and a double launch, and the state resets after every resolution.

**After closing an app, wait about 0.3 s before tapping the widget.** During the home-screen
transition the launcher swallows the tap — the widget never receives it, so the first tap does
nothing. Waiting a moment after the home screen settles fixes it; pressing the same digit again
right afterwards works fine.

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
app/build/outputs/apk/release/TapKey-1.2.1.apk
```

## Project layout

```
app/src/main/java/com/tapkey/app/
  MainActivity.java          shortcut rows, how-it-works, Settings entry point
  SettingsActivity.java      appearance, theme gallery, tap timeout, share
  ChooseAppActivity.java     app picker: installed launcher apps + search
  TapReceiver.java           tap entry point and the sequence engine (broadcast, no window)
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
  layout/activity_settings.xml    appearance, theme gallery and tap timing
  layout/item_theme.xml           one gallery tile
  drawable/widget_bg_*.xml        ten backgrounds, one per theme
  drawable/theme_ring_*.xml       gallery tile outlines (Clear gets a dark backdrop variant)
  values/themes.xml               light palette, values-night/themes.xml → dark palette
  xml/tapkey_widget_info.xml      resizable 2×1 provider, updatePeriodMillis = 0
  xml/backup_rules.xml            Auto Backup (API ≤ 30)
  xml/data_extraction_rules.xml   backup + device transfer (API 31+)

docs/icon.png, docs/icon.svg      app icon for the README (matches the adaptive launcher icon)
docs/TECHNICAL-REPORT.md          audit, measurements, test matrix, known gaps
```

## Architecture

```
widget tap ── PendingIntent(getBroadcast) ──> TapReceiver (no window, nothing started per tap)
                                               │
                           first digit ────────┤ tap timeout window
                                               │
                        second digit ── resolve "12" / "21" / "11" / "22" immediately
                        timeout ────── resolve "1" / "2"
                                               │
                               SequenceLauncher.launch()
                                 ├─ assigned + installed ─> startActivity(launch intent)
                                 └─ empty / uninstalled ──> MainActivity + ChooseAppActivity(sequence)
```

Key decisions, each of which exists for a reason:

- **A tap is a broadcast, not an activity launch.** Two taps of the same digit carry identical
  intents, so the platform merged the second launch into the activity already on top; while the
  process was starting, or the surface was finishing, that merge dropped the tap altogether and
  `1 + 1` resolved as a single `1`. Broadcasts are never merged, so each half of the widget sends
  its own `PendingIntent.getBroadcast`, keyed by widget id and digit. The widget click is sent by the
  foreground launcher, which is what keeps the resulting app launch inside Android's
  background-activity-start rules.
- **No component is started per tap.** `TapReceiver` holds the first digit and counts down the
  window in a handler; a second tap resolves the pair at once. The receive itself is finished
  before the window ends: broadcasts to one receiver are serialized, so holding the dispatch open
  with `goAsync()` blocked the second tap's broadcast until the single had already launched.
- **One layout, weighted halves.** Two weighted `TextView`s and a 1 dp divider keep the digits centred
  and the divider in the middle at every resize. RemoteViews cannot inflate a plain `View`, so the
  divider is a `TextView` too.
- **A theme is three values.** Background drawable, digit colour, divider colour — applied by the same
  code to the real widget and to the in-app previews, so the preview cannot drift from the home screen.
  Clear's three values are a transparent background, white digits and a transparent divider, so it is
  literally just the digits.
- **Clear has its own layout.** Only Clear needs a digit shadow; putting it in the shared layout made
  Invisible render ghost glyphs, because a text shadow is drawn even for fully transparent text and
  RemoteViews cannot set a shadow per widget update.
- **The tap window is a preference.** `Prefs.getTapTimeoutMs` is read when the first digit is held, so a
  change in Settings applies to the very next tap — no restart, no widget refresh.
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
5. `am force-stop` the app and relaunch: assignments, theme, appearance and tap timeout must survive.
6. Change the tap timeout, then check a single tap and pairs with slow (~1 s) and fast gaps.
7. While a digit waits, tap another home-screen icon: it must react normally, which proves the tap
   surface is not stealing touches.

## Signing and releases

The release build is signed when `keystore.properties` exists in the project root; without it the build
falls back to an unsigned release. Signing material is git-ignored (`*.jks`, `*.keystore`,
`keystore.properties`).

```bash
gradle :app:assembleRelease
gh release create v1.2.1 app/build/outputs/apk/release/TapKey-1.2.1.apk \
  --target main --title "TapKey 1.2.1" --notes "…"
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

Release APK for v1.2.1: `TapKey-1.2.1.apk` — 55,942 bytes, zero permissions, versionCode 4, minSdk 23,
targetSdk 35, SHA-256 `d729ad294ab5dfe08dfdfb829a04d65c7e831e033026830954bbfe1ce1d519a3`.
