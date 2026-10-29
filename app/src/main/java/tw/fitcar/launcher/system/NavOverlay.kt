package tw.fitcar.launcher.system

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 導航 App 在前景時，左側中間顯示一顆「回桌面」浮動鍵（需使用者允許浮動視窗）。
 * 只用一般 View，不讀取導航畫面內容；點一下回車機桌面，長按隱藏到下次開啟導航。
 */
object NavOverlay {
    private var view: View? = null
    private var dismissedFor: String? = null

    fun update(context: Context, change: DesktopGuardPolicy.OverlayChange, foreground: String?) {
        when (change) {
            DesktopGuardPolicy.OverlayChange.SHOW -> if (dismissedFor != foreground) show(context, foreground) else Unit
            DesktopGuardPolicy.OverlayChange.HIDE -> { hide(context); if (foreground != dismissedFor) dismissedFor = null }
            DesktopGuardPolicy.OverlayChange.KEEP -> Unit
        }
    }

    private fun dp(context: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    private fun show(context: Context, foreground: String?) {
        if (view != null || !LauncherPermissions.canOverlay(context)) return
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        val button = TextView(app).apply {
            text = "⌂ 桌面"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            minHeight = dp(app, 64f); minWidth = dp(app, 96f)
            setPadding(dp(app, 16f), dp(app, 8f), dp(app, 16f), dp(app, 8f))
            background = GradientDrawable().apply { cornerRadius = dp(app, 32f).toFloat(); setColor(0xCC202833.toInt()) }
            contentDescription = "回到車機桌面"
            setOnClickListener { hide(app); DesktopGuard.launchDesktop(app) }
            setOnLongClickListener { dismissedFor = foreground; hide(app); true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; x = dp(app, 8f) }
        try { wm.addView(button, params); view = button } catch (_: Exception) { view = null }
    }

    fun hide(context: Context) {
        val v = view ?: return
        view = null
        try { context.applicationContext.getSystemService(WindowManager::class.java)?.removeView(v) } catch (_: Exception) { }
    }
}
