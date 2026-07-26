package com.joyal.swyplauncher.ui.screens

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.ActiveMediaSession
import com.joyal.swyplauncher.ui.theme.BentoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private val AccentMedia = BentoColors.AccentGreen
private val MediaIconOnAccent = Color(0xFF0B1020)

/**
 * Compact full-width "now playing" card (notification-panel style): artwork + title/artist
 * + previous/play-pause/next, with a seek bar when the session reports a duration.
 * Tapping anywhere outside the buttons opens the app that owns the media session.
 */
@Composable
fun MediaControlsCard(
    session: ActiveMediaSession,
    cardBackground: Color,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    // MediaController hands back a fresh Bitmap instance on every state publish, so track
    // change is detected via track identity + art dimensions and the first instance is kept —
    // otherwise the ambient crossfade would re-run on every play/pause/seek.
    val artwork = remember(
        session.artwork?.let {
            "${session.packageName}|${session.title}|${session.artist}|${it.width}x${it.height}"
        }
    ) { session.artwork }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBackground)
            .border(1.dp, BentoColors.BorderLight, shape)
            .clickable(onClick = onOpenApp)
    ) {
        AmbientArtworkBackground(
            artwork = artwork,
            isPlaying = session.isPlaying,
            modifier = Modifier.matchParentSize()
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoColors.CardBackgroundLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = remember(artwork) { artwork.asImageBitmap() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = BentoColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title ?: stringResource(R.string.cc_media_unknown_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    session.artist?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                MediaIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.cc_media_previous),
                    enabled = session.canSkipPrevious,
                    onClick = onSkipPrevious
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (session.canTogglePlay) AccentMedia else BentoColors.CardBackgroundLight)
                        .clickable(enabled = session.canTogglePlay, onClick = onTogglePlay)
                        .alpha(if (session.canTogglePlay) 1f else 0.4f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (session.isPlaying) R.string.cc_media_pause else R.string.cc_media_play
                        ),
                        tint = MediaIconOnAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                MediaIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.cc_media_next),
                    enabled = session.canSkipNext,
                    onClick = onSkipNext
                )
            }
            // Duration often arrives a beat after a new track starts; grow the card gently
            // instead of snapping the seek bar in. The row's gap animates with it, so the
            // spacing lives here rather than on the column.
            AnimatedVisibility(
                visible = session.durationMs > 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MediaSeekRow(
                    session = session,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

/**
 * Ambient wash behind the media card: the artwork's dominant color as the base coat, the
 * artwork itself heavily blurred on top, and a dark scrim so the white text, icons and seek
 * bar stay legible. Crossfades between tracks, fades out to the plain card while playback
 * is paused (and back in on resume); draws nothing when there's no artwork.
 */
@Composable
private fun AmbientArtworkBackground(
    artwork: Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val ambientAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "mediaAmbientAlpha"
    )
    // Fully faded: skip the layers entirely so the blur isn't rendered under a 0-alpha wash.
    if (ambientAlpha <= 0f) return
    Crossfade(
        targetState = artwork,
        animationSpec = tween(durationMillis = 600),
        label = "mediaAmbient",
        modifier = modifier.alpha(ambientAlpha)
    ) { art ->
        if (art != null) {
            val dominant by produceState<Color?>(initialValue = null, art) {
                value = withContext(Dispatchers.Default) { dominantColorOf(art) }
            }
            val baseColor by animateColorAsState(
                targetValue = dominant ?: Color.Transparent,
                animationSpec = tween(durationMillis = 400),
                label = "mediaAmbientBase"
            )
            Box(modifier = Modifier.fillMaxSize().background(baseColor)) {
                Image(
                    bitmap = remember(art) { art.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                    modifier = Modifier.fillMaxSize().blur(56.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f))
                )
            }
        }
    }
}

/**
 * Coarse dominant color: downscale, histogram pixels into 4-bit-per-channel buckets scored
 * toward saturated colors (so vivid accents beat large dull areas), then average the winning
 * bucket. Brightness is capped so the card's white foreground keeps contrast.
 */
private fun dominantColorOf(artwork: Bitmap): Color? = runCatching {
    val source = if (artwork.config == Bitmap.Config.HARDWARE) {
        artwork.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
    } else {
        artwork
    }
    val scaled = Bitmap.createScaledBitmap(source, 24, 24, true)
    val pixels = IntArray(24 * 24)
    scaled.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    if (scaled !== source) scaled.recycle()
    if (source !== artwork) source.recycle()

    val weights = FloatArray(4096)
    val sumR = FloatArray(4096)
    val sumG = FloatArray(4096)
    val sumB = FloatArray(4096)
    for (pixel in pixels) {
        if (pixel ushr 24 < 0x80) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val max = maxOf(r, g, b)
        val saturation = if (max == 0) 0f else (max - minOf(r, g, b)) / max.toFloat()
        val weight = 0.25f + saturation
        val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        weights[bucket] += weight
        sumR[bucket] += r * weight
        sumG[bucket] += g * weight
        sumB[bucket] += b * weight
    }
    val best = weights.indices.maxByOrNull { weights[it] } ?: return@runCatching null
    if (weights[best] <= 0f) return@runCatching null
    var r = sumR[best] / weights[best]
    var g = sumG[best] / weights[best]
    var b = sumB[best] / weights[best]
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    if (luminance > 150f) {
        val scale = 150f / luminance
        r *= scale
        g *= scale
        b *= scale
    }
    Color(r.toInt(), g.toInt(), b.toInt())
}.getOrNull()

/**
 * Shown in place of the media card while music is audible but notification-listener
 * access (the platform's only route to MediaSessions) hasn't been granted yet.
 */
@Composable
fun MediaAccessPromptCard(
    cardBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBackground)
            .border(1.dp, BentoColors.BorderLight, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = AccentMedia,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.cc_media_needs_access),
            style = MaterialTheme.typography.bodyMedium,
            color = BentoColors.TextSecondary
        )
    }
}

@Composable
private fun MediaIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun MediaSeekRow(
    session: ActiveMediaSession,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Sessions only report position on state changes; tick locally while playing.
    var playedMs by remember { mutableLongStateOf(livePositionMs(session)) }
    // Fraction under the finger during a drag; then pending until the session confirms.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(
        session.positionMs, session.positionUpdatedAtMs, session.isPlaying,
        session.playbackSpeed, session.durationMs
    ) {
        pendingSeekFraction = null
        playedMs = livePositionMs(session)
        while (session.isPlaying) {
            delay(1_000L)
            playedMs = livePositionMs(session)
        }
    }

    val overrideFraction = dragFraction ?: pendingSeekFraction
    val displayedMs = overrideFraction?.let { (it * session.durationMs).toLong() } ?: playedMs
    // durationMs can hit 0 while the row is still animating out — avoid a NaN fraction.
    val displayedFraction = if (session.durationMs > 0) {
        (displayedMs / session.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatTime(displayedMs),
            style = MaterialTheme.typography.labelSmall,
            color = BentoColors.TextMuted
        )
        Spacer(modifier = Modifier.width(10.dp))
        MediaSeekBar(
            fraction = displayedFraction,
            canSeek = session.canSeek,
            onDragFraction = { dragFraction = it },
            onDragCancel = { dragFraction = null },
            onCommitFraction = { fraction ->
                dragFraction = null
                pendingSeekFraction = fraction
                onSeekTo((fraction * session.durationMs).toLong())
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = formatTime(session.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = BentoColors.TextMuted
        )
    }
}

@Composable
private fun MediaSeekBar(
    fraction: Float,
    canSeek: Boolean,
    onDragFraction: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onCommitFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnDrag by rememberUpdatedState(onDragFraction)
    val currentOnCancel by rememberUpdatedState(onDragCancel)
    val currentOnCommit by rememberUpdatedState(onCommitFraction)

    // Horizontal twin of the volume/brightness sliders' arbiter: claim taps and horizontal
    // drags (consuming them away from the pager and the card's open-app click), hand
    // vertical movement back to the grid/sheet untouched.
    val gestureModifier = if (canSeek) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                var dx = 0f
                var dy = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@awaitEachGesture
                    if (!change.pressed) {
                        // Tap: jump straight to the released position
                        if (size.width > 0) {
                            currentOnCommit((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                        change.consume()
                        return@awaitEachGesture
                    }
                    val delta = change.positionChange()
                    dx += abs(delta.x)
                    dy += abs(delta.y)
                    if (dx > slop && dx > dy) {
                        change.consume()
                        break
                    }
                    if (dy > slop && dy >= dx) return@awaitEachGesture
                }
                // Horizontal drag: the seek bar owns the rest of the gesture
                var fraction = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: break
                    if (size.width > 0) {
                        fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                    if (!change.pressed) {
                        change.consume()
                        currentOnCommit(fraction)
                        return@awaitEachGesture
                    }
                    currentOnDrag(fraction)
                    change.consume()
                }
                // Pointer vanished without an up event — drop the preview
                currentOnCancel()
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.height(24.dp).then(gestureModifier)) {
        val trackHeight = 4.dp.toPx()
        val thumbRadius = if (canSeek) 6.dp.toPx() else 0f
        val trackWidth = (size.width - thumbRadius * 2).coerceAtLeast(0f)
        val centerY = size.height / 2f
        val corner = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = BentoColors.SliderTrack,
            topLeft = Offset(thumbRadius, centerY - trackHeight / 2f),
            size = Size(trackWidth, trackHeight),
            cornerRadius = corner
        )
        val fillWidth = trackWidth * fraction.coerceIn(0f, 1f)
        if (fillWidth > 0f) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(thumbRadius, centerY - trackHeight / 2f),
                size = Size(fillWidth, trackHeight),
                cornerRadius = corner
            )
        }
        if (canSeek) {
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbRadius + fillWidth, centerY)
            )
        }
    }
}

private fun livePositionMs(session: ActiveMediaSession): Long {
    val extrapolated = if (session.isPlaying && session.positionUpdatedAtMs > 0) {
        session.positionMs +
            ((SystemClock.elapsedRealtime() - session.positionUpdatedAtMs) * session.playbackSpeed)
                .toLong()
    } else {
        session.positionMs
    }
    return if (session.durationMs > 0) {
        extrapolated.coerceIn(0L, session.durationMs)
    } else {
        extrapolated.coerceAtLeast(0L)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
