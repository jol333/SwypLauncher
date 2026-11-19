package com.joyal.swyplauncher.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.ui.AssistActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class SwypLauncherWidget : GlanceAppWidget() {

    // The launcher shows the closest of these that fits the widget's real size, so a layout always
    // gets at least its bucket (LocalSize) and stretches into the rest with weights; only a widget
    // smaller than 40x40 gets less. RemoteViews accepts at most 16 sizes (this is all of them).
    override val sizeMode = SizeMode.Responsive(
        setOf(
            // 1x1 (and 1x2)
            DpSize(40.dp, 40.dp), DpSize(64.dp, 64.dp),
            // Short rows of round buttons (2x1, 3x1, half-width)
            DpSize(130.dp, 40.dp), DpSize(130.dp, 64.dp),
            // Search bar (4x1 and up); anything narrower, like a half-width widget, gets buttons only
            DpSize(340.dp, 40.dp), DpSize(340.dp, 64.dp), DpSize(340.dp, 88.dp),
            // One-column strip (1x3 and up)
            DpSize(56.dp, 200.dp),
            // Bento grid: 2x2, 2x3, 3x2, 4x2, 3x3, 4x3 and up. It starts at 140dp tall because some
            // launchers (vivo) lay out a one-row widget ~117dp tall and scale it down
            DpSize(130.dp, 140.dp), DpSize(150.dp, 150.dp), DpSize(150.dp, 230.dp),
            DpSize(220.dp, 150.dp), DpSize(300.dp, 150.dp), DpSize(300.dp, 195.dp),
            DpSize(220.dp, 230.dp), DpSize(300.dp, 230.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("swyplauncher_prefs", Context.MODE_PRIVATE)
        val initialModes = readEnabledModes(prefs)

        provideContent {
            // Collected inside the composition: while a session is alive, update()/updateAll()
            // only recompose it and never call provideGlance again, so a value read above would
            // keep showing modes that were just turned off in settings.
            val enabledModes by remember { enabledModesFlow(prefs) }.collectAsState(initialModes)
            WidgetContent(enabledModes)
        }
    }
}

private const val KEY_ENABLED_MODES = "enabled_modes" // = PreferencesRepositoryImpl

private fun readEnabledModes(prefs: SharedPreferences): List<LauncherMode> {
    val saved = prefs.getString(KEY_ENABLED_MODES, null) ?: return LauncherMode.entries.toList()
    return saved.split(",").mapNotNull { name -> LauncherMode.entries.find { it.name == name } }
}

private fun enabledModesFlow(prefs: SharedPreferences): Flow<List<LauncherMode>> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ENABLED_MODES) trySend(readEnabledModes(prefs))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    send(readEnabledModes(prefs))
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()

// Launchers clip widgets to their own corner shape (anything from square to a full capsule on a
// one-row widget), often not the system radius. Content is inset and rounded so that it sits inside
// any corner up to ~50dp: a rounded element of radius r at inset p stays clear of a corner of
// radius R while R <= (p + 0.29 r) * 3.41. Pills and circles at the ends of a bar follow the bar's
// own capsule, so they are safe whatever its height.
private val TILE_RADIUS = 20.dp // = widget_tile.xml
private val GRID_PADDING = 12.dp
private val HEADER_HEIGHT = 28.dp
private val GAP = 8.dp

private val accent = ColorProvider(R.color.widget_accent)
private val onAccent = ColorProvider(R.color.widget_on_accent)
private val textPrimary = ColorProvider(R.color.widget_text_color)
private val textSecondary = ColorProvider(R.color.widget_text_secondary)

@Composable
private fun WidgetContent(enabledModes: List<LauncherMode>) {
    val size = LocalSize.current

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_background))
            .cornerRadius(android.R.dimen.system_app_widget_background_radius),
        contentAlignment = Alignment.Center
    ) {
        when {
            enabledModes.isEmpty() || (size.width < 110.dp && size.height < 150.dp) -> CompactLayout()
            size.width < 110.dp -> ColumnLayout(enabledModes)
            size.height < 140.dp && size.width >= 340.dp -> SearchBarLayout(enabledModes)
            size.height < 140.dp -> ButtonRowLayout(enabledModes)
            else -> BentoGridLayout(enabledModes)
        }
    }
}

/** 1x1: the Swyp mark; opens the assistant in its last mode. */
@Composable
private fun CompactLayout() {
    val context = LocalContext.current
    val size = LocalSize.current
    val badge = (minOf(size.width, size.height) * 0.7f).coerceIn(28.dp, 48.dp)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(createOpenIntent(context))),
        contentAlignment = Alignment.Center
    ) {
        BrandBadge(badge, context.getString(R.string.swyp_launcher_tile_label))
    }
}

/**
 * Short and wide (4x1, 5x1): a search pill that opens Type mode, then one button per other mode.
 * Everything fills the bar's real height (the bucket is only a lower bound, and some launchers,
 * like vivo's, lay a widget out larger than its cell and scale it down), with round buttons as
 * wide as they are tall. The pill always gets at least half the width so its hint stays readable
 * however tall the buttons grow.
 */
@Composable
private fun SearchBarLayout(modes: List<LauncherMode>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val padding = barPadding(size.height)
    val minHeight = size.height - padding * 2
    val searchMode = LauncherMode.KEYBOARD.takeIf { it in modes }
    val otherModes = if (searchMode != null) modes - searchMode else modes

    val pillWidth = if (otherModes.size >= 2) {
        (size.width - padding * 2 - GAP) / 2
    } else {
        size.width - padding * 2 - (minHeight + GAP) * otherModes.size
    }
    // Badge + gap + ~60dp of hint + end padding; without the hint a pill is just another button
    if (pillWidth < minHeight + 82.dp) {
        ButtonRowLayout(modes)
        return
    }
    val searchIntent = if (searchMode != null) createModeIntent(context, searchMode) else createOpenIntent(context)
    val hint = context.getString(R.string.search_apps_hint)
    val hintSize = if (minHeight >= 40.dp) 16.sp else 14.sp

    if (otherModes.size >= 2) {
        // The other half holds one equal cell per button, each circle as large as its cell's width
        // and the bar's height allow. The bar wraps its height around those circles, and a
        // horizontal LinearLayout stretches its match-parent pill to that same height, so the pill
        // is never taller than the buttons.
        Row(
            modifier = GlanceModifier.fillMaxWidth().wrapContentHeight().padding(padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchPill(searchIntent, hint, hintSize)
            Spacer(GlanceModifier.width(GAP))
            Row(
                modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                otherModes.forEachIndexed { index, mode ->
                    if (index > 0) Spacer(GlanceModifier.width(GAP))
                    Box(
                        modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        ModeButton(mode, GlanceModifier.fillMaxWidth(), sizeFromWidth = true)
                    }
                }
            }
        }
    } else {
        // At most one button: it and the pill both fill the bar's height
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchPill(searchIntent, hint, hintSize)
            otherModes.forEach { mode ->
                Spacer(GlanceModifier.width(GAP))
                ModeButton(mode, GlanceModifier.fillMaxHeight())
            }
        }
    }
}

/**
 * The search pill: the Swyp mark filling the pill's height with an even 5dp inset, then the hint.
 * It is as tall as its row. Its height is match-parent through a dimen rather than fillMaxHeight(),
 * which Glance would pass up to the row (a wrap-height row with a fill-height child is made
 * fill-height), stretching the bar and the pill past the round buttons.
 */
@Composable
private fun RowScope.SearchPill(searchIntent: Intent, hint: String, hintSize: TextUnit) {
    Row(
        modifier = GlanceModifier
            .defaultWeight()
            .height(R.dimen.widget_match_parent)
            .background(ImageProvider(R.drawable.widget_pill))
            .clickable(actionStartActivity(searchIntent), rippleOverride = R.drawable.widget_ripple)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fillHeight = GlanceModifier.fillMaxHeight().wrapContentWidth()
        Box(modifier = GlanceModifier.fillMaxHeight().padding(5.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(R.drawable.widget_badge_circle),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = fillHeight
            )
            Image(
                provider = ImageProvider(R.drawable.widget_glyph_brand),
                contentDescription = hint,
                contentScale = ContentScale.Fit,
                modifier = fillHeight,
                colorFilter = ColorFilter.tint(onAccent)
            )
        }
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = hint,
            style = TextStyle(color = textSecondary, fontSize = hintSize),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

/** Short and narrow (2x1, 3x1): round mode buttons spread evenly, as large as each cell allows. */
@Composable
private fun ButtonRowLayout(modes: List<LauncherMode>) {
    val size = LocalSize.current
    val padding = barPadding(size.height)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(padding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEachIndexed { index, mode ->
            if (index > 0) Spacer(GlanceModifier.width(GAP))
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ModeButton(mode, GlanceModifier.fillMaxHeight())
            }
        }
    }
}

/** Tall and narrow (1x3 and up): round mode buttons stacked evenly, as large as each cell allows. */
@Composable
private fun ColumnLayout(modes: List<LauncherMode>) {
    val size = LocalSize.current
    val padding = 8.dp

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        modes.forEachIndexed { index, mode ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ModeButton(mode, GlanceModifier.fillMaxHeight())
            }
        }
    }
}

/**
 * 2x2 and up: bento tiles like the settings screen, under a section-style header when there is
 * room. Each tile picks the richest layout its estimated size can hold.
 */
@Composable
private fun BentoGridLayout(modes: List<LauncherMode>) {
    val context = LocalContext.current
    val size = LocalSize.current

    val aspect = size.width / size.height
    val columns = when {
        modes.size == 1 || aspect < 0.8f -> 1
        modes.size == 2 -> if (aspect >= 1f) 2 else 1
        modes.size == 3 -> if (aspect >= 1.6f) 3 else 2
        else -> if (aspect >= 2.4f) modes.size else 2
    }
    val rows = modes.chunked(columns)
    val innerWidth = size.width - GRID_PADDING * 2
    val narrowestTile = (innerWidth - GAP * (columns - 1)) / columns
    fun tileHeight(header: Dp) = (size.height - GRID_PADDING * 2 - header - GAP * (rows.size - 1)) / rows.size
    // The header only takes room the tiles don't need for their labels
    val showHeader = size.height >= 190.dp &&
        tileStyle(narrowestTile, tileHeight(HEADER_HEIGHT + GAP)) == tileStyle(narrowestTile, tileHeight(0.dp))
    val tileHeight = tileHeight(if (showHeader) HEADER_HEIGHT + GAP else 0.dp)

    Column(modifier = GlanceModifier.fillMaxSize().padding(GRID_PADDING)) {
        if (showHeader) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .cornerRadius(HEADER_HEIGHT / 2)
                    .clickable(actionStartActivity(createOpenIntent(context)))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_quick_tile),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(accent)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = context.getString(R.string.swyp_launcher_tile_label).uppercase(),
                    style = TextStyle(
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(GAP))
        }

        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(GAP))
            // A short last row (3 modes in 2 columns) stretches its tiles across the full width
            val tileWidth = (innerWidth - GAP * (row.size - 1)) / row.size
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                row.forEachIndexed { index, mode ->
                    if (index > 0) Spacer(GlanceModifier.width(GAP))
                    BentoTile(
                        mode = mode,
                        width = tileWidth,
                        height = tileHeight,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoTile(mode: LauncherMode, width: Dp, height: Dp, modifier: GlanceModifier) {
    val context = LocalContext.current
    val (iconRes, label) = getModeIconAndLabel(context, mode)

    Box(
        modifier = modifier
            .background(ImageProvider(R.drawable.widget_tile))
            .cornerRadius(TILE_RADIUS)
            .clickable(actionStartActivity(createModeIntent(context, mode)))
    ) {
        when (tileStyle(width, height)) {
            // Badge at the top, title at the bottom: the settings cards' layout
            TileStyle.STACKED -> Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                ModeBadge(iconRes, 36.dp)
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = label,
                    style = TextStyle(color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            // Badge and title side by side
            TileStyle.INLINE -> Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeBadge(iconRes, minOf(36.dp, height - 12.dp))
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = label,
                    style = TextStyle(color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            // Small square tiles: badge over a small centered title
            TileStyle.CENTERED -> Column(
                modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeBadge(iconRes, 28.dp)
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = label,
                    style = TextStyle(
                        color = textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
            TileStyle.ICON -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ModeBadge(iconRes, minOf(36.dp, height - 12.dp, width - 12.dp).coerceAtLeast(20.dp))
            }
        }
    }
}

private enum class TileStyle { STACKED, INLINE, CENTERED, ICON }

/** The richest tile content that fits a tile of this (minimum) size. */
private fun tileStyle(width: Dp, height: Dp): TileStyle = when {
    height >= 80.dp && width >= 90.dp -> TileStyle.STACKED
    height >= 44.dp && width >= 92.dp -> TileStyle.INLINE
    height >= 54.dp && width >= 56.dp -> TileStyle.CENTERED
    else -> TileStyle.ICON
}

/** Round, outlined mode button; see [RoundIcon] for its size. */
@Composable
private fun ModeButton(mode: LauncherMode, modifier: GlanceModifier, sizeFromWidth: Boolean = false) {
    val context = LocalContext.current
    val (_, label) = getModeIconAndLabel(context, mode)

    Box(
        modifier = modifier.clickable(
            actionStartActivity(createModeIntent(context, mode)),
            rippleOverride = R.drawable.widget_ripple
        ),
        contentAlignment = Alignment.Center
    ) {
        RoundIcon(modeGlyph(mode), label, sizeFromWidth)
    }
}

/**
 * A circle with a mode icon in its middle, as tall as its parent and exactly as wide (or, with
 * [sizeFromWidth], as wide as its parent and exactly as tall, but never taller than the space
 * allows): both are images with an aspect ratio, so ContentScale.Fit with one side wrapped makes
 * each ImageView size that side from the other (adjustViewBounds), and the icon (inset by a
 * fraction) scales with the circle. Where the space runs out the other way, both stay round and fit.
 */
@Composable
private fun RoundIcon(@DrawableRes glyphRes: Int, contentDescription: String, sizeFromWidth: Boolean = false) {
    // Glance turns on adjustViewBounds only for a Fit image with an explicitly wrapped side
    val imageModifier = if (sizeFromWidth) {
        GlanceModifier.fillMaxWidth().wrapContentHeight()
    } else {
        GlanceModifier.fillMaxHeight().wrapContentWidth()
    }
    Box(
        modifier = if (sizeFromWidth) GlanceModifier.fillMaxWidth() else GlanceModifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_circle),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = imageModifier
        )
        Image(
            provider = ImageProvider(glyphRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = imageModifier,
            colorFilter = ColorFilter.tint(accent)
        )
    }
}

/** Mode icon on a soft accent circle, as in the settings screen's option rows. */
@Composable
private fun ModeBadge(@DrawableRes iconRes: Int, size: Dp) {
    Box(
        modifier = GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_badge)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(size * 0.6f),
            colorFilter = ColorFilter.tint(accent)
        )
    }
}

/** The Swyp mark on a filled accent circle. */
@Composable
private fun BrandBadge(size: Dp, contentDescription: String) {
    Box(
        modifier = GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_accent_circle)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_quick_tile),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(size * 0.58f),
            colorFilter = ColorFilter.tint(onAccent)
        )
    }
}

/** Padding around one-row layouts; it grows with the height so the buttons don't get too large. */
private fun barPadding(height: Dp): Dp = when {
    height < 64.dp -> 6.dp
    height < 88.dp -> 10.dp
    else -> 14.dp
}


private fun getModeIconAndLabel(context: Context, mode: LauncherMode): Pair<Int, String> {
    return when (mode) {
        LauncherMode.HANDWRITING -> R.drawable.ic_handwriting to context.getString(R.string.shortcut_handwriting_short)
        LauncherMode.INDEX -> R.drawable.ic_index to context.getString(R.string.shortcut_index_short)
        LauncherMode.KEYBOARD -> R.drawable.ic_keyboard to context.getString(R.string.shortcut_keyboard_short)
        LauncherMode.VOICE -> R.drawable.ic_microphone to context.getString(R.string.shortcut_voice_short)
    }
}

/** The mode's icon inset to sit inside widget_circle (see [RoundIcon]). */
@DrawableRes
private fun modeGlyph(mode: LauncherMode): Int = when (mode) {
    LauncherMode.HANDWRITING -> R.drawable.widget_glyph_handwriting
    LauncherMode.INDEX -> R.drawable.widget_glyph_index
    LauncherMode.KEYBOARD -> R.drawable.widget_glyph_keyboard
    LauncherMode.VOICE -> R.drawable.widget_glyph_microphone
}

private fun createOpenIntent(context: Context): Intent {
    return Intent(context, AssistActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}

private fun createModeIntent(context: Context, mode: LauncherMode): Intent {
    return createOpenIntent(context).putExtra("launcher_mode", mode.name)
}
