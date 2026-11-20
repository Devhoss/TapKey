# TapKey — audit, architecture, measurements and test results

Verification device: **Samsung SM-A536E (Galaxy A53 5G), Android 16 / One UI 8.0** — the real phone,
driven over ADB against the **signed release APK**. `minSdk 23`, `targetSdk 35`.

Everything marked **Pass** below was executed on that device against the release build installed from
`app-release.apk`. The earlier debug-build results are not reused anywhere in this document.

---

## 1. Reference audit (Phase 0)

What could actually be established about *Digits – Tap Shortcuts to Apps* (`com.dowble.digits`, Dowble):

| Item | Finding |
|---|---|
| Shortcut codes | `1`, `2`, `11`, `12`, `21`, `22` — confirmed by the store listing text. |
| Reference APK size | ≈ 2.02 MB (AppBrain metadata for v1.0.0). |
| Advertised features | six codes on one small widget, automatic backup/restore, "no unnecessary permissions", "minimal battery usage". |
| Widget styles | ten named styles: Gold, Black, Ivory, Clear, Sapphire, Invisible, Slate, Coral, Ruby, Champagne. |

**What could not be measured.** The reference is a paid Play-only app and was never installed, so its
exact tap window, tutorial flow, resize behaviour and picker layout were **not** observed. The sequence
window is therefore the specified fallback, a single constant `TapActivity.SEQUENCE_TIMEOUT_MS = 350`.

**The nine-versus-ten theme question** is unresolved for the same reason. All ten advertised styles ship,
and none is gated.

## 2. Architecture

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

* `Prefs` — one `SharedPreferences` file: six `shortcut_<seq>` package names, `widget_theme`,
  `appearance`.
* `Shortcuts` — the canonical sequence list and pref keys.
* `ThemeStyle` — a widget theme is three values (background drawable, digit colour, divider colour);
  the same method styles the real widget and the in-app previews.
* `Appearance` — System / Light / Dark for the app screens, implemented with framework theme
  attributes (`paletteBg`, `paletteSurface`, …) defined by `Theme.TapKey.Light` / `.Dark`.
  `values-night/themes.xml` points `Theme.TapKey` at the dark palette so "System" follows the OS.
  No AppCompat, no `AppCompatDelegate`, no new dependency.
* `TapKeyWidgetProvider` — `RemoteViews`, one PendingIntent per half, `refreshAll()` for every placed
  instance.
* `ChooseAppActivity` — background launcher scan, in-memory filter.
* `Ui` — system-bar insets (edge-to-edge is forced by `targetSdk 35`) plus palette attribute resolution.

Widget layout: one horizontal `LinearLayout`, two weighted `TextView`s, 1 dp divider (`TextView`, because
RemoteViews cannot inflate a plain `View`). Weighted halves keep both digits centred and the divider dead
centre at every size.

## 3. Changes made in this validation pass

| Change | Reason |
|---|---|
| Share promotes the app and appends an optional public link | The old string only told the recipient what to tap; it never named the app. `share_link` is empty until a repo/store URL exists and is then appended automatically. Still `text/plain`, `EXTRA_TEXT` + `EXTRA_SUBJECT` only — no stream, no URI, no FileProvider. |
| System / Light / Dark appearance setting | Requested. Three pills above the shortcut list; `recreate()` applies the choice; `values-night` makes System follow the OS. |
| Theme cards: removed the full-cell ripple overlay | The press ripple rendered as a light grey rectangle over the whole card. Removed; the accent ring plus bold accent label carry the selection state, and the whole card remains tappable. |
| Dark palette for the app UI | Surfaces, text, accent, badge, ripple and icons now come from theme attributes, so one swap repaints everything. |
| `.gitignore` hardened | `tapkey-release.jks`, `*.jks`, `*.keystore`, `keystore.properties` were **not** ignored before a repository existed. |

## 4. Release artifact

| Property | Value |
|---|---|
| File | `app/build/outputs/apk/release/app-release.apk` |
| Size | **49,226 bytes (48.1 KB)**; 75,178 bytes uncompressed; 43 entries |
| SHA-256 | `360b928b20648ba8db8f67131d24d45ef4d7075388d912226482e3a90c664608` |
| Signing | v1 (JAR) **and** v2 verified by `apksigner`; v3 not present |
| Certificate | `CN=TapKey, OU=Personal, O=TapKey, C=US`, SHA-256 `b324e17cec603258466c6774f7d7e547446e06dc10ad9ee67065817ba298f702` |
| Build | clean `:app:clean :app:assembleRelease`, R8 `minifyEnabled true`, `shrinkResources true`, AGP 8.7.3 / Gradle 8.11.1 |
| Permissions | **none** — `aapt2 dump permissions` lists only `package: com.tapkey.app` |
| Debuggable | absent from the manifest; `run-as com.tapkey.app` is refused by the platform |
| Dependencies | none — the module's dependency set is empty |

Size breakdown: `classes.dex` 20,108 B, `resources.arsc` 12,100 B, `AndroidManifest.xml` 3,928 B,
~30 resource XML files (themes, layouts, vectors, widget info) totalling ~25 KB, plus the v1 signature
files and the zip container. No native libraries, no assets, no unexpected entries. The only growth over
the previous 44.8 KB build (~4.4 KB) is the appearance-mode palettes, the `values-night` theme, the three
appearance pills and the longer share copy.

Both `META-INF/com/android/build/gradle/app-metadata.properties` and
`META-INF/version-control-info.textproto` are outside the v1 signature; `apksigner` warns about them. They
are Gradle-injected metadata, not code, and are covered by the v2 signature.

## 5. Tests performed on the signed release build

Device: SM-A536E, Android 16, Samsung One UI Home as default launcher. The debug build was uninstalled,
the release APK installed fresh, the widget placed through the launcher's widget picker, and apps assigned
through TapKey's own picker (the release build is not debuggable, so preferences cannot be injected).

| # | Test | Result |
|---|---|---|
| 1 | Debug build removed, signed release installed | Pass |
| 2 | Release signature verified (`apksigner verify`, v1+v2) | Pass |
| 3 | No `debuggable`, no debug hook, `run-as` refused | Pass |
| 4 | Permission audit (zero permissions, no `INTERNET`) | Pass |
| 5 | `1` single tap → Settings | Pass (`com.android.settings/.Settings`, exactly 1 start) |
| 6 | `2` single tap → Calendar | Pass (`com.samsung.android.calendar`) |
| 7 | `11` double tap → Clock | Pass (`com.sec.android.app.clockpackage/.ClockPackage`) |
| 8 | `22` double tap → Messages | Pass (`com.samsung.android.messaging`) |
| 9 | `12` left then right → Gallery | Pass (`com.sec.android.gallery3d`) |
| 10 | `21` right then left → My Files | Pass (`com.sec.android.app.myfiles`) |
| 11 | Single tap does not also fire a double | Pass — 1 Settings start, 0 Clock starts |
| 12 | Double tap does not also fire singles | Pass — 1 Clock start, 0 Settings starts |
| 13 | Rapid triple tap `1,1,1` | Pass — `11` launched Clock, the third tap started a new sequence and launched Settings; no duplicate of either |
| 14 | Sequence window behaviour | Pass — second tap inside the window resolves a pair; after the window the first digit has already resolved and the tap lands in the launched app |
| 15 | Invisible theme, both regions hidden | Pass (screen capture shows no widget chrome) |
| 16 | Invisible theme, left region → correct app | Pass → Settings |
| 17 | Invisible theme, right region → correct app | Pass → Calendar |
| 18 | Invisible theme after resize (3×2), both regions | Pass → Settings / Calendar |
| 19 | Horizontal resize | Pass — 167 dp → 262 dp, 2 → 3 columns, halves re-balanced, divider re-centred |
| 20 | Vertical resize | Pass — 84 dp → 191 dp, 1 → 2 rows |
| 21 | Taps after resizing | Pass → Settings / Calendar at the new geometry |
| 22 | Two widget instances at once | Pass — second instance added on another home page |
| 23 | Theme change reaches every instance | Pass — both instances switched to the new theme |
| 24 | Assignments survive app restart | Pass |
| 25 | Assignments survive reboot | Pass — all six rows still populated after `adb reboot` |
| 26 | Widget theme restored after reboot | Pass — Coral card repainted (one initial-layout frame at boot, see limitations) |
| 27 | Widget survives process death | Pass — `am kill`, process gone, next tap still launched Settings |
| 28 | Existing instances receive configuration changes | Pass — resize and theme changes redrew the live instance |
| 29 | Missing app handled | Pass — assigned app disabled with `pm disable-user`: tapping `21` opened the picker, no crash |
| 30 | Same assignment usable again after the app returns | Pass — re-enabled: tapping `21` launched My Files, assignment unchanged |
| 31 | Android Auto Backup round trip | Pass — `bmgr backupnow com.tapkey.app` → Success; uninstall; reinstall; `bmgr restore 37f5ad630c82000d com.tapkey.app` → all six assignments restored |
| 32 | Samsung One UI Home | Pass — all of the above |
| 33 | Smart Launcher (`ginlemon.flowerfree`), both halves | Pass — widget renders the Coral card, left → Settings, right → Calendar. First attempt showed digits only because Smart Launcher's own widget styling was enabled; after the owner changed that launcher setting the themed card renders normally. |
| 34 | Appearance: System | Pass — follows the phone's dark setting |
| 35 | Appearance: Light | Pass — full light palette, dark status-bar icons |
| 36 | Appearance: Dark | Pass — full dark palette, light status-bar icons, dialogs included |
| 37 | Theme cards: no grey press overlay | Pass — inspected in light and dark |
| 38 | Share sheet | Pass — text-only intent with subject and body, no attachment |
| 39 | Default launcher restored | Pass — Smart Launcher was switched in for test 33 and Samsung One UI Home restored afterwards; the owner then set Smart Launcher as their default themselves |

### Failed tests

None. No test produced a wrong launch, a duplicate launch, a crash, or an ANR.

### Not performed

* **Cloud Auto Backup to Google's servers.** The round trip above used the device's local backup
  transport (`bmgr`), which exercises the same `allowBackup` + `backup_rules.xml` +
  `data_extraction_rules.xml` path, but not Google's cloud storage or a second device. Device-to-device
  transfer is not verified either.
* **Other Android versions / other devices.** Only Android 16 on a Galaxy A53 was available.
* **Exact 350 ms boundary measurement.** ADB's `input tap` has 100–200 ms of process-start jitter, larger
  than the window itself, so the boundary was confirmed qualitatively (taps inside the window pair up,
  taps after it do not) rather than timed to the millisecond.
* **Release build on a second launcher's widget picker beyond Smart Launcher.**

## 6. Known limitations

* **Smart Launcher's widget styling can replace the themed card.** With that launcher option enabled the
  widget showed only digits and divider colours; with it changed the Coral card rendered normally. This is
  a launcher setting, not a TapKey behaviour, and it is worth knowing when a user reports a missing widget
  background on a third-party launcher.
* **A single frame of the default theme can appear at boot.** The launcher may inflate `initialLayout`
  (which is the Black theme) before the boot-time `onUpdate` repaint lands. Themed repaint follows without
  user action.
* **The 350 ms window is a chosen value**, not a measurement of the reference app. It is one constant in
  `TapActivity`.
* **The tap surface is a transparent activity.** This is deliberate: it keeps the follow-up launch inside
  the foreground-activity rules on Android 10+. It means the tap is handled by an activity that becomes
  the foreground app for up to 350 ms.
* **Settings resumes its own last screen.** After the user left Settings on "Default home app", tapping
  `1` reopened Settings on that page. TapKey launched `com.android.settings/.Settings` once; the visible
  page is Android's task restoration inside Settings, not a TapKey behaviour.
* **Package visibility** relies on the `<queries>` declaration for `MAIN`/`LAUNCHER`; `QUERY_ALL_PACKAGES`
  is not used, and there are no permissions.

## 7. Security and repository hygiene

* No signing material in Git: `tapkey-release.jks`, `*.jks`, `*.keystore` and `keystore.properties` are
  ignored (verified with `git check-ignore`); the repository was initialised before the first commit.
* No debug hooks, no debug-only activities, no exported test entry points. Exported components:
  `MainActivity` (launcher entry) and `TapKeyWidgetProvider` (the system must be able to send
  `APPWIDGET_UPDATE`). `TapActivity` and `ChooseAppActivity` are `exported="false"`.
* PendingIntents use `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, one request code per digit and a distinct
  `tapkey://tap/<digit>` data URI, so the two halves can never collide.
* No network code, no analytics, no storage of user content beyond six package names and two integers.

**Key backup requirement.** `tapkey-release.jks` is the only key that can update an installed TapKey.
Losing it means every future build is a different app to Android and users must uninstall/reinstall.
Back it up offline (password manager or encrypted archive), together with the alias and both passwords
from `keystore.properties`, and never commit it.

## 8. Differences from the reference, by design

* Identity: TapKey name, icon, palette and copy are original; no Digits assets, artwork or source.
* All ten themes are free; no purchase, subscription, ad or account layer.
* No premium gating, no favourites list, no hidden features.
