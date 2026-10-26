package tw.fitcar.launcher.nav

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import tw.fitcar.launcher.data.Prefs

object GoogleNavigation {
    const val PACKAGE = "com.google.android.apps.maps"
    fun isSelected(context: Context): Boolean = Prefs.get(context).state.value.bindings.navigation
        ?.let { ComponentName.unflattenFromString(it)?.packageName } == PACKAGE
    fun destinationIntent(destination: String): Intent = Intent(Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=d"))
        .setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
