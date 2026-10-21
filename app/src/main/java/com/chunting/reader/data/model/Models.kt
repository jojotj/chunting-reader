package com.chunting.reader.data.model

/** 一条音频（对应磁盘上的一个文件） */
data class AudioTrack(
    val mediaId: Long,
    val path: String,          // 绝对路径
    val fileName: String,      // 真实文件名（含扩展名）
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
) {
    val folderPath: String get() = path.substringBeforeLast('/', "")
    /** 供 ExoPlayer 使用的 URI：媒体库有记录走 content://，否则回退 file:// */
    val contentUri: String
        get() = if (mediaId > 0) {
            "content://media/external/audio/media/$mediaId"
        } else {
            "file://" + java.net.URLEncoder.encode(path, "UTF-8")
                .replace("%2F", "/").replace("+", "%20")
        }
}

/** 扫描到的文件夹（尚未加入书架） */
data class ScannedFolder(
    val path: String,
    val name: String,              // 文件夹名
    val trackCount: Int,
    val totalDurationMs: Long,
    val sampleTracks: List<AudioTrack>,
)

/** 书架中的文件夹 */
data class ShelfFolder(
    val path: String,
    val name: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val addedAt: Long,
    val lastPlayedAt: Long,
    val progress: Float,           // 0..1，已播完的音频占比
)
