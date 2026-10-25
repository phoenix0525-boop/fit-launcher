package tw.fitcar.launcher.check

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.fitcar.launcher.BuildInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------------- 手勢：判斷原車機雙指音量手勢是否在 FIT 桌面內仍有效 ---------------- */

enum class GestureKind(val id: String, val label: String, val expect: String) {
    TWO_UP("gesture_two_up", "雙指上滑", "音量增加"),
    TWO_DOWN("gesture_two_down", "雙指下滑", "音量降低"),
    DOUBLE_TAP_ONE("gesture_tap_one", "單指雙擊", "靜音"),
    DOUBLE_TAP_TWO("gesture_tap_two", "雙指雙擊", "靜音"),
}

data class VolumeSnapshot(val volume: Int, val muted: Boolean)

object GestureTest {
    fun snapshot(context: Context): VolumeSnapshot {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return VolumeSnapshot(am.getStreamVolume(AudioManager.STREAM_MUSIC), am.isStreamMute(AudioManager.STREAM_MUSIC))
    }

    /** 做完手勢 0.8 秒後比對音量，判斷系統是否處理了這個手勢。 */
    fun evaluate(kind: GestureKind, before: VolumeSnapshot, after: VolumeSnapshot, appSaw: Boolean): CheckResult {
        val changed = when (kind) {
            GestureKind.TWO_UP -> after.volume > before.volume || (before.muted && !after.muted)
            GestureKind.TWO_DOWN -> after.volume < before.volume
            else -> after.muted != before.muted || (after.volume == 0) != (before.volume == 0)
        }
        val f = "在 FIT 桌面內做「${kind.label}」：音量 ${before.volume}${if (before.muted) "（靜音）" else ""} → ${after.volume}${if (after.muted) "（靜音）" else ""}；" +
            (if (appSaw) "App 有收到這個手勢的觸控。" else "App 沒收到這個手勢的觸控（被系統先攔截，或手勢未做完整）。")
        return if (changed) {
            CheckResult(kind.id, "操作", "手勢：${kind.label}（原車機＝${kind.expect}）", Status.PASS, f + "系統層級有處理。",
                "這個手勢由車機系統或觸控驅動提供，換成 FIT 桌面後仍有效，不需要另外實作。")
        } else {
            CheckResult(kind.id, "操作", "手勢：${kind.label}（原車機＝${kind.expect}）", Status.WARN, f + "音量沒有變化。",
                "若在原廠桌面做同樣手勢會${kind.expect}，表示手勢只存在原廠桌面，FIT 桌面需要自行實作（記入下一版需求）。" +
                    "若原廠桌面也沒反應，代表原車機不是用這個手勢，可忽略。")
        }
    }
}

/* ---------------- 按鍵：記錄方向盤／面板按鍵送到 App 的 KeyEvent ---------------- */

data class KeyRecord(val code: Int, val name: String, val time: Long)

object KeyLog {
    private val _events = MutableStateFlow<List<KeyRecord>>(emptyList())
    val events: StateFlow<List<KeyRecord>> = _events.asStateFlow()
    @Volatile var recording = false

    fun onKey(event: KeyEvent) {
        if (!recording || event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        _events.value = (listOf(KeyRecord(event.keyCode, KeyEvent.keyCodeToString(event.keyCode), System.currentTimeMillis())) + _events.value).take(30)
    }

    fun clear() { _events.value = emptyList() }

    fun result(): CheckResult {
        val codes = _events.value.map { it.name.removePrefix("KEYCODE_") }.distinct()
        return if (codes.isEmpty()) {
            CheckResult("keys", "操作", "方向盤／面板按鍵", Status.INFO, "測試期間 App 沒有收到任何按鍵事件。",
                "方向盤按鍵由車機系統（MCU／CAN 盒）直接處理，App 收不到是正常的；請在手動檢查確認切歌、音量在 FIT 桌面上仍有效。")
        } else {
            CheckResult("keys", "操作", "方向盤／面板按鍵", Status.INFO, "App 收到：${codes.joinToString("、")}。",
                "這些按鍵 FIT 桌面可以接收；若有按鍵在 FIT 桌面上失效（例如 HOME、MODE），記下來可在下一版對應處理。")
        }
    }
}

/* ---------------- 亮度：寫入後是否被覆寫、背光是否真的改變 ---------------- */

object BrightnessTest {
    data class Outcome(val written: Int, val readBack: Int, val modeKept: Boolean, val original: Int)

    /** 寫入一個明顯不同的亮度，觀察 [waitMs] 後的值，最後恢復原值。 */
    suspend fun run(context: Context, waitMs: Long = 4000, onWritten: (Int) -> Unit = {}): Outcome? {
        if (!Settings.System.canWrite(context)) return null
        val r = context.contentResolver
        val origMode = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val orig = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128)
        val target = if (orig > 128) 40 else 230
        Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, target)
        onWritten(target)
        delay(waitMs)
        val back = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, -1)
        val mode = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, orig)
        Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, origMode)
        return Outcome(target, back, mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, orig)
    }

    fun result(o: Outcome, screenChanged: Boolean?): CheckResult {
        val kept = o.readBack == o.written && o.modeKept
        val f = "寫入 ${o.written}/255，4 秒後讀回 ${o.readBack}/255${if (o.modeKept) "" else "（亮度模式被改回自動）"}；已恢復原值 ${o.original}。" +
            when (screenChanged) { true -> "使用者確認螢幕有變化。"; false -> "使用者確認螢幕沒有變化。"; null -> "" }
        return when {
            screenChanged == false -> CheckResult("brightness_test", "亮度", "背光實測", Status.FAIL, f,
                "車機背光不是走 Android 系統亮度（可能由 MCU／原廠設定控制），FIT 桌面的亮度排程不會有效果，建議關閉，只保留日夜主題。",
                Fix("關閉 FIT 亮度排程", FixKind.DISABLE_BRIGHTNESS_SCHEDULE))
            !kept -> CheckResult("brightness_test", "亮度", "背光實測", Status.WARN, f,
                "寫入後被系統改回，可能是車燈調光或原廠自動亮度在控制。若開大燈時亮度會自己變，建議關閉 FIT 亮度排程以免互搶。",
                Fix("關閉 FIT 亮度排程", FixKind.DISABLE_BRIGHTNESS_SCHEDULE))
            screenChanged == true -> CheckResult("brightness_test", "亮度", "背光實測", Status.PASS, f, "背光可由 FIT 桌面控制。夜間開大燈時再確認一次不會被車燈調光覆蓋。")
            else -> CheckResult("brightness_test", "亮度", "背光實測", Status.TODO, f, "請按「有變化／沒變化」確認螢幕實際亮度。")
        }
    }
}

/* ---------------- GPS：首次定位時間、衛星數、速度 ---------------- */

data class GpsTestState(
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val firstFixMs: Long? = null,
    val satellitesInView: Int = 0,
    val satellitesUsed: Int = 0,
    val hasSpeed: Boolean = false,
    val accuracy: Float? = null,
)

object GpsTest {
    private val _state = MutableStateFlow(GpsTestState())
    val state: StateFlow<GpsTestState> = _state.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var lm: LocationManager? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val s = _state.value
            _state.value = s.copy(
                firstFixMs = s.firstFixMs ?: (SystemClock.elapsedRealtime() - s.startedAt),
                hasSpeed = s.hasSpeed || location.hasSpeed(),
                accuracy = if (location.hasAccuracy()) location.accuracy else s.accuracy,
            )
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Android 10 以下仍會呼叫")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _state.value = _state.value.copy(satellitesInView = status.satelliteCount, satellitesUsed = used)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        stop()
        val m = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = m
        _state.value = GpsTestState(running = true, startedAt = SystemClock.elapsedRealtime())
        return try {
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            @Suppress("DEPRECATION")
            m.registerGnssStatusCallback(gnss, main)
            true
        } catch (e: Exception) {
            _state.value = GpsTestState()
            false
        }
    }

    fun stop() {
        lm?.removeUpdates(listener)
        lm?.unregisterGnssStatusCallback(gnss)
        _state.value = _state.value.copy(running = false)
    }

    fun result(): CheckResult {
        val s = _state.value
        val elapsed = (SystemClock.elapsedRealtime() - s.startedAt) / 1000
        val f = "首次定位：${s.firstFixMs?.let { "${it / 1000} 秒" } ?: "未定位（已等 $elapsed 秒）"}；衛星 可見 ${s.satellitesInView}／使用 ${s.satellitesUsed}；" +
            "速度欄位：${if (s.hasSpeed) "有" else "無"}${s.accuracy?.let { "；精度 ±%.0f m".format(it) } ?: ""}。"
        return when {
            s.firstFixMs == null -> CheckResult("gps_test", "定位", "GPS 定位實測", Status.WARN, f,
                "在室內或地下室通常定位不到，請到戶外重測；若戶外 2 分鐘仍無定位，檢查 GPS 天線是否接好。")
            !s.hasSpeed -> CheckResult("gps_test", "定位", "GPS 定位實測", Status.WARN, f, "定位成功但沒有速度資料，狀態列 GPS 車速會一直顯示「—」，需改用位置差計算（記入下一版）。")
            s.firstFixMs > 60_000 -> CheckResult("gps_test", "定位", "GPS 定位實測", Status.WARN, f, "定位偏慢，剛發動時車速會延遲出現；屬車機 GPS 特性。")
            else -> CheckResult("gps_test", "定位", "GPS 定位實測", Status.PASS, f, "GPS 車速可正常顯示（行駛中再確認數值與儀表接近）。")
        }
    }
}

/* ---------------- 手動檢查（需人眼判斷的項目） ---------------- */

data class ManualItem(val id: String, val group: String, val title: String, val howTo: String, val ifBad: String)

val MANUAL_ITEMS = listOf(
    ManualItem("m_reverse", "手動檢查", "倒車影像", "FIT 桌面為預設桌面時打 R 檔。", "倒車畫面被蓋住或延遲：立即改回原廠桌面，回報給 AI 處理（最高優先）。"),
    ManualItem("m_home", "手動檢查", "Home 鍵", "在導航王、KKBOX 中按 Home 鍵或面板 Home。", "沒回到 FIT 桌面：記下按鍵位置，下一版處理。"),
    ManualItem("m_wake", "手動檢查", "熄火後再發動", "熄火 5 分鐘以上再發動。", "畫面空白、卡住或回到原廠桌面：記錄情況，下一版處理。"),
    ManualItem("m_wheel", "手動檢查", "方向盤按鍵", "在 FIT 桌面與導航中按切歌、音量、語音鍵。", "在 FIT 桌面失效：記下哪個鍵，配合按鍵測試結果處理。"),
    ManualItem("m_shield", "手動檢查", "神盾浮動窗", "開神盾後回到 FIT 桌面，看浮動窗與警示音。", "浮動窗消失：到神盾的「顯示在其他應用程式上層」開啟。"),
    ManualItem("m_headlight", "手動檢查", "車燈調光", "夜間開關大燈，看螢幕亮度是否自行改變。", "會自己變：關閉 FIT 亮度排程，只保留日夜主題。"),
    ManualItem("m_nav_landscape", "手動檢查", "導航王顯示", "從 FIT 桌面開導航王並開始導航。", "畫面方向、比例不對或返回後中斷：記錄情況。"),
    ManualItem("m_readability", "手動檢查", "行車中可讀性", "白天與夜間各看一次主畫面。", "字太小／太亮／反光：記下哪個區塊，調整字級或配色。"),
    ManualItem("m_touch", "手動檢查", "觸控範圍", "停車時點擊捷徑、播放鍵、所有 App。", "常點錯：記下哪個按鈕，放大觸控範圍。"),
)

fun manualResult(item: ManualItem, ok: Boolean?, note: String = ""): CheckResult = CheckResult(
    item.id, item.group, item.title,
    when (ok) { true -> Status.PASS; false -> Status.FAIL; null -> Status.TODO },
    item.howTo + when (ok) { true -> "　結果：正常。"; false -> "　結果：有問題。"; null -> "" } + if (note.isNotBlank()) "　備註：$note" else "",
    if (ok == false) item.ifBad else "",
)

/* ---------------- 匯出報告 ---------------- */

object Report {
    fun text(context: Context, results: Map<String, CheckResult>): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).format(Date())
        sb.appendLine("FIT 桌面 實機檢測報告")
        sb.appendLine("時間：$now　版本：${BuildInfo.versionName(context)}")
        sb.appendLine("裝置：${Build.MANUFACTURER} ${Build.MODEL}／Android ${Build.VERSION.RELEASE}")
        val counts = Status.entries.joinToString("　") { s -> "${s.mark}${s.label} ${results.values.count { it.status == s }}" }
        sb.appendLine(counts)
        sb.appendLine()
        val imp = CheckStore.improvements(results)
        sb.appendLine("【可改進的地方】${if (imp.isEmpty()) "目前沒有" else "${imp.size} 項"}")
        imp.forEachIndexed { i, r ->
            sb.appendLine("${i + 1}. ${r.status.mark} [${r.group}] ${r.title}")
            sb.appendLine("   發現：${r.finding}")
            if (r.suggestion.isNotBlank()) sb.appendLine("   建議：${r.suggestion}")
        }
        sb.appendLine()
        sb.appendLine("【全部項目】")
        CheckStore.sorted(results).forEach { r ->
            sb.appendLine("${r.status.mark} [${r.group}] ${r.title}：${r.finding}")
            if (r.suggestion.isNotBlank() && r.status != Status.WARN && r.status != Status.FAIL) sb.appendLine("   → ${r.suggestion}")
        }
        sb.appendLine()
        sb.appendLine("（報告不含序號、IMEI 等裝置識別碼）")
        return sb.toString()
    }

    /** 存到「下載」資料夾；回傳顯示用的位置說明。 */
    fun save(context: Context, text: String): String {
        val name = "FIT實機檢測_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.TAIWAN).format(Date()) + ".txt"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("無法建立檔案")
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("無法寫入檔案")
            "內部儲存空間／Download／$name"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            File(dir, name).writeText(text, Charsets.UTF_8)
            File(dir, name).absolutePath
        }
    }
}
