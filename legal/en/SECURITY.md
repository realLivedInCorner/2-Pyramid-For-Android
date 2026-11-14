# Security Policy

**Effective date:** 2026-11-14
**Applies to:** the 2-Pyramid for Android app and APKs from official release channels.

---

## 1. Supported versions

| Version line | Support status |
|---|---|
| Current line (**2.1.x**, latest Stable tag) | Security fixes prioritized |
| Latest Beta / UnStable | Best effort, no SLA |
| Older versions (**2.0.x and earlier**, pre-M3 core) | Upgrade recommended; generally no further patches |

## 2. Reporting a vulnerability

**Do not** post directly exploitable details in a public issue.

Recommended path:

1. Open [Security Advisories](https://github.com/realLivedInCorner/2-Pyramid-For-Android/security/advisories)
   and file a **Private vulnerability report**. If that entry point is unavailable, open an issue prefixed
   `[SECURITY]` and the maintainer will move it to a private channel.
2. Include: affected version and BUILD (Settings → About), Android version, device model, reproduction steps,
   expected vs. actual behaviour, and a PoC or sample pack (privately if needed).

We aim to acknowledge within **7 business days**; for confirmed vulnerabilities we coordinate the fix and
disclosure timeline.

## 3. Security design notes (for your risk assessment)

- **No network capability**: the APK does not declare the `INTERNET` permission, so it cannot open network
  connections — there is no data-exfiltration channel and no remote update/config surface. See
  [`PRIVACY.md`](./PRIVACY.md).
- **Least-privilege file access**: no broad storage permission; input packs and output folders are accessed
  only through SAF, with the grant scoped to locations you explicitly choose.
- **No root / no system privileges**: the app runs in the ordinary app sandbox and never requests root,
  accessibility, device-admin or similar sensitive permissions.
- **Input parsing boundaries**: the conversion engine parses arbitrary user-provided zips / PNGs / JSON /
  text, with bounds on Zip Slip, decompression bombs, entry counts and extracted sizes (inherited from the
  desktop 2.203.0 engine). Malformed input normally results in a failed conversion rather than executing
  pack content.
- **No untrusted binaries executed**: shaders are text/JSON rewritten as GLSL source; nothing is compiled or
  executed on the GPU from untrusted binaries. The app loads no external code or plugins.
- **Rust panic boundary**: the conversion core is built with `panic = "unwind"`, so UniFFI's `catch_unwind`
  turns Rust panics into Kotlin exceptions instead of crashing the foreground service process.
- **Release integrity**: each Release ships the APK plus a same-named `2-Pyramid-Android-<version>.apk.sha256`.
  Verify the hash.
- **Signing (important)**: official APKs are signed with the repository's public **debug keystore**
  (`tools/ci/debug.keystore`; CI and local builds share the same key, so signatures stay stable across
  versions and overwrite-installs keep working). The Android debug keystore is the standard, publicly known
  debug key (its passwords are public), so **anyone can produce an identically signed package**; a matching
  signature therefore does **not** prove provenance or integrity. Integrity comes only from the official
  Release page plus the published `.sha256`. If you need stronger guarantees, build from source (see the README).

## 4. Known boundaries (not vulnerabilities, but worth knowing)

- Converting arbitrary user-provided zips parses many PNGs / JSON files / text; very large packs
  (256x / 512x) may be slow or fail on low-memory devices — a resource limit, not a security issue;
- **Not fully validated on physical devices**: builds and differential checks were done on a PC; UImage
  first-launch extraction, SAF input/output and cross-thread JNA progress callbacks still need more device
  coverage. Report anomalies via section 2;
- **Debug-keystore signing** (above) means "signature mismatch" cannot be the sole reason to refuse an
  install — overwrite-install convenience takes priority over signature consistency, so always verify the
  checksum;
- APKs from unofficial channels may be re-signed or tampered with, bypassing the SHA chain; trust only this
  repository's GitHub Releases.

## 5. Acknowledgements

Valid security reports are acknowledged in Release Notes or in this file (anonymity available on request).

---

**中文版：** [legal/SECURITY.md](../SECURITY.md)
