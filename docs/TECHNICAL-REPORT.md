# TapKey — audit, architecture, measurements and test results

Target device used for verification: **Samsung SM-A536E (Galaxy A53 5G), Android 16 / One UI 8.0**,
minimum SDK 23, target SDK 35.

---

## 1. Reference audit (Phase 0)

What could actually be established about *Digits – Tap Shortcuts to Apps* (`com.dowble.digits`, Dowble):

| Item | Finding |
|---|---|
| Shortcut codes | `1`, `2`, `11`, `12`, `21`, `22` — confirmed by the store listing text ("Click 1, 2, 11, 22, 12, 21, to open the application"). |
| Reference APK size | ≈ 2.02 MB (AppBrain metadata for v1.0.0). |
| Advertised features | six codes on one small widget, automatic backup/restore, "no unnecessary permissions", "minimal battery usage". |
| Widget styles | ten named styles: Gold, Black, Ivory, Clear, Sapphire, Invisible, Slate, Coral, Ruby, Champagne. |

**What could not be measured.** The reference is a paid Play-only app; it was not installed on this
device, so its exact single/double-tap window, its tutorial flow, its resize behaviour and its
inside-the-picker layout were **not** observed. Per the specification's fallback, the sequence window is
a single centralised constant set to **350 ms** (`TapActivity.SEQUENCE_TIMEOUT_MS`) — adjustable in one
place and testable on-device (the boundary is easy to feel: tap slower than ~350 ms and you get two
single shortcuts).

**The nine-versus-ten theme discrepancy** is unresolved for the same reason: the screenshot set is
ambiguous (most likely an older capture or a grid that pushed one cell off-screen). Since every theme is
free in our build, the safe reading is to offer all **ten** advertised styles — which is what TapKey
does. Nothing is gated.

## 2. Plan review — risks found before implementation, and resolutions

1. **Launching an app from a delayed widget tap.** A `BroadcastReceiver` that sleeps and then calls
   `startActivity` is blocked by Android 10+ background-activity-launch restrictions. Resolved by making
   the tap surface a **transparent `singleTop` activity in its own task**: TapKey is briefly the
   foreground app while the sequence window runs, so the follow-up launch is a normal foreground launch.
   Verified on Android 16, the strictest current case.
2. **Process/activity recreation mid-sequence.** Sequence state is static in the tap-surface process; it
   survives activity recreation, and `onDestroy` resolves a still-held digit via the application context
   as a safety net instead of swallowing the tap. After every resolution the state is cleared, so the
   next tap always starts a new sequence.
3. **Double launch from one gesture.** A sequence is resolved exactly once (`resolved` guard) and the
   pending runnable is cancelled before resolving a pair. Verified: double taps produced two tap-surface
   deliveries and **one** app launch.
4. **RemoteViews is a restricted inflation environment.** Found during on-device testing: a plain
   `<View>` divider fails with `Class not allowed to be inflated android.view.View`, and the launcher
   refuses the widget ("Couldn't add widget"). The divider is a `TextView`; `setBackgroundResource`,
   `setTextColor` and `setBackgroundColor` are applied through `RemoteViews` and work.
5. **Package visibility on Android 11+.** A `<queries>` block for `MAIN`/`LAUNCHER` exposes exactly the
   apps the picker needs. `QUERY_ALL_PACKAGES` is unnecessary and deliberately not used — the release
   APK declares **zero permissions**.
6. **APK size.** No AndroidX, no Compose, no Material, no networking, no database, no analytics. Release
   build with R8 + resource shrinking, English resources only, vector drawables only.
7. **Battery.** No services, no alarms, no periodic work, `updatePeriodMillis="0"`. The widget is redrawn
   only on install/update, resize, theme change and assignment change; taps are plain PendingIntents.
8. **Restored assignments pointing at missing apps.** Package names are the stored keys; a missing app is
   kept and rendered as "App unavailable", and tapping the sequence opens the picker instead of crashing.

## 3. Architecture

```
widget tap ── PendingIntent(getActivity) ──> TapActivity (transparent, singleTop, own task)
                                              │
                          first digit ────────┤ 350 ms window
                                              │
                       second digit ── resolve "12" / "21" / "11" / "22" immediately
                       timeout ─────── resolve "1" / "2"
                                              │
                              SequenceLauncher.launch()
                                ├─ assigned + installed ─> startActivity(launch intent)
                                └─ empty / uninstalled ──> MainActivity + ChooseAppActivity(sequence)
```

* `Prefs` — one `SharedPreferences` file: six `shortcut_<seq>` package names, `widget_theme` index.
* `Shortcuts` — the canonical sequence list and pref keys (single source of ordering).
* `ThemeStyle` — a theme is three values (rounded background drawable, digit colour, divider colour).
  The **same** method styles the real widget (`RemoteViews`) and the in-app preview (`View`), so the
  preview cannot drift from the home screen.
* `TapKeyWidgetProvider` — inflates `widget_tapkey.xml`, applies the theme, attaches one PendingIntent per
  half, and `refreshAll()` redraws every placed instance after assignment/theme changes.
* `ChooseAppActivity` — scans `MAIN`/`LAUNCHER` activities on a worker thread, de-duplicates by package,
  sorts case-insensitively by label, filters in memory by label or package name while typing.
* `Ui` — applies system-bar insets as padding, required because `targetSdk 35` forces edge-to-edge.

Widget layout: one horizontal `LinearLayout` with two weighted `TextView`s (weight 1 each) and a 1 dp
divider. Because the halves are weighted, any resize keeps both digits centred in equal halves and the
divider dead centre; no size-specific layout branches are needed.

## 4. Release APK measurement

`app-release.apk` — **44,804 bytes (43.8 KB)**, 69,300 bytes uncompressed.

| Component | Uncompressed | Compressed |
|---|---|---|
| `classes.dex` (all Java, R8-shrunk) | 18,956 | 9,164 |
| `resources.arsc` | 9,752 | 9,752 (stored) |
| `AndroidManifest.xml` | 3,928 | 1,261 |
| `res/*` (10 theme backgrounds, layouts, strings, vectors, widget info, backup rules) | ≈ 18,000 total | ≈ 9,000 total |
| v1 signature files (`META-INF/*.SF`, `MANIFEST.MF`) | 6,537 | 2,711 |
| v2/v3 signing block + zip metadata | — | remainder |

Debug APK for comparison: 72,189 bytes. No native libraries, no unexpected dependencies (the module has
an empty dependency set).

**Permissions: none.** `aapt2 dump permissions` lists no `<uses-permission>` at all — in particular no
`INTERNET`. The auto-generated APK metadata JAR entry produces an `apksigner` "not protected by
signature" warning for `META-INF/com/android/build/gradle/app-metadata.properties`; it is harmless and
does not affect installation.

## 5. Device test results

Verified by driving the real widget on the Samsung home screen (not a simulation), reading the foreground
activity from `dumpsys activity activities` and the widget view tree from `uiautomator`.

| Check | Result |
|---|---|
| Widget appears in the launcher's widget picker and renders digits | Pass (preview shows `1 │ 2`) |
| Widget placed on home screen | Pass |
| Tap `1` | Pass → Settings |
| Tap `2` | Pass → Calendar |
| Double tap `1` | Pass → Clock |
| Double tap `2` | Pass → Messages |
| Tap `1` then `2` | Pass → Gallery |
| Tap `2` then `1` | Pass → My Files |
| Single tap does not also fire the double-tap shortcut | Pass (one launch per gesture, two tap deliveries, one app) |
| Empty shortcut → assignment screen for that sequence | Pass (`ChooseAppActivity`, header "Shortcut 21 · Currently: not assigned") |
| Assignment to an uninstalled package | Pass (opens picker, no crash, no exception in logcat) |
| Picker lists installed apps with icon, name, package | Pass |
| Search filtering (`clock`) | Pass (single matching row) |
| Selection saves immediately and returns to main screen | Pass (pref written, row updated) |
| Same app assignable to two sequences | Pass |
| Long-press row clears assignment | Pass (key removed, row shows "Choose an app") |
| Theme selection updates the real widget immediately | Pass (Invisible applied live) |
| Invisible theme keeps tap areas | Pass (nothing visible; right half still launched Calendar; the left-half check was blocked by an incoming WhatsApp call banner over that spot) |
| Invisible theme persists across app restart | Pass (`widget_theme=5` read back on relaunch) |
| Widget resizing | Pass horizontally: 352 px → 551 px, halves re-balanced, divider re-centred |
| No `INTERNET` / no permissions | Pass (aapt2 report empty) |
| Release APK signed | Pass (CN=TapKey, SHA-256 `b324e17c…a298f702`) |

## 6. Known gaps and honest limitations

* The **reference app was never run**, so the 350 ms window is the specified fallback rather than a
  measured copy of Digits' timing. It is one constant and easy to tune after a side-by-side test.
* **Auto Backup** is configured (`allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml`) but the
  full cloud-backup round trip was **not** exercised — that needs a Google backup cycle and a second
  device. Device-to-device transfer is configured the same way.
* **Reboot persistence** was not tested; assignments live in SharedPreferences and the launcher re-requests
  the widget view on boot (`onUpdate`), which is the standard path.
* **Vertical resize, two simultaneous widget instances and theming on a second launcher** were not
  verified. Testing happened on Samsung One UI Home. Smart Launcher's widget preview of TapKey rendered
  before the `RemoteViews` fix and was not re-checked afterwards.
* The **release APK was not installed on the device** (the debug build is installed). Signing, permissions
  and contents were verified statically; the sequence engine and widget were exercised from the debug
  build, whose code is identical apart from `debuggable`.
* The last verification pass was interrupted by the owner's own taps on the device, so the final
  re-check after removing the temporary debug export hook is inconclusive. Nothing in that change
  affects the PendingIntent path (`exported` does not gate PendingIntent delivery), but it is listed here
  rather than claimed as verified.

## 7. Differences from the reference, by design

* Identity: TapKey name, icon (a two-button widget silhouette), palette and copy are original. No Digits
  assets, artwork or source were used.
* All ten themes are free; there is no purchase, subscription, ad or account layer.
* No premium gating, no favourites list, no per-shortcut restrictions, no hidden features.
* The nine-theme screenshot question is resolved in favour of shipping all ten advertised styles.
