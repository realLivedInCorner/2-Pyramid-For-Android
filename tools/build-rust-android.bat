@echo off
REM ===============================================================
REM 2FA Rust build wrapper (pure ASCII, no multi-line if blocks)
REM Usage: build-rust-android.bat arm64-v8a
REM ===============================================================
setlocal

set "ABI=%~1"
if "%ABI%"=="" set "ABI=arm64-v8a"

set "ROOT_DIR=%~dp0.."
set "RUST_CRATE_DIR=%ROOT_DIR%\convert-core"
set "APP_DIR=%ROOT_DIR%\2fa-android"
set "OUT_DIR=%APP_DIR%\src\main\jniLibs\%ABI%"
REM BINDING_DIR is the uniffi-bindgen output dir (a *child* of com/twopyramid/twofa/).
REM Do NOT point it at com/twopyramid/twofa/ directly — `rmdir /s /q` below would
REM wipe all sibling packages (ui, service, queue, native, etc.). The binding
REM generation only needs to refresh files under the `uniffi/` subdir.
set "BINDING_DIR=%APP_DIR%\app\src\main\kotlin\com\twopyramid\twofa\uniffi"
set "CARGO_TARGET_DIR=%RUST_CRATE_DIR%\target\android"

REM Translate ABI to cargo target triple
set "TARGET_TRIPLE="
if /I "%ABI%"=="arm64-v8a"   set "TARGET_TRIPLE=aarch64-linux-android"
if /I "%ABI%"=="armeabi-v7a" set "TARGET_TRIPLE=armv7-linux-androideabi"
if /I "%ABI%"=="x86_64"      set "TARGET_TRIPLE=x86_64-linux-android"
if /I "%ABI%"=="i686"        set "TARGET_TRIPLE=i686-linux-android"
if "%TARGET_TRIPLE%"=="" echo [FAIL] Unknown ABI "%ABI%" & exit /b 1

echo 2FA Rust build start. ABI=%ABI% target=%TARGET_TRIPLE%

REM [1] prereqs
where cargo >nul 2>&1
if errorlevel 1 echo [FAIL] cargo not found & exit /b 1
where cargo-ndk >nul 2>&1
if errorlevel 1 echo [FAIL] cargo-ndk not found. Run cargo install cargo-ndk & exit /b 1
where uniffi-bindgen >nul 2>&1
if errorlevel 1 (
    echo [1/5] Installing uniffi-bindgen, one-time, may take 2-5 min
    cargo install uniffi --features=cli --locked
    if errorlevel 1 echo [FAIL] cargo install uniffi failed & exit /b 1
)

REM [2] rustup target add (best effort: rsproxy mirror works, tuna 404s)
echo [2/5] rustup target add %TARGET_TRIPLE%
rustup target add %TARGET_TRIPLE%
if errorlevel 1 (
    echo       [WARN] rustup target add failed - using local wrapper
    set "USE_WRAPPER=1"
) else (
    set "USE_WRAPPER=0"
)

REM [3] cargo ndk build (slow first time)
echo [3/5] cargo ndk build, this may take 5-10 minutes first time
if "%ANDROID_NDK_HOME%"=="" (
    if exist "%ANDROID_HOME%\ndk\27.2.12479018" set "ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\27.2.12479018"
)
echo       ANDROID_NDK_HOME=%ANDROID_NDK_HOME%
if "%ANDROID_NDK_HOME%"=="" echo [FAIL] ANDROID_NDK_HOME not set and NDK 27.2.12479018 not found. Install via SDK Manager. & exit /b 1
pushd "%RUST_CRATE_DIR%"
if "%USE_WRAPPER%"=="1" (
    set "RUSTC=%~dp0rustc-aarch64-wrapper.bat"
    echo       RUSTC=%RUSTC%  (redirects --sysroot to local rust-std staging)
)
cargo ndk --target %TARGET_TRIPLE% --platform 31 -o "%OUT_DIR%" build --release --manifest-path "%RUST_CRATE_DIR%\Cargo.toml" --target-dir "%CARGO_TARGET_DIR%"
set "CARGO_EXIT=%errorlevel%"
popd
if not "%CARGO_EXIT%"=="0" echo [FAIL] cargo ndk build exited %CARGO_EXIT% & exit /b %CARGO_EXIT%
REM cargo ndk writes to <OUT_DIR>/<ABI>/libconvert_core.so (extra ABI subdir).
REM Copy to outer <OUT_DIR>/libconvert_core.so so Gradle's ndk { abiFilters }
REM picks it up (AGP only looks in jniLibs/<abi>/, not nested jniLibs/<abi>/<abi>/).
if not exist "%OUT_DIR%\%ABI%\libconvert_core.so" echo [FAIL] %OUT_DIR%\%ABI%\libconvert_core.so not found & exit /b 1
copy /Y "%OUT_DIR%\%ABI%\libconvert_core.so" "%OUT_DIR%\libconvert_core.so" >nul
if errorlevel 1 echo [FAIL] copy to %OUT_DIR%\libconvert_core.so failed & exit /b 1

REM [4] uniffi-bindgen — refresh ONLY the uniffi/ subdir, never the parent.
echo [4/5] uniffi-bindgen generate
if exist "%BINDING_DIR%\uniffi" rmdir /s /q "%BINDING_DIR%\uniffi"
uniffi-bindgen generate --library "%OUT_DIR%\%ABI%\libconvert_core.so" --language kotlin --out-dir "%BINDING_DIR%" --crate convert_core
if errorlevel 1 echo [FAIL] uniffi-bindgen failed & exit /b 1

REM [5] Done
echo [5/5] Done
echo       .so       = %OUT_DIR%\%ABI%\libconvert_core.so
echo       binding   = %BINDING_DIR%
endlocal
