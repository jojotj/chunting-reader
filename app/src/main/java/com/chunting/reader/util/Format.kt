package com.chunting.reader.util

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "%d 小时 %d 分".format(h, m) else "%d 分钟".format(m)
}

/**
 * 取文件夹名首字作为徽章。
 * 优先级：汉字 > 英文字母 > 数字。
 * 这样「106大人物·项羽评传」取「大」而不是「1」。
 */
fun monogram(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "?"
    t.firstOrNull { isCjk(it) }?.let { return it.toString() }
    t.firstOrNull { it.isLetter() }?.let { return it.toString() }
    t.firstOrNull { it.isDigit() }?.let { return it.toString() }
    return "?"
}

/** 是否汉字（含扩展 A 区与兼容区） */
private fun isCjk(ch: Char): Boolean =
    ch.code in 0x4E00..0x9FFF ||
        ch.code in 0x3400..0x4DBF ||
        ch.code in 0xF900..0xFAFF

/** 提取拼音首字母提示（仅用于封面右下角小字，取前两个音节） */
fun pinyinHint(name: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < name.length && sb.length < 8) {
        val c = name[i]
        if (c.isLetter()) sb.append(c.lowercaseChar())
        i++
    }
    return sb.toString().ifEmpty { "" }
}
