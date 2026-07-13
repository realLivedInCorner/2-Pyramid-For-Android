# 一次性手工 build + 复制 .so + 生成 UniFFI binding 的脚本
# 调用前提：Rust + cargo-ndk + uniffi-bindgen + Android NDK 全部装好

[CmdletBinding()]
param(
    [string]$ConvertCoreDir = "$PSScriptRoot\..\convert-core",
    [string]$AppDir = "$PSScriptRoot\..\2fa-android",
    [string]$Abi = "arm64-v8a",      # arm64-v8a / armeabi-v7a / x86_64
    [string]$CargoTargetDir = "$PSScriptRoot\..\convert-core\target\android"
)

$ErrorActionPreference = "Stop"

function Check-Prereq {
    foreach ($cmd in @("cargo", "rustup")) {
        if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
            throw "Missing prerequisite: $cmd. Install Rust from https://rustup.rs"
        }
    }
    if (-not (Get-Command "cargo-ndk" -ErrorAction SilentlyContinue)) {
        Write-Host "cargo-ndk not found. Install via: cargo install cargo-ndk" -ForegroundColor Yellow
    }
}

function Resolve-NdkRoot {
    if ($env:ANDROID_NDK_HOME) {
        return $env:ANDROID_NDK_HOME
    }
    if ($env:ANDROID_HOME) {
        $candidates = Get-ChildItem -Path "$env:ANDROID_HOME\ndk" -ErrorAction SilentlyContinue
        if ($candidates) {
            return ($candidates | Sort-Object Name -Descending | Select-Object -First 1).FullName
        }
    }
    throw "ANDROID_NDK_HOME / ANDROID_HOME not set, and no NDK found in SDK. Install NDK 27+ via SDK Manager."
}

function Resolve-UniffiTarget {
    param([string]$abi)
    switch ($abi) {
        "arm64-v8a"   { "aarch64-linux-android" }
        "armeabi-v7a" { "armv7-linux-androideabi" }
        "x86_64"      { "x86_64-linux-android" }
        default       { throw "Unknown ABI: $abi" }
    }
}

function Main {
    Check-Prereq

    $ndkRoot = Resolve-NdkRoot
    $target = Resolve-UniffiTarget $Abi

    # 确保 cargo-ndk 在 PATH
    if (-not (Get-Command "cargo-ndk" -ErrorAction SilentlyContinue)) {
        throw "Missing cargo-ndk. Install: cargo install cargo-ndk"
    }

    # 确保 uniffi-bindgen 在 PATH（一次性安装）
    if (-not (Get-Command "uniffi-bindgen" -ErrorAction SilentlyContinue)) {
        Write-Host "[1/5] uniffi-bindgen not found, installing (one-time)..." -ForegroundColor Cyan
        cargo install uniffi --features=cli --locked
    }

    Write-Host "[2/5] rustup target add ($target)..." -ForegroundColor Cyan
    & rustup target add $target

    Write-Host "[3/5] cargo ndk build for $Abi ($target)..." -ForegroundColor Cyan
    Push-Location $ConvertCoreDir
    & cargo ndk `
        --target $target `
        --platform 31 `
        -o "$AppDir\app\src\main\jniLibs\$Abi" `
        build --release `
        --manifest-path "$ConvertCoreDir\Cargo.toml" `
        --target-dir $CargoTargetDir
    Pop-Location

    $soPath = "$AppDir\app\src\main\jniLibs\$Abi\convert_core.so"
    if (-not (Test-Path $soPath)) {
        throw "cargo ndk did not produce expected .so at $soPath"
    }

    Write-Host "[4/5] Generate Kotlin UniFFI binding via uniffi-bindgen..." -ForegroundColor Cyan
    $bindingOut = "$AppDir\app\src\main\kotlin\com\twopyramid\twofa\uniffi"
    if (Test-Path $bindingOut) { Remove-Item -Recurse -Force $bindingOut }
    New-Item -ItemType Directory -Force -Path $bindingOut | Out-Null

    & uniffi-bindgen generate `
        --library $soPath `
        --language kotlin `
        --out-dir $bindingOut

    Write-Host "[5/5] Done." -ForegroundColor Green
    Write-Host "       .so       : $soPath"  -ForegroundColor Green
    Write-Host "       binding   : $bindingOut"  -ForegroundColor Green
}

Main
