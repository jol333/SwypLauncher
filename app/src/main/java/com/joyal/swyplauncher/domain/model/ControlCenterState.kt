package com.joyal.swyplauncher.domain.model

/**
 * Availability + on/off snapshot of a single control-center toggle.
 * [isAvailable] is false when the device lacks the hardware/service (no flash unit,
 * no telephony, …); the tile renders dimmed and taps only show a toast.
 */
data class ControlToggle(
    val isOn: Boolean = false,
    val isAvailable: Boolean = true,
    /** Dynamic label (carrier name, …); null falls back to the generic title. */
    val label: String? = null
)

enum class RingerMode { RING, VIBRATE, SILENT }

data class ControlCenterState(
    val wifi: ControlToggle = ControlToggle(),
    val bluetooth: ControlToggle = ControlToggle(),
    val mobileData: ControlToggle = ControlToggle(),
    val location: ControlToggle = ControlToggle(),
    val autoRotate: ControlToggle = ControlToggle(),
    val flashlight: ControlToggle = ControlToggle(),
    val airplaneMode: ControlToggle = ControlToggle(),
    val ringerMode: RingerMode = RingerMode.RING,
    /** Media (music) stream volume, normalized 0f..1f. */
    val mediaVolume: Float = 0f,
    /** Screen brightness, normalized 0f..1f on a perceptual (sqrt) curve. */
    val brightness: Float = 0.5f,
    val isAutoBrightness: Boolean = false
)
