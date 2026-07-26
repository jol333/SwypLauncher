package com.joyal.swyplauncher.domain.model

import android.graphics.Bitmap

/** Snapshot of the media session shown on the Control Center media card. */
data class ActiveMediaSession(
    val packageName: String,
    val title: String? = null,
    val artist: String? = null,
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    /** Playback position at [positionUpdatedAtMs]; the UI extrapolates between updates. */
    val positionMs: Long = 0L,
    /** [android.os.SystemClock.elapsedRealtime] basis; 0 when the app never reported one. */
    val positionUpdatedAtMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    /** <= 0 when unknown (live streams); the seek bar hides then. */
    val durationMs: Long = 0L,
    val canTogglePlay: Boolean = true,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSeek: Boolean = false
)

data class MediaControlsState(
    /** Best active session (playing preferred over paused), or null — card hidden. */
    val session: ActiveMediaSession? = null,
    /** Music is audibly playing but notification-listener access is missing. */
    val needsNotificationAccess: Boolean = false
)
