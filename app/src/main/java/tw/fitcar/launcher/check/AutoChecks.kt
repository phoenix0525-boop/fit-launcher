package tw.fitcar.launcher.check

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.data.AppRepository
import tw.fitcar.launcher.data.NavOpenMode
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.media.MediaRepository
import tw.fitcar.launcher.system.BrightnessController
import tw.fitcar.launcher.system.GpsSpeed
import java.io.File
import kotlin.math.roundToInt

/**
 * 自動檢測：每次開啟檢測畫面時重算，不改變任何系統設定。
 * 對照專案規格（AKZ ZX09 標示 8 GB／256 GB、8 核 2.7 GHz、Android 13、2000×1200）。
 */
object AutoChecks {

    private const val GB = 1_000_000_000.0

    fun run(context: Context): List<CheckResult> {
        val out = mutableListOf<CheckResult>()
        val apps = AppRepository.launchableApps(context)
        out += device()
        out += memory(context)
        out += storage()
        out += cpu()
        out += android()
        out += screen(context)
        out += defaultHome(context)
        out += navigation(context, apps)
        out += multiWindow(context)
        val placement=tw.fitcar.launcher.nav.SplitPlacement.observation.value
        val side=Prefs.get(context).state.value.splitNavigationSide
        out += CheckResult("split_placement","導航","分割排列（觀察）",Status.INFO,
            tw.fitcar.launcher.nav.SplitPlacement.describe(side,placement?.pane ?: tw.fitcar.launcher.nav.PaneSide.UNKNOWN)+
                (placement?.let { " 最近觀察：${java.text.SimpleDateFormat("M/d HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date(it.at))}；不是目前畫面持續成功的保證。" } ?: ""),
            "左右位置由原廠系統決定；選擇只記住排列目標，不會自動交換視窗。")
        out += googleAndVoice(context)
        out += music(context, apps)
        out += shield(context, apps)
        out += brightness(context)
        out += location(context)
        out += mapChecks()
        out += audio(context)
        out += boot(context)
        return out
    }

    private fun googleAndVoice(context: Context): List<CheckResult> {
        val google = tw.fitcar.launcher.nav.GoogleNavigation.isSelected(context)
        val hint = tw.fitcar.launcher.nav.GoogleGuidanceRepository.state.value
        val nav = when {
            !google -> CheckResult("google_hint", "導航", "Google 導航通知", Status.NA, "目前選擇其他導航；Google 通知不讀取。")
            !MediaRepository.isPermissionGranted(context) -> CheckResult("google_hint", "導航", "Google 導航通知", Status.TODO,
                "尚未授權通知存取。", fix = Fix("開啟通知存取", FixKind.OPEN_NOTIFICATION_ACCESS))
            !hint.connected -> CheckResult("google_hint", "導航", "Google 導航通知", Status.TODO, "通知服務未連線。")
            hint.fresh() -> CheckResult("google_hint", "導航", "Google 導航通知", Status.PASS, "收到 Google 地圖導航文字；停車時通知仍在即有效，行駛中 30～90 秒無新資料即隱藏。轉彎正確性仍需人工確認。")
            else -> CheckResult("google_hint", "導航", "Google 導航通知", Status.TODO, "沒有新鮮導航文字；請在 Google 地圖開始導航。沒有資料時不猜測方向與距離。")
        }
        val recognizer = context.packageManager.resolveActivity(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        val voice = CheckResult("voice_service", "操作", "語音辨識服務", if (recognizer == null) Status.TODO else Status.INFO,
            if (recognizer == null) "找不到可處理語音辨識的服務。請安裝或啟用中文語音辨識。" else "找到語音辨識入口；麥克風、中文辨識與指令執行仍需在車機測試。")
        return listOf(nav, voice)
    }

    private fun device() = CheckResult(
        "device", "車機硬體", "裝置識別", Status.INFO,
        "${Build.MANUFACTURER} ${Build.MODEL}／裝置 ${Build.DEVICE}／主板 ${Build.BOARD}／硬體 ${Build.HARDWARE}／品牌 ${Build.BRAND}",
        "若主板或硬體欄位出現 FYT、sp7731、ums512 等字樣再評估板型；目前仍不假設 FYT。",
    )

    private fun memory(context: Context): CheckResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val total = mi.totalMem / GB
        val avail = mi.availMem / GB
        val f = "可用給系統 %.1f GB（目前剩 %.1f GB）。".format(total, avail)
        return when {
            total >= 6.8 -> CheckResult("memory", "車機硬體", "記憶體（標示 8 GB）", Status.PASS, f + "符合 8 GB 等級（系統保留後通常顯示 7.x GB）。")
            total >= 5.0 -> CheckResult("memory", "車機硬體", "記憶體（標示 8 GB）", Status.FAIL, f + "低於 8 GB 等級，實體可能是 6 GB。",
                "車機桌面已很輕量，不影響使用；如需確認可再以 CPU-Z 類工具比對。")
            else -> CheckResult("memory", "車機硬體", "記憶體（標示 8 GB）", Status.FAIL, f + "與標示 8 GB 明顯不符，系統資訊可能被廠商改寫。",
                "建議記錄並向賣家反映；同時開導航＋音樂時留意是否被系統關閉。")
        }
    }

    private fun storage(): CheckResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes / GB
        val free = stat.availableBytes / GB
        val f = "使用者資料區 %.0f GB（剩 %.0f GB）。".format(total, free)
        return when {
            total >= 200 -> CheckResult("storage", "車機硬體", "儲存空間（標示 256 GB）", Status.PASS, f + "符合 256 GB 等級（系統分割區另計）。")
            total >= 100 -> CheckResult("storage", "車機硬體", "儲存空間（標示 256 GB）", Status.FAIL, f + "像是 128 GB 等級。", "實際可用空間足夠導航圖資與音樂快取；可向賣家確認規格。")
            else -> CheckResult("storage", "車機硬體", "儲存空間（標示 256 GB）", Status.FAIL, f + "與標示 256 GB 明顯不符。", "建議記錄並向賣家反映。")
        }
    }

    private fun cpu(): CheckResult {
        val cores = Runtime.getRuntime().availableProcessors()
        var maxKhz = 0L
        for (i in 0 until cores) {
            val v = readText("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")?.trim()?.toLongOrNull() ?: continue
            if (v > maxKhz) maxKhz = v
        }
        val hw = readText("/proc/cpuinfo")?.lineSequence()?.firstOrNull { it.startsWith("Hardware") }?.substringAfter(":")?.trim()
        val ghz = if (maxKhz > 0) "%.2f GHz".format(maxKhz / 1_000_000.0) else "無法讀取"
        val f = "$cores 核，最高 $ghz" + (hw?.let { "，$it" } ?: "") + "。"
        val ok = cores >= 8 && (maxKhz == 0L || maxKhz >= 2_500_000)
        return CheckResult("cpu", "車機硬體", "處理器（標示 UIS7870SC 8 核 2.7 GHz）", if (ok) Status.PASS else Status.INFO, f,
            if (ok) "" else "核心數或時脈低於標示，記錄下來即可，不影響 車機桌面。")
    }

    private fun android() = CheckResult(
        "android", "車機硬體", "Android 版本",
        if (Build.VERSION.SDK_INT >= 33) Status.PASS else Status.INFO,
        "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}），安全性修補 ${if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "—"}。",
        if (Build.VERSION.SDK_INT >= 33) "" else "實際版本低於畫面標示的 Android 13，系統資訊可能被改寫；車機桌面仍支援 Android 8 以上。",
    )

    @Suppress("DEPRECATION")
    private fun screen(context: Context): CheckResult {
        val dm = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        val w = maxOf(dm.widthPixels, dm.heightPixels)
        val h = minOf(dm.widthPixels, dm.heightPixels)
        val wDp = (w / dm.density).roundToInt()
        val hDp = (h / dm.density).roundToInt()
        val fontScale = context.resources.configuration.fontScale
        val f = "$w×$h px，${dm.densityDpi} dpi，約 $wDp×$hDp dp，字體縮放 ${"%.2f".format(fontScale)}。"
        return CheckResult("screen", "顯示", "顯示環境資訊", Status.INFO, f,
            "字體縮放本身不列為問題；請在手動可讀性測試記錄實際文字溢出或裁字。")
    }

    private fun defaultHome(context: Context): CheckResult {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val pkg = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val others = AppRepository.otherHomeApps(context).joinToString("、") { "${it.label}（${it.component.packageName}）" }.ifEmpty { "無" }
        return when (pkg) {
            context.packageName -> CheckResult("home", "桌面", "預設桌面", Status.PASS, "車機桌面已是預設桌面。其他桌面：$others。",
                "請完成「手動檢查」中的 Home 鍵、休眠喚醒、倒車影像項目。")
            else -> CheckResult("home", "桌面", "預設桌面", Status.TODO, "目前預設桌面：${pkg ?: "未選擇"}。其他桌面：$others。",
                "其他項目都檢查過後，再到設定 → 返回原廠桌面 → 變更預設桌面，選 車機桌面並做手動檢查。")
        }
    }

    private fun find(apps: List<AppEntry>, vararg words: String) =
        apps.filter { a -> words.any { w -> a.label.contains(w, ignoreCase = true) || a.component.packageName.contains(w, ignoreCase = true) } }

    private fun bindingCheck(
        context: Context, id: String, group: String, title: String, boundKey: String?,
        candidates: List<AppEntry>, kind: FixKind, appName: String,
    ): CheckResult {
        val bound = AppRepository.resolve(context, boundKey)
        val names = candidates.joinToString("、") { "${it.label}（${it.component.packageName}）" }
        return when {
            bound != null -> CheckResult(id, group, title, Status.PASS, "已綁定：${bound.label}（${bound.component.packageName}）。")
            boundKey != null -> CheckResult(id, group, title, Status.FAIL, "已綁定的 App 找不到，可能已移除或更新後改名。",
                "請重新選擇$appName。", candidates.firstOrNull()?.let { Fix("綁定 ${it.label}", kind, it.key) })
            candidates.isNotEmpty() -> CheckResult(id, group, title, Status.INFO, "尚未綁定；偵測到可能的$appName：$names。",
                "綁定後才能從 車機桌面直接開啟。", Fix("綁定 ${candidates.first().label}", kind, candidates.first().key))
            else -> CheckResult(id, group, title, Status.INFO, "尚未綁定，也找不到名稱含「$appName」的 App。", "請確認已安裝，或到設定 → App 綁定手動選擇。")
        }
    }

    private fun navigation(context: Context, apps: List<AppEntry>) = bindingCheck(
        context, "bind_nav", "導航", "導航王", Prefs.get(context).state.value.bindings.navigation,
        find(apps, "導航王", "kingway", "naviking"), FixKind.BIND_NAV, "導航王",
    )

    private fun shield(context: Context, apps: List<AppEntry>): List<CheckResult> {
        val s = Prefs.get(context).state.value.bindings.shield
        val cands = find(apps, "神盾", "inmirai")
        val bind = bindingCheck(context, "bind_shield", "測速", "神盾測速照相", s, cands, FixKind.BIND_SHIELD, "神盾")
        val pkg = AppRepository.resolve(context, s)?.component?.packageName ?: cands.firstOrNull()?.component?.packageName
        val overlay = CheckResult(
            "shield_overlay", "測速", "神盾浮動視窗權限", Status.TODO,
            "Android 不允許其他 App 讀取神盾的浮動窗權限，需手動確認。",
            "開啟神盾後回到 車機桌面，確認浮動窗與警示音仍在；若沒有，按下方按鈕到神盾的「顯示在其他應用程式上層」開啟。",
            pkg?.let { Fix("開啟神盾浮動窗設定", FixKind.OPEN_OVERLAY_SETTINGS, it) },
        )
        return listOf(bind, overlay)
    }

    private fun sysBool(name: String): Boolean? {
        val id = Resources.getSystem().getIdentifier(name, "bool", "android")
        return if (id == 0) null else try { Resources.getSystem().getBoolean(id) } catch (e: Exception) { null }
    }

    private fun multiWindow(context: Context): CheckResult {
        val multi = sysBool("config_supportsMultiWindow")
        val split = sysBool("config_supportsSplitScreenMultiWindow")
        val freeform = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val forceResize = Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0) == 1
        val last = Prefs.get(context).state.value.lastSplitResult
        val f = "系統多視窗：${yn(multi)}，分割畫面：${yn(split)}，自由視窗：${if (freeform) "有" else "無"}，強制可調整大小：${if (forceResize) "開" else "關"}。" +
            "實測：${when (last) { true -> "已偵測左右分割，排列需目視確認"; false -> "未進入分割"; null -> "尚未測試" }}。"
        return CheckResult("split", "導航", "分割畫面（實驗功能）", if (last == false || multi == false || split == false) Status.NA else Status.INFO,
            f, "導航預設全螢幕。分割畫面僅供手動選擇的實驗測試，不影響正常導航驗收。")
    }

    private fun yn(b: Boolean?) = when (b) { true -> "支援"; false -> "不支援"; null -> "未知" }

    private fun music(context: Context, apps: List<AppEntry>): List<CheckResult> {
        val prefs = Prefs.get(context).state.value
        val selected = AppRepository.resolve(context, prefs.bindings.music)
        val bind = CheckResult("bind_music", "音樂", "播放器選擇", if (selected == null) Status.INFO else Status.PASS,
            "模式=${prefs.mediaMode}；指定=${selected?.label ?: "未指定"}（${selected?.component?.packageName ?: "無"}）。固定模式只控制指定 App；跟隨模式優先記住的最後播放來源，否則跟隨其他播放中的 App。記憶來源=${MediaRepository.rememberedPackage(context) ?: "無"}。",
            "官方 YT Music 與 Morphe Music 使用不同套件，請從實際安裝清單分別選擇。")
        MediaRepository.refresh(context)
        val media = MediaRepository.state.value
        val source = "實際控制=${media.playerName ?: "無"}（${media.packageName ?: "無"}）；歌名=${media.title ?: "未提供"}；狀態=${media.playbackState}；控制 actions=${media.actions}；作用中=" +
            media.sessions.joinToString { "${it.packageName}:${it.state}" }
        val check = when {
            !media.permissionGranted -> CheckResult("media", "音樂", "目前播放器工作階段", Status.TODO,
                "尚無通知存取權限。${media.error.orEmpty()}", "開啟通知存取後再播放。", Fix("開啟通知存取", FixKind.OPEN_NOTIFICATION_ACCESS))
            !media.hasSession -> CheckResult("media", "音樂", "目前播放器工作階段", Status.TODO, source, "開啟指定播放器播放歌曲，再重新檢測。")
            !media.isPlaying -> CheckResult("media", "音樂", "目前播放器工作階段", Status.TODO, source, "指定來源目前未播放；播放後驗證歌名與控制對象。")
            media.title.isNullOrBlank() -> CheckResult("media", "音樂", "目前播放器工作階段", Status.FAIL, source, "播放器未提供歌名。")
            else -> CheckResult("media", "音樂", "目前播放器工作階段", Status.PASS, source,
                "報告、卡片與播放控制共用同一工作階段；未提供的控制按鈕停用。仍需實機確認控制結果。")
        }
        return listOf(bind, check, CheckResult("lyrics", "音樂", "歌詞", Status.INFO, if (context.getSharedPreferences("desktop_lyrics", Context.MODE_PRIVATE).getBoolean("lrclib", false)) "LRCLIB 桌面歌詞已啟用，實際收錄與同步需逐曲確認" else "桌面歌詞未啟用，可至 App 綁定開啟"))
    }

    private fun brightness(context: Context): CheckResult {
        val can = BrightnessController.canWrite(context)
        val auto = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) { false }
        val cur = BrightnessController.currentPercent(context)
        val schedule = Prefs.get(context).state.value.display.autoSchedule
        val f = "修改系統設定：${if (can) "已允許" else "未允許"}；系統自動亮度：${if (auto) "開" else "關"}；目前亮度約 ${cur ?: "—"}%。"
        return when {
            !can && schedule -> CheckResult("brightness_perm", "亮度", "亮度排程權限", Status.FAIL, f,
                "亮度排程已開但沒有權限，不會生效。", Fix("前往允許", FixKind.OPEN_WRITE_SETTINGS))
            auto && schedule -> CheckResult("brightness_perm", "亮度", "亮度排程權限", Status.INFO, f,
                "系統自動亮度開啟中，車機桌面排程寫入時會改為手動亮度；若想保留系統自動亮度，請關閉 車機桌面的亮度排程。",
                Fix("關閉 車機桌面亮度排程", FixKind.DISABLE_BRIGHTNESS_SCHEDULE))
            else -> CheckResult("brightness_perm", "亮度", "亮度排程權限", if (can || !schedule) Status.PASS else Status.TODO, f,
                "請到「互動測試 → 亮度」確認背光真的有變化、且不會被車燈調光改回。")
        }
    }

    private fun location(context: Context): CheckResult {
        val g = GpsSpeed.state.value
        val perm = GpsSpeed.hasPermission(context)
        return when {
            !perm -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.TODO, "尚未允許定位，狀態列不會顯示 GPS 車速。", "", Fix("允許定位", FixKind.REQUEST_LOCATION))
            !g.providerEnabled -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.TODO, "已允許，但系統 GPS 關閉。", "請在系統設定開啟定位。")
            else -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.PASS, "已允許且 GPS 開啟。", "請到「互動測試 → GPS」量測定位時間。")
        }
    }

    private fun audio(context: Context): CheckResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        val last = context.getSharedPreferences("volume_diagnostics", Context.MODE_PRIVATE).getString("last_readback", null)
        return CheckResult("audio", "操作", "媒體音量", Status.INFO, "Android 媒體音量 $v／$max${if (muted) "（靜音）" else ""}；fixed=${am.isVolumeFixed}。數值變化不代表原廠主機實際音量已改變。${last?.let { "\n最近回讀（不是實際聲音驗證）：$it" }.orEmpty()}",
            suggestion = "到「音量檢測」分別對照桌面控制、小芳及原廠按鍵，再回報實際聲音是否改變。")
    }

    /** 比對上次開啟時的開機時間，判斷熄火後是休眠喚醒還是重新開機。 */
    private fun mapChecks(): List<CheckResult> {
        val base = tw.fitcar.launcher.system.MapDiagnostics.base.value
        val address = tw.fitcar.launcher.system.MapDiagnostics.address.value
        val gps = GpsSpeed.state.value
        val gpsText = when {
            !gps.permission -> "定位尚未授權"
            !gps.providerEnabled -> "GPS 已關閉"
            gps.latitude == null || gps.longitude == null -> "等待定位"
            SystemClock.elapsedRealtime() - gps.fixElapsed >= 10_000 -> "GPS 定位資料已過期"
            else -> "GPS 定位有效"
        }
        fun observed(id: String, title: String, value: tw.fitcar.launcher.system.MapObservation) = CheckResult(
            id, "定位", title, if (value.time == 0L) Status.TODO else if ("失敗" in value.state || "逾時" in value.state) Status.FAIL else Status.INFO,
            "${value.state}；最近觀察=${if (value.time == 0L) "無" else java.util.Date(value.time).toString()}。這是最近觀察，非目前畫面持續成功的保證。")
        return listOf(observed("map_base", "免費底圖狀態", base),
            CheckResult("map_gps", "定位", "地圖 GPS 狀態", if (gpsText == "GPS 定位有效") Status.PASS else Status.TODO, gpsText),
            observed("map_address", "地址查詢狀態", address))
    }

    private fun boot(context: Context): CheckResult {
        return CheckResult("boot", "電源", "熄火測試流程", Status.INFO,
            "本次開機已 ${SystemClock.elapsedRealtime() / 60_000} 分鐘。",
            "請在手動檢查按「開始熄火測試」，再發動後按「完成測試」，另外確認畫面、音樂與 GPS。開機時間本身不代表喚醒正常。")
    }

    private fun readText(path: String): String? = try { File(path).readText() } catch (e: Exception) { null }
}
