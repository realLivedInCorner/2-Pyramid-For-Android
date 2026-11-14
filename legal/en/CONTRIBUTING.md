# Contributing

Thanks for your interest in **2-Pyramid for Android**! Issues and pull requests are welcome.

This repository is the Android-native port of the desktop [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid):
the conversion core (`convert-core/`, Rust) is shared with desktop 2.203.0, and the Android side is Kotlin +
Jetpack Compose.

---

## 1. Before you start

- Search [Issues](https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues) first to avoid duplicates.
- For larger features or UI changes, open an issue to align on the approach before writing code.
- Code of conduct: be respectful and stay technical; no personal attacks or unrelated political arguments.

## 2. Development environment

| Dependency | Version / notes |
|---|---|
| Rust | stable (`rustup`; Android target `aarch64-linux-android`) |
| Android NDK | **27.3.13750724** (must match `android.ndkVersion` in `2fa-android/gradle.properties`) |
| JDK | 17 |
| Android SDK | `compileSdk = 37` / `targetSdk = 37` / `minSdk = 31` (Android 12+) |
| Python | 3.8+ (only for `tools/set_version.py`; no third-party packages) |
| Helper tools | `cargo-ndk`, `uniffi-bindgen` (`cargo install uniffi --features=cli --locked`) |

First build:

```bash
# 1) SDK path: 2fa-android/local.properties (template: local.properties.example)
cp 2fa-android/local.properties.example 2fa-android/local.properties
# edit sdk.dir

# 2) Rust -> .so + UniFFI Kotlin binding (only when Rust changes)
tools/build-rust-android.bat arm64-v8a      # Windows
bash tools/build-rust-android.sh arm64-v8a  # Linux / macOS / CI

# 3) Build the APK
cd 2fa-android
./gradlew :app:assembleDebug
```

> `.so`, UniFFI bindings, `target/`, `app/build/` and `jniLibs/` are gitignored — **do not commit them**.
> On Windows, Gradle resolves the SDK/NDK from `local.properties` and injects them into the cargo-ndk child
> process, so you do not need machine-level `ANDROID_HOME` / `ANDROID_NDK_HOME`.

## 3. Run before opening a PR

```bash
# Rust (host; Windows / Linux / macOS)
cd convert-core
cargo test --lib                  # 220 passed / 6 ignored (real-pack E2E needs AROM_REAL_PACK)
cargo test --test android_smoke   # 6 passed

# Kotlin / APK (reuse the existing .so and binding; no Rust rebuild needed for Kotlin-only changes)
cd ../2fa-android
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
SKIP_RUST_BUILD=true ./gradlew :app:assembleDebug
```

If you changed **Rust (convert-core)**, you must regenerate the `.so` + binding and run
`:app:compileDebugKotlin` again. A stale generated binding usually surfaces as inscrutable JNA symbol errors.

**Optional: differential check against desktop 2.203.0** (strongly recommended when conversion behaviour
changes):

```bash
cd convert-core
cargo run --release --example convert_cli -- \
  --convert <your-pack.zip> --to 55 --out /tmp/out55
# compare against desktop: 2-pyramid.exe --convert <same pack> --to 55 --out <other dir>
# comparison basis: entry set + per-entry SHA-256 of decompressed bytes + compression method +
# pack.mcmeta semantics; timestamps/order are ignored.
```

## 4. Where code goes

| Content | Location |
|---|---|
| Conversion task implementations (grouped by role) | `convert-core/src/natives/{eraser,architect,surgeon,reverse}/` |
| Task metadata (name / type / phase) | `convert-core/src/task_registry.rs` |
| Object model and engine (BFS scheduler / version edges) | `convert-core/src/arom/` (scheduler in `arom/engine/`) |
| Pack I/O, production orchestration, analysis, output diff | `convert-core/src/pack/` |
| Bedrock ↔ Java conversion | `convert-core/src/bedrock_convert/` |
| UniFFI facade / progress / Android adapters | `convert-core/src/{lib.rs,progress.rs,runtime.rs,logger.rs,image_utils.rs,commands/}` |
| Queue / foreground service / UniFFI bridge | `2fa-android/app/src/main/kotlin/com/twopyramid/twofa/{native,service,queue}/` |
| Compose screens / components / design tokens | `.../ui/{screens,components,theme}/` |
| Settings / history persistence | `.../settings/`, `.../history/` |
| Brand vector sources + generator | `brand/` + `tools/generate-android-brand.mjs` |
| Rust → `.so` + binding build | `tools/build-rust-android.{bat,ps1,sh}` |
| Version / release | root `BUILD`, `tools/set_version.py`, `.github/workflows/` |

## 5. Conventions

### Rust (convert-core)

- Keep the core **in sync with desktop 2.203.0**: prefer directory-level diffs against the desktop tree over
  ad-hoc rewrites;
- Stay UI-framework free: **no Tauri / Windows dependencies** (`grep -ri tauri convert-core/src` must be empty);
- Access paths through `runtime::*_dir()` (Android has no standard HOME); do not hardcode `dirs` system paths;
- Avoid new `unwrap()` / `expect()` outside tests;
- Add `#[cfg(test)]` tests for image/path/structure logic; every new `#[ignore]` test needs a stated reason;
- **Never write a `/*` sequence inside comments or doc comments**: UniFFI copies Rust doc comments verbatim
  into the generated Kotlin KDoc, and Kotlin supports nested block comments — one `/*` breaks the generated
  `convert_core.kt`. Use forms like `items/<name>.json` for directory globs.

### Kotlin / Jetpack Compose

- Material 3 + Compose only; do not introduce XML View dependencies. Design tokens live in `ui/theme/`
  (color / type / radius / motion) — **do not hardcode colors or durations in screens**;
- Maximum font weight is 600 (the brand name is the only exception); CJK titles use zero letter-spacing;
  no default shadows — hierarchy comes from type scale and whitespace;
- Dialogs / bottom sheets share a single shell component, not per-screen implementations;
- Background conversion runs in `service/ZipQueueService` (foreground service type fixed to `dataSync`).
  Progress flows to the UI only through `ProgressRepository`; **callbacks arrive off the UI thread — never
  touch Compose state from them**;
- Conversion options are snapshotted into `QueueItem.options` at enqueue time; later settings changes must not
  affect queued tasks;
- Access user files only through SAF (`DocumentFile`); do not request broad storage permissions;
- **Kotlin source/comments must also avoid the `/*` sequence** (KDoc nested comments cause
  `Unclosed comment`).

### Assets and brand

- `brand/` is the **single vector source**: `res/drawable/brand_*.xml`, launcher icons and splash icons are
  generated by `node tools/generate-android-brand.mjs` (idempotent, self-checking). **Do not hand-edit
  generated files**;
- When updating brand assets, change `brand/*.svg` and re-run the generator; the output must have no diff.

### Version and build number

- Version: `versionName` in `2fa-android/app/build.gradle.kts`; build number: repository-root `BUILD`;
- **Builds are read-only**: never `+1` the build number, and there is no `version.properties` (that mechanism
  was removed);
- Before a release: `python tools/set_version.py --set <ver> [--build <n>]`, then
  `python tools/set_version.py --check --version Stable-<ver>`;
- Release notes live in `docs/compose/releases/news-<version>.md` and are used verbatim as the Release body.

## 6. Pull requests

1. Fork and branch from `main` (e.g. `feat/xxx`);
2. Make sure the commands in section 3 pass; for UI changes, explain how you verified them (screenshots/clips
   are fine, but don't commit large binaries);
3. Describe: motivation, changes, test results, related issues;
4. Keep the diff readable; explain the provenance of generated or large files, and avoid committing them when
   possible.

## 7. Commit messages

English imperative + conventional commits, for example:

```
fix(ui): keep dialog scrim warm on dark theme
feat(convert): expose CIT toggle in conversion options
docs: sync legal documents with Android distribution facts
```

One PR, one concern; unrelated formatting goes in a separate commit.

## 8. Reporting bugs

Please include:

- App version and BUILD (Settings → About);
- Android version and device model;
- Source / target version (Java `pack_format` or Bedrock);
- The conversion options (six toggles + naming template) you used;
- Reproduction steps and expected result; recent logs are available in Settings → Developer mode (copyable).

A **small, publicly shareable sample pack** makes conversion bugs much faster to fix.

## 9. Feature requests

Explain the use case, why the current flow falls short, and the interaction you expect. Screenshots or
sketches help.

## 10. Licensing of contributions

This project operates equivalently to a Developer Certificate of Origin (DCO). No extra CLA is required, but
you must have the right to submit your contribution under MIT. Unless agreed otherwise in writing, your
contribution is licensed to the public under the **MIT License**, allowing 2-Pyramid Studio **and all
downstream recipients** to use, modify and redistribute it. Submitting means you have the right to grant this
(e.g. no conflict with an employment agreement or third-party copyright).

**Please do not submit:**

- Unauthorized official Minecraft assets, other people's resource packs, or cracked content (exception: the
  maintainer-owned `UImage/` reference assets — open an issue before changing them);
- Oversized binaries (compress screenshots; synthesize test textures, or keep them in `_sync/` locally);
- Paths, logs or account data containing personal information.

---

**中文版：** [legal/CONTRIBUTING.md](../CONTRIBUTING.md)
