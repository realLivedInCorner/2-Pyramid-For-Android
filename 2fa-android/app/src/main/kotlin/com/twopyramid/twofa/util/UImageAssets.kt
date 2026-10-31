package com.twopyramid.twofa.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * UImage 原版素材（桌面 `src-tauri/UImage/` 的 80 张 PNG，16 个分类目录）随 APK 以
 * `assets/UImage/` 下分发；首次启动（或素材版本变化）时同步解压到 `filesDir/UImage`。
 *
 * 为什么需要：convert-core 的 `image_utils::get_uimage_path()` 依赖这个目录来跑
 * 「用原版素材生成/叠加贴图」的任务（tipped arrow、smithing overlay、snow bucket、
 * overlay icons、brewing stand、villager2、horse 等）；没有素材时这些任务会跳过。
 *
 * 注意：
 *  - 解压必须发生在 `RustNative.initialize(..., uimageDir)` 之前（Application.onCreate 内）。
 *  - 80 个文件 / 约 0.56 MB，同步复制开销很小；用 `.asset_version` 标记避免每次启动重解压。
 *  - 素材放 assets 而不是直接读 assets 路径，是因为 Rust 侧只接受文件系统路径。
 */
object UImageAssets {

    private const val TAG = "UImageAssets"
    private const val ASSET_ROOT = "UImage"
    private const val MARKER = ".asset_version"

    /** 素材集合版本：UImage 内容变化时 +1 强制重新解压。 */
    private const val ASSET_VERSION = "1"

    /**
     * 确保 [targetDir] 内是可用的 UImage 素材。
     *
     * @return true = 目录内有素材（已有或解压成功）；false = 解压失败（相关任务将跳过）
     */
    fun ensureExtracted(context: Context, targetDir: File): Boolean {
        return try {
            val marker = File(targetDir, MARKER)
            if (marker.isFile && marker.readText().trim() == ASSET_VERSION && hasAnyAsset(targetDir)) {
                Log.i(TAG, "UImage assets up to date: ${targetDir.absolutePath}")
                return true
            }

            // 首次启动或素材版本变化：清空后重新解压，避免旧素材残留。
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val assets = context.assets
            var copied = 0
            fun copyDir(assetPath: String, outDir: File) {
                outDir.mkdirs()
                val children = assets.list(assetPath) ?: return
                for (name in children) {
                    val childAsset = "$assetPath/$name"
                    val grandChildren = assets.list(childAsset)
                    if (grandChildren != null && grandChildren.isNotEmpty()) {
                        copyDir(childAsset, File(outDir, name))
                    } else {
                        assets.open(childAsset).use { input ->
                            File(outDir, name).outputStream().use { output -> input.copyTo(output) }
                        }
                        copied++
                    }
                }
            }
            copyDir(ASSET_ROOT, targetDir)

            if (copied > 0) {
                marker.writeText(ASSET_VERSION)
                Log.i(TAG, "UImage assets extracted: $copied files -> ${targetDir.absolutePath}")
            } else {
                Log.w(TAG, "UImage assets empty (assets/$ASSET_ROOT missing?)")
            }
            copied > 0
        } catch (t: Throwable) {
            Log.e(TAG, "UImage asset extraction failed; UImage-dependent tasks will be skipped", t)
            false
        }
    }

    private fun hasAnyAsset(dir: File): Boolean =
        dir.listFiles()?.any { f -> f.isFile || (f.isDirectory && hasAnyAsset(f)) } == true
}
