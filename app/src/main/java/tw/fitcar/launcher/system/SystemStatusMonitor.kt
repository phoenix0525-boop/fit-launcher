package tw.fitcar.launcher.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 狀態列的網路／藍牙／USB／電池狀態。前景時才監聽（onStart～onStop），不常駐背景。
 * 藍牙「已連線」需要 Android 12 以上的「附近裝置」權限；未允許時只顯示藍牙開關。
 */
object SystemStatusMonitor {
    private val _state = MutableStateFlow(SystemStatus())
    val state: StateFlow<SystemStatus> = _state.asStateFlow()

    private var appContext: Context? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiverRegistered = false
    private var pollJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshLocal(context)
    }

    fun canReadBluetoothConnection(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (callback == null) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val kind = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
                        else -> NetworkKind.OTHER
                    }
                    _state.value = _state.value.copy(network = kind, internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                }
                override fun onLost(network: Network) {
                    _state.value = _state.value.copy(network = NetworkKind.NONE, internet = false)
                }
            }
            try {
                cm?.registerDefaultNetworkCallback(cb)
                callback = cb
            } catch (_: Exception) { }
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        refreshLocal(ctx)
        // USB 隨身碟掛載沒有可靠的免權限廣播，前景時每 10 秒檢查一次
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                while (isActive) {
                    delay(10_000)
                    appContext?.let { refreshLocal(it) }
                }
            }
        }
    }

    fun stop() {
        val ctx = appContext ?: return
        pollJob?.cancel()
        pollJob = null
        callback?.let { cb -> try { ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } catch (_: Exception) { } }
        callback = null
        if (receiverRegistered) {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshLocal(ctx: Context) {
        val adapter = try { ctx.getSystemService(BluetoothManager::class.java)?.adapter } catch (_: Exception) { null }
        val btOn = try { adapter?.isEnabled } catch (_: SecurityException) { null }
        val btConnected = if (adapter != null && btOn == true && canReadBluetoothConnection(ctx)) {
            try {
                adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                    adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            } catch (_: SecurityException) { null }
        } else null
        val usb = try {
            ctx.getSystemService(StorageManager::class.java)?.storageVolumes
                ?.count { it.isRemovable && it.state == Environment.MEDIA_MOUNTED } ?: 0
        } catch (_: Exception) { 0 }
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val present = battery?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        _state.value = _state.value.copy(
            bluetoothOn = btOn,
            bluetoothConnected = btConnected,
            usbStorage = usb,
            batteryPercent = if (present && level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }
}
