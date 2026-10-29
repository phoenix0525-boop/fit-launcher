package tw.fitcar.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat

class AndroidAudioProbe(private val context: Context) : AudioProbePort {
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

    /** Public ContentResolver read of the three Settings tables; nothing is written. */
    override fun settings(): SettingsSnapshot {
        val values = linkedMapOf<String, String?>()
        val unreadable = mutableListOf<String>()
        listOf("system" to Settings.System.CONTENT_URI, "global" to Settings.Global.CONTENT_URI, "secure" to Settings.Secure.CONTENT_URI)
            .forEach { (name, uri) ->
                try {
                    context.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                        while (c.moveToNext()) values["$name:${c.getString(0)}"] = c.getString(1)
                    } ?: unreadable.add(name)
                } catch (e: Exception) { unreadable.add("$name(${e.javaClass.simpleName})") }
            }
        return SettingsSnapshot(values, unreadable)
    }

    private var receiver: BroadcastReceiver? = null
    private val events = mutableListOf<String>()
    private var eventStart = 0L
    /** Records the system's own VOLUME_CHANGED broadcasts while the user presses keys or talks to the assistant. */
    override fun beginEvents() {
        endEvents()
        synchronized(events) { events.clear() }
        eventStart = SystemClock.elapsedRealtime()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val line = "+${SystemClock.elapsedRealtime() - eventStart}ms VOLUME_CHANGED 流=${i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)} " +
                    "${i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)}→${i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)}"
                synchronized(events) { if (events.size < 60) events += line }
            }
        }
        try {
            ContextCompat.registerReceiver(context, r, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
        } catch (_: Exception) { receiver = null }
    }
    override fun endEvents(): List<String> {
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        return synchronized(events) { events.toList() }
    }
}
