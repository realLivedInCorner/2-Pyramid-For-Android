# 2-Pyramid for Android End User Agreement

**Effective date:** 2026-11-14
**Applies to:** individuals or organizations obtaining and using the 2-Pyramid for Android APK.

---

## 0. Relationship to the open-source license (read this first)

The project's **source code and official build artifacts (the APK)** are provided under the **MIT License**;
see [`LICENSE`](../LICENSE).

- Under MIT you already have the rights to use, copy, modify, merge, publish, redistribute, sublicense and
  sell copies. **This agreement does not revoke or reduce those rights**, nor does it impose restrictions
  beyond MIT. That includes reverse engineering and research to the extent permitted by applicable law;
  the project contains no closed-source "proprietary parts", so no such restriction is added.
- Where MIT and this agreement differ, **MIT controls**.
- This agreement exists only to:
  1. remind you to comply with the **Minecraft EULA** and other third-party rules;
  2. ask you to read and acknowledge [`DISCLAIMER.md`](./DISCLAIMER.md) and [`PRIVACY.md`](./PRIVACY.md);
  3. explain the correct use of the **trademarks**, the **distribution channel**, and the "unofficial" status.

You are encouraged to read those documents before use. **This agreement adds no license conditions beyond
MIT** — exercising your MIT rights does not require accepting it. (If you build from source yourself, this
agreement does not apply to you either.)

---

## 1. Your responsibilities when using the software

1. **Minecraft terms.** When converting, using or distributing resource packs you must ensure compliance with
   the Mojang Studios / Microsoft EULA and Terms of Service, and with the original pack authors' licenses.
   This software does not judge legality for you.
2. **Lawful use.** Do not use the software to infringe the rights of others or for any illegal activity.
3. **Back up and verify.** Conversion is heuristic and may fail or damage files. Back up the originals and
   verify results in a test environment. Bedrock conversion is experimental and OptiFine CIT conversion is
   off by default (see [`DISCLAIMER.md`](./DISCLAIMER.md)).

## 2. Metadata file in conversion output (2pyr-gen)

A successful **Java Edition** conversion writes a `2pyr-gen.json` at the **pack root** recording the
parameters of that conversion. This behaviour:

- is **on by default** and can be disabled in the conversion options (persisted with your settings); when
  disabled the file is not produced;
- **records**: generator name and version, build number (BUILD), generation time (UTC, RFC 3339, second
  precision), source and target `pack_format`, target version label (when reliably available), and the six
  conversion switches used (layer fix, shader adaptation, delete shaders, item definitions, OptiFine CIT
  conversion, delete CIT). It also contains a **neutral note** telling recipients that the file was generated
  by 2-Pyramid, that Minecraft does not read it, and that it can be disabled in the conversion options;
- **contains no personal information**: no file paths, usernames, device names, hardware IDs, UUIDs, IP
  addresses or network information — it describes conversion parameters only;
- exists to make output traceable (which build, with which options) and to provide context for support;
- **is not read by the Minecraft client** and plays no part in loading the pack: it sits at the pack root and
  clients ignore unknown root files;
- **is distributed with the pack**: recipients can see the same conversion parameters (no personal data). If
  that matters, disable the toggle in the conversion options or delete the file afterwards.

## 3. Distribution channel, signing and updates

- The app is distributed as a **sideloaded APK on GitHub Releases**. There is **no in-app auto-update and no
  background update check**.
- Official APKs are signed with the project's **debug keystore** (this keeps overwrite-installs and
  self-built packages interoperable). That keystore is the standard public debug key shipped with Android
  development tools: **anyone can produce an identically signed package**, so the signature **does not
  prove** provenance or integrity. Download only from this repository's Releases and verify the published
  `.sha256`.
- Building from source does not change this agreement; MIT still controls.
- APKs obtained from unofficial channels are your own responsibility in terms of provenance, integrity and
  safety.

## 4. Trademarks and "unofficial" status

- You may redistribute the software under MIT; keep `LICENSE` and the copyright notice (MIT's only hard
  requirement).
- The "2-Pyramid" name and marks identify this project. Do not imply that a redistribution is officially
  endorsed by 2-Pyramid Studio unless it is an official release.
- The MIT License **grants no trademark rights** in the "2-Pyramid" name or marks; redistributors may use them
  only for reasonable identification.
- This software is **not affiliated with, sponsored by, or endorsed by Mojang Studios / Microsoft**. Do not
  use their trademarks to imply a partnership; refer to Minecraft descriptively only.
- The bundled `UImage` reference assets relate to content owned by Mojang Studios / Microsoft; this project
  claims no ownership and uses them only as conversion reference material (see
  [`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md)).

## 5. Disclaimer (summary)

The software is provided "as is" without warranty; the authors are not liable for damages arising from use.
The full text is in [`DISCLAIMER.md`](./DISCLAIMER.md). This summary neither replaces the AS IS clause in MIT
nor adds obligations.

## 6. Privacy

**Fully local processing, no account, no telemetry, no network permission**: your packs never leave the
device. The app reads and writes only the input pack and output folder you explicitly grant through the system
file picker (SAF). There is no in-app update check and the app never goes online. Java-target output contains
a conversion-parameter record by default (`2pyr-gen.json`, no personal data, see section 2, can be disabled).
See [`PRIVACY.md`](./PRIVACY.md).

## 7. Third-party components

Dependencies are provided under their respective licenses; see
[`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md).

## 8. Changes and contact

Updates to this agreement are announced in the repository. Updates **do not affect** rights you already have
under MIT and add no obligations beyond it. Questions: open an
[Issue](https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues).

---

**中文版：** [legal/EULA.md](../EULA.md)
