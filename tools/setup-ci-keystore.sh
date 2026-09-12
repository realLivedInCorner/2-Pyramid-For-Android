#!/usr/bin/env bash
# ===============================================================
# 2FA CI debug keystore 生成器
# 生成 Android SDK 标准 debug.keystore(密码/alias=android),
# 放到 $HOME/.android/debug.keystore,给 assembleRelease 签名用。
#
# 调用: ./tools/setup-ci-keystore.sh
# 必须在 ./gradlew :app:assembleRelease 之前跑。
# ===============================================================
set -euo pipefail

KEYSTORE_DIR="${HOME}/.android"
KEYSTORE_FILE="$KEYSTORE_DIR/debug.keystore"

# 标准 debug keystore 参数(Android SDK / Android Studio 默认)
# 这些不是秘密:文档公开,任何人都能复现
DISTINGUISHED_NAME="CN=Android Debug,O=Android,C=US"
KEY_ALIAS="androiddebugkey"
STORE_PASS="android"
KEY_PASS="android"
VALIDITY_DAYS=10000

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "[2FA-Keystore] Already exists at $KEYSTORE_FILE, skipping generation."
  exit 0
fi

mkdir -p "$KEYSTORE_DIR"

# keytool 在 JDK 里,CI runner 已经装好 JDK 17(setup-android action 会装)
if ! command -v keytool >/dev/null 2>&1; then
  echo "[FAIL] keytool not found in PATH" >&2
  echo "       Install JDK 17+ (keytool is in \$JAVA_HOME/bin)" >&2
  exit 1
fi

echo "[2FA-Keystore] Generating debug.keystore at $KEYSTORE_FILE"
keytool -genkeypair \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -dname "$DISTINGUISHED_NAME" 2>&1

echo "[2FA-Keystore] Done."
keytool -list -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASS" 2>&1 | head -20
