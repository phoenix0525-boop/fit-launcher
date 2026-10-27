package tw.fitcar.launcher.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import tw.fitcar.launcher.system.DrivingMode
import tw.fitcar.launcher.system.GpsState

/** 行車中（GPS 車速 ≥ 10 km/h，含遲滯）：主畫面隱藏次要說明、放大按鈕與關鍵文字。只在主畫面提供，設定頁不受影響。 */
val LocalDriving = compositionLocalOf { false }

/** 主畫面是否在最上層；開啟設定／所有 App 時為 false，用來暫停地圖繪製。 */
val LocalHomeVisible = compositionLocalOf { true }

@Composable
fun rememberDriving(gps: GpsState): Boolean {
    var driving by remember { mutableStateOf(false) }
    // 只為了「失聯超過 60 秒退出行車模式」而慢速檢查，GPS 更新本身會觸發重算
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = SystemClock.elapsedRealtime() } }
    LaunchedEffect(gps, now) {
        val t = maxOf(now, SystemClock.elapsedRealtime())
        driving = DrivingMode.next(driving, gps.speedKmh, gps.isFresh(t), if (gps.fixElapsed > 0) t - gps.fixElapsed else Long.MAX_VALUE)
    }
    return driving
}

/** 行車時的字級：125% 字體下 ≥ 24 dp（程式 ≥ 19.2 sp）。 */
object DriveText {
    const val BUTTON = 20
    const val BODY = 20
}
