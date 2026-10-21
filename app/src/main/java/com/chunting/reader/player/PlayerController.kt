package com.chunting.reader.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chunting.reader.data.model.AudioTrack
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放控制单例。UI 通过它连接 PlaybackService。
 */
@UnstableApi
object PlayerController {

    private var controller: MediaController? = null
    private var appCtx: Context? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private var skipSeconds = 30

    /** 当前播放队列，用于把 mediaItem 还原成完整 AudioTrack（含真实路径） */
    private var queue: List<AudioTrack> = emptyList()

    /** 冷启动恢复态：只记录「上次曲目 + 位置」，队列尚未建立，等用户点击再建 */
    private var resumePath: String? = null
    private var resumePosMs: Long = 0L

    /** 控制器尚未连接时的待执行播放动作 */
    private var pendingPlay: (() -> Unit)? = null

    /** 是否已有可播放队列（冷启动恢复态下为 false） */
    fun hasQueue(): Boolean = (controller?.mediaItemCount ?: 0) > 0

    /** 当前曲目在播放队列中的序号（0-based），无队列返回 -1 */
    fun currentIndex(): Int = controller?.currentMediaItemIndex ?: -1

    /** 当前播放队列总集数 */
    fun queueSize(): Int = queue.size

    /**
     * 冷启动恢复：把上次的曲目/位置回填到 UI（不建队列、不播放）。
     * 用户点播放条时再真正加载队列并从这个位置续播。
     */
    fun restorePreview(track: AudioTrack, positionMs: Long) {
        if (_currentTrack.value != null || hasQueue()) return
        _currentTrack.value = track
        _positionMs.value = positionMs.coerceAtLeast(0L)
        _durationMs.value = track.durationMs
        resumePath = track.path
        resumePosMs = positionMs.coerceAtLeast(0L)
    }

    /** 取走某曲的待续播位置（取走后即清除） */
    fun takeResumePosition(path: String): Long? {
        if (resumePath != path) return null
        val p = resumePosMs
        resumePath = null
        resumePosMs = 0L
        return p
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            if (item == null) {
                _currentTrack.value = null
                return
            }
            resumePath = null
            _positionMs.value = (controller?.currentPosition ?: 0L).coerceAtLeast(0L)
            val idx = controller?.currentMediaItemIndex ?: -1
            val fromQueue = queue.getOrNull(idx)
            _currentTrack.value = fromQueue ?: AudioTrack(
                mediaId = item.mediaId.toLongOrNull() ?: -1L,
                path = item.localConfiguration?.uri?.toString() ?: "",
                fileName = item.mediaMetadata.title?.toString() ?: "",
                durationMs = 0L,
                sizeBytes = 0L,
                dateAddedSec = 0L,
            )
        }
        override fun onPlaybackStateChanged(state: Int) {
            _durationMs.value = controller?.duration?.takeIf { it > 0 } ?: 0L
        }
    }

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(listener)
            _connected.value = controller != null
            _speed.value = controller?.playbackParameters?.speed ?: 1.0f
            pendingPlay?.let { act ->
                pendingPlay = null
                if (controller != null) act()
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _connected.value = false
        appCtx = null
    }

    /** 播放一个文件夹的列表；startPositionMs 用于从上次进度续播 */
    fun playList(
        tracks: List<AudioTrack>,
        startIndex: Int = 0,
        speed: Float = 1.0f,
        startPositionMs: Long = 0L,
    ) {
        if (tracks.isEmpty()) return
        val c = controller
        if (c == null) {
            pendingPlay = { playList(tracks, startIndex, speed, startPositionMs) }
            return
        }
        queue = tracks
        resumePath = null
        resumePosMs = 0L
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setMediaId(t.mediaId.toString())
                .setUri(t.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.fileName)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
        c.setMediaItems(
            items,
            startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            startPositionMs.coerceAtLeast(0L)
        )
        c.prepare()
        setSpeed(speed)
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun play() = controller?.play()
    fun pause() = controller?.pause()

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(ms: Long) = controller?.seekTo(ms.coerceAtLeast(0L))
    fun forward() = controller?.seekTo((controller?.currentPosition ?: 0L) + skipSeconds * 1000L)
    fun rewind() = controller?.seekTo(((controller?.currentPosition ?: 0L) - skipSeconds * 1000L).coerceAtLeast(0L))

    fun setSkipSeconds(s: Int) { skipSeconds = s }

    fun setSpeed(v: Float) {
        val c = controller ?: return
        runCatching { c.setPlaybackSpeed(v) }
        _speed.value = c.playbackParameters?.speed ?: v
    }

    fun refreshPosition() {
        val c = controller ?: return
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        _durationMs.value = c.duration.takeIf { it > 0 } ?: _durationMs.value
    }

    fun stopAndClear() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        queue = emptyList()
        resumePath = null
        resumePosMs = 0L
        _currentTrack.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
    }

    /** 只清空 UI 播放态（例如正在播放的书被移出书架后） */
    fun clearCurrent() {
        val c = controller
        runCatching { c?.stop(); c?.clearMediaItems() }
        queue = emptyList()
        resumePath = null
        resumePosMs = 0L
        pendingPlay = null
        _currentTrack.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
    }

    /** 某路径是否属于给定目录（含子目录） */
    fun isPlayingUnder(bases: List<String>): Boolean {
        val p = _currentTrack.value?.path ?: return false
        return bases.any { b -> p == b || p.startsWith("$b/") }
    }
}
