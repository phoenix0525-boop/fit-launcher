package tw.fitcar.launcher.system

/** 桌面接管的說明文字與判斷；純邏輯，方便測試。 */
object DesktopTakeoverAdvice {
    /**
     * 原廠更新包 config.txt 有 `ro.fyt.launcher=com.syu.launcher`：系統層級指定原廠桌面。
     * 這是唯讀系統值，只能刷韌體修改，所以按 Home 或開機仍可能回到原廠桌面（推論，待實車對照）。
     */
    const val FACTORY_PROPERTY_NOTE =
        "部分 FYT 車機由系統設定指定原廠桌面，開機或按 Home 仍可能回到原廠桌面；請搭配下方「原廠桌面出現時切回」。"
}
