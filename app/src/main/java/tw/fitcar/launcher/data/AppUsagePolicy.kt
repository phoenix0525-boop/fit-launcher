package tw.fitcar.launcher.data

/** 最近使用與搜尋的純邏輯（可做 JVM 單元測試）。 */
object AppUsagePolicy {
    const val MAX_RECENT = 8

    fun push(list: List<String>, key: String): List<String> =
        (listOf(key) + list.filter { it != key && it.isNotBlank() }).take(MAX_RECENT)

    fun decode(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(MAX_RECENT).orEmpty()

    fun encode(list: List<String>): String = list.take(MAX_RECENT).joinToString("\n")

    /** 名稱或套件包含關鍵字即符合（不分大小寫、忽略前後空白）；空白關鍵字全部符合。 */
    fun matches(label: String, packageName: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return label.contains(q, ignoreCase = true) || packageName.contains(q, ignoreCase = true)
    }
}
