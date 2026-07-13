package com.twopyramid.twofa.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Storage Access Framework 工具：把 content:// URI 复制成 cache 目录里的真实文件。
 *
 * 为什么需要：Rust 端只能读文件系统，无法处理 SAF 的 content:// 虚拟 URI。
 * 复制到 cache 后给 Rust 用绝对路径访问。
 *
 * cache 不安全 / 不持久：用户清缓存时自动清；启动时加 SAF 文件应该重新选。
 * 真实项目如果需要持久化 zip，要用 `ContentResolver#openFileDescriptor` + `App Specific External Storage`。
 */
object SafUtils {

    /**
     * 把 content URI 复制到 cacheDir/<fileName>。返回 cache 路径。
     * @param ctx Android Context
     * @param uri SAF 选文件后返回的 content:// URI
     * @param prefix cache 下子目录名（建议每个 zip 一个子目录便于追溯）
     * @return 复制完成后的 File
     */
    suspend fun copyToCache(ctx: Context, uri: Uri, prefix: String = "zips"): File =
        withContext(Dispatchers.IO) {
            val fileName = queryDisplayName(ctx, uri) ?: "pack_${System.currentTimeMillis()}.zip"
            val safeName = fileName.replace("[^A-Za-z0-9._-]".toRegex(), "_")
            val destDir = File(ctx.cacheDir, prefix).apply { mkdirs() }
            val destFile = File(destDir, safeName)

            val input = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open URI: $uri")

            input.use { inStream ->
                FileOutputStream(destFile).use { outStream ->
                    inStream.copyTo(outStream, bufferSize = 64 * 1024)
                }
            }

            destFile
        }

    /**
     * 读 SAF 拿 DISPLAY_NAME，失败兜底用 URI 最后一段
     */
    private fun queryDisplayName(ctx: Context, uri: Uri): String? {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                return c.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    /**
     * 复制完后清理过期的 cache（保留最近 N 个）。
     * 用户在 service 启动或 home 打开时调一次。
     */
    fun cleanupOldCache(ctx: Context, keep: Int = 5, prefix: String = "zips") {
        val dir = File(ctx.cacheDir, prefix)
        if (!dir.exists()) return
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (files.size <= keep) return
        files.drop(keep).forEach { it.delete() }
    }

    // ── 输出侧 SAF 工具（v1.2.2：FIXED 模式支持 SAF）───────────────────

    /**
     * 把 SAF tree URI 解析成 Rust 可写的 staging 目录（cacheDir/converted）。
     *
     * 为什么不直接给 Rust 传 SAF path：Rust 只认文件系统路径，content:// 是虚拟 URI。
     * 解决：先 convert 到 staging（cacheDir），转换成功后再用 [copyToSafTree] 复制到用户选的 SAF 目录。
     */
    suspend fun resolveSafOutputDir(ctx: Context, @Suppress("UNUSED_PARAMETER") uri: Uri): File =
        withContext(Dispatchers.IO) {
            File(ctx.cacheDir, "converted").apply { mkdirs() }
        }

    /**
     * 检查 SAF tree URI 是否仍然有效（用户可能在系统设置里撤销了权限）。
     */
    fun isSafTreeAccessible(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    } catch (_: Exception) {
        false
    }

    /**
     * 把 staging 的 zip 复制到 SAF tree 下，文件名 = file.name。
     * 同名会自动加 (1)、(2) 后缀（DocumentsContract.createDocument 行为）。
     *
     * @return 复制成功 → SAF 子文件 URI（content:// 形式）；失败 → null
     */
    suspend fun copyToSafTree(ctx: Context, treeUri: Uri, file: File): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return@withContext null
                val target = tree.createFile("application/zip", file.name) ?: return@withContext null
                val ok = ctx.contentResolver.openOutputStream(target.uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out, bufferSize = 64 * 1024) }
                    true
                } ?: false
                if (ok) target.uri else null
            } catch (e: Exception) {
                android.util.Log.e("SafUtils", "copyToSafTree failed: ${e.message}", e)
                null
            }
        }
}
