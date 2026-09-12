#!/usr/bin/env bash
# ===============================================================
# 2FA Rust build (Linux/bash) — CI 专用
# 翻译自 tools/build-rust-android.bat
#
# 调用: ./tools/build-rust-android.sh [ABI]
# 默认 ABI: arm64-v8a
#
# 产出:
#   - <APP>/app/src/main/jniLibs/<ABI>/libconvert_core.so
#   - <APP>/app/src/main/kotlin/com/twopyramid/twofa/uniffi/* (UniFFI binding)
# ===============================================================
set -euo pipefail

# ── 路径解析 ───────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUST_CRATE_DIR="$ROOT_DIR/convert-core"
APP_DIR="$ROOT_DIR/2fa-android"

# ── 参数 ───────────────────────────────────────────────────────
ABI="${1:-arm64-v8a}"
case "$ABI" in
  arm64-v8a)     TARGET_TRIPLE="aarch64-linux-android" ;;
  armeabi-v7a)   TARGET_TRIPLE="armv7-linux-androideabi" ;;
  x86_64)        TARGET_TRIPLE="x86_64-linux-android" ;;
  i686)          TARGET_TRIPLE="i686-linux-android" ;;
  *)
    echo "[FAIL] Unknown ABI: $ABI" >&2
    echo "       Supported: arm64-v8a, armeabi-v7a, x86_64, i686" >&2
    exit 1
    ;;
esac

# ── 路径常量 ───────────────────────────────────────────────────
# 重要:cargo ndk 的 -o 参数是 base dir,它会自动追加 <ABI>/ 子目录。
# 所以这里 OUT_DIR 不能带 ABI 后缀,否则会嵌套成 jniLibs/<ABI>/<ABI>/。
OUT_DIR="$APP_DIR/app/src/main/jniLibs"
# 只刷 uniffi/ 子目录,不动 com/twopyramid/twofa/ 下的其他 package
BINDING_DIR="$APP_DIR/app/src/main/kotlin/com/twopyramid/twofa/uniffi"
CARGO_TARGET_DIR="$RUST_CRATE_DIR/target/android"

# ── Android NDK ───────────────────────────────────────────────
# 优先:ANDROID_NDK_HOME → ANDROID_HOME/ndk/<最新版本> → 默认
NDK_ROOT="${ANDROID_NDK_HOME:-}"
if [[ -z "$NDK_ROOT" ]]; then
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    ndk_candidate="$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -1 || true)"
    if [[ -n "$ndk_candidate" && -d "$ndk_candidate" ]]; then
      NDK_ROOT="$ndk_candidate"
    fi
  fi
fi
if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT" ]]; then
  echo "[FAIL] ANDROID_NDK_HOME not set and no NDK found in ANDROID_HOME/ndk/" >&2
  echo "       Set ANDROID_NDK_HOME to a NDK 27+ install, or install via sdkmanager." >&2
  exit 1
fi
echo "[2FA-Rust] ANDROID_NDK_HOME=$NDK_ROOT"
echo "[2FA-Rust] ABI=$ABI  target=$TARGET_TRIPLE"

# ── 前置检查 ───────────────────────────────────────────────────
for cmd in cargo rustup; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[FAIL] $cmd not found in PATH" >&2
    exit 1
  fi
done

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "[1/5] Installing cargo-ndk (one-time, ~2 min)..."
  cargo install cargo-ndk --locked
fi

if ! command -v uniffi-bindgen >/dev/null 2>&1; then
  echo "[1/5] Installing uniffi-bindgen (one-time, ~2-5 min)..."
  cargo install uniffi --features=cli --locked
fi

# ── [2] rustup target add ─────────────────────────────────────
echo "[2/5] rustup target add $TARGET_TRIPLE"
# 用 || true 容错:GitHub runner 默认走 fastly CDN,一般 OK;
# 如果用户以后改用 tuna 镜像失败,wrapper workaround 是用户本地的事
if ! rustup target add "$TARGET_TRIPLE" 2>&1; then
  echo "       [WARN] rustup target add failed, continuing (will fail at build if target really missing)" >&2
fi

# ── [3] cargo ndk build ──────────────────────────────────────
echo "[3/5] cargo ndk build (this may take 5-10 minutes on first run)"
mkdir -p "$OUT_DIR"
pushd "$RUST_CRATE_DIR" >/dev/null
cargo ndk \
  --target "$TARGET_TRIPLE" \
  --platform 31 \
  -o "$OUT_DIR" \
  build --release \
  --manifest-path "$RUST_CRATE_DIR/Cargo.toml" \
  --target-dir "$CARGO_TARGET_DIR"
popd >/dev/null

# cargo ndk 写 <OUT_DIR>/<ABI>/libconvert_core.so
SO_NESTED="$OUT_DIR/$ABI/libconvert_core.so"
SO_OUT="$OUT_DIR/$ABI/libconvert_core.so"
if [[ ! -f "$SO_NESTED" ]]; then
  echo "[FAIL] Expected .so not found at $SO_NESTED" >&2
  exit 1
fi
# 已经在正确位置(同路径) — 留着这步以防不同 cargo-ndk 版本行为变化
if [[ "$SO_NESTED" != "$SO_OUT" ]]; then
  cp -f "$SO_NESTED" "$SO_OUT"
fi
echo "       .so: $SO_OUT"

# ── [4] uniffi-bindgen generate ───────────────────────────────
echo "[4/5] uniffi-bindgen generate"
# 重要:只删 uniffi/ 子目录,绝不动 BINDING_DIR 本身(否则会牵连 ui/service/queue/native/ 等 package)
if [[ -d "$BINDING_DIR/uniffi" ]]; then
  rm -rf "$BINDING_DIR/uniffi"
fi
mkdir -p "$BINDING_DIR"
uniffi-bindgen generate \
  --library "$SO_OUT" \
  --language kotlin \
  --out-dir "$BINDING_DIR" \
  --crate convert_core
echo "       binding: $BINDING_DIR"

# ── [5] Done ──────────────────────────────────────────────────
echo "[5/5] Done"
echo "       .so       = $SO_OUT"
echo "       binding   = $BINDING_DIR"
