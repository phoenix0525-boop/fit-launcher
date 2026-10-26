package tw.fitcar.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 已安裝 App 清單快取。
 *
 * 讀取清單與圖示（loadIcon）很耗時，原本每次切換畫面都在主執行緒重讀，開「所有 App」會卡頓。
 * 改為背景執行緒載入一次，之後只在安裝／移除／更新 App 時，或回到桌面且距上次超過 30 秒時重讀。
 */
object AppCatalog {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var loadedAt = 0L
    private var registered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh(context, force = true)
    }

    fun start(context: Context) {
        val app = context.applicationContext
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        refresh(app)
    }

    fun refresh(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && _apps.value.isNotEmpty() && now - loadedAt < 30_000) return
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            val list = try { AppRepository.launchableApps(app) } catch (e: Exception) { return@launch }
            loadedAt = SystemClock.elapsedRealtime()
            _apps.value = list
        }
    }

    /** 先查快取，查不到（例如原廠隱藏入口或剛安裝）才直接向系統查詢。 */
    fun resolve(context: Context, key: String?): AppEntry? {
        if (key.isNullOrBlank()) return null
        return _apps.value.firstOrNull { it.key == key } ?: AppRepository.resolve(context, key)
    }
}
