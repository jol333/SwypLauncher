package com.joyal.swyplauncher.data.repository

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.joyal.swyplauncher.domain.model.ControlCenterState
import com.joyal.swyplauncher.domain.model.ControlToggle
import com.joyal.swyplauncher.domain.model.RingerMode
import com.joyal.swyplauncher.domain.repository.ControlCenterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Singleton
class ControlCenterRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ControlCenterRepository {

    private val _state = MutableStateFlow(ControlCenterState())
    override val state: StateFlow<ControlCenterState> = _state.asStateFlow()

    private val wifiManager by lazy { getSystemServiceOrNull(WifiManager::class.java) }
    private val bluetoothManager by lazy { getSystemServiceOrNull(BluetoothManager::class.java) }
    private val telephonyManager by lazy { getSystemServiceOrNull(TelephonyManager::class.java) }
    private val audioManager by lazy { getSystemServiceOrNull(AudioManager::class.java) }
    private val cameraManager by lazy { getSystemServiceOrNull(CameraManager::class.java) }
    private val locationManager by lazy { getSystemServiceOrNull(LocationManager::class.java) }
    private val notificationManager by lazy { getSystemServiceOrNull(NotificationManager::class.java) }
    private val connectivityManager by lazy { getSystemServiceOrNull(ConnectivityManager::class.java) }

    /** First camera with a flash unit; the one [CameraManager.setTorchMode] can drive. */
    private val torchCameraId: String? by lazy {
        runCatching {
            val cm = cameraManager ?: return@runCatching null
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    // registerTorchCallback replays the current state on registration, so this stays accurate.
    @Volatile
    private var torchOn = false

    // Connected-network / device labels, best effort: SSID needs NEARBY_WIFI_DEVICES and the
    // device name needs BLUETOOTH_CONNECT; both fall back to null (generic tile titles).
    @Volatile
    private var wifiSsid: String? = null

    @Volatile
    private var btDeviceName: String? = null

    private var a2dpProxy: BluetoothProfile? = null
    private var headsetProxy: BluetoothProfile? = null

    @Volatile
    private var observing = false
    private val observeLock = Any()

    private val brightnessUri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)
    private val brightnessModeUri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)
    private val rotationUri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)

    /** Broadcasts carry stale/no extras for some states — re-read the affected slice instead. */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val on = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN
                    ) == WifiManager.WIFI_STATE_ENABLED
                    if (!on) wifiSsid = null
                    _state.update { it.copy(wifi = it.wifi.copy(isOn = on, label = wifiSsid)) }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val on = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR
                    ) == BluetoothAdapter.STATE_ON
                    if (!on) btDeviceName = null
                    _state.update {
                        it.copy(bluetooth = it.bluetooth.copy(isOn = on, label = btDeviceName))
                    }
                }
                // ACL fires before the audio profile connects, so name the device from the
                // intent right away; on disconnect fall back to whatever is still attached.
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val name = runCatching {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                        )?.name
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    if (name != null) {
                        btDeviceName = name
                        _state.update { it.copy(bluetooth = it.bluetooth.copy(label = name)) }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateBluetoothLabel()
                LocationManager.MODE_CHANGED_ACTION -> {
                    val on = readLocationOn()
                    _state.update { it.copy(location = it.location.copy(isOn = on)) }
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val on = readAirplaneModeOn()
                    _state.update { it.copy(airplaneMode = it.airplaneMode.copy(isOn = on)) }
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION ->
                    _state.update { it.copy(ringerMode = readRingerMode()) }
                ACTION_VOLUME_CHANGED ->
                    _state.update { it.copy(mediaVolume = readMediaVolume()) }
            }
        }
    }

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            when (uri) {
                brightnessUri -> _state.update { it.copy(brightness = readBrightness()) }
                brightnessModeUri -> _state.update { it.copy(isAutoBrightness = readAutoBrightness()) }
                rotationUri -> _state.update {
                    it.copy(autoRotate = it.autoRotate.copy(isOn = readAutoRotateOn()))
                }
                // Some OEMs notify without a uri — re-read all three observed settings.
                else -> _state.update {
                    it.copy(
                        brightness = readBrightness(),
                        isAutoBrightness = readAutoBrightness(),
                        autoRotate = it.autoRotate.copy(isOn = readAutoRotateOn())
                    )
                }
            }
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != torchCameraId) return
            torchOn = enabled
            _state.update { it.copy(flashlight = it.flashlight.copy(isOn = enabled)) }
        }
    }

    // FLAG_INCLUDE_LOCATION_INFO exposes the SSID in transportInfo once ACCESS_FINE_LOCATION
    // is granted and location is on; otherwise the platform redacts to <unknown ssid>.
    private val wifiNetworkCallback =
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val ssid = sanitizeSsid((capabilities.transportInfo as? WifiInfo)?.ssid)
                wifiSsid = ssid
                _state.update { it.copy(wifi = it.wifi.copy(label = ssid)) }
            }

            override fun onLost(network: Network) {
                wifiSsid = null
                _state.update { it.copy(wifi = it.wifi.copy(label = null)) }
            }
        }

    // Audio profiles cover the devices worth naming on the tile (buds, speakers, car kits).
    private val btProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            synchronized(observeLock) {
                // The bind is async; if the screen was hidden meanwhile, close the proxy
                // here or its profile-service binding leaks for the process lifetime.
                if (!observing) {
                    runCatching { bluetoothManager?.adapter?.closeProfileProxy(profile, proxy) }
                    return
                }
                when (profile) {
                    BluetoothProfile.A2DP -> a2dpProxy = proxy
                    BluetoothProfile.HEADSET -> headsetProxy = proxy
                }
            }
            updateBluetoothLabel()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
        }
    }

    private fun updateBluetoothLabel() {
        val name = runCatching {
            listOfNotNull(a2dpProxy, headsetProxy)
                .flatMap { proxy -> runCatching { proxy.connectedDevices }.getOrDefault(emptyList()) }
                .firstNotNullOfOrNull { device -> runCatching { device.name }.getOrNull() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        btDeviceName = name
        _state.update { it.copy(bluetooth = it.bluetooth.copy(label = name)) }
    }

    private fun sanitizeSsid(raw: String?): String? = raw
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }

    override fun startObserving() {
        synchronized(observeLock) {
            if (observing) return
            observing = true
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(LocationManager.MODE_CHANGED_ACTION)
                    addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                    addAction(ACTION_VOLUME_CHANGED)
                }
                // System broadcasts are still delivered to not-exported receivers.
                ContextCompat.registerReceiver(
                    context, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            runCatching {
                val resolver = context.contentResolver
                resolver.registerContentObserver(brightnessUri, false, settingsObserver)
                resolver.registerContentObserver(brightnessModeUri, false, settingsObserver)
                resolver.registerContentObserver(rotationUri, false, settingsObserver)
            }
            runCatching {
                cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            }
            runCatching {
                // A disconnect while unobserved delivers no onLost; drop any stale name and
                // let the registration replay repopulate it for a live network.
                wifiSsid = null
                connectivityManager?.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build(),
                    wifiNetworkCallback
                )
            }
            if (isBluetoothConnectGranted()) {
                runCatching {
                    bluetoothManager?.adapter?.let { adapter ->
                        adapter.getProfileProxy(context, btProfileListener, BluetoothProfile.A2DP)
                        adapter.getProfileProxy(context, btProfileListener, BluetoothProfile.HEADSET)
                    }
                }
            }
        }
    }

    override fun stopObserving() {
        synchronized(observeLock) {
            if (!observing) return
            observing = false
            runCatching { context.unregisterReceiver(stateReceiver) }
            runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
            runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
            runCatching { connectivityManager?.unregisterNetworkCallback(wifiNetworkCallback) }
            runCatching {
                val adapter = bluetoothManager?.adapter
                a2dpProxy?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
                headsetProxy?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            }
            a2dpProxy = null
            headsetProxy = null
        }
    }

    override suspend fun refresh() {
        withContext(Dispatchers.IO) { _state.value = readFullState() }
    }

    private fun readFullState() = ControlCenterState(
        wifi = readWifi(),
        bluetooth = readBluetooth(),
        mobileData = readMobileData(),
        location = ControlToggle(isOn = readLocationOn(), isAvailable = locationManager != null),
        autoRotate = ControlToggle(isOn = readAutoRotateOn()),
        flashlight = ControlToggle(isOn = torchOn, isAvailable = torchCameraId != null),
        airplaneMode = ControlToggle(isOn = readAirplaneModeOn()),
        ringerMode = readRingerMode(),
        mediaVolume = readMediaVolume(),
        brightness = readBrightness(),
        isAutoBrightness = readAutoBrightness()
    )

    private fun readWifi(): ControlToggle = runCatching {
        val wm = wifiManager
            ?.takeIf { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) }
            ?: return@runCatching ControlToggle(isAvailable = false)
        ControlToggle(isOn = wm.isWifiEnabled, label = wifiSsid)
    }.getOrDefault(ControlToggle(isAvailable = false))

    private fun readBluetooth(): ControlToggle = runCatching {
        val adapter = bluetoothManager?.adapter
            ?: return@runCatching ControlToggle(isAvailable = false)
        ControlToggle(
            isOn = runCatching { adapter.isEnabled }.getOrDefault(false),
            label = btDeviceName
        )
    }.getOrDefault(ControlToggle(isAvailable = false))

    private fun readMobileData(): ControlToggle = runCatching {
        val tm = telephonyManager
            ?.takeIf { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) }
            ?: return@runCatching ControlToggle(isAvailable = false)
        // The bare TelephonyManager is bound to the default (voice) subscription, so on
        // dual-SIM devices it named SIM 1 even while SIM 2 carried the data — bind to the
        // default *data* subscription instead.
        val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val dataTm = if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            tm.createForSubscriptionId(dataSubId)
        } else {
            tm
        }
        ControlToggle(
            isOn = runCatching { dataTm.isDataEnabled }.getOrDefault(false),
            label = runCatching { dataTm.simOperatorName?.takeIf { it.isNotBlank() } }.getOrNull()
        )
    }.getOrDefault(ControlToggle(isAvailable = false))

    private fun readLocationOn(): Boolean =
        runCatching { locationManager?.isLocationEnabled == true }.getOrDefault(false)

    private fun readAutoRotateOn(): Boolean = runCatching {
        Settings.System.getInt(
            context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
    }.getOrDefault(false)

    private fun readAirplaneModeOn(): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
        ) == 1
    }.getOrDefault(false)

    private fun readRingerMode(): RingerMode = runCatching {
        when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            else -> RingerMode.RING
        }
    }.getOrDefault(RingerMode.RING)

    private fun readMediaVolume(): Float = runCatching {
        val am = audioManager ?: return@runCatching 0f
        val min = am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val range = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) - min
        if (range <= 0) return@runCatching 0f
        ((am.getStreamVolume(AudioManager.STREAM_MUSIC) - min) / range.toFloat()).coerceIn(0f, 1f)
    }.getOrDefault(0f)

    private fun readBrightness(): Float = runCatching {
        val raw = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
        )
        // Some OEMs store past the advertised max — adopt any larger observed value so the
        // slider's scale self-heals instead of pinning at 100%.
        if (raw > brightnessMax()) brightnessCeiling = raw
        // The system slider is roughly gamma-mapped; sqrt keeps our linear slider perceptual.
        sqrt(raw / brightnessMax().toFloat()).coerceIn(0f, 1f)
    }.getOrDefault(0.5f)

    private fun readAutoBrightness(): Boolean = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    override fun setMediaVolume(fraction: Float) {
        runCatching {
            val am = audioManager ?: return
            val min = am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val coerced = fraction.coerceIn(0f, 1f)
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC, min + ((max - min) * coerced).roundToInt(), 0
            )
        }
        // Read back rather than trusting the write: fixed-volume devices and DND policies
        // can ignore it, and the slider should settle on the truth.
        _state.update { it.copy(mediaVolume = readMediaVolume()) }
    }

    override fun setBrightness(fraction: Float): Boolean {
        // No canWrite() pre-check: this runs at drag rate, and without access putInt
        // throws SecurityException which the runCatching below turns into false anyway.
        val coerced = fraction.coerceIn(0f, 1f)
        // Adaptive brightness re-overrides manual writes on most OEMs; a slider drag is an
        // explicit manual request, so drop to manual mode first (long-press restores auto).
        if (_state.value.isAutoBrightness) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            }.onSuccess { _state.update { it.copy(isAutoBrightness = false) } }
        }
        val max = brightnessMax()
        // Squaring inverts the sqrt read curve; floor of 1 — raw 0 blacks out some OEM panels.
        val value = (coerced * coerced * max).roundToInt().coerceIn(1, max)
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.getOrDefault(false).also { ok ->
            if (ok) _state.update { it.copy(brightness = coerced) }
        }
    }

    override fun setAutoBrightness(enabled: Boolean): Boolean {
        if (!hasWriteSettingsAccess()) return false
        val mode = if (enabled) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        return runCatching {
            Settings.System.putInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode
            )
        }.getOrDefault(false).also { ok ->
            if (ok) _state.update { it.copy(isAutoBrightness = enabled) }
        }
    }

    override fun setAutoRotate(enabled: Boolean): Boolean {
        if (!hasWriteSettingsAccess()) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
        }.getOrDefault(false).also { ok ->
            if (ok) _state.update { it.copy(autoRotate = it.autoRotate.copy(isOn = enabled)) }
        }
    }

    override fun setRingerMode(mode: RingerMode): Boolean {
        val am = audioManager ?: return false
        val current = readRingerMode()
        if (mode == current) return true
        // Entering/leaving SILENT toggles Do Not Disturb, which needs policy access.
        if ((mode == RingerMode.SILENT || current == RingerMode.SILENT) &&
            !hasNotificationPolicyAccess()
        ) {
            return false
        }
        val applied = runCatching {
            // AudioService's zen delegate swallows an external VIBRATE -> SILENT switch;
            // hop through NORMAL first so the silent request is honored.
            if (mode == RingerMode.SILENT && current == RingerMode.VIBRATE) {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            am.ringerMode = when (mode) {
                RingerMode.RING -> AudioManager.RINGER_MODE_NORMAL
                RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
        }.isSuccess
        if (!applied) return false
        // Publish what the device actually landed on — some OEMs coerce the request.
        val result = readRingerMode()
        _state.update { it.copy(ringerMode = result) }
        return result == mode
    }

    override fun setFlashlight(enabled: Boolean): Boolean {
        val id = torchCameraId ?: return false
        val cm = cameraManager ?: return false
        // CameraAccessException when another app holds the camera — surface as failure.
        return runCatching { cm.setTorchMode(id, enabled) }.isSuccess.also { ok ->
            if (ok) {
                torchOn = enabled
                _state.update { it.copy(flashlight = it.flashlight.copy(isOn = enabled)) }
            }
        }
    }

    override fun hasWriteSettingsAccess(): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    override fun hasNotificationPolicyAccess(): Boolean =
        runCatching { notificationManager?.isNotificationPolicyAccessGranted == true }
            .getOrDefault(false)

    override fun isBluetoothConnectGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    override fun isWifiNameAccessGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun <T> getSystemServiceOrNull(clazz: Class<T>): T? =
        runCatching { context.getSystemService(clazz) }.getOrNull()

    /** Max raw value of [Settings.System.SCREEN_BRIGHTNESS]; OEMs commonly extend past 255. */
    private val detectedMaxBrightness: Int by lazy {
        runCatching {
            val res = Resources.getSystem()
            val id = res.getIdentifier(
                "config_screenBrightnessSettingMaximum", "integer", "android"
            )
            if (id > 0) res.getInteger(id) else DEFAULT_MAX_BRIGHTNESS
        }.getOrDefault(DEFAULT_MAX_BRIGHTNESS).coerceAtLeast(DEFAULT_MAX_BRIGHTNESS)
    }

    // Raised by readBrightness() whenever a stored value exceeds the detected max.
    @Volatile
    private var brightnessCeiling = 0

    private fun brightnessMax(): Int {
        if (brightnessCeiling == 0) brightnessCeiling = detectedMaxBrightness
        return brightnessCeiling
    }

    companion object {
        // Hidden framework constant; a read-only broadcast that has been stable for years.
        private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val DEFAULT_MAX_BRIGHTNESS = 255
    }
}
