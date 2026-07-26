package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.domain.model.MediaControlsState
import kotlinx.coroutines.flow.StateFlow

/**
 * Live "now playing" state + transport actions for the Control Center media card.
 * Session access is platform-gated behind an enabled notification listener; when it is
 * missing the state only reports [MediaControlsState.needsNotificationAccess] so the UI
 * can prompt. Implementations must swallow OEM/permission failures — never crash.
 */
interface MediaSessionRepository {
    val state: StateFlow<MediaControlsState>

    /** Registers session/controller listeners for live updates. Pair with [stopObserving]. */
    fun startObserving()
    fun stopObserving()

    /** Re-resolves access + the active session (e.g. after returning from Settings). */
    suspend fun refresh()

    fun togglePlayPause()
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(positionMs: Long)

    /** Opens the session's app (sessionActivity, then launch intent). False when neither works. */
    fun openMediaApp(): Boolean

    fun isNotificationAccessGranted(): Boolean
}
