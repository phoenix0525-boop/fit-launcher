package tw.fitcar.launcher.system

/** Android stream readback is never evidence that the vehicle amplifier changed. */
enum class VolumeReadback(val message: String) {
    FIXED("Android 回報音量固定；實際聲音是否改變需以聽感確認，原廠音量可能不經 Android 數值。"),
    NOT_APPLIED("Android 數值未改變；這不代表實際聲音一定沒變，請以聽感回報為準。"),
    REVERTED("Android 數值變動後又被改回；實際聲音是否改變需以聽感回報確認。"),
    NUMERIC_ONLY("Android 數值已改變；仍需確認實際聲音是否改變。"),
    NO_CHANGE_REQUESTED("已達音量邊界，沒有調整。"),
    ;
    val verifiesVehicleSound: Boolean get() = false
}
object VolumeVerification {
    fun check(before: Int, target: Int, early: Int, delayed: Int, fixed: Boolean): VolumeReadback = when {
        fixed -> VolumeReadback.FIXED
        before == target -> VolumeReadback.NO_CHANGE_REQUESTED
        delayed == target -> VolumeReadback.NUMERIC_ONLY
        early == target -> VolumeReadback.REVERTED
        else -> VolumeReadback.NOT_APPLIED
    }
}
