# Privacy Policy

**Effective date:** 2026-11-14
**Applies to:** 2-Pyramid for Android (the Android app and officially built APKs)

2-Pyramid for Android is designed around one principle: **fully local processing, no account, no telemetry,
no network permission**. This document explains exactly what is and is not collected.

---

## One-line summary

When converting resource packs, your files **never leave the device**. The APK **does not declare the
`INTERNET` permission**, so at the OS level the app cannot access the network — no uploads, no telemetry, no
crash reporting, no in-app update checks. Java-target output contains a **no-personal-data** conversion
parameter record (`2pyr-gen.json`, section 2.1) that can be disabled in the conversion options.

---

## 1. What we do not collect

- Your imported resource packs, textures, shaders or conversion options are never uploaded;
- No automatic crash reporting, no analytics, no DAU / feature-usage tracking;
- No account registration; no passwords, e-mail addresses or phone numbers stored;
- No access to contacts, SMS, call logs, location, camera, microphone or other apps' data unrelated to the
  feature;
- No ad SDKs and no third-party analytics/tracking SDKs.

## 2. Permissions and their only purposes

The app declares only the following permissions (see `AndroidManifest.xml`):

| Permission | Purpose | Behaviour |
|---|---|---|
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Keep conversions running in a background queue (foreground service, type `dataSync`) | A progress notification is shown while converting; leaving the screen does not abort the conversion |
| `POST_NOTIFICATIONS` (Android 13+) | Show the foreground-service notification and queue progress | If denied, conversion still works; only the notification is missing |

The app does **not** request `INTERNET`, `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` or similar.

File access goes exclusively through the Android **Storage Access Framework (SAF)**: you pick one input pack
file and one output folder, and the system picker grants the app access to exactly those locations (the
persisted grant covers only what you selected). The app reads **only** the pack you chose and writes **only**
to the output folder you chose; without that grant it cannot touch other files.

## 2.2 On-device storage (all inside app-private directories)

| Content | Actual location | Notes |
|---|---|---|
| Settings (conversion toggles, output mode/folder, naming preset, theme, accent color, last target version, developer mode) | App-private `SharedPreferences` | Local only; removed on uninstall |
| Conversion history | App-private `SharedPreferences` | Local only; clearable from the History screen |
| Runtime logs | App-private `filesDir/logs/2_pyramid_<date>.log` | Date-rolled; release builds log Warn/Error only, Info after enabling developer mode; recent logs viewable in Settings |
| `UImage` reference assets | App-private `filesDir/UImage` | Shipped inside the APK, extracted on first launch (section 4) |
| Temporary files | App-private `cacheDir` (including SAF input copies under `cacheDir/zips`) | Conversion scratch space; removed when the OS clears the cache or the app is uninstalled |
| Conversion output | **The output folder you choose via SAF** | Its location is up to you; the app keeps no additional copy |

- Android system backup: the app declares `android:allowBackup="false"`, so it is **excluded from** cloud
  backup / device transfer; data is never synced to a Google account or another device.
- Uninstalling removes **all** of the app-private data above (settings / history / logs / assets / cache).
  Conversion output you saved through SAF into your own folder is **unaffected** and remains yours to manage.

Logs may contain **pack file names and output paths** to aid debugging. Logs stay in app-private storage and
are never sent automatically. If you copy log text into a public issue, those paths/names become public
too — please review before sharing.

## 2.1 Metadata file in conversion output (2pyr-gen)

After a successful **Java Edition** conversion, the output pack root gains `2pyr-gen.json` (**on by default**,
can be disabled in the conversion options; when disabled the file is not produced). It belongs to the **local
output only**: it is never uploaded and never sent anywhere; it is distributed only if you share the converted
pack.

It records **conversion parameters only**:

- generator name, version and build number (BUILD);
- generation time (UTC, RFC 3339, second precision);
- source and target `pack_format`, target version label (when reliably available);
- the six conversion switches used (layer fix, shader adaptation, delete shaders, item definitions, OptiFine
  CIT conversion, delete CIT).

It contains **no** file paths, usernames, device names, hardware IDs, UUIDs, IP addresses or network
information. It also contains a **neutral note** (telling recipients the file was generated by 2-Pyramid, that
Minecraft does not read it, and that it can be disabled) that is not personal data. Its purpose is
traceability and support. The Minecraft client **does not read** it and it plays no part in loading the pack.

If you do not want to share these conversion parameters, disable "write conversion metadata" in the conversion
options or delete the file afterwards (deleting it does not affect the pack).

## 3. Network access

**None.**

- The APK does **not** declare the `INTERNET` permission and cannot open network connections;
- **No auto-update / update checks**: upgrades are manual downloads from GitHub Releases;
- No remote configuration, no online asset downloads, no third-party API calls.

Consequently there is no "data transfer abroad" and no "third-party processor": apart from you deliberately
visiting GitHub (browser/client) to download the APK or file an issue, the app itself performs no network
transmission.

## 4. Assets shipped with the app (UImage)

The APK bundles 80 `UImage` reference assets (~0.56 MB) used by conversion tasks that generate/overlay
vanilla-style textures (tipped arrow, smithing overlay, snow bucket, and others). They come from the
maintainer's desktop **2-Pyramid** project, ship inside the APK, and are extracted once into app-private
storage on first launch:

- they are **reference material**, not your data, and are never uploaded;
- the underlying Minecraft-related content belongs to its rights holders; this project claims no ownership
  and uses it only as conversion reference;
- if a rights holder objects to the distribution, contact the maintainer via Issues and we will evaluate
  removal.

See the [`brand/`](../brand/) directory and [`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md) for details.

## 5. Third-party processors

None. The app sends no data to any third party (it has no network capability). GitHub as the hosting and
distribution platform processes metadata for Issues / PRs / Release downloads under GitHub's own privacy
policy — that is **platform behaviour**, not the app's.

## 6. Children

The app is not directed at children under 13 and does not knowingly collect personal information from
children (in fact it collects no personal information at all).

## 7. Your rights

- Clear conversion history / reset settings in the app at any time; clear the app cache in system settings;
- Uninstall the app to delete all app-private data;
- Conversion output written through SAF lives in your own folder and is fully under your control;
- Privacy questions: open an issue in the repository.

## 8. Changes

Policy updates change the effective date above and are announced in the repository. Significant changes are
highlighted in Release Notes and `CHANGELOG.md`.

---

**中文版：** [legal/PRIVACY.md](../PRIVACY.md)

**Contact:** GitHub Issues — https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues
