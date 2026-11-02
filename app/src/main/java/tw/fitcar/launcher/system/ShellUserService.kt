package tw.fitcar.launcher.system

import android.os.Binder
import android.os.Parcel
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

/**
 * 由 Shizuku 以 shell 身分啟動的服務（不在車機桌面行程內執行）。
 * 只接受 [ShizukuShell] 送來的參數陣列並逐一執行，不經 sh 解析；輸出最多保留 2000 字。
 */
class ShellUserService : Binder {
    @Keep constructor() : super()
    @Keep constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context) : super()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
        RUN -> {
            data.enforceInterface(DESCRIPTOR)
            val count = data.readInt().coerceIn(0, 32)
            val results = (0 until count).map { run(data.createStringArray() ?: emptyArray()) }
            reply?.writeNoException()
            reply?.writeInt(results.size)
            results.forEach { (exit, out) -> reply?.writeInt(exit); reply?.writeString(out) }
            true
        }
        DESTROY -> { System.exit(0); true }
        else -> super.onTransact(code, data, reply, flags)
    }

    private fun run(argv: Array<String>): Pair<Int, String> {
        if (argv.isEmpty() || argv.first() !in ALLOWED) return -2 to "不允許的指令"
        return try {
            val p = ProcessBuilder(*argv).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroy(); -3 to "逾時" } else p.exitValue() to out.take(2000)
        } catch (e: Exception) { -1 to (e.javaClass.simpleName + ": " + (e.message ?: "")) }
    }

    companion object {
        const val DESCRIPTOR = "tw.fitcar.launcher.ShellUserService"
        const val RUN = FIRST_CALL_TRANSACTION
        /** Shizuku 保留的 destroy 交易碼（ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy）。 */
        const val DESTROY = 16777115
        /** 只允許這幾個系統工具；其餘一律拒絕。 */
        val ALLOWED = setOf("pm", "cmd", "appops", "settings")
    }
}
