package com.chunting.reader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 自定义封面存储。
 *
 * 只在本机私有目录放一份裁好的 2:3 JPEG（约 600x900，与书架卡片同比例），不依赖原图权限，
 * 卸载/清数据即自然消失，也不会上传到任何地方。
 */
object CoverStore {

    private const val DIR = "covers"
    private const val OUT_W = 600
    private const val OUT_H = 900
    private const val MAX_DECODE = 1600
    private const val CACHE_LIMIT = 48

    /** 版本号变化时让所有封面 Composable 重新取图 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > CACHE_LIMIT
    }

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 统一路径形态：去掉首尾空白与结尾斜杠，保证各页面 key 一致 */
    private fun norm(folderPath: String): String = folderPath.trim().trimEnd('/')

    private fun key(folderPath: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(norm(folderPath).toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        bytes.forEach { sb.append("%02x".format(it)) }
        return sb.toString()
    }

    fun fileOf(ctx: Context, folderPath: String): File = File(dir(ctx), key(folderPath) + ".jpg")

    fun has(ctx: Context, folderPath: String): Boolean =
        folderPath.isNotEmpty() && fileOf(ctx, folderPath).exists()

    fun load(ctx: Context, folderPath: String): Bitmap? {
        if (folderPath.isEmpty()) return null
        val k = key(folderPath)
        synchronized(cache) { cache[k]?.let { return it } }
        val f = fileOf(ctx, folderPath)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath, opts) }.getOrNull()
        if (bmp == null) return null
        synchronized(cache) { cache[k] = bmp }
        return bmp
    }

    /**
     * 把用户从相册选的图裁成 2:3 竖版封面保存。
     * 先按 EXIF 转正，再居中裁切，避免横拍照片被压扁。
     */
    fun saveFromUri(ctx: Context, folderPath: String, uri: Uri): Boolean {
        if (folderPath.isEmpty()) return false
        var tmp: File? = null
        var src: Bitmap? = null
        var out: Bitmap? = null
        return try {
            // ① 先把流落到本机临时文件，EXIF 只有文件路径才读得稳（兼容 API 23）
            tmp = File.createTempFile("cover_in_", ".tmp", ctx.cacheDir)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return false

            val rotation = readRotationDegrees(tmp)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            src = BitmapFactory.decodeFile(tmp.absolutePath, opts) ?: return false
            val upright = if (rotation == 0) src else rotate(src, rotation)
            out = crop3x4Scale(upright, OUT_W, OUT_H)

            val dst = fileOf(ctx, folderPath)
            FileOutputStream(dst).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            synchronized(cache) { cache.remove(key(folderPath)) }
            _version.value = _version.value + 1
            true
        } catch (t: Throwable) {
            android.util.Log.w("CoverStore", "saveFromUri failed: $uri", t)
            false
        } finally {
            if (out != null && out !== src) runCatching { out.recycle() }
            if (src != null && (out == null || src !== out)) runCatching { src.recycle() }
            tmp?.let { runCatching { it.delete() } }
        }
    }

    fun delete(ctx: Context, folderPath: String): Boolean {
        if (folderPath.isEmpty()) return false
        synchronized(cache) { cache.remove(key(folderPath)) }
        _version.value = _version.value + 1
        return runCatching { fileOf(ctx, folderPath).delete() }.getOrDefault(false)
    }

    /**
     * 批量还原：把多本文件夹的自定义封面一起删掉，回落到原来的生成式封面。
     * 没换过封面的文件夹只是删不掉，不影响其它本；真的删掉了才递增版本，避免无谓重组。
     */
    fun reset(ctx: Context, folderPaths: Collection<String>): Int {
        var removed = 0
        folderPaths.forEach { p ->
            if (p.isEmpty()) return@forEach
            synchronized(cache) { cache.remove(key(p)) }
            if (runCatching { fileOf(ctx, p).delete() }.getOrDefault(false)) removed++
        }
        if (removed > 0) _version.value = _version.value + 1
        return removed
    }

    /* ---------------- 内部工具 ---------------- */

    private fun readRotationDegrees(file: File): Int = runCatching {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun rotate(src: Bitmap, degrees: Int): Bitmap = runCatching {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }.getOrDefault(src)

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        var long = maxOf(w, h)
        while (long / 2 >= target) {
            long /= 2
            s *= 2
        }
        return s
    }

    /** 居中裁成 2:3 后缩放到目标尺寸 */
    private fun crop3x4Scale(src: Bitmap, outW: Int, outH: Int): Bitmap {
        val targetRatio = outW.toFloat() / outH.toFloat()
        val w = src.width
        val h = src.height
        val cropW: Int
        val cropH: Int
        if (w.toFloat() / h.toFloat() > targetRatio) {
            cropH = h
            cropW = (h * targetRatio).toInt().coerceAtLeast(1)
        } else {
            cropW = w
            cropH = (w / targetRatio).toInt().coerceAtLeast(1)
        }
        val x = ((w - cropW) / 2).coerceAtLeast(0)
        val y = ((h - cropH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
        if (cropped !== src && cropped !== scaled) runCatching { cropped.recycle() }
        return scaled
    }
}
