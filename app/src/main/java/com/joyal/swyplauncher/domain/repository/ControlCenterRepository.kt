package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.domain.model.ControlCenterState
import com.joyal.swyplauncher.domain.model.RingerMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Live device-control state + direct-settable actions for the Control Center mode.
 * Wi-Fi / mobile data / location / airplane mode cannot be toggled by third-party
 * apps on modern Android, so this interface only reports their state; the UI deep
 * links into the matching Settings page/panel instead. Every implementation must
 * swallow OEM gaps (missing services, SecurityException) — never crash.
 */
interface ControlCenterRepository {
    val state: StateFlow<ControlCenterState>

    /** Registers receivers/observers for live updates. Idempotent; pair with [stopObserving]. */
    fun startObserving()
    fun stopObserving()

    /** Re-polls everything; some states (mobile data) have no broadcast to observe. */
    suspend fun refresh()

    fun setMediaVolume(fraction: Float)

    /** The setters below return false when the required special access is missing. */
    fun setBrightness(fraction: Float): Boolean
    fun setAutoBrightness(enabled: Boolean): Boolean
    fun setAutoRotate(enabled: Boolean): Boolean

    /** False when Do Not Disturb access is required (entering/leaving SILENT) but not granted. */
    fun setRingerMode(mode: RingerMode): Boolean

    /** False when there is no flash unit or the camera is busy. */
    fun setFlashlight(enabled: Boolean): Boolean

    fun hasWriteSettingsAccess(): Boolean
    fun hasNotificationPolicyAccess(): Boolean
    fun isBluetoothConnectGranted(): Boolean

    /** ACCESS_FINE_LOCATION — the platform gates the connected network's SSID behind it. */
    fun isWifiNameAccessGranted(): Boolean
}
