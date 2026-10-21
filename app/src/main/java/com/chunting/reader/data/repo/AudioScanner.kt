package com.chunting.reader.data.repo

import android.content.Context
import android.provider.MediaStore
import com.chunting.reader.data.model.AudioTrack
import com.chunting.reader.data.model.ScannedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 MediaStore 扫描本机音频。
 * 只认音频文件（PDF 等自动排除），按所在文件夹分组。
 */
object AudioScanner {

    private val AUDIO_EXT = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "amr", "awb", "3gp", "mp4"
    )

    private val PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
    )

    private fun isAudio(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXT
    }

    /** 扫描全部音频，按文件夹分组（文件夹按名称排序） */
    suspend fun scanAll(ctx: Context): List<ScannedFolder> = withContext(Dispatchers.IO) {
        val tracks = query(ctx, null, null)
        group(tracks)
    }

    /** 扫描全部音频的原始列表（用于构建目录树） */
    suspend fun scanAllTracks(ctx: Context): List<AudioTrack> = withContext(Dispatchers.IO) {
        query(ctx, null, null)
    }

    /** 扫描指定文件夹下的音频（用于加入书架时按路径挑选） */
    suspend fun scanFolder(ctx: Context, folderPath: String): List<AudioTrack> =
        withContext(Dispatchers.IO) {
            val p = folderPath.trimEnd('/')
            query(ctx, "${MediaStore.Audio.Media.DATA} LIKE ?", arrayOf("$p/%"))
                .sortedBy { it.fileName }
        }

    /** 按关键字过滤文件夹（支持手动输入路径片段） */
    suspend fun searchFolders(ctx: Context, keyword: String): List<ScannedFolder> =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext emptyList()
            val k = keyword.trim()
            val byPath = query(ctx, "${MediaStore.Audio.Media.DATA} LIKE ?", arrayOf("%$k%"))
            if (byPath.isNotEmpty()) return@withContext group(byPath)
            // 路径匹配不到时，退回按文件夹名模糊匹配
            group(query(ctx, null, null)).filter { it.name.contains(k, true) }
        }

    private fun query(ctx: Context, sel: String?, args: Array<String>?): List<AudioTrack> {
        val out = ArrayList<AudioTrack>()
        val resolver = ctx.contentResolver
        val cursor = resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, PROJECTION, sel, args,
            "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
        ) ?: return out
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                if (!isAudio(name)) continue
                val path = c.getString(iData) ?: continue
                out += AudioTrack(
                    mediaId = c.getLong(iId),
                    path = path,
                    fileName = name,
                    durationMs = c.getLong(iDur).coerceAtLeast(0L),
                    sizeBytes = c.getLong(iSize),
                    dateAddedSec = c.getLong(iDate),
                )
            }
        }
        return out
    }

    private fun group(tracks: List<AudioTrack>): List<ScannedFolder> {
        val map = LinkedHashMap<String, MutableList<AudioTrack>>()
        for (t in tracks) {
            val dir = t.folderPath
            map.getOrPut(dir) { ArrayList() }.add(t)
        }
        return map.entries
            .map { (dir, list) ->
                ScannedFolder(
                    path = dir,
                    name = dir.substringAfterLast('/'),
                    trackCount = list.size,
                    totalDurationMs = list.sumOf { it.durationMs },
                    sampleTracks = list.sortedBy { it.fileName },
                )
            }
            .sortedBy { it.name }
    }
}
