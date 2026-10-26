package tw.fitcar.launcher.system

enum class NetworkKind { WIFI, CELLULAR, ETHERNET, OTHER, NONE }
enum class IndicatorKind { NETWORK, BLUETOOTH, USB, BATTERY }

/** 狀態列需要的系統狀態快照（GPS 另由 GpsSpeed 顯示）。 */
data class SystemStatus(
    val network: NetworkKind = NetworkKind.NONE,
    val internet: Boolean = false,
    val bluetoothOn: Boolean? = null,
    /** null＝未授權「附近裝置」，只顯示開關。 */
    val bluetoothConnected: Boolean? = null,
    val usbStorage: Int = 0,
    /** null＝沒有電池（多數車機），不顯示。 */
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
)

data class Indicator(val kind: IndicatorKind, val label: String, val active: Boolean, val warning: Boolean = false)

/** 參考 AGAMA 等車機桌面的狀態列：只列出有意義的項目，順序固定。純邏輯，可單元測試。 */
object StatusIndicators {
    fun build(s: SystemStatus): List<Indicator> = buildList {
        add(
            when (s.network) {
                NetworkKind.NONE -> Indicator(IndicatorKind.NETWORK, "離線", active = false)
                else -> {
                    val name = when (s.network) {
                        NetworkKind.WIFI -> "Wi-Fi"
                        NetworkKind.CELLULAR -> "行動網路"
                        NetworkKind.ETHERNET -> "有線網路"
                        else -> "網路"
                    }
                    if (s.internet) Indicator(IndicatorKind.NETWORK, name, active = true)
                    else Indicator(IndicatorKind.NETWORK, "$name 無網路", active = true, warning = true)
                }
            },
        )
        s.bluetoothOn?.let { on ->
            add(
                when {
                    !on -> Indicator(IndicatorKind.BLUETOOTH, "藍牙關", active = false)
                    s.bluetoothConnected == true -> Indicator(IndicatorKind.BLUETOOTH, "藍牙已連線", active = true)
                    else -> Indicator(IndicatorKind.BLUETOOTH, "藍牙", active = true)
                },
            )
        }
        if (s.usbStorage > 0) add(Indicator(IndicatorKind.USB, if (s.usbStorage > 1) "USB×${s.usbStorage}" else "USB", active = true))
        s.batteryPercent?.let { pct ->
            add(Indicator(IndicatorKind.BATTERY, (if (s.charging) "⚡" else "") + "$pct%", active = true, warning = !s.charging && pct <= 20))
        }
    }
}
