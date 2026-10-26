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
        out += platform(context)
        out += memory(context)
        out += storage()
        out += cpu()
        out += android()
        out += screen(context)
        out += defaultHome(context)
        out += navigation(context, apps)
        out += multiWindow(context)
        out += navGuidance(context)
        out += music(context, apps)
        out += shield(context, apps)
        out += brightness(context)
        out += location(context)
        out += audio(context)
        out += boot(context)
        return out
    }

    private fun device() = CheckResult(
        "device", "車機硬體", "裝置識別", Status.INFO,
        "${Build.MANUFACTURER} ${Build.MODEL}／裝置 ${Build.DEVICE}／主板 ${Build.BOARD}／硬體 ${Build.HARDWARE}／品牌 ${Build.BRAND}",
        "若主板或硬體欄位出現 FYT、sp7731、ums512 等字樣再評估板型；目前仍不假設 FYT。",
    )

    /** 平台與 Google Play：只檢查套件是否存在，不呼叫任何私有介面。 */
    private fun platform(context: Context): List<CheckResult> {
        val found = AppRepository.installedPackages(context, tw.fitcar.launcher.data.FactoryApps.PLATFORM_MARKERS)
        val fyt = tw.fitcar.launcher.data.FactoryApps.detectPlatform(found) == tw.fitcar.launcher.data.Platform.FYT_SYU
        val platform = CheckResult(
            "platform", "車機硬體", "車機平台", Status.INFO,
            if (fyt) "偵測到 FYT／SYU 平台套件：${found.sorted().joinToString("、")}。" else "未偵測到 FYT／SYU 平台套件（${found.sorted().joinToString("、").ifEmpty { "無" }}）。",
            if (fyt) "原廠收音機、藍牙電話、藍牙音樂、EQ 等已補進「所有 App」與捷徑選單；FIT 桌面不呼叫 FYT 私有服務、不刷韌體。"
            else "原廠車機 App 若有一般圖示仍會出現在「所有 App」。",
        )
        val hasPlay = AppRepository.installedPackages(context, listOf("com.android.vending")).isNotEmpty()
        val play = CheckResult(
            "google_play", "車機硬體", "Google Play 商店", if (hasPlay) Status.PASS else Status.INFO,
            if (hasPlay) "車機有 Google Play 商店。" else "車機沒有 Google Play 商店。",
            if (hasPlay) "偵測到商店套件不等於已確認 Google 認證；是否受安裝與開發者驗證規則限制，仍須以車機 Play Protect 認證頁及實際安裝結果確認。"
            else "未偵測到 Play 商店；僅憑套件不存在無法判定 Google 認證狀態，也不能保證側載不受限制。",
        )
        return listOf(platform, play)
    }

    private fun memory(context: Context): CheckResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val total = mi.totalMem / GB
        val avail = mi.availMem / GB
        val f = "可用給系統 %.1f GB（目前剩 %.1f GB）。".format(total, avail)
        return when {
            total >= 6.8 -> CheckResult("memory", "車機硬體", "記憶體（標示 8 GB）", Status.PASS, f + "符合 8 GB 等級（系統保留後通常顯示 7.x GB）。")
            total >= 5.0 -> CheckResult("memory", "車機硬體", "記憶體（標示 8 GB）", Status.WARN, f + "低於 8 GB 等級，實體可能是 6 GB。",
                "FIT 桌面已很輕量，不影響使用；如需確認可再以 CPU-Z 類工具比對。")
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
            total >= 100 -> CheckResult("storage", "車機硬體", "儲存空間（標示 256 GB）", Status.WARN, f + "像是 128 GB 等級。", "實際可用空間足夠導航圖資與音樂快取；可向賣家確認規格。")
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
        return CheckResult("cpu", "車機硬體", "處理器（標示 UIS7870SC 8 核 2.7 GHz）", if (ok) Status.PASS else Status.WARN, f,
            if (ok) "" else "核心數或時脈低於標示，記錄下來即可，不影響 FIT 桌面。")
    }

    private fun android() = CheckResult(
        "android", "車機硬體", "Android 版本",
        if (Build.VERSION.SDK_INT >= 33) Status.PASS else Status.WARN,
        "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}），安全性修補 ${if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "—"}。",
        if (Build.VERSION.SDK_INT >= 33) "" else "實際版本低於畫面標示的 Android 13，系統資訊可能被改寫；FIT 桌面仍支援 Android 8 以上。",
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
        val issues = mutableListOf<String>()
        if (w != 2000 || h != 1200) issues += "解析度不是規格的 2000×1200，版面比例需實機確認。"
        if (wDp < 960) issues += "畫面寬度僅 $wDp dp，FIT 桌面版面會偏擠，建議縮小狀態列與捷徑字級。"
        if (wDp > 1400) issues += "畫面寬度 $wDp dp，文字和按鈕會顯得偏小，建議放大字級。"
        if (fontScale > 1.15f) issues += "系統字體放大 ${"%.0f".format(fontScale * 100)}%，部分文字可能被截斷。"
        return CheckResult("screen", "顯示", "螢幕與版面", if (issues.isEmpty()) Status.PASS else Status.WARN, f,
            if (issues.isEmpty()) "FIT 桌面以約 1000–1333 dp 寬設計，目前尺寸合適。" else issues.joinToString(""))
    }

    private fun defaultHome(context: Context): CheckResult {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val pkg = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val others = AppRepository.otherHomeApps(context).joinToString("、") { "${it.label}（${it.component.packageName}）" }.ifEmpty { "無" }
        return when (pkg) {
            context.packageName -> CheckResult("home", "桌面", "預設桌面", Status.PASS, "FIT 桌面已是預設桌面。其他桌面：$others。",
                "請完成「手動檢查」中的 Home 鍵、休眠喚醒、倒車影像項目。")
            else -> CheckResult("home", "桌面", "預設桌面", Status.TODO, "目前預設桌面：${pkg ?: "未選擇"}。其他桌面：$others。",
                "其他項目都檢查過後，再到設定 → 返回原廠桌面 → 變更預設桌面，選 FIT 桌面並做手動檢查。")
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
            candidates.isNotEmpty() -> CheckResult(id, group, title, Status.WARN, "尚未綁定；偵測到可能的$appName：$names。",
                "綁定後才能從 FIT 桌面直接開啟。", Fix("綁定 ${candidates.first().label}", kind, candidates.first().key))
            else -> CheckResult(id, group, title, Status.WARN, "尚未綁定，也找不到名稱含「$appName」的 App。", "請確認已安裝，或到設定 → App 綁定手動選擇。")
        }
    }

    /** 導航王是否在通知列提供轉彎文字（決定桌面導航區能不能顯示轉彎提示）。 */
    private fun navGuidance(context: Context): CheckResult {
        val s = tw.fitcar.launcher.nav.NavGuidanceRepository.state.value
        val title = "導航轉彎提示（讀取導航通知）"
        return when {
            !MediaRepository.isPermissionGranted(context) -> CheckResult("nav_guidance", "導航", title, Status.TODO,
                "尚未開啟通知存取。", "開啟後用導航王導航一段路，再回到這裡重新檢測。", Fix("開啟通知存取", FixKind.OPEN_NOTIFICATION_ACCESS))
            s.guidance != null -> CheckResult("nav_guidance", "導航", title, Status.PASS,
                "已收到導航通知：「${s.guidance.primary}」" + (s.guidance.distance?.let { "，距離 $it" } ?: "") + "。",
                "桌面導航區會顯示轉彎、距離與路名；行車中確認更新速度是否跟得上。")
            s.lastHadText == false -> CheckResult("nav_guidance", "導航", title, Status.WARN,
                "收到導航 App 的進行中通知，但通知沒有文字（可能使用自訂版面）。",
                "導航區無法顯示轉彎提示，維持「開始導航／全螢幕」按鈕；可改用分割畫面同時看地圖。")
            else -> CheckResult("nav_guidance", "導航", title, Status.TODO,
                "尚未收到綁定導航 App 的導航通知。", "用導航王設定目的地開始導航，回到 FIT 桌面看導航區是否出現轉彎提示，再重新檢測。")
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
            "開啟神盾後回到 FIT 桌面，確認浮動窗與警示音仍在；若沒有，按下方按鈕到神盾的「顯示在其他應用程式上層」開啟。",
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
        val mode = Prefs.get(context).state.value.navOpenMode
        val f = "系統多視窗：${yn(multi)}，分割畫面：${yn(split)}，自由視窗：${if (freeform) "有" else "無"}，強制可調整大小：${if (forceResize) "開" else "關"}。" +
            "實測：${when (last) { true -> "成功"; false -> "未進入分割"; null -> "尚未測試" }}。"
        return when {
            last == true -> CheckResult("split", "導航", "分割畫面", Status.PASS, f,
                if (mode == NavOpenMode.SPLIT_FIRST) "維持「分割畫面優先」。" else "車機可分割，可改回「分割畫面優先」。",
                if (mode == NavOpenMode.SPLIT_FIRST) null else Fix("改為分割畫面優先", FixKind.NAV_SPLIT))
            last == false || multi == false || split == false -> CheckResult("split", "導航", "分割畫面", Status.WARN, f,
                "車機無法以程式進入分割畫面。建議把導航改為「一律全螢幕」，省去每次約 2 秒的嘗試；可先用系統最近使用畫面試試手動分割。",
                if (mode == NavOpenMode.FULLSCREEN) null else Fix("改為一律全螢幕", FixKind.NAV_FULLSCREEN))
            else -> CheckResult("split", "導航", "分割畫面", Status.TODO, f, "系統設定看起來支援，請到「互動測試 → 分割畫面」實測。")
        }
    }

    private fun yn(b: Boolean?) = when (b) { true -> "支援"; false -> "不支援"; null -> "未知" }

    private fun music(context: Context, apps: List<AppEntry>): List<CheckResult> {
        val prefs = Prefs.get(context).state.value
        val cands = find(apps, "kkbox")
        val bind = bindingCheck(context, "bind_music", "音樂", "KKBOX", prefs.bindings.music, cands, FixKind.BIND_MUSIC, "KKBOX")
        if (!MediaRepository.isPermissionGranted(context)) {
            return listOf(bind, CheckResult("media", "音樂", "KKBOX 播放資訊（MediaSession）", Status.TODO,
                "尚未開啟 FIT 桌面的通知存取，無法讀取播放資訊。", "開啟後播放 KKBOX，再重新檢測。",
                Fix("開啟通知存取", FixKind.OPEN_NOTIFICATION_ACCESS)))
        }
        val controllers: List<MediaController> = try {
            val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            m.getActiveSessions(MediaRepository.listenerComponent(context))
        } catch (e: SecurityException) { emptyList() }
        val musicPkg = AppRepository.resolve(context, prefs.bindings.music)?.component?.packageName ?: cands.firstOrNull()?.component?.packageName
        val kk = controllers.firstOrNull { it.packageName == musicPkg }
            ?: controllers.firstOrNull { it.packageName.contains("kkbox", ignoreCase = true) }
        val others = controllers.joinToString("、") { it.packageName }.ifEmpty { "無" }
        if (kk == null) {
            return listOf(bind, CheckResult("media", "音樂", "KKBOX 播放資訊（MediaSession）", Status.TODO,
                "目前沒有偵測到 KKBOX 的媒體工作階段（作用中：$others）。", "請開 KKBOX 播放一首歌，回到這裡按「重新檢測」。"))
        }
        val md = kk.metadata
        val ps = kk.playbackState
        val actions = ps?.actions ?: 0L
        fun has(a: Long) = actions and a != 0L
        val fields = listOf(
            "歌名" to (md?.getString(MediaMetadata.METADATA_KEY_TITLE) != null),
            "歌手" to (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) != null),
            "專輯" to (md?.getString(MediaMetadata.METADATA_KEY_ALBUM) != null),
            "封面" to (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null || md?.getBitmap(MediaMetadata.METADATA_KEY_ART) != null ||
                md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) != null),
            "長度" to ((md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) > 0),
        )
        val controls = listOf(
            "播放" to (has(PlaybackState.ACTION_PLAY) || has(PlaybackState.ACTION_PLAY_PAUSE)),
            "暫停" to (has(PlaybackState.ACTION_PAUSE) || has(PlaybackState.ACTION_PLAY_PAUSE)),
            "上一首" to has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            "下一首" to has(PlaybackState.ACTION_SKIP_TO_NEXT),
            "拖曳進度" to has(PlaybackState.ACTION_SEEK_TO),
        )
        fun fmt(l: List<Pair<String, Boolean>>) = l.joinToString("　") { "${if (it.second) "✓" else "✗"}${it.first}" }
        val missingFields = fields.filter { !it.second }.map { it.first }
        val missingControls = controls.filter { !it.second }.map { it.first }
        val sugg = buildList {
            if ("歌名" in missingFields || "歌手" in missingFields) add("KKBOX 未提供歌名／歌手，音樂卡會顯示「App 未提供」；可試更新 KKBOX。")
            if ("封面" in missingFields) add("沒有封面圖，音樂卡改為只顯示文字（已自動處理）。")
            if ("拖曳進度" in missingControls) add("不支援拖曳進度，進度條會是唯讀（已自動處理）。")
            if ("上一首" in missingControls || "下一首" in missingControls) add("缺少上下首控制，對應按鈕會變灰；方向盤切歌是否有效請在按鍵測試確認。")
        }
        val status = when {
            "歌名" in missingFields -> Status.FAIL
            sugg.isNotEmpty() -> Status.WARN
            else -> Status.PASS
        }
        return listOf(bind, CheckResult("media", "音樂", "KKBOX 播放資訊（MediaSession）", status,
            "套件 ${kk.packageName}。欄位：${fmt(fields)}。控制：${fmt(controls)}。",
            sugg.joinToString("").ifEmpty { "KKBOX 提供完整資訊，音樂卡所有功能可用。歌詞仍無合法來源。" }))
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
            !can && schedule -> CheckResult("brightness_perm", "亮度", "亮度排程權限", Status.WARN, f,
                "亮度排程已開但沒有權限，不會生效。", Fix("前往允許", FixKind.OPEN_WRITE_SETTINGS))
            auto && schedule -> CheckResult("brightness_perm", "亮度", "亮度排程權限", Status.WARN, f,
                "系統自動亮度開啟中，FIT 桌面排程寫入時會改為手動亮度；若想保留系統自動亮度，請關閉 FIT 桌面的亮度排程。",
                Fix("關閉 FIT 亮度排程", FixKind.DISABLE_BRIGHTNESS_SCHEDULE))
            else -> CheckResult("brightness_perm", "亮度", "亮度排程權限", if (can || !schedule) Status.PASS else Status.TODO, f,
                "請到「互動測試 → 亮度」確認背光真的有變化、且不會被車燈調光改回。")
        }
    }

    private fun location(context: Context): CheckResult {
        val g = GpsSpeed.state.value
        val perm = GpsSpeed.hasPermission(context)
        val approximate = GpsSpeed.hasApproximatePermission(context)
        return when {
            !perm && approximate -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.WARN,
                "目前只允許約略定位，無法可靠顯示 GPS 車速。", "請到 FIT 桌面設定 → 權限 → 定位，開啟精確位置。",
                Fix("重新要求精確定位", FixKind.REQUEST_LOCATION))
            !perm -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.WARN, "尚未允許精確定位，狀態列不會顯示 GPS 車速。", "", Fix("允許精確定位", FixKind.REQUEST_LOCATION))
            !g.providerEnabled -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.WARN, "已允許，但系統 GPS 關閉。", "請在系統設定開啟定位。")
            else -> CheckResult("gps_perm", "定位", "GPS 車速權限", Status.PASS, "已允許且 GPS 開啟。", "請到「互動測試 → GPS」量測定位時間。")
        }
    }

    private fun audio(context: Context): CheckResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        return CheckResult("audio", "操作", "媒體音量", Status.INFO, "媒體音量 $v／$max${if (muted) "（靜音）" else ""}；手勢測試會以此判斷音量是否改變。")
    }

    /** 比對上次開啟時的開機時間，判斷熄火後是休眠喚醒還是重新開機。 */
    private fun boot(context: Context): CheckResult {
        val sp = context.getSharedPreferences("fit_checks_boot", Context.MODE_PRIVATE)
        val bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val prev = sp.getLong("boot_at", 0L)
        val prevSeen = sp.getLong("seen_at", 0L)
        sp.edit().putLong("boot_at", bootAt).putLong("seen_at", System.currentTimeMillis()).apply()
        val upMin = SystemClock.elapsedRealtime() / 60_000
        val sameBoot = prev != 0L && kotlin.math.abs(prev - bootAt) < 60_000
        val f = "本次開機已 $upMin 分鐘。" + when {
            prev == 0L -> "第一次檢測，熄火再發動後請重開此頁比對。"
            sameBoot -> "與上次檢測（${(System.currentTimeMillis() - prevSeen) / 60_000} 分鐘前）是同一次開機 → 熄火後是「休眠喚醒」。"
            else -> "與上次檢測不同次開機 → 熄火後車機會「重新開機」或曾重開。"
        }
        val sugg = when {
            prev == 0L -> ""
            sameBoot -> "休眠喚醒時 FIT 桌面會保留狀態；請在手動檢查確認喚醒後畫面、音樂與 GPS 正常。"
            else -> "若每次熄火都重開機，開機後亮度與主題會自動重算（已處理）；請確認開機後預設桌面仍是 FIT 桌面。"
        }
        return CheckResult("boot", "電源", "熄火後的行為", if (prev == 0L) Status.TODO else Status.INFO, f, sugg)
    }

    private fun readText(path: String): String? = try { File(path).readText() } catch (e: Exception) { null }
}
