package com.chunting.reader.data.repo

import com.chunting.reader.data.model.ScannedFolder

/**
 * 一个可浏览的目录节点。
 * 由 MediaStore 扫描出的「含音频的目录」向上补齐祖先目录推导而来，
 * 因此整棵树就是手机上真实存在的目录层级，只是不含音频的空目录不会出现。
 */
data class DirNode(
    val path: String,
    val name: String,          // 展示名（根目录用「内部存储」等中文名）
    val directCount: Int,      // 本目录直属音频数
    val deepCount: Int,        // 含所有子目录的音频总数
    val deepDurationMs: Long,  // 含所有子目录的音频总时长
    val childCount: Int,       // 子目录数量
    val parentPath: String?,   // 上级目录（根目录为 null）
    val isRoot: Boolean,
)

/**
 * 目录树：把扁平的 ScannedFolder 列表还原成可逐级进入的目录结构。
 */
class DirTree private constructor(
    val nodes: Map<String, DirNode>,
    val roots: List<DirNode>,
    private val childrenMap: Map<String, List<DirNode>>,
) {

    /** path 传 null 表示取根列表 */
    fun childrenOf(path: String?): List<DirNode> =
        childrenMap[path ?: ROOT_KEY].orEmpty()

    fun node(path: String): DirNode? = nodes[path]

    companion object {
        private const val ROOT_KEY = ""
        private const val INTERNAL = "/storage/emulated/0"

        fun normalize(raw: String): String {
            var p = raw.trim().trimEnd('/')
            if (p.isEmpty()) return p
            if (p == "/sdcard") return INTERNAL
            if (p.startsWith("/sdcard/")) p = INTERNAL + p.removePrefix("/sdcard")
            return p
        }

        /** 判断某个绝对路径属于哪个存储根（内部存储 / 某张外置卡） */
        fun rootOf(path: String): String {
            val parts = path.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return path
            return if (parts.size >= 2 && parts[0] == "storage") {
                if (parts.size >= 3 && parts[1] == "emulated") INTERNAL
                else "/storage/${parts[1]}"
            } else "/${parts[0]}"
        }

        fun build(folders: List<ScannedFolder>): DirTree {
            val norm = folders.mapNotNull { f ->
                val p = normalize(f.path)
                if (p.count { it == '/' } < 2) null else f.copy(path = p)
            }

            val directCount = HashMap<String, Int>()
            val deepCount = HashMap<String, Int>()
            val deepDur = HashMap<String, Long>()
            val parents = LinkedHashMap<String, String?>()

            for (f in norm) {
                directCount[f.path] = directCount.getOrDefault(f.path, 0) + f.trackCount
                val root = rootOf(f.path)
                var p = f.path
                while (true) {
                    deepCount[p] = deepCount.getOrDefault(p, 0) + f.trackCount
                    deepDur[p] = deepDur.getOrDefault(p, 0L) + f.totalDurationMs
                    if (p == root) {
                        parents[p] = null
                        break
                    }
                    val parent = p.substringBeforeLast('/', "")
                    if (parent.isEmpty() || parent.length < root.length) {
                        parents[p] = root
                        parents.putIfAbsent(root, null)
                        break
                    }
                    parents[p] = parent
                    p = parent
                }
            }

            // 子目录索引（key = 父路径，根用 ROOT_KEY）
            val childIndex = HashMap<String, MutableList<String>>()
            for ((p, parent) in parents) {
                val key = if (parent == null || !parents.containsKey(parent)) ROOT_KEY else parent
                childIndex.getOrPut(key) { ArrayList() }.add(p)
            }

            val nodes = LinkedHashMap<String, DirNode>()
            for (p in parents.keys) {
                val root = rootOf(p)
                val isRoot = p == root
                val name = when {
                    p == INTERNAL -> "内部存储"
                    isRoot -> "存储卡 " + p.substringAfterLast('/')
                    else -> p.substringAfterLast('/').ifEmpty { p }
                }
                nodes[p] = DirNode(
                    path = p,
                    name = name,
                    directCount = directCount[p] ?: 0,
                    deepCount = deepCount[p] ?: 0,
                    deepDurationMs = deepDur[p] ?: 0L,
                    childCount = 0,
                    parentPath = parents[p]?.takeIf { parents.containsKey(it) },
                    isRoot = isRoot,
                )
            }

            val childrenMap = HashMap<String, List<DirNode>>()
            for ((key, list) in childIndex) {
                childrenMap[key] = list.mapNotNull { nodes[it] }
                    .sortedWith(compareBy({ it.name.lowercase() }))
            }

            // 回填 childCount
            val withChild = nodes.mapValues { (path, node) ->
                node.copy(childCount = childrenMap[path]?.size ?: 0)
            }
            val sortedChildren = childrenMap.mapValues { (_, list) ->
                list.map { withChild[it.path] ?: it }
                    .sortedWith(compareBy({ it.name.lowercase() }))
            }

            return DirTree(
                nodes = withChild,
                roots = sortedChildren[ROOT_KEY].orEmpty(),
                childrenMap = sortedChildren,
            )
        }
    }
}
