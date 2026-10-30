package tw.fitcar.launcher.check

/** 「0 問題」只代表沒有已確認的問題；仍有待測項目時必須一併說明，不能當成全部正常。 */
object ReportSummary {
    fun headline(problems: Int, todo: Int): String = when {
        problems > 0 && todo > 0 -> "$problems 項；另有 $todo 項待測試"
        problems > 0 -> "$problems 項"
        todo > 0 -> "目前沒有已確認的問題，但還有 $todo 項待測試，不代表全部正常"
        else -> "目前沒有"
    }
}
