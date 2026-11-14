# Third-Party Notices

**Effective date:** 2026-11-14

2-Pyramid for Android includes several third-party open-source components. They are provided under their
**respective licenses**; this notice is a summary only, and the authoritative texts are the components'
repositories / `Cargo.lock` / `gradle/libs.versions.toml`.

The main program's license is in [`LICENSE`](../LICENSE) (MIT © 2025–2026 2-Pyramid Studio). Where a
third-party license conflicts with MIT, **that component** is governed by its own license; the main program
remains MIT.

---

## 1. Rust conversion core (`convert-core/`, shipped in the APK as `libconvert_core.so`)

| Component | Version | License (typical) | Purpose |
|---|---|---|---|
| [serde](https://serde.rs) / serde_json | 1.0.228 / 1.0.150 | MIT OR Apache-2.0 | Serialization (`pack.mcmeta`, item definitions, `2pyr-gen.json`) |
| [image](https://github.com/image-rs/image) | 0.24.9 | MIT OR Apache-2.0 | Texture read/write and generation |
| [png](https://github.com/image-rs/image-png) / [jpeg-decoder](https://github.com/image-rs/jpeg-decoder) | 0.17.16 / 0.3.2 | MIT OR Apache-2.0 | PNG / JPEG codecs (optional backends of `image`) |
| [zip](https://github.com/zip-rs/zip) | 0.6.6 | MIT | Pack extraction/packing |
| [flate2](https://github.com/rust-lang/flate2-rs) | 1.1.9 | MIT OR Apache-2.0 | Deflate compression / CRC32 |
| [miniz_oxide](https://github.com/Frommi/miniz_oxide) | 0.8.9 | MIT OR Zlib OR Apache-2.0 | Pure-Rust backend for flate2 |
| [walkdir](https://github.com/BurntSushi/walkdir) | 2.5.0 | Unlicense OR MIT | Directory traversal |
| [rayon](https://github.com/rayon-rs/rayon) | 1.12.0 | MIT OR Apache-2.0 | Parallel processing (tasks / extraction) |
| [regex](https://github.com/rust-lang/regex) | 1.13.0 | MIT OR Apache-2.0 | Text and format parsing |
| [chrono](https://github.com/chronotope/chrono) | 0.4.45 | MIT OR Apache-2.0 | Timestamps (UTC in `2pyr-gen.json`) |
| [uuid](https://github.com/uuid-rs/uuid) | 1.23.4 | MIT OR Apache-2.0 | Temporary identifiers |
| [sha2](https://github.com/RustCrypto/hashes) | 0.10.9 | MIT OR Apache-2.0 | Per-entry SHA-256 |
| [tempfile](https://github.com/Stebalien/tempfile) | 3.27.0 | MIT OR Apache-2.0 | Temporary directories (inside the app cache) |
| [dirs](https://github.com/dirs-dev/dirs-rs) | 5.0.1 | MIT OR Apache-2.0 | System directories fallback (Android uses injected paths) |
| [lazy_static](https://github.com/rust-lang-nursery/lazy-static.rs) | 1.5.0 | MIT OR Apache-2.0 | Static initialization |
| [once_cell](https://github.com/matklad/once_cell) | 1.21.4 | MIT OR Apache-2.0 | Static initialization |
| [UniFFI](https://github.com/mozilla/uniffi-rs) (uniffi / uniffi_core etc.) | 0.32.0 | **MPL-2.0** | Rust ↔ Kotlin bridge (facade, scaffolding) |

> The authoritative list is [`convert-core/Cargo.lock`](../convert-core/Cargo.lock) (including all transitive
> dependencies). UniFFI is MPL-2.0; its source is publicly available at
> [mozilla/uniffi-rs](https://github.com/mozilla/uniffi-rs). This project has not modified UniFFI sources.

## 2. Android / Kotlin side (shipped in the APK)

| Component | Version | License (typical) | Purpose |
|---|---|---|---|
| [Kotlin](https://kotlinlang.org) (stdlib) | 2.0.21 | Apache-2.0 | Language runtime |
| [AndroidX core-ktx](https://developer.android.com/jetpack/androidx) | 1.15.0 | Apache-2.0 | Android core APIs |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) (runtime / service / viewmodel-compose) | 2.8.7 | Apache-2.0 | Lifecycle, foreground service, ViewModel |
| [AndroidX Activity Compose](https://developer.android.com/jetpack/androidx/releases/activity) | 1.9.3 | Apache-2.0 | Compose host activity |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM 2024.10.01 / ui 1.7.5 / material3 1.3.1 / material-icons-extended) | — | Apache-2.0 | UI framework and components |
| [Navigation Compose](https://developer.android.com/jetpack/androidx/releases/navigation) | 2.8.3 | Apache-2.0 | Screen navigation |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.9.0 | Apache-2.0 | Coroutines |
| [AndroidX DocumentFile](https://developer.android.com/jetpack/androidx/releases/documentfile) | 1.0.1 | Apache-2.0 | SAF file access |
| [JNA](https://github.com/java-native-access/jna) | 5.14.0 | **LGPL-2.1-or-later OR Apache-2.0** | Native bridge used by the generated UniFFI binding |

> The authoritative list is [`2fa-android/gradle/libs.versions.toml`](../2fa-android/gradle/libs.versions.toml).
> JNA is dual-licensed; this project uses it under the **Apache-2.0** option and ships the Android native
> library `libjnidispatch.so` extracted from the `jna-platform` AAR. For LGPL terms / relinking information,
> see the JNA upstream repository.

## 3. Build-time tools (not shipped in the APK)

- **Android Gradle Plugin 8.11.1 / Gradle 8.13** (Apache-2.0) — build system;
- **Android SDK / NDK 27.3**, **Rust stable toolchain**, **cargo-ndk**, **uniffi-bindgen** — compilation and
  binding generation; not shipped in the APK (Rust std is MIT OR Apache-2.0);
- System/NDK libraries statically linked into the `.so` are part of the build toolchain.

## 4. Platform and game assets

- **Minecraft** names, trademarks and in-game behaviour belong to Mojang Studios / Microsoft. This software
  contains **no** Minecraft client and no official resource packs; you convert packs you lawfully hold.
- Textures generated during conversion are derived from **your** packs; copyright remains with the original
  rights holders.
- **`UImage` reference assets**: the APK bundles 80 PNGs (~0.56 MB, under
  `2fa-android/app/src/main/assets/UImage/`) taken from the maintainer's desktop project
  [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid). They are used by tasks that generate/overlay
  vanilla-style textures. Any Minecraft-derived content remains owned by its rights holders; this project
  **claims no ownership** and uses it only as conversion reference. If a rights holder objects to the
  distribution, contact the maintainer via Issues and we will evaluate removal.
- The Precision Geometry marks in `brand/` (SVG / PNG) are 2-Pyramid Studio brand assets, provided with the
  repository for build and presentation purposes; trademark usage rules are in [`EULA.md`](./EULA.md)
  section 4.

## 5. How to obtain full license texts

- **Rust**: `convert-core/Cargo.lock` records exact `(name, version)` pairs; LICENSE files are available on
  [crates.io](https://crates.io) or the upstream repositories;
- **Android / Kotlin**: versions are in `gradle/libs.versions.toml`; each AAR ships its license in its POM /
  repository page;
- Repository root `LICENSE` (MIT, the main program's license).

The APK does **not** embed per-component license text files. If you redistribute it, you are responsible for
satisfying each component's attribution and copy requirements (e.g. retaining Apache-2.0 NOTICE files,
providing MPL-2.0 source availability).

If you believe a component is missing from this notice, please open an issue and we will add it promptly.

---

**中文版：** [legal/THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md)
