package com.chunting.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chunting.reader.data.Settings
import com.chunting.reader.data.SettingsStore
import com.chunting.reader.data.db.AppDatabase
import com.chunting.reader.data.db.ShelfFolderEntity
import com.chunting.reader.data.db.TrackProgressEntity
import com.chunting.reader.data.model.AudioTrack
import com.chunting.reader.data.model.ScannedFolder
import com.chunting.reader.data.model.ShelfFolder
import com.chunting.reader.data.repo.AudioScanner
import com.chunting.reader.data.repo.DirNode
import com.chunting.reader.data.repo.FsBrowser
import com.chunting.reader.player.PlayerController
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 文件夹续播提示：上次在该文件夹播到哪一首、哪个位置 */
data class ResumeHint(
    val trackPath: String,
    val trackIndex: Int,
    val fileName: String,
    val positionMs: Long,
    val durationMs: Long,
)

/** 倍速档位 */
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    val settings: SettingsStore = SettingsStore(app)

    val settingsFlow: StateFlow<Settings> = settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** 书架：Room 数据 + 播放进度合并 */
    val shelf: StateFlow<List<ShelfFolder>> =
        combine(db.shelfDao().observeAll(), db.progressDao().observeAll()) { folders, progress ->
            folders.map { f ->
                val base = f.path.trimEnd('/')
                val prefix = "$base/"
                // 该文件夹（含子目录）下全部曲目进度，按文件路径前缀匹配
                val ps = progress.filter { it.path.startsWith(prefix) }
                val fallbackTotal = if (f.trackCount > 0) f.trackCount else ps.size
                // 进度 = 当前播放进度点所在的位置（第 N 集 + 该集内比例），
                // 不关心是否整集听完，也不做"最远到达"记忆：回听前面的集数，进度就跟着回落。
                // 取该文件夹内 updatedAt 最新的一条记录作为当前进度点。
                val latest = ps.filter { it.trackIndex >= 0 }.maxByOrNull { it.updatedAt }
                val current = latest?.let { p ->
                    val total = if (p.trackTotal > 0) p.trackTotal else fallbackTotal
                    if (total <= 0) null else {
                        val d = p.durationMs
                        val frac = if (d > 0) p.positionMs.coerceIn(0L, d).toDouble() / d else 0.0
                        (p.trackIndex + frac) / total
                    }
                }
                // 兼容旧记录（无队列序号）：退化为逐集收听比例求和
                val legacy = if (fallbackTotal <= 0) 0.0 else ps.sumOf { p ->
                    val d = p.durationMs
                    if (d > 0) p.positionMs.coerceIn(0L, d).toDouble() / d else 0.0
                } / fallbackTotal
                ShelfFolder(
                    path = f.path,
                    name = f.name,
                    trackCount = f.trackCount,
                    totalDurationMs = f.totalDurationMs,
                    addedAt = f.addedAt,
                    lastPlayedAt = f.lastPlayedAt,
                    progress = (current ?: legacy).toFloat().coerceIn(0f, 1f),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前打开的文件夹 */
    private val _currentFolder = MutableStateFlow<ShelfFolder?>(null)
    val currentFolder: StateFlow<ShelfFolder?> = _currentFolder

    private val _currentTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val currentTracks: StateFlow<List<AudioTrack>> = _currentTracks

    /** 当前文件夹的上次播放位置，用于「继续上次」提示 */
    private val _resumeHint = MutableStateFlow<ResumeHint?>(null)
    val resumeHint: StateFlow<ResumeHint?> = _resumeHint

    /** path -> 进度，内存缓存，单曲点击时同步判断是否续播 */
    private var progressMap: Map<String, TrackProgressEntity> = emptyMap()

    /** path -> 上次听到的毫秒位置（仅未听完的），供列表逐条显示自己的进度 */
    private val _trackPositions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val trackPositions: StateFlow<Map<String, Long>> = _trackPositions

    private val _pickerFolders = MutableStateFlow<List<ScannedFolder>>(emptyList())
    val pickerFolders: StateFlow<List<ScannedFolder>> = _pickerFolders

    private val _pickerLoading = MutableStateFlow(false)
    val pickerLoading: StateFlow<Boolean> = _pickerLoading

    private val _pickerKeyword = MutableStateFlow("")
    val pickerKeyword: StateFlow<String> = _pickerKeyword

    /* ---------- 目录浏览器（按文件系统懒加载，不依赖媒体库索引） ---------- */
    private val _browsePath = MutableStateFlow<String?>(null)
    val browsePath: StateFlow<String?> = _browsePath

    private val _browseEntries = MutableStateFlow<List<DirNode>>(emptyList())
    val browseEntries: StateFlow<List<DirNode>> = _browseEntries

    private val _breadcrumb = MutableStateFlow<List<DirNode>>(emptyList())
    val breadcrumb: StateFlow<List<DirNode>> = _breadcrumb

    private val _checkedDirs = MutableStateFlow<Set<String>>(emptySet())
    val checkedDirs: StateFlow<Set<String>> = _checkedDirs

    private val _checkedAudio = MutableStateFlow(0)
    val checkedAudio: StateFlow<Int> = _checkedAudio

    private val _addingFolders = MutableStateFlow(false)
    val addingFolders: StateFlow<Boolean> = _addingFolders

    private val _browseTotal = MutableStateFlow(0)
    val browseTotal: StateFlow<Int> = _browseTotal

    init {
        viewModelScope.launch { scanPicker() }
        viewModelScope.launch {
            settingsFlow.collect { s ->
                PlayerController.setSkipSeconds(s.skipSeconds)
            }
        }
        // 进度缓存：单曲点击时据此判断是否从上次位置续播
        viewModelScope.launch {
            db.progressDao().observeAll().collect { list ->
                progressMap = list.associateBy { it.path }
                _trackPositions.value = list.asSequence()
                    .filter { isResumable(it.positionMs, it.durationMs) }
                    .associate { it.path to it.positionMs }
            }
        }
        // 冷启动：把上次播放的曲目回填到播放条（不自动播放，等用户点）
        viewModelScope.launch {
            val last = withContext(Dispatchers.IO) {
                runCatching { db.progressDao().latest() }.getOrNull()
            }
            if (last != null && isResumable(last.positionMs, last.durationMs)) {
                PlayerController.restorePreview(
                    AudioTrack(
                        mediaId = -1L,
                        path = last.path,
                        fileName = last.fileName,
                        durationMs = last.durationMs,
                        sizeBytes = 0L,
                        dateAddedSec = 0L,
                    ),
                    last.positionMs
                )
            }
        }
        // 心跳保存：不管在哪个页面播放，都每 3 秒落一次进度，进程被杀也能续播
        viewModelScope.launch {
            while (true) {
                delay(3000)
                saveProgress()
            }
        }
    }

    /** 进度是否值得续播：超过 3 秒且没播完 */
    private fun isResumable(pos: Long, dur: Long): Boolean =
        pos > 3000L && (dur <= 0L || pos < dur - 3000L)

    /* ---------- 书架 ---------- */

    /**
     * 从书架移除文件夹（书架长按多选删除）。
     * 只删书架条目与其阅读进度记录，手机里的音频文件不动。
     */
    fun removeFolders(paths: List<String>) {
        if (paths.isEmpty()) return
        val bases = paths.map { it.trimEnd('/') }
        // 正在播放的书被移出书架：立即停止播放并收起播放条
        if (PlayerController.isPlayingUnder(bases)) {
            PlayerController.clearCurrent()
            _currentTracks.value = emptyList()
            _resumeHint.value = null
        }
        // 正在看的文件夹被移出书架：清空当前文件夹状态
        if (_currentFolder.value?.path in paths) {
            _currentFolder.value = null
            _currentTracks.value = emptyList()
            _resumeHint.value = null
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    db.shelfDao().deleteAll(paths)
                    bases.forEach { base ->
                        db.progressDao().deleteUnder("$base/", base + "0")
                    }
                }
            }
        }
    }

    fun openFolder(folder: ShelfFolder) {
        _currentFolder.value = folder
        _resumeHint.value = null
        viewModelScope.launch {
            val tracks = FsBrowser.scanRecursive(getApplication(), folder.path)
            _currentTracks.value = tracks
            // 注意：这里不再 touch 更新时间戳。只浏览（点开书单）不改变书架排序，
            // 只有真正点了播放才会置顶，见 markPlayed()。
            // 该文件夹（含子目录）里最后一次播放位置：用于「继续上次」提示
            val base = folder.path.trimEnd('/')
            val last = withContext(Dispatchers.IO) {
                runCatching { db.progressDao().latestUnder("$base/", base + "0") }.getOrNull()
            }
            val idx = if (last != null && isResumable(last.positionMs, last.durationMs)) {
                tracks.indexOfFirst { it.path == last.path }
            } else -1
            _resumeHint.value = if (last != null && idx >= 0) {
                ResumeHint(
                    trackPath = last.path,
                    trackIndex = idx,
                    fileName = last.fileName,
                    positionMs = last.positionMs,
                    durationMs = last.durationMs,
                )
            } else null
        }
    }

    fun removeFromShelf(path: String) {
        val base = path.trimEnd('/')
        if (PlayerController.isPlayingUnder(listOf(base))) {
            PlayerController.clearCurrent()
            _currentTracks.value = emptyList()
            _resumeHint.value = null
        }
        if (_currentFolder.value?.path == path) {
            _currentFolder.value = null
            _currentTracks.value = emptyList()
            _resumeHint.value = null
        }
        viewModelScope.launch {
            db.shelfDao().delete(path)
            withContext(Dispatchers.IO) {
                db.progressDao().getForFolder(path).forEach { db.progressDao().delete(it.path) }
                db.progressDao().deleteUnder("$base/", base + "0")
            }
        }
    }

    /* ---------- 添加：扫描后手动勾选 ---------- */

    fun scanPicker() {
        viewModelScope.launch {
            _pickerLoading.value = true
            _pickerFolders.value = runCatching {
                AudioScanner.scanAll(getApplication())
            }.getOrDefault(emptyList())
            updateBrowse(_browsePath.value)
            _pickerLoading.value = false
        }
    }

    fun setPickerKeyword(kw: String) {
        _pickerKeyword.value = kw
        scanPicker()
    }

    suspend fun isInShelf(path: String): Boolean = withContext(Dispatchers.IO) {
        db.shelfDao().exists(path)
    }

    fun addToShelf(folder: ScannedFolder) {
        viewModelScope.launch {
            db.shelfDao().upsert(
                ShelfFolderEntity(
                    path = folder.path,
                    name = folder.name,
                    trackCount = folder.trackCount,
                    totalDurationMs = folder.totalDurationMs,
                    addedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /* ---------- 目录浏览：真实文件系统 + 逐级进入 + 多选 ---------- */

    /** 进入某个目录（path 传 null 回到存储根列表） */
    fun browseInto(path: String?) {
        updateBrowse(path)
    }

    /** 返回上一级；已在存储根列表时返回 false（调用方据此退出页面） */
    fun browseUp(): Boolean {
        val cur = _browsePath.value ?: return false
        val parent = cur.trimEnd('/').substringBeforeLast('/', "")
        if (parent.isEmpty() || parent == "/storage" || parent == "/") {
            updateBrowse(null)
        } else {
            updateBrowse(parent)
        }
        return true
    }

    fun toggleChecked(path: String) {
        val cur = _checkedDirs.value
        val next = if (path in cur) cur - path else cur + path
        _checkedDirs.value = next
        recomputeCheckedAudio()
    }

    fun clearChecked() {
        _checkedDirs.value = emptySet()
        _checkedAudio.value = 0
    }

    /** path -> 该目录（含子目录）音频数，浏览与勾选过程中逐步补全 */
    private val deepCache = HashMap<String, Int>()

    /** 已勾选目录包含的音频总条数（含子目录） */
    private fun recomputeCheckedAudio() {
        _checkedAudio.value = _checkedDirs.value.sumOf { deepCache[it] ?: 0 }
    }

    /** 批量加入书架：选中目录连同其所有子目录的音频汇成一个书架项 */
    fun addCheckedToShelf(onDone: (Int) -> Unit = {}) {
        val paths = _checkedDirs.value.toList()
        if (paths.isEmpty() || _addingFolders.value) return
        viewModelScope.launch {
            _addingFolders.value = true
            val ctx = getApplication<Application>()
            var added = 0
            for (p in paths) {
                val tracks = FsBrowser.scanRecursive(ctx, p)
                if (tracks.isEmpty()) continue
                db.shelfDao().upsert(
                    ShelfFolderEntity(
                        path = p,
                        name = p.substringAfterLast('/'),
                        trackCount = tracks.size,
                        totalDurationMs = tracks.sumOf { it.durationMs },
                        addedAt = System.currentTimeMillis(),
                    )
                )
                deepCache[p] = tracks.size
                added++
            }
            _checkedDirs.value = emptySet()
            _checkedAudio.value = 0
            _addingFolders.value = false
            onDone(added)
        }
    }

    private fun updateBrowse(path: String?) {
        _browsePath.value = path
        // 只列当前这一层的子目录，不进子目录统计音频，避免根目录卡「统计中」
        _browseEntries.value = when (path) {
            null -> FsBrowser.roots()
            else -> FsBrowser.listDirs(path)
        }
        _breadcrumb.value = buildCrumbs(path)
    }

    /** 由绝对路径生成面包屑（不依赖预扫描，进到哪层就有哪层） */
    private fun buildCrumbs(path: String?): List<DirNode> {
        if (path == null) return emptyList()
        val root = storageRootOf(path)
        val out = ArrayList<DirNode>()
        var p = path.trimEnd('/')
        while (p.length >= root.length) {
            val name = when (p) {
                "/storage/emulated/0" -> "手机存储"
                root -> if (p.startsWith("/storage/emulated/0")) "手机存储" else "存储卡 " + p.substringAfterLast('/')
                else -> p.substringAfterLast('/')
            }
            val parent = if (p == root) null else p.substringBeforeLast('/', "")
            out.add(DirNode(p, name, 0, -1, 0L, 0, parent, p == root))
            if (p == root) break
            p = parent ?: break
        }
        out.reverse()
        return out
    }

    private fun storageRootOf(path: String): String {
        val parts = path.trim('/').split('/')
        return if (parts.size >= 2 && parts[0] == "storage") {
            if (parts[1] == "emulated") "/storage/emulated/0" else "/storage/" + parts[1]
        } else {
            "/" + parts.first()
        }
    }

    /* ---------- 播放 ---------- */

    fun play(tracks: List<AudioTrack>, index: Int, startPositionMs: Long? = null) {
        _currentTracks.value = tracks
        val t = tracks.getOrNull(index) ?: return
        // 跳听别的曲目前，先把正在播的这一首位置落库（此刻仍指向旧曲，位置最准）
        if (PlayerController.hasQueue() &&
            PlayerController.currentTrack.value?.path != t.path
        ) {
            saveProgress(force = true)
        }
        val s = settingsFlow.value
        val folder = _currentFolder.value?.path?.takeIf { t.path.startsWith(it) } ?: t.folderPath
        val speed = if (s.rememberSpeedPerFolder) savedSpeed(folder, s.defaultSpeed) else s.defaultSpeed
        // 未指定起点时，若该曲有未听完的进度，从上次位置续播
        val start = startPositionMs ?: progressMap[t.path]?.let {
            if (isResumable(it.positionMs, it.durationMs)) it.positionMs else 0L
        } ?: 0L
        markPlayed(folder)
        PlayerController.playList(tracks, index, speed, start)
    }

    /** 真正开始播放时才把该文件夹排到书架最前（仅浏览 book 不排序）。 */
    private fun markPlayed(folder: String?) {
        val p = folder?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { db.shelfDao().touch(p, System.currentTimeMillis()) }
            }
        }
    }

    /** 播放条右侧按钮：有队列就切播放/暂停，冷启动恢复态则先建队列续播 */
    fun toggleOrResume() {
        if (PlayerController.hasQueue()) PlayerController.togglePlay() else ensurePlayback()
    }

    /** 点「继续播放」：按该文件夹最后一次的记录，直接播到那个文件的续播位置 */
    fun playResumeHint() {
        val h = _resumeHint.value ?: return
        val tracks = _currentTracks.value
        if (h.trackIndex !in tracks.indices) return
        play(tracks, h.trackIndex, h.positionMs)
    }

    /** 书架播放条点击：打开当前（或上次）播放曲目所在的文件夹列表 */
    fun openCurrentFolder() {
        val t = PlayerController.currentTrack.value ?: return
        val dir = t.path.substringBeforeLast('/', "")
        if (dir.isEmpty()) return
        val f = shelf.value.firstOrNull { it.path == dir } ?: ShelfFolder(
            path = dir,
            name = dir.substringAfterLast('/'),
            trackCount = 0,
            totalDurationMs = 0L,
            addedAt = System.currentTimeMillis(),
            lastPlayedAt = 0L,
            progress = 0f,
        )
        openFolder(f)
    }

    /** 倍速循环切换：0.5 → 0.75 → 1 → 1.25 → 1.5 → 1.75 → 2 → 0.5 */
    fun cycleSpeed() {
        val cur = PlayerController.speed.value
        val i = SPEEDS.indexOfFirst { kotlin.math.abs(it - cur) < 0.01f }
        val next = SPEEDS[if (i < 0) 2 else (i + 1) % SPEEDS.size]
        setSpeed(next)
    }

    /** 点播放条：冷启动恢复态下先按上次位置真正开始播放 */
    fun ensurePlayback() {
        if (PlayerController.hasQueue()) return
        val t = PlayerController.currentTrack.value ?: return
        val resumeAt = PlayerController.takeResumePosition(t.path)
        viewModelScope.launch {
            val dir = t.path.substringBeforeLast('/', "")
            if (dir.isEmpty()) return@launch
            val tracks = runCatching {
                FsBrowser.scanRecursive(getApplication(), dir)
            }.getOrDefault(emptyList())
            val idx = tracks.indexOfFirst { it.path == t.path }
            if (idx >= 0) {
                _currentTracks.value = tracks
                play(tracks, idx, resumeAt)
            }
        }
    }

    private fun savedSpeed(folder: String, def: Float): Float {
        // 简化：从该文件夹最近一次进度记录读倍速
        return lastSpeedCache[folder] ?: def
    }

    private val lastSpeedCache = mutableMapOf<String, Float>()

    fun setSpeed(v: Float) {
        PlayerController.setSpeed(v)
        _currentFolder.value?.path?.let { lastSpeedCache[it] = v }
    }

    /**
     * 保存当前曲目进度。每首曲按自己的 path 单独记一行，互不影响。
     * force=true 用于切换曲目前的收尾落库；否则会拦截「切歌瞬间位置被读成 0」的误写。
     */
    fun saveProgress(force: Boolean = false) {
        val track = PlayerController.currentTrack.value ?: return
        // 冷启动恢复态（尚未真正建队列）不写，避免把旧位置覆盖成 0
        if (!PlayerController.hasQueue()) return
        PlayerController.refreshPosition()
        val pos = PlayerController.positionMs.value
        val dur = PlayerController.durationMs.value.takeIf { it > 0 } ?: track.durationMs
        if (track.path.isEmpty() || dur <= 0) return
        if (!force) {
            // 保护：刚切歌时可能读到新曲的 0 位置，不能把这首已有的进度清掉
            val prev = progressMap[track.path]
            if (prev != null && pos < 2000L && prev.positionMs > 30000L &&
                System.currentTimeMillis() - prev.updatedAt < 15000L
            ) return
        }
        // 记录当前进度点在队列中的位置（第几集 / 共几集），供书架直接换算百分比
        val idx = PlayerController.currentIndex()
        val total = PlayerController.queueSize()
        viewModelScope.launch {
            db.progressDao().upsert(
                TrackProgressEntity(
                    path = track.path,
                    folderPath = track.folderPath,
                    fileName = track.fileName,
                    positionMs = pos,
                    durationMs = dur,
                    speed = PlayerController.speed.value,
                    updatedAt = System.currentTimeMillis(),
                    trackIndex = idx,
                    trackTotal = total,
                )
            )
        }
    }

    /* ---------- 设置 ---------- */

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }
    fun setLayout(l: ShelfLayout) = viewModelScope.launch { settings.setLayout(l) }
    fun setAccent(i: Int) = viewModelScope.launch { settings.setAccent(i) }
    fun setSkipSeconds(s: Int) {
        viewModelScope.launch { settings.setSkipSeconds(s) }
        PlayerController.setSkipSeconds(s)
    }
    fun setDefaultSpeed(v: Float) = viewModelScope.launch { settings.setDefaultSpeed(v) }
    fun setRememberSpeed(b: Boolean) = viewModelScope.launch { settings.setRememberSpeed(b) }
    fun setAutoNext(b: Boolean) = viewModelScope.launch { settings.setAutoNext(b) }
}
