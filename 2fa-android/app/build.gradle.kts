import org.gradle.api.tasks.Exec
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ── Rust crate 路径（默认相对路径；放到 ../convert-core）─────────────
val rustCrateDir: String =
    System.getenv("CONVERT_CORE_DIR")
        ?: rootProject.file("../convert-core").absolutePath

// v1.2.2 精简：arm64-only 砍掉 armeabi-v7a + x86_64 的 libjnidispatch.so。
// 用户的 Pixel 6 / 现代设备都是 arm64-v8a；旧设备/模拟器不在 v1.2 支持范围。
// CI 想要全 ABI：临时改回 ["arm64-v8a","armeabi-v7a","x86_64"]。
val abiList: List<String> = listOf("arm64-v8a")
val primaryAbi: String = "arm64-v8a"

// ── 自动 versionCode +1（每次 build 跑过就 +1，不用手动改）─────────────
// version.properties 在 2fa-android/ 根目录，存 VERSION_CODE。
// 第一次跑：读 0 → +1 → 写 1。
// 第 N 次：读 N-1 → +1 → 写 N。
// versionName 固定为正式版 2.0.0（versionCode 仍自动 +1）
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val baseVersionCode: Int = versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 0
val newVersionCode: Int = baseVersionCode + 1
versionProps.setProperty("VERSION_CODE", newVersionCode.toString())
versionPropsFile.outputStream().use { versionProps.store(it, "auto-bumped by gradle build") }
logger.lifecycle("[2FA] versionCode -> $newVersionCode")

android {
    namespace = "com.twopyramid.twofa"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.twopyramid.twofa"
        minSdk = 31
        targetSdk = 37
        versionCode = newVersionCode
        versionName = "2.0.0"

        ndk {
            abiFilters += abiList
        }
    }

    // ── 签名：用 Android SDK 默认 debug keystore（用户主目录 ~/.android/debug.keystore）──
    // 用户的设备上 install release build 不需要先卸载 debug，因为 debug 和 release 都用同一个
    // debug.keystore 签，签名相同才能覆盖安装。
    signingConfigs {
        create("releaseWithDebugKey") {
            val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // v1.2.3 开启 R8 + resource shrink → .dex 从 52MB 砍到 5-10MB。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("releaseWithDebugKey")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                // JNA desktop natives — Android 永远用不到（我们只用 com/sun/jna/android-aarch64/libjnidispatch.so）
                "com/sun/jna/aix-ppc/**",
                "com/sun/jna/aix-ppc64/**",
                "com/sun/jna/win32-*/**",
                "com/sun/jna/darwin-*/**",
            )
        }
    }
}

// ── Rust 编译任务 ──────────────────────────────────────────────────────
// 流程：preBuild → installRustTargets → buildRustAndroid
// `buildRustAndroid` 调 PowerShell 脚本（.ps1）走完整 pipeline：
//   1. `cargo ndk` 编译 → jniLibs/<abi>/convert_core.so
//   2. `uniffi-bindgen generate` 抽 metadata → Kotlin UniFFI binding
// 首次大约 5-10 分钟（image + zip + regex 编译），改 Kotlin 不触发 cargo
// incremental 即可秒过（cargo 自身 incremental），改 Rust 重新计 time。
//
// 前置要求：cargo + rustup + cargo-ndk + uniffi-bindgen + Android NDK 27+
//
// 注意：当任何一个工具链缺失时，PowerShell 脚本会 exit 1，Gradle build 失败。
// 用户也可以切换到手动模式：`PRIMARY_ANDROID_ABI=x86_64` env 改 ABI，
// 或者 `RUN_ANDROID_BUILD_RUST=true` 关掉（详见 README.md）。
val runRustBuild: Boolean = (System.getenv("SKIP_RUST_BUILD")?.toBoolean() != true)

if (runRustBuild) {
    tasks.register("installRustTargets") {
        group = "rust"
        description = "Ensure Rust Android target for primaryAbi is installed (skips if already in toolchain)"

        doLast {
            val userHome = System.getProperty("user.home")
                ?: error("user.home not set")
            val toolchainDir = file("$userHome/.rustup/toolchains/stable-x86_64-pc-windows-msvc/lib/rustlib")

            // 只装 primaryAbi 对应的 single target — cargo ndk 一次只编一个 ABI 的 .so
            val primaryTarget = when (primaryAbi) {
                "arm64-v8a"   -> "aarch64-linux-android"
                "armeabi-v7a" -> "armv7-linux-androideabi"
                "x86_64"      -> "x86_64-linux-android"
                else          -> "aarch64-linux-android"
            }

            if (file("$toolchainDir/$primaryTarget").isDirectory) {
                logger.lifecycle("[2FA] $primaryTarget present, skip rustup.")
                return@doLast
            }

            val cmd = "rustup target add $primaryTarget"
            logger.lifecycle("[2FA] $primaryTarget missing — running: $cmd")
            val proc = ProcessBuilder("cmd.exe", "/c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            logger.lifecycle(output)
            if (proc.exitValue() != 0) {
                logger.warn("[2FA] rustup target add failed (mirror 404?). Manually extract:")
                logger.warn("[2FA]   Invoke-WebRequest https://rsproxy.cn/dist/2025-12-11/rust-std-1.92.0-$primaryTarget.tar.xz -OutFile \$env:USERPROFILE\\Downloads\\$primaryTarget.tar.xz")
                logger.warn("[2FA]   tar -xJf \$env:USERPROFILE\\Downloads\\$primaryTarget.tar.xz --strip-components=2 -C \$env:USERPROFILE\\.rustup\\toolchains\\stable-x86_64-pc-windows-msvc\\lib\\rustlib\\$primaryTarget\\")
            }
        }
    }

    tasks.register<Exec>("buildRustAndroid") {
        group = "rust"
        description = "Compile Rust convert-core to .so + Kotlin UniFFI bindings (slow first time)"
        dependsOn("installRustTargets")

        doFirst {
            val soDir = file("src/main/jniLibs/$primaryAbi")
            soDir.mkdirs()
            logger.lifecycle("[2FA] Building Rust for $primaryAbi → $soDir")
        }

        commandLine(
            "cmd.exe", "/c", "$rootDir\\tools\\build-rust-android.bat", primaryAbi
        )
    }

    // 让 AS Run (Assemble + preBuild) 自动触发 Rust 编译
    afterEvaluate {
        tasks.findByName("preBuild")?.dependsOn("buildRustAndroid")
    }
} else {
    // 用户跳过 Rust 编译 → 占位 task，让 layout 一致
    tasks.register("buildRustAndroid") {
        group = "rust"
        description = "Stub (SKIP_RUST_BUILD=true)"
    }
}

// ── 依赖 ──────────────────────────────────────────────────────────────
dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.platform))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)
    implementation(libs.net.java.dev.jna)
}
