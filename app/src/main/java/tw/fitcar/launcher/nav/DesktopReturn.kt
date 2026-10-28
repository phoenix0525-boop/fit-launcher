package tw.fitcar.launcher.nav

import android.content.Context
import android.content.Intent
import tw.fitcar.launcher.MainActivity

/** App-owned return action; never changes or intercepts the system's hardware Home key. */
object DesktopReturn {
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
