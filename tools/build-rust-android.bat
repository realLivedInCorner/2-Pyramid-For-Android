@echo off
REM ===============================================================
REM 2FA Rust build wrapper (pure ASCII, no multi-line if blocks)
REM Usage: build-rust-android.bat arm64-v8a
REM
REM Optional env (all resolved with sensible fallbacks; the Gradle
REM buildRustAndroid Exec task injects ANDROID_HOME/ANDROID_SDK_ROOT/
REM ANDROID_NDK_HOME/ANDROID_NDK_VERSION explicitly):
REM   ANDROID_HOME / ANDROID_SDK_ROOT : Android SDK root
REM                                     (else: local.properties sdk.dir,
REM                                     else: unset -> NDK must come from ANDROID_NDK_HOME)
REM   ANDROID_NDK_HOME                : NDK root
REM                                     (else: <SDK>\ndk\<ANDROID_NDK_VERSION>,
REM                                     else: first <SDK>\ndk\<27.3|27.2|any> found)
REM   ANDROID_NDK_VERSION             : preferred NDK version (default 27.3.13750724)
REM   DRY_RUN=1                       : resolve paths + print, do NOT compile
REM ===============================================================
setlocal

set "ABI=%~1"
if "%ABI%"=="" set "ABI=arm64-v8a"

set "ROOT_DIR=%~dp0.."
set "RUST_CRATE_DIR=%ROOT_DIR%\convert-core"
set "APP_DIR=%ROOT_DIR%\2fa-android"
REM NOTE: the Gradle app project lives in 2fa-android\app\ (jniLibs and the Kotlin
REM sources are under that directory). The pre-L1 script pointed OUT_DIR at
REM 2fa-android\src\main\jniLibs (missing "app") so the freshly built .so was never
REM picked up by AGP; fixed at L1.
set "APP_PROJECT_DIR=%APP_DIR%\app"
set "OUT_DIR=%APP_PROJECT_DIR%\src\main\jniLibs\%ABI%"
REM BINDING_DIR is the uniffi-bindgen output dir (a *child* of com/twopyramid/twofa/).
REM Do NOT point it at com/twopyramid/twofa/ directly — `rmdir /s /q` below would
REM wipe all sibling packages (ui, service, queue, native, etc.). The binding
REM generation only needs to refresh files under the `uniffi/` subdir.
set "BINDING_DIR=%APP_PROJECT_DIR%\src\main\kotlin\com\twopyramid\twofa\uniffi"
set "CARGO_TARGET_DIR=%RUST_CRATE_DIR%\target\android"

REM Translate ABI to cargo target triple
set "TARGET_TRIPLE="
if /I "%ABI%"=="arm64-v8a"   set "TARGET_TRIPLE=aarch64-linux-android"
if /I "%ABI%"=="armeabi-v7a" set "TARGET_TRIPLE=armv7-linux-androideabi"
if /I "%ABI%"=="x86_64"      set "TARGET_TRIPLE=x86_64-linux-android"
if /I "%ABI%"=="i686"        set "TARGET_TRIPLE=i686-linux-android"
if "%TARGET_TRIPLE%"=="" echo [FAIL] Unknown ABI "%ABI%" & exit /b 1

echo 2FA Rust build start. ABI=%ABI% target=%TARGET_TRIPLE%

REM ===============================================================
REM [0] Resolve Android SDK root (needed to locate an NDK)
REM     Priority: ANDROID_HOME > ANDROID_SDK_ROOT > local.properties sdk.dir
REM     local.properties escapes `\` and `:` (e.g. sdk.dir=D\:\\Android), so
REM     unescape them. Kept outside any ( ) block: %VAR% would expand early.
REM ===============================================================
if "%ANDROID_HOME%"=="" if not "%ANDROID_SDK_ROOT%"=="" set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not "%ANDROID_HOME%"=="" goto :sdk_clean
if not exist "%APP_DIR%\local.properties" goto :sdk_clean
for /f "usebackq tokens=1,* delims==" %%A in ("%APP_DIR%\local.properties") do if /I "%%A"=="sdk.dir" set "ANDROID_HOME=%%B"
:sdk_clean
if "%ANDROID_HOME%"=="" goto :sdk_done
set "ANDROID_HOME=%ANDROID_HOME:\:=:%"
set "ANDROID_HOME=%ANDROID_HOME:\\=\%"
if not exist "%ANDROID_HOME%" set "ANDROID_HOME="
:sdk_done
echo       ANDROID_HOME=%ANDROID_HOME%

REM ===============================================================
REM [1] prereqs
REM ===============================================================
where cargo >nul 2>&1
if errorlevel 1 echo [FAIL] cargo not found & exit /b 1
where cargo-ndk >nul 2>&1
if errorlevel 1 echo [FAIL] cargo-ndk not found. Run cargo install cargo-ndk & exit /b 1
if "%DRY_RUN%"=="1" goto :prereq_bindgen_done
where uniffi-bindgen >nul 2>&1
if errorlevel 1 (
    echo [1/5] Installing uniffi-bindgen, one-time, may take 2-5 min
    cargo install uniffi --features=cli --locked
    if errorlevel 1 echo [FAIL] cargo install uniffi failed & exit /b 1
)
:prereq_bindgen_done

REM ===============================================================
REM [2] rustup target add (offline-safe)
REM     The aarch64-linux-android rust-std may be staged *manually* into the
REM     rustup toolchain (historically via the tuna-404 workaround), in which
REM     case `rustup target list --installed` does NOT list it but rustc can
REM     still find it. Probing `rustc --print target-libdir` avoids a pointless
REM     (and network-dependent) `rustup target add`, and avoids selecting the
REM     local RUSTC wrapper whose staging dir no longer exists.
REM ===============================================================
set "TARGET_LIBDIR="
for /f "delims=" %%i in ('rustc --print target-libdir --target %TARGET_TRIPLE% 2^>nul') do set "TARGET_LIBDIR=%%i"
if "%TARGET_LIBDIR%"=="" goto :rustup_add
if not exist "%TARGET_LIBDIR%" goto :rustup_add
echo [2/5] rust-std for %TARGET_TRIPLE% already staged, skip rustup: %TARGET_LIBDIR%
set "USE_WRAPPER=0"
goto :rustup_done
:rustup_add
echo [2/5] rustup target add %TARGET_TRIPLE%
rustup target add %TARGET_TRIPLE%
if errorlevel 1 (
    echo       [WARN] rustup target add failed - using local wrapper
    set "USE_WRAPPER=1"
) else (
    set "USE_WRAPPER=0"
)
:rustup_done

REM ===============================================================
REM [3] Resolve NDK root
REM     Priority: ANDROID_NDK_HOME > <SDK>\ndk\<version> > 27.3 > 27.2 > any
REM ===============================================================
if "%ANDROID_NDK_VERSION%"=="" set "ANDROID_NDK_VERSION=27.3.13750724"
if not "%ANDROID_NDK_HOME%"=="" if not exist "%ANDROID_NDK_HOME%\source.properties" set "ANDROID_NDK_HOME="
set "NDK_ROOT="
if not "%ANDROID_NDK_HOME%"=="" set "NDK_ROOT=%ANDROID_NDK_HOME%"
if "%NDK_ROOT%"=="" if not "%ANDROID_HOME%"=="" if exist "%ANDROID_HOME%\ndk\%ANDROID_NDK_VERSION%" set "NDK_ROOT=%ANDROID_HOME%\ndk\%ANDROID_NDK_VERSION%"
if "%NDK_ROOT%"=="" if not "%ANDROID_HOME%"=="" if exist "%ANDROID_HOME%\ndk\27.3.13750724" set "NDK_ROOT=%ANDROID_HOME%\ndk\27.3.13750724"
if "%NDK_ROOT%"=="" if not "%ANDROID_HOME%"=="" if exist "%ANDROID_HOME%\ndk\27.2.12479018" set "NDK_ROOT=%ANDROID_HOME%\ndk\27.2.12479018"
if "%NDK_ROOT%"=="" if not "%ANDROID_HOME%"=="" for /d %%D in ("%ANDROID_HOME%\ndk\*") do if not defined NDK_ROOT set "NDK_ROOT=%%D"
if "%NDK_ROOT%"=="" echo [FAIL] ANDROID_NDK_HOME not set and no NDK 27.x found under %%ANDROID_HOME%%\ndk. Set ANDROID_NDK_HOME. & exit /b 1
set "ANDROID_NDK_HOME=%NDK_ROOT%"
echo [3/5] ANDROID_NDK_HOME=%ANDROID_NDK_HOME%

if "%DRY_RUN%"=="1" (
    echo [DRY_RUN] resolved config, skipping compile:
    echo       ROOT_DIR=%ROOT_DIR%
    echo       RUST_CRATE_DIR=%RUST_CRATE_DIR%
    echo       OUT_DIR=%OUT_DIR%
    echo       BINDING_DIR=%BINDING_DIR%
    echo       CARGO_TARGET_DIR=%CARGO_TARGET_DIR%
    echo       ANDROID_HOME=%ANDROID_HOME%
    echo       ANDROID_NDK_HOME=%ANDROID_NDK_HOME%
    echo       TARGET_LIBDIR=%TARGET_LIBDIR%
    echo       USE_WRAPPER=%USE_WRAPPER%
    exit /b 0
)

REM ===============================================================
REM [4] cargo ndk build (slow first time)
REM ===============================================================
echo [3/5] cargo ndk build, this may take 5-10 minutes first time
pushd "%RUST_CRATE_DIR%"
if "%USE_WRAPPER%"=="1" (
    if not exist "%~dp0rustc-aarch64-wrapper.bat" echo [FAIL] wrapper missing: %~dp0rustc-aarch64-wrapper.bat & popd & exit /b 1
    set "RUSTC=%~dp0rustc-aarch64-wrapper.bat"
)
if "%USE_WRAPPER%"=="1" echo       RUSTC=%RUSTC%  (redirects --sysroot to local rust-std staging)
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

REM ===============================================================
REM [5] uniffi-bindgen — refresh ONLY the uniffi/ subdir, never the parent.
REM     uniffi-bindgen 0.32 runs `cargo metadata` internally even in --library
REM     mode, so the cwd must be inside a cargo project -> pushd the crate dir.
REM ===============================================================
echo [4/5] uniffi-bindgen generate
if exist "%BINDING_DIR%\uniffi" rmdir /s /q "%BINDING_DIR%\uniffi"
pushd "%RUST_CRATE_DIR%"
uniffi-bindgen generate --library "%OUT_DIR%\%ABI%\libconvert_core.so" --language kotlin --out-dir "%BINDING_DIR%" --crate convert_core
set "BINDGEN_EXIT=%errorlevel%"
popd
if not "%BINDGEN_EXIT%"=="0" echo [FAIL] uniffi-bindgen failed & exit /b %BINDGEN_EXIT%

REM cargo-ndk also leaves the nested <OUT_DIR>\<ABI>\libconvert_core.so behind; AGP only
REM reads jniLibs/<abi>/*.so, so delete the nested dir to keep jniLibs (and the APK) clean.
if exist "%OUT_DIR%\%ABI%" rmdir /s /q "%OUT_DIR%\%ABI%"

echo [5/5] Done
echo       .so       = %OUT_DIR%\libconvert_core.so
echo       binding   = %BINDING_DIR%
endlocal
