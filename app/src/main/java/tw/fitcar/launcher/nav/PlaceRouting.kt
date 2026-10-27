package tw.fitcar.launcher.nav

/** A geo map search is not a documented NaviKing destination command. */
enum class PlaceRoute { GOOGLE, GEO, CONFIRM }
object PlaceRouting {
    fun route(packageName: String?, acceptsGeo: Boolean): PlaceRoute = when {
        packageName == "com.google.android.apps.maps" -> PlaceRoute.GOOGLE
        packageName?.startsWith("com.kingwaytek.") == true -> PlaceRoute.CONFIRM
        packageName != null && acceptsGeo -> PlaceRoute.GEO
        else -> PlaceRoute.CONFIRM
    }
}
