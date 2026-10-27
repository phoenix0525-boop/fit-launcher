package tw.fitcar.launcher.system

/** Android stream readback is never evidence that the vehicle amplifier changed. */
enum class VolumeReadback(val message: String) {
    FIXED("Android 音量固定，請使用系統音量或車機音量鍵。"),
    NOT_APPLIED("Android 數值未改變，這個控制方式未生效。請使用系統音量或車機音量鍵。"),
    REVERTED("音量數值被系統改回，請使用系統音量或車機音量鍵。"),
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
