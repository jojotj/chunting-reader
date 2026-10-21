package com.chunting.reader.data.repo

import android.content.Context
import android.provider.MediaStore
import com.chunting.reader.data.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 直接按文件系统浏览手机目录（不依赖 MediaStore 索引）。
 * MediaStore 只在「读取音频时长 / 取 mediaId」时作为加速表使用。
 *
 * 设计要点：
 * - 列目录是懒加载的：进哪个目录才列哪个，绝不预扫全盘；
 * - 子目录的音频数量用「限节点递归探测」异步补，先出列表再逐步填数；
 * - 无权限的系统目录（Android/data、Android/obb）直接跳过。
 */
object FsBrowser {

    private val AUDIO_EXT = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "wav", "flac", "opus",
        "amr", "awb", "3gp", "mp4", "wma", "ape", "aiff", "aif", "mp2", "mid"
    )

    /** 无法读取、列了也没用的目录 */
    private val BLOCKED = setOf(
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/obb",
    )

    fun isAudioName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXT
    }

    private fun blocked(path: String): Boolean =
        BLOCKED.any { path == it || path.startsWith("$it/") }

    /** 可浏览的存储根：内部存储 + 外置卡 */
    fun roots(): List<DirNode> {
        val out = ArrayList<DirNode>()
        val internal = File("/storage/emulated/0")
        if (internal.isDirectory) {
            out += DirNode(
                path = internal.path,
                name = "手机存储",
                directCount = 0,
                deepCount = -1,          // -1 = 尚未统计
                deepDurationMs = 0L,
                childCount = internal.listFiles()?.count { it.isDirectory } ?: 0,
                parentPath = null,
                isRoot = true,
            )
        }
        File("/storage").listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead()) {
                out += DirNode(
                    path = f.path,
                    name = "存储卡 ${f.name}",
                    directCount = 0,
                    deepCount = -1,
                    deepDurationMs = 0L,
                    childCount = f.listFiles()?.count { it.isDirectory } ?: 0,
                    parentPath = null,
                    isRoot = true,
                )
            }
        }
        return out
    }

    /**
     * 立即返回某目录下的子目录列表。
     * 只 list 当前这一层，不进入子目录、不统计音频条数与时长——
     * 根目录下往往有几十上百个文件夹，逐个探音频会让列表卡在「统计中」。
     */
    fun listDirs(path: String): List<DirNode> {
        val dir = File(path)
        val kids = dir.listFiles() ?: return emptyList()
        val dirs = ArrayList<DirNode>()
        for (f in kids) {
            if (f.isDirectory) {
                if (f.name.startsWith(".") || blocked(f.path)) continue
                dirs += DirNode(
                    path = f.path,
                    name = f.name,
                    directCount = 0,
                    deepCount = 0,
                    deepDurationMs = 0L,
                    childCount = 0,
                    parentPath = path,
                    isRoot = false,
                )
            }
        }
        return dirs.sortedBy { it.name.lowercase() }
    }

    /** 直接位于某目录下的音频文件数 */
    fun directAudioCount(path: String): Int {
        val kids = File(path).listFiles() ?: return 0
        return kids.count { !it.isDirectory && isAudioName(it.name) }
    }

    /** 该目录及其所有子目录的音频总数与子目录数（限节点，防全盘卡死） */
    fun probe(path: String, maxNodes: Int = 1500): Pair<Int, Int> {
        var audio = 0
        var dirs = 0
        var nodes = 0
        val stack = ArrayDeque<File>()
        stack.addLast(File(path))
        while (stack.isNotEmpty()) {
            if (nodes++ > maxNodes) break
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (f in kids) {
                if (f.isDirectory) {
                    if (f.name.startsWith(".") || blocked(f.path)) continue
                    dirs++
                    stack.addLast(f)
                } else if (isAudioName(f.name)) {
                    audio++
                }
            }
        }
        return audio to dirs
    }

    /**
     * 递归收集某目录下全部音频（含子目录），按「目录层级 + 文件名」自然排序。
     * 时长与 mediaId 用 MediaStore 的索引表补齐；索引里没有的文件 mediaId = -1，
     * 播放时自动退回 file:// 由 ExoPlayer 直接解码。
     */
    suspend fun scanRecursive(ctx: Context, path: String): List<AudioTrack> =
        withContext(Dispatchers.IO) {
            val index = mediaIndex(ctx)
            val root = File(path)
            val out = ArrayList<AudioTrack>()
            val stack = ArrayDeque<File>()
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val d = stack.removeLast()
                val kids = d.listFiles() ?: continue
                for (f in kids) {
                    if (f.isDirectory) {
                        if (f.name.startsWith(".") || blocked(f.path)) continue
                        stack.addLast(f)
                    } else if (isAudioName(f.name)) {
                        val hit = index[f.path]
                        out += AudioTrack(
                            mediaId = hit?.id ?: -1L,
                            path = f.path,
                            fileName = f.name,
                            durationMs = hit?.durationMs ?: 0L,
                            sizeBytes = f.length(),
                            dateAddedSec = f.lastModified() / 1000L,
                        )
                    }
                }
            }
            out.sortWith(compareBy({ it.path }, { naturalKey(it.fileName) }))
            out
        }

    /** 同目录内按文件名里的数字排序（第2集 < 第10集） */
    private fun naturalKey(name: String): String =
        name.replace(Regex("(\\d+)"), { m -> m.value.padStart(8, '0') })

    private data class MediaHit(val id: Long, val durationMs: Long)

    /** path -> (mediaId, duration)，一次性构建，避免逐个文件查库 */
    private fun mediaIndex(ctx: Context): Map<String, MediaHit> {
        val map = HashMap<String, MediaHit>(4096)
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null, null
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val p = c.getString(iData) ?: continue
                    map[p] = MediaHit(c.getLong(iId), c.getLong(iDur).coerceAtLeast(0L))
                }
            }
        }
        return map
    }
}
