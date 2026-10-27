package tw.fitcar.launcher.data

import android.content.Context

/** 「所有 App」最近使用紀錄：只存在車機，不上傳。 */
object AppUsage {
    private const val PREFS = "app_usage"

    fun matches(label: String, packageName: String, query: String) = AppUsagePolicy.matches(label, packageName, query)

    fun recent(context: Context): List<String> =
        AppUsagePolicy.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("recent", null))

    fun record(context: Context, key: String) {
        val next = AppUsagePolicy.push(recent(context), key)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("recent", AppUsagePolicy.encode(next)).apply()
    }
}
