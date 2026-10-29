package tw.fitcar.launcher.system

import android.content.Context
import android.media.AudioManager
import android.os.Build

class AndroidAudioProbe(context: Context) : AudioProbePort {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    override fun read(): AudioReading {
        val streams = linkedMapOf("MUSIC" to AudioManager.STREAM_MUSIC, "SYSTEM" to AudioManager.STREAM_SYSTEM,
            "VOICE_CALL" to AudioManager.STREAM_VOICE_CALL, "RING" to AudioManager.STREAM_RING,
            "NOTIFICATION" to AudioManager.STREAM_NOTIFICATION, "ALARM" to AudioManager.STREAM_ALARM)
        return AudioReading(streams.mapValues { audio.getStreamVolume(it.value) },
            if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0,
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.isVolumeFixed, audio.mode, audio.isMusicActive,
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.distinct().sorted(),
            streams.filterValues { audio.isStreamMute(it) }.keys.toSet())
    }
    override fun setMusic(target: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    override fun lowerMusic() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
    override fun lowerCurrent() = audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
}
