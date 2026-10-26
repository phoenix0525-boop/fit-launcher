package tw.fitcar.launcher.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.fitcar.launcher.check.BrightnessTest
import tw.fitcar.launcher.check.CheckResult
import tw.fitcar.launcher.check.CheckStore
import tw.fitcar.launcher.check.Fix
import tw.fitcar.launcher.check.GROUPS
import tw.fitcar.launcher.check.GestureKind
import tw.fitcar.launcher.check.GestureTest
import tw.fitcar.launcher.check.GpsTest
import tw.fitcar.launcher.check.KeyLog
import tw.fitcar.launcher.check.MANUAL_ITEMS
import tw.fitcar.launcher.check.Report
import tw.fitcar.launcher.check.Status
import tw.fitcar.launcher.check.manualResult
import tw.fitcar.launcher.system.BrightnessController
import tw.fitcar.launcher.system.GpsSpeed

class CheckerActions(
    val back: () -> Unit,
    val rerun: () -> Unit,
    val applyFix: (Fix) -> Unit,
    val startSplitTest: () -> Unit,
    val requestLocation: () -> Unit,
    val requestWriteSettings: () -> Unit,
)

private val TABS = listOf("可改進的地方", "全部結果", "手勢測試", "按鍵測試", "亮度測試", "分割畫面", "GPS 測試", "手動檢查", "匯出報告")

@Composable
fun CheckerScreen(actions: CheckerActions) {
    val p = LocalPalette.current
    val results by CheckStore.results.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val improvements = CheckStore.improvements(results)
    Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("實機檢測", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Hint(Status.entries.joinToString("　") { s -> "${s.mark}${s.label} ${results.values.count { it.status == s }}" }, size = 15)
            }
            SecondaryButton("重新檢測", actions.rerun)
            PrimaryButton("返回", actions.back, Modifier.heightIn(min = 72.dp), icon = { GlyphIcon(Glyph.BACK, size = 26.dp, tint = if (p.isNight) p.bgDeep else Color.White) })
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.width(240.dp).fillMaxHeight().clip(RadiusLg).background(p.surface).border(1.dp, p.line, RadiusLg)
                    .verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TABS.forEachIndexed { i, name ->
                    val selected = i == tab
                    val label = if (i == 0 && improvements.isNotEmpty()) "$name（${improvements.size}）" else name
                    Text(
                        label,
                        Modifier.fillMaxWidth().clip(RadiusMd)
                            .background(if (selected) p.accentSoft else Color.Transparent)
                            .clickable { tab = i }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        color = if (selected) p.accentStrong else p.text,
                        fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> ImprovementsTab(improvements, results.values.count { it.status == Status.TODO }, actions)
                    1 -> AllResultsTab(results, actions)
                    2 -> GestureTab()
                    3 -> KeysTab()
                    4 -> BrightnessTab(actions)
                    5 -> SplitTab(results["split"], actions)
                    6 -> GpsTab(actions)
                    7 -> ManualTab(results)
                    else -> ReportTab(results)
                }
            }
        }
    }
}

@Composable
private fun statusColor(s: Status): Color {
    val p = LocalPalette.current
    return when (s) {
        Status.FAIL -> p.danger
        Status.NA, Status.HISTORY -> p.textSoft
        Status.PASS -> p.success
        Status.TODO -> p.accent
        Status.INFO -> p.textSoft
    }
}

@Composable
fun ResultCard(r: CheckResult, onFix: ((Fix) -> Unit)?) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${r.status.mark} ${r.status.label}", color = statusColor(r.status), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Pill(r.group)
            Text(r.title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
        Hint(r.finding, size = 14, color = p.text)
        if (r.appVersion.isNotBlank() || r.status == Status.HISTORY) {
            Hint("版本：${r.appVersion.ifBlank { "舊版未記錄" }}；時間：${if (r.time > 0) java.util.Date(r.time).toString() else "尚未測試"}", size = 12)
            Hint("情境：${r.scenario.ifBlank { "未記錄" }}；備註：${r.notes.ifBlank { "無" }}", size = 12)
        }
        if (r.suggestion.isNotBlank()) Hint("建議：${r.suggestion}", size = 14)
        val fix = r.fix
        if (fix != null && onFix != null) SecondaryButton(fix.label, { onFix(fix) })
    }
}

@Composable
private fun ImprovementsTab(list: List<CheckResult>, todo: Int, actions: CheckerActions) {
    Section("可改進的地方") {
        if (list.isEmpty()) {
            Hint("目前沒有需要改進的項目。" + if (todo > 0) "還有 $todo 項待測試，請完成互動測試與手動檢查。" else "", size = 16)
        } else {
            Hint("依嚴重程度排列。有按鈕的項目可以直接在 FIT 桌面套用建議；其他項目請匯出報告交給 AI 處理。", size = 14)
        }
    }
    list.forEach { ResultCard(it, actions.applyFix) }
}

@Composable
private fun AllResultsTab(results: Map<String, CheckResult>, actions: CheckerActions) {
    val p = LocalPalette.current
    val sorted = CheckStore.sorted(results)
    GROUPS.forEach { g ->
        val items = sorted.filter { it.group == g }
        if (items.isNotEmpty()) {
            Text(g, color = p.textSoft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            items.forEach { ResultCard(it, actions.applyFix) }
        }
    }
}

@Composable
private fun GestureTab() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val results by CheckStore.results.collectAsState()
    var armed by remember { mutableStateOf<GestureKind?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var seen by remember { mutableStateOf(setOf<GestureKind>()) }
    var lastSeen by remember { mutableStateOf("尚未偵測到手勢") }

    Section("原車機音量手勢在 FIT 桌面內是否仍有效") {
        Hint("1. 先調一個中間音量並播放音樂。2. 按下要測的手勢。3. 5 秒內在下方灰色區域做該手勢。系統會比對前後音量，判斷手勢是由車機系統處理（不需實作），還是只存在原廠桌面（需要實作）。", size = 14)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GestureKind.entries.forEach { k ->
                val busy = armed != null
                if (armed == k) PrimaryButton("${k.label}（$countdown）", {}) else SecondaryButton(k.label, {
                    val before = GestureTest.snapshot(ctx)
                    armed = k; seen = emptySet(); countdown = 5
                    scope.launch {
                        while (countdown > 0) { delay(1000); countdown-- }
                        delay(800)
                        val after = GestureTest.snapshot(ctx)
                        CheckStore.put(ctx, GestureTest.evaluate(k, before, after, k in seen))
                        armed = null
                    }
                }, enabled = !busy)
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().height(320.dp).clip(RadiusLg).background(p.surfaceMuted).border(2.dp, if (armed != null) p.accent else p.line, RadiusLg)
            .pointerInput(Unit) {
                var lastTapAt = 0L
                var lastTapPointers = 0
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val downAt = first.uptimeMillis
                    val start = first.position
                    var maxPointers = 1
                    var twoStartY: Float? = null
                    var twoLastY = 0f
                    var moved = 0f
                    var upAt = downAt
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.size > maxPointers) maxPointers = pressed.size
                        if (pressed.size >= 2) {
                            val y = pressed.take(2).map { it.position.y }.average().toFloat()
                            if (twoStartY == null) twoStartY = y
                            twoLastY = y
                        }
                        pressed.firstOrNull()?.let { moved = maxOf(moved, (it.position - start).getDistance()) }
                        ev.changes.forEach { upAt = maxOf(upAt, it.uptimeMillis); it.consume() }
                        if (ev.changes.none { it.pressed }) break
                    }
                    val dy = twoStartY?.let { twoLastY - it }
                    val swipe = 80.dp.toPx()
                    val kind: GestureKind? = when {
                        maxPointers >= 2 && dy != null && dy < -swipe -> GestureKind.TWO_UP
                        maxPointers >= 2 && dy != null && dy > swipe -> GestureKind.TWO_DOWN
                        upAt - downAt < 300 && moved < 30.dp.toPx() -> {
                            if (downAt - lastTapAt < 450 && lastTapPointers == maxPointers) {
                                lastTapAt = 0L
                                if (maxPointers >= 2) GestureKind.DOUBLE_TAP_TWO else GestureKind.DOUBLE_TAP_ONE
                            } else {
                                lastTapAt = upAt; lastTapPointers = maxPointers; null
                            }
                        }
                        else -> null
                    }
                    if (kind != null) {
                        seen = seen + kind
                        lastSeen = "App 偵測到：${kind.label}"
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(armed?.let { "請現在做：${it.label}" } ?: "手勢測試區", color = p.text, fontSize = 24.sp)
            Hint(lastSeen, size = 15)
        }
    }
    GestureKind.entries.mapNotNull { results[it.id] }.forEach { ResultCard(it, null) }
}

@Composable
private fun KeysTab() {
    val ctx = LocalContext.current
    val events by KeyLog.events.collectAsState()
    val results by CheckStore.results.collectAsState()
    DisposableEffect(Unit) {
        KeyLog.recording = true
        onDispose { KeyLog.recording = false }
    }
    Section("方向盤／面板按鍵") {
        Hint("停留在此頁時，按方向盤的切歌、音量、語音、通話鍵與面板按鍵。下方會列出 App 實際收到的按鍵。收不到不一定是問題：很多車機由系統直接處理方向盤按鍵。", size = 14)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("儲存結果", { CheckStore.put(ctx, KeyLog.result()) })
            SecondaryButton("清除", { KeyLog.clear() })
        }
        if (events.isEmpty()) Hint("尚未收到按鍵。", size = 15)
        events.forEach { e ->
            Hint("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date(e.time))}　${e.name}（${e.code}）", size = 15, color = LocalPalette.current.text)
        }
    }
    results["keys"]?.let { ResultCard(it, null) }
}

@Composable
private fun BrightnessTab(actions: CheckerActions) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val results by CheckStore.results.collectAsState()
    var phase by remember { mutableStateOf("idle") }
    var outcome by remember { mutableStateOf<BrightnessTest.Outcome?>(null) }
    Section("背光實測") {
        Hint("會把系統亮度暫時改成明顯不同的值 4 秒，再自動恢復。請注意螢幕有沒有真的變亮或變暗，並確認期間數值沒被系統改回。", size = 14)
        if (!BrightnessController.canWrite(ctx)) {
            Hint("需要先允許「修改系統設定」。", size = 15)
            PrimaryButton("前往允許", actions.requestWriteSettings)
        } else {
            PrimaryButton(if (phase == "running") "測試中…請看螢幕" else "開始測試", {
                phase = "running"
                scope.launch {
                    val o = BrightnessTest.run(ctx)
                    outcome = o
                    phase = if (o == null) "idle" else "ask"
                    if (o != null) CheckStore.put(ctx, BrightnessTest.result(o, null))
                }
            }, enabled = phase != "running")
            val o = outcome
            if (phase == "ask" && o != null) {
                Text("剛才螢幕亮度有明顯變化嗎？", color = LocalPalette.current.text, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("有變化", { CheckStore.put(ctx, BrightnessTest.result(o, true)); phase = "done" })
                    SecondaryButton("沒變化", { CheckStore.put(ctx, BrightnessTest.result(o, false)); phase = "done" })
                }
            }
        }
    }
    results["brightness_test"]?.let { ResultCard(it, actions.applyFix) }
}

@Composable
private fun SplitTab(auto: CheckResult?, actions: CheckerActions) {
    val ctx = LocalContext.current
    val results by CheckStore.results.collectAsState()
    Section("分割畫面實測") {
        Hint("按「開始測試」會開啟 FIT 側欄，並嘗試把導航王開在旁邊；約 2 秒後自動記錄結果。回到 FIT 桌面後，再進入 設定 → 實機檢測 查看結果。", size = 14)
        PrimaryButton("開始測試", actions.startSplitTest)
        Hint("也可以試試系統的手動分割（例如在最近使用畫面長按 App 圖示選「分割畫面」），結果在這裡記錄：", size = 14)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("手動分割可以", {
                CheckStore.put(ctx, CheckResult("split_manual", "導航", "手動分割畫面", Status.PASS, "使用者確認可用系統手勢手動分割。",
                    "程式自動分割若失敗，可改「一律全螢幕」，需要時再手動分割。"))
            })
            SecondaryButton("手動也不行", {
                CheckStore.put(ctx, CheckResult("split_manual", "導航", "手動分割畫面", Status.INFO, "使用者確認系統沒有分割畫面功能。",
                    "建議導航改「一律全螢幕」；音樂與捷徑改在導航王上方用浮動控制，列入下一版評估。",
                    Fix("改為一律全螢幕", tw.fitcar.launcher.check.FixKind.NAV_FULLSCREEN)))
            })
        }
    }
    auto?.let { ResultCard(it, actions.applyFix) }
    results["split_manual"]?.let { ResultCard(it, actions.applyFix) }
}

@Composable
private fun GpsTab(actions: CheckerActions) {
    val ctx = LocalContext.current
    val s by GpsTest.state.collectAsState()
    val results by CheckStore.results.collectAsState()
    var now by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(s.running) { while (s.running) { now = android.os.SystemClock.elapsedRealtime(); delay(1000) } }
    DisposableEffect(Unit) { onDispose { if (GpsTest.state.value.running) GpsTest.stop() } }
    Section("GPS 定位實測") {
        Hint("請在戶外、車停在空曠處測試。會記錄首次定位時間、衛星數與是否有速度資料。", size = 14)
        if (!GpsSpeed.hasPermission(ctx)) {
            PrimaryButton("允許定位", actions.requestLocation)
        } else if (!s.running) {
            PrimaryButton("開始量測", { if (!GpsTest.start(ctx)) Toast.makeText(ctx, "無法啟動 GPS", Toast.LENGTH_LONG).show() })
        } else {
            Text("量測中 ${(now - s.startedAt) / 1000} 秒", color = LocalPalette.current.text, fontSize = 20.sp)
            Hint("首次定位：${s.firstFixMs?.let { "${it / 1000} 秒" } ?: "尚未"}　衛星 可見 ${s.satellitesInView}／使用 ${s.satellitesUsed}　速度欄位：${if (s.hasSpeed) "有" else "尚無"}", size = 16, color = LocalPalette.current.text)
            PrimaryButton("停止並儲存結果", { CheckStore.put(ctx, GpsTest.result()); GpsTest.stop() })
        }
    }
    results["gps_test"]?.let { ResultCard(it, null) }
}

@Composable
private fun ManualTab(results: Map<String, CheckResult>) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    Section("手動檢查") {
        Hint("需要人眼判斷的項目。倒車影像請務必最先確認。", size = 14)
    }
    IgnitionPanel()
    MANUAL_ITEMS.forEach { item ->
        var note by rememberSaveable(item.id) { mutableStateOf("") }
        var scenario by rememberSaveable(item.id) { mutableStateOf("") }
        var testScenario by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
        var testVersion by rememberSaveable(item.id) { mutableStateOf("") }
        var testTime by rememberSaveable(item.id) { mutableStateOf(0L) }
        val r = results[item.id]
        Column(
            Modifier.fillMaxWidth().clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(r?.let { "${it.status.mark} ${it.status.label}" } ?: "☐ 待測試", color = statusColor(r?.status ?: Status.TODO), fontSize = 15.sp)
            }
            Hint("做法：${item.howTo}", size = 14)
            Hint("有問題時：${item.ifBad}", size = 13)
            SecondaryButton("開始本項測試", {
                testScenario = CheckStore.scenario(ctx)
                testVersion = CheckStore.version(ctx)
                testTime = System.currentTimeMillis()
            })
            Hint(testScenario ?: "請先開始測試，記錄當時預設桌面與播放器情境。", size = 12)
            OutlinedTextField(value = scenario, onValueChange = { scenario = it }, label = { Text("測試情境（例如使用中的 App、白天／夜間）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("備註（問題位置、裁字等）") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("正常", { CheckStore.put(ctx, manualResult(item, true, note, testScenario.orEmpty() + "; " + scenario).copy(appVersion = testVersion, contextKey = testScenario.orEmpty(), time = testTime)) }, enabled = testScenario != null)
                SecondaryButton("有問題", { CheckStore.put(ctx, manualResult(item, false, note, testScenario.orEmpty() + "; " + scenario).copy(appVersion = testVersion, contextKey = testScenario.orEmpty(), time = testTime)) }, enabled = testScenario != null)
                SecondaryButton("未測", { CheckStore.put(ctx, manualResult(item, null, note, testScenario.orEmpty() + "; " + scenario).copy(appVersion = testVersion, contextKey = testScenario.orEmpty(), time = testTime)) }, enabled = testScenario != null)
            }
        }
    }
}

@Composable
private fun IgnitionPanel() {
    val ctx = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val test = remember(revision) { tw.fitcar.launcher.check.IgnitionTest.read(ctx) }
    var screen by rememberSaveable(test.startedAt) { mutableStateOf<Boolean?>(null) }
    var music by rememberSaveable(test.startedAt) { mutableStateOf<Boolean?>(null) }
    var gps by rememberSaveable(test.startedAt) { mutableStateOf<Boolean?>(null) }
    var note by rememberSaveable(test.startedAt) { mutableStateOf("") }
    Section("熄火後再發動") {
        Hint("停車時開始測試，熄火後再發動，再完成測試。相同開機只是一項觀察，不能代表休眠喚醒正常。", size = 14)
        Hint(test.description, size = 14)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("開始熄火測試", { tw.fitcar.launcher.check.IgnitionTest.start(ctx); revision++ })
            SecondaryButton("完成測試", {
                tw.fitcar.launcher.check.IgnitionTest.complete(ctx)
                revision++
            }, enabled = test.startedAt > 0 && test.completedAt == 0L)
        }
        if (test.completedAt > 0) {
            listOf("畫面恢復" to screen, "音樂恢復" to music, "GPS 恢復" to gps).forEachIndexed { index, (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Hint("$label：${when (value) { true -> "正常"; false -> "有問題"; null -> "待確認" }}", size = 14)
                    fun set(ok: Boolean) { when(index) { 0 -> screen = ok; 1 -> music = ok; else -> gps = ok } }
                    SecondaryButton("正常", { set(true) })
                    SecondaryButton("有問題", { set(false) })
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("熄火時間、恢復情況與備註") }, modifier = Modifier.fillMaxWidth())
            SecondaryButton("保存恢復確認", {
                tw.fitcar.launcher.check.IgnitionTest.confirm(ctx, screen, music, gps, note)
            })
        }
    }
}

@Composable
private fun ReportTab(results: Map<String, CheckResult>) {
    val ctx = LocalContext.current
    val text = remember(results) { Report.text(ctx, results) }
    var saved by remember { mutableStateOf<String?>(null) }
    Section("匯出報告") {
        Hint("存成文字檔後，可用隨身碟或手機帶出，直接交給 AI 判斷下一步要改什麼。報告不含序號、IMEI。", size = 14)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("存到「下載」資料夾", {
                saved = try { "已儲存：" + Report.save(ctx, text) } catch (e: Exception) { "儲存失敗：${e.message}" }
            })
            SecondaryButton("分享…", {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                try { ctx.startActivity(Intent.createChooser(send, "分享檢測報告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { Toast.makeText(ctx, "車機沒有可分享的 App", Toast.LENGTH_LONG).show() }
            })
            SecondaryButton("清除測試紀錄", { CheckStore.clearSaved(ctx); saved = "已清除互動測試與手動檢查紀錄" })
        }
        saved?.let { Hint(it, size = 15, color = LocalPalette.current.text) }
    }
    Section("預覽") {
        Text(text, color = LocalPalette.current.text, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
