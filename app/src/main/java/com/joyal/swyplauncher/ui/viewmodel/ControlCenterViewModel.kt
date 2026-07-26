package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.domain.model.ControlCenterState
import com.joyal.swyplauncher.domain.model.MediaControlsState
import com.joyal.swyplauncher.domain.model.RingerMode
import com.joyal.swyplauncher.domain.repository.ControlCenterRepository
import com.joyal.swyplauncher.domain.repository.MediaSessionRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ControlCenterViewModel @Inject constructor(
    private val controlCenterRepository: ControlCenterRepository,
    private val mediaSessionRepository: MediaSessionRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val state: StateFlow<ControlCenterState> = controlCenterRepository.state
    val mediaState: StateFlow<MediaControlsState> = mediaSessionRepository.state

    // Slider writes arrive at drag-frame rate; a single-slot DROP_OLDEST buffer keeps
    // only the latest value so the system setters never lag behind the finger.
    private val volumeRequests =
        MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val brightnessRequests =
        MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            volumeRequests.collect { controlCenterRepository.setMediaVolume(it) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            brightnessRequests.collect { controlCenterRepository.setBrightness(it) }
        }
    }

    // Set/read only on the main thread (viewModelScope + DisposableEffect), so the
    // visibility check below cannot interleave with onHidden.
    private var visible = false

    fun onVisible() {
        visible = true
        // Refresh first: it resolves the torch camera id on IO, so the torch callback's
        // replay (main thread) never triggers the camera enumeration itself.
        viewModelScope.launch {
            controlCenterRepository.refresh()
            mediaSessionRepository.refresh()
            if (visible) {
                controlCenterRepository.startObserving()
                mediaSessionRepository.startObserving()
            }
        }
    }

    fun onHidden() {
        visible = false
        controlCenterRepository.stopObserving()
        mediaSessionRepository.stopObserving()
    }

    /** Re-registers observers after a runtime-permission grant so gated data (SSID,
     *  connected-device name) is picked up without leaving the screen. */
    fun restartObservers() {
        if (!visible) return
        controlCenterRepository.stopObserving()
        mediaSessionRepository.stopObserving()
        onVisible()
    }

    fun refresh() {
        viewModelScope.launch {
            controlCenterRepository.refresh()
            mediaSessionRepository.refresh()
        }
    }

    fun toggleMediaPlayPause() = mediaSessionRepository.togglePlayPause()

    fun skipMediaToPrevious() = mediaSessionRepository.skipToPrevious()

    fun skipMediaToNext() = mediaSessionRepository.skipToNext()

    fun seekMediaTo(positionMs: Long) = mediaSessionRepository.seekTo(positionMs)

    fun openMediaApp(): Boolean = mediaSessionRepository.openMediaApp()

    fun setMediaVolume(fraction: Float) {
        volumeRequests.tryEmit(fraction)
    }

    fun setBrightness(fraction: Float) {
        brightnessRequests.tryEmit(fraction)
    }

    fun setAutoBrightness(enabled: Boolean): Boolean =
        controlCenterRepository.setAutoBrightness(enabled)

    fun setAutoRotate(enabled: Boolean): Boolean =
        controlCenterRepository.setAutoRotate(enabled)

    fun setRingerMode(mode: RingerMode): Boolean =
        controlCenterRepository.setRingerMode(mode)

    fun setFlashlight(enabled: Boolean): Boolean =
        controlCenterRepository.setFlashlight(enabled)

    fun hasWriteSettingsAccess(): Boolean = controlCenterRepository.hasWriteSettingsAccess()

    fun hasNotificationPolicyAccess(): Boolean = controlCenterRepository.hasNotificationPolicyAccess()

    fun isBluetoothConnectGranted(): Boolean = controlCenterRepository.isBluetoothConnectGranted()

    fun isWifiNameAccessGranted(): Boolean = controlCenterRepository.isWifiNameAccessGranted()

    /** True exactly once: the Wi-Fi tile's location ask (for the SSID) is one-shot. */
    fun consumeWifiNamePermissionPrompt(): Boolean {
        if (preferencesRepository.hasPromptedForWifiNamePermission()) return false
        preferencesRepository.setWifiNamePermissionPrompted()
        return true
    }
}
