package tw.fitcar.launcher.nav

import java.net.URLEncoder

/** 常用地點（回家／去公司）：以標準 geo: 地址交給使用者綁定的導航 App。（自 0.3.2 線移植） */
object Places {
    const val MAX_LENGTH = 120

    fun clean(address: String?): String? =
        address?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }

    fun geoUri(address: String?): String? {
        val a = clean(address) ?: return null
        return "geo:0,0?q=" + URLEncoder.encode(a, "UTF-8").replace("+", "%20")
    }
}
