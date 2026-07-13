# =============================================================
# 2-Pyramid for Android — ProGuard / R8 rules
# =============================================================
# v1.2.3 启用 R8 (isMinifyEnabled = true) + resource shrinker。
# 规则目的：
#   1. 保留 UniFFI 生成的 JNA bridge + Rust 端 callback 接口
#   2. 保留我们自己通过反射访问的 Compose 工具代码
#   3. 移除 JNA 内置的 desktop native libs（Android 不用）
# -------------------------------------------------------------

# ── 1. UniFFI / JNA bridge 必须保留 ─────────────────────────────
# UniFFI 生成的 binding 在 package `uniffi.convert_core`（不是 com.twopyramid.twofa.uniffi.* ，
# 目录是双 uniffi 但 package 只一个 uniffi）。R8 按 class name 匹配，所以必须用 `uniffi.**`。
# `external fun` 里的方法名（如 uniffi_convert_core_fn_func_convert_core_version）
# 必须在 R8 之后还叫这个名，JNA 才能在 .so 里找到对应符号。
-keep class uniffi.** { *; }
-keepclassmembers class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
-keepclassmembers class * implements com.sun.jna.Library {
    *;
}
-keepclassmembers class * implements com.sun.jna.Callback {
    *;
}

# UniFFI 用的 RustBuffer / ForeignFuture 辅助类
-keep class com.sun.jna.Native$* { *; }
-keep class com.sun.jna.Pointer { *; }
-keep class com.sun.jna.Structure { *; }
-keep class com.sun.jna.IntegerType { *; }

# ── 2. JNA desktop native 资源裁掉（Android 只用 libjnidispatch.so）────
# JNA jar 里塞了 Windows / Mac / Linux desktop / AIX 的 native libs，
# Android 永远用不到，R8 资源裁剪也只会按引用图保留。
# 用 -assumenosideeffects 配合 resource shrink 删 com/sun/jna/*desktop paths。
# 实际资源裁剪由 isShrinkResources 处理；这条只防 R8 把 .so 文件当 keep 资源拉回来。
-assumenosideeffects class com.sun.jna.Native {
    public static ** loadLibrary(java.lang.String);
}

# ── 3. Compose 工具类（@Preview、@Composable 内反射用） ──────────
-keep class androidx.compose.ui.tooling.preview.** { *; }
-keep class androidx.compose.runtime.** { *; }

# ── 4. 我们的 Application + Service + Activity 全保 ─────────────
-keep class com.twopyramid.twofa.TwoFAApp { *; }
-keep class com.twopyramid.twofa.service.ZipQueueService { *; }
-keep class com.twopyramid.twofa.ui.MainActivity { *; }

# ── 5. Kotlin Metadata（debug 崩溃栈需要） ──────────────────────
# Compose 的 Layout Inspector / Preview 依赖 Kotlin metadata，保留即可。
# release 给用户测试用，metadata 不是必要，但删了反而容易出诡异问题，留着安全。
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── 6. 其它常见坑 ─────────────────────────────────────────────
# Kotlinx coroutines 内部用 AtomicReferenceFieldUpdater 等反射。
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.Metadata { *; }
# OkHttp / Okio 不在我们项目里用，但 AGP 默认会保留，先不动。
# AndroidX lifecycle service 用 Service 反射，service 子类已用 -keep。

# ── 7. 不要打印"找不到规则"警告 ──────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ── 8. JNA 的 desktop-only 反射引用（Android 没有 java.awt）──────────
# JNA jar 的 Native$AWT 内部用反射引用 java.awt.{Component,Window,...}，
# 那些是 desktop 用的，Android 永远走不到。R8 默认当 missing class 报错。
-dontwarn java.awt.**
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
