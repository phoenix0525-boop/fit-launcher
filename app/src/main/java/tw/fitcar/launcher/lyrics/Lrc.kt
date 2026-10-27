package tw.fitcar.launcher.lyrics

data class LyricLine(val timeMs: Long, val text: String)
object Lrc {
    fun parse(value: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val stamp = Regex("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
        return value.lineSequence().take(4000).flatMap { line ->
            val text = line.replace(stamp, "").trim()
            stamp.findAll(line).map { m ->
                val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                LyricLine((m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + fraction - offset).coerceAtLeast(0), text)
            }
        }.sortedBy { it.timeMs }.groupBy { it.timeMs }.map { (time, lines) -> LyricLine(time, lines.map { it.text }.distinct().joinToString("\n")) }
    }
    fun current(lines: List<LyricLine>, positionMs: Long): Int = lines.indexOfLast { it.timeMs <= positionMs }

    /** 無時間碼歌詞：行車中不列整首，只取前幾行非空白文字；第二個值表示是否還有更多。 */
    fun preview(text: String, maxLines: Int): Pair<String, Boolean> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return lines.take(maxLines.coerceAtLeast(1)).joinToString("\n") to (lines.size > maxLines)
    }
}
