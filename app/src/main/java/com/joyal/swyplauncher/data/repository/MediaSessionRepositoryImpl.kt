package com.joyal.swyplauncher.data.repository

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.joyal.swyplauncher.domain.model.ActiveMediaSession
import com.joyal.swyplauncher.domain.model.MediaControlsState
import com.joyal.swyplauncher.domain.repository.MediaSessionRepository
import com.joyal.swyplauncher.service.MediaNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How recently a session must have been paused to still earn the card when it's first seen
 * non-playing (e.g. the sheet opened moments after the user paused). Sessions seen playing
 * while observing stay visible regardless, until observation stops.
 */
private const val RECENT_PAUSE_WINDOW_MS = 5_000L

/**
 * Tracks the device's active [android.media.session.MediaSession]s for the Control Center
 * media card. Session access is gated behind an enabled notification listener
 * ([MediaNotificationListenerService]); without it the state degrades to a "grant access"
 * flag raised only while music is audibly playing, so the prompt never shows up idly.
 *
 * A paused session stays in the platform's active list indefinitely (apps often keep it
 * alive even after being swiped from recents), so visibility is sticky per observation
 * window instead: a session seen playing while the sheet is open ([shownToken]) keeps the
 * card — through pause/stop — until [stopObserving] (sheet dismissed / tab left). A session
 * first seen non-playing only gets the card if it was paused within
 * [RECENT_PAUSE_WINDOW_MS]; otherwise it stays hidden, though the controller is still
 * tracked so resuming playback brings the card straight back.
 * [activeController] and all publishes are main-thread confined: platform callbacks arrive
 * on [mainHandler] and [refresh] hops to Main before mutating.
 */
@Singleton
class MediaSessionRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MediaSessionRepository {

    private val _state = MutableStateFlow(MediaControlsState())
    override val state: StateFlow<MediaControlsState> = _state.asStateFlow()

    private val mediaSessionManager by lazy {
        runCatching { context.getSystemService(MediaSessionManager::class.java) }.getOrNull()
    }
    private val audioManager by lazy {
        runCatching { context.getSystemService(AudioManager::class.java) }.getOrNull()
    }
    private val listenerComponent by lazy {
        ComponentName(context, MediaNotificationListenerService::class.java)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var observing = false
    private val observeLock = Any()
    private var sessionsListenerRegistered = false

    private var activeController: MediaController? = null

    // Main thread: the session that earned card visibility for this observation window —
    // once set, the card survives pause/stop until stopObserving() resets it.
    private var shownToken: MediaSession.Token? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            if (observing) publish(controllers.orEmpty())
        }

    // Play/pause/seek/track changes on the shown session; re-picking from a fresh query
    // also catches the session handing off (e.g. its app got killed, another took over).
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = republish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = republish()
        override fun onSessionDestroyed() = republish()
    }

    private fun republish() {
        if (observing) publish(queryControllers())
    }

    override fun startObserving() {
        synchronized(observeLock) {
            if (observing) return
            observing = true
            if (isNotificationAccessGranted()) {
                registerSessionsListener()
                // No replay on registration — publish the current sessions ourselves.
                publish(queryControllers())
            }
        }
    }

    override fun stopObserving() {
        synchronized(observeLock) {
            // No early return: refresh() may have attached a controller before
            // startObserving ever ran, and that callback must not outlive the screen.
            observing = false
            if (sessionsListenerRegistered) {
                runCatching {
                    mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
                }
                sessionsListenerRegistered = false
            }
            detachController()
            _state.value = MediaControlsState()
        }
    }

    override suspend fun refresh() {
        val granted = isNotificationAccessGranted()
        val controllers = if (granted) {
            withContext(Dispatchers.IO) { queryControllers() }
        } else {
            emptyList()
        }
        val musicActive = if (granted) false else withContext(Dispatchers.IO) { isMusicActive() }
        withContext(Dispatchers.Main.immediate) {
            synchronized(observeLock) {
                if (granted) {
                    // Access granted since startObserving (Settings round-trip) — hook up now
                    if (observing && !sessionsListenerRegistered) registerSessionsListener()
                } else if (sessionsListenerRegistered) {
                    // Access revoked while observing
                    runCatching {
                        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
                    }
                    sessionsListenerRegistered = false
                }
            }
            if (granted) {
                publish(controllers)
            } else {
                detachController()
                _state.value = MediaControlsState(needsNotificationAccess = musicActive)
            }
        }
    }

    /** Must hold [observeLock]. */
    private fun registerSessionsListener() {
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsListener, listenerComponent, mainHandler
            ) ?: return
            sessionsListenerRegistered = true
        }
    }

    /** SecurityException when listener access is missing/revoked — treated as no sessions. */
    private fun queryControllers(): List<MediaController> = runCatching {
        mediaSessionManager?.getActiveSessions(listenerComponent).orEmpty()
    }.getOrDefault(emptyList())

    /** Main thread only: swaps the tracked controller's callback and publishes a snapshot. */
    private fun publish(controllers: List<MediaController>) {
        val picked = pickController(controllers)
        val current = activeController
        if (current?.sessionToken != picked?.sessionToken) {
            current?.let { runCatching { it.unregisterCallback(controllerCallback) } }
            picked?.let { runCatching { it.registerCallback(controllerCallback, mainHandler) } }
            activeController = picked
        }
        val snapshot = picked?.let { snapshot(it) }
        val visible = when {
            snapshot == null -> false
            // Playing earns the session visibility for the rest of this observation window.
            snapshot.isPlaying -> true
            // Already earned it — the card survives pause/stop until the sheet closes.
            picked.sessionToken == shownToken -> true
            // First seen non-playing this window: only paused-moments-ago sessions show;
            // anything older stays hidden until it plays again.
            else -> {
                val now = SystemClock.elapsedRealtime()
                snapshot.positionUpdatedAtMs in (now - RECENT_PAUSE_WINDOW_MS)..now
            }
        }
        if (visible) shownToken = picked?.sessionToken
        _state.value = MediaControlsState(session = snapshot.takeIf { visible })
    }

    private fun detachController() {
        shownToken = null
        activeController?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        activeController = null
    }

    private fun pickController(controllers: List<MediaController>): MediaController? {
        val candidates = controllers.filter { controller ->
            when (runCatching { controller.playbackState?.state }.getOrNull()) {
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_PAUSED -> true
                // A session that already owns the card this window stays pickable even once
                // stopped, so stopping playback doesn't drop the card mid-sheet.
                else -> controller.sessionToken == shownToken
            }
        }
        val playing = candidates.firstOrNull { controller ->
            val state = runCatching { controller.playbackState?.state }.getOrNull()
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        }
        if (playing != null) return playing
        // Between paused sessions, stick with the one already shown so the card doesn't flap.
        val currentToken = activeController?.sessionToken
        return candidates.firstOrNull { it.sessionToken == currentToken } ?: candidates.firstOrNull()
    }

    private fun snapshot(controller: MediaController): ActiveMediaSession {
        val metadata = runCatching { controller.metadata }.getOrNull()
        val playback = runCatching { controller.playbackState }.getOrNull()
        val actions = playback?.actions ?: 0L
        val playState = playback?.state
        return ActiveMediaSession(
            packageName = controller.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            artwork = metadata?.let { m ->
                runCatching {
                    m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                }.getOrNull()
            },
            isPlaying = playState == PlaybackState.STATE_PLAYING ||
                playState == PlaybackState.STATE_BUFFERING,
            positionMs = (playback?.position ?: 0L).coerceAtLeast(0L),
            positionUpdatedAtMs = playback?.lastPositionUpdateTime ?: 0L,
            playbackSpeed = playback?.playbackSpeed?.takeIf { it != 0f } ?: 1f,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            canTogglePlay = actions and (
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE
                ) != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L
        )
    }

    override fun togglePlayPause() {
        val controller = activeController ?: return
        runCatching {
            val playState = controller.playbackState?.state
            if (playState == PlaybackState.STATE_PLAYING ||
                playState == PlaybackState.STATE_BUFFERING
            ) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
    }

    override fun skipToPrevious() {
        runCatching { activeController?.transportControls?.skipToPrevious() }
    }

    override fun skipToNext() {
        runCatching { activeController?.transportControls?.skipToNext() }
    }

    override fun seekTo(positionMs: Long) {
        runCatching { activeController?.transportControls?.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    override fun openMediaApp(): Boolean {
        val controller = activeController ?: return false
        // The overlay is visible, but sending another app's PendingIntent still needs an
        // explicit background-start opt-in on Android 14+.
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                pendingIntentBackgroundStartMode()
            )
            .toBundle()
        val sessionActivity = runCatching { controller.sessionActivity }.getOrNull()
        if (sessionActivity != null &&
            runCatching { sessionActivity.send(context, 0, null, null, null, null, options) }.isSuccess
        ) {
            return true
        }
        val launchIntent = runCatching {
            context.packageManager.getLaunchIntentForPackage(controller.packageName)
        }.getOrNull() ?: return false
        return runCatching {
            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    private fun pendingIntentBackgroundStartMode(): Int =
        if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        } else {
            legacyPendingIntentBackgroundStartMode()
        }

    @Suppress("DEPRECATION")
    private fun legacyPendingIntentBackgroundStartMode(): Int =
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED

    override fun isNotificationAccessGranted(): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }.getOrDefault(false)

    private fun isMusicActive(): Boolean =
        runCatching { audioManager?.isMusicActive == true }.getOrDefault(false)
}
