package tw.fitcar.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * 0.4.35 Shizuku 整合：使用者自行安裝並啟動 Shizuku、允許車機桌面使用後，才可一鍵執行授權或停用原廠桌面。
 * 沒有 Shizuku 時所有功能維持原狀，adb 指令區仍可用。
 */
object ShizukuShell {
    const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    const val REQUEST_CODE = 4350

    enum class State(val text: String) {
        NOT_INSTALLED("未安裝 Shizuku"),
        NOT_RUNNING("已安裝，但 Shizuku 尚未啟動（重開機後需要重新啟動一次）"),
        UNSUPPORTED("Shizuku 版本太舊，請更新"),
        NO_PERMISSION("Shizuku 已啟動，尚未允許車機桌面使用"),
        READY("可使用：車機桌面可代你執行授權指令"),
    }

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0); true
    } catch (_: Exception) { false }

    fun state(context: Context): State {
        val alive = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        if (!alive) return if (installed(context)) State.NOT_RUNNING else State.NOT_INSTALLED
        return try {
            when {
                Shizuku.isPreV11() -> State.UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
                else -> State.NO_PERMISSION
            }
        } catch (_: Throwable) { State.NOT_RUNNING }
    }

    fun stateText(context: Context): String = state(context).text

    fun requestPermission(): Boolean = try { Shizuku.requestPermission(REQUEST_CODE); true } catch (_: Throwable) { false }

    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
        .daemon(false).processNameSuffix("shell").debuggable(false).version(versionCode(context))

    private fun versionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (_: Exception) { 1 }

    /** 依序執行；任何一步失敗都繼續下一步，結果逐項回報。 */
    suspend fun run(context: Context, steps: List<ShellPlan.Step>): List<ShellPlan.Result> {
        if (state(context) != State.READY) return steps.map { ShellPlan.Result(it.label, -10, stateText(context)) }
        val args = args(context)
        var connection: ServiceConnection? = null
        return try {
            val binder = withTimeout(15_000) {
                suspendCancellableCoroutine<IBinder> { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            if (service != null && service.pingBinder() && cont.isActive) cont.resume(service)
                        }
                        override fun onServiceDisconnected(name: ComponentName?) = Unit
                    }
                    connection = conn
                    Shizuku.bindUserService(args, conn)
                }
            }
            withContext(Dispatchers.IO) { transact(binder, steps) }
        } catch (e: Throwable) {
            steps.map { ShellPlan.Result(it.label, -11, "無法連線 Shizuku 服務：${e.javaClass.simpleName}") }
        } finally {
            try { Shizuku.unbindUserService(args, connection, true) } catch (_: Throwable) { }
        }
    }

    private fun transact(binder: IBinder, steps: List<ShellPlan.Step>): List<ShellPlan.Result> {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShellUserService.DESCRIPTOR)
            data.writeInt(steps.size)
            steps.forEach { data.writeStringArray(it.argv.toTypedArray()) }
            binder.transact(ShellUserService.RUN, data, reply, 0)
            reply.readException()
            val n = reply.readInt()
            return steps.mapIndexed { i, s ->
                if (i < n) ShellPlan.Result(s.label, reply.readInt(), reply.readString().orEmpty()) else ShellPlan.Result(s.label, -12, "沒有回應")
            }
        } finally { data.recycle(); reply.recycle() }
    }
}
