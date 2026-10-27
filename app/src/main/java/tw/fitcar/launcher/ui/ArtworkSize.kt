package tw.fitcar.launcher.ui

object ArtworkSize {
    val originals=mapOf("pompompurin" to (240 to 240), "pompompurin_sleep" to (240 to 240), "pompompurin_cheer" to (240 to 240), "pompompurin_thanks" to (240 to 240), "spiderman_crouch" to (360 to 360), "spiderman_perch" to (600 to 600), "spiderman_reach" to (665 to 479), "spiderman_swing" to (400 to 400))
    fun fit(w: Int,h: Int,maxWidthDp: Float,maxHeightDp: Float,density: Float): Pair<Float,Float> {
        require(w>0 && h>0 && density.isFinite() && density>0)
        val scale=minOf(1.5f,maxWidthDp*density/w,maxHeightDp*density/h).coerceAtLeast(0f)
        return w*scale/density to h*scale/density
    }
}
