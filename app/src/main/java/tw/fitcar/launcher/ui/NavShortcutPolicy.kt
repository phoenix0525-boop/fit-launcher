package tw.fitcar.launcher.ui

import tw.fitcar.launcher.nav.GoogleNavigation
import tw.fitcar.launcher.nav.NavigationKingShortcut

/** 導航王與 Google 地圖固定並排（導航王在左），目前選定的那顆用主要按鈕樣式。 */
object NavShortcutPolicy {
    enum class Action { OPEN_CURRENT, SELECT_APP, KING, GOOGLE }
    data class Button(val label: String, val primary: Boolean, val action: Action)
    enum class Selected { NONE, KING, GOOGLE, OTHER }
    const val KING_LABEL = "導航王"
    const val GOOGLE_LABEL = "Google 地圖"

    fun selected(label: String?, packageName: String?): Selected = when {
        packageName == null -> Selected.NONE
        packageName == GoogleNavigation.PACKAGE -> Selected.GOOGLE
        NavigationKingShortcut.matches(label.orEmpty(), packageName) -> Selected.KING
        else -> Selected.OTHER
    }

    fun buttons(selected: Selected): List<Button> {
        val king = Button(KING_LABEL, selected == Selected.KING, if (selected == Selected.KING) Action.OPEN_CURRENT else Action.KING)
        val google = Button(GOOGLE_LABEL, selected == Selected.GOOGLE, if (selected == Selected.GOOGLE) Action.OPEN_CURRENT else Action.GOOGLE)
        return when (selected) {
            Selected.NONE -> listOf(Button("選擇導航 App", true, Action.SELECT_APP), king, google)
            Selected.OTHER -> listOf(Button("開啟導航", true, Action.OPEN_CURRENT), king, google)
            else -> listOf(king, google)
        }
    }

    /** 三顆導航鍵時，回家／去公司改放「更多」，工具列維持一排。 */
    fun placesInline(compactChrome: Boolean, buttonCount: Int) = !compactChrome && buttonCount <= 2
}
