package com.joyal.swyplauncher.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.ui.AssistActivity

class SwypLauncherWidget : GlanceAppWidget() {

    companion object {
        // Breakpoints for responsive layout
        private val SMALL = DpSize(100.dp, 48.dp)
        private val MEDIUM = DpSize(180.dp, 140.dp)
        private val WIDE = DpSize(250.dp, 56.dp)
        private val VERTICAL = DpSize(48.dp, 260.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, WIDE, VERTICAL)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("swyplauncher_prefs", Context.MODE_PRIVATE)
        val savedModes = prefs.getString("enabled_modes", null)
        val enabledModes = if (savedModes != null) {
            savedModes.split(",").mapNotNull {
                try { LauncherMode.valueOf(it) } catch (e: Exception) { null }
            }
        } else {
            LauncherMode.entries.toList()
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context, enabledModes)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, enabledModes: List<LauncherMode>) {
    val size = LocalSize.current

    val backgroundColor = ColorProvider(R.color.widget_background)
    val containerColor = ColorProvider(R.color.widget_icon_container)
    val textColor = ColorProvider(R.color.widget_text_color)

    Box(
    modifier = GlanceModifier
        .fillMaxSize()
        .background(backgroundColor)
        .cornerRadius(12.dp),
    contentAlignment = Alignment.Center
) {
    // Define breakpoints for cleaner reading
    val minGridWidth = 180.dp
    val minGridHeight = 140.dp
    val minWideWidth = 250.dp
    val minTallHeight = 250.dp
    
    when {
        // 1. GRID / LARGE SQUARE
        // We only show Grid if BOTH dimensions are substantial.
        // This takes priority if the widget is truly "big" in both directions (e.g., 4x4).
        size.width >= minGridWidth && size.height >= minGridHeight -> {
            GridLayout(context, enabledModes, containerColor, textColor)
        }

        // 2. WIDE STRIP (Horizontal)
        // If it failed Grid, but has width, it is a horizontal strip (e.g. 4x1, 3x1).
        // We lower the height requirement slightly here to ensure 4x1s are caught.
        size.width >= minWideWidth -> {
            WideLayout(context, enabledModes, containerColor, textColor)
        }

        // 3. TALL STRIP (Vertical)
        // If it failed Grid and Wide, but has height, it is a vertical strip (e.g. 1x3, 1x4).
        // We relax the height threshold slightly compared to your original 260dp to catch 1x3s.
        size.height >= 200.dp -> { 
            VerticalLayout(context, enabledModes, containerColor, textColor)
        }

        // 4. COMPACT (1x1)
        // Fallback for anything smaller.
        else -> {
            CompactLayout(context, containerColor)
        }
    }
}
}

@Composable
private fun GridLayout(
    context: Context,
    enabledModes: List<LauncherMode>,
    containerColor: ColorProvider,
    textColor: ColorProvider
) {
    val rows = enabledModes.chunked(2)

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = GlanceModifier
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(rows, itemId = { row -> row.hashCode().toLong() }) { row ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { index, mode ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.width(12.dp))
                        }
                        ModeButton(context, mode, containerColor, textColor, showLabel = true, large = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun WideLayout(
    context: Context,
    enabledModes: List<LauncherMode>,
    containerColor: ColorProvider,
    textColor: ColorProvider
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        enabledModes.forEachIndexed { index, mode ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            ModeButton(context, mode, containerColor, textColor, showLabel = true, large = false)
        }
    }
}

@Composable
private fun VerticalLayout(
    context: Context,
    enabledModes: List<LauncherMode>,
    containerColor: ColorProvider,
    textColor: ColorProvider
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = GlanceModifier
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(enabledModes, itemId = { it.ordinal.toLong() }) { mode ->
                ModeButton(context, mode, containerColor, textColor, showLabel = true, large = false)
            }
        }
    }
}

@Composable
private fun CompactLayout(
    context: Context,
    containerColor: ColorProvider
) {
    val intent = Intent(context, AssistActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .background(containerColor)
                .cornerRadius(12.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_quick_tile),
                contentDescription = context.getString(R.string.swyp_launcher_tile_label),
                modifier = GlanceModifier.size(20.dp),
                colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(R.color.widget_icon_tint))
            )
        }
    }
}

@Composable
private fun ModeButton(
    context: Context,
    mode: LauncherMode,
    containerColor: ColorProvider,
    textColor: ColorProvider,
    showLabel: Boolean,
    large: Boolean
) {
    val (iconRes, label) = getModeIconAndLabel(context, mode)
    val intent = createModeIntent(context, mode)

    val iconSize = if (large) 32.dp else 26.dp
    val containerSize = if (large) 56.dp else 44.dp
    val cornerSize = if (large) 18.dp else 14.dp

    Column(
        modifier = GlanceModifier
            .clickable(actionStartActivity(intent))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .size(containerSize)
                .background(containerColor)
                .cornerRadius(cornerSize),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(R.color.widget_icon_tint))
            )
        }
        if (showLabel) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}



private fun getModeIconAndLabel(context: Context, mode: LauncherMode): Pair<Int, String> {
    return when (mode) {
        LauncherMode.HANDWRITING -> R.drawable.ic_handwriting to context.getString(R.string.shortcut_handwriting_short)
        LauncherMode.INDEX -> R.drawable.ic_index to context.getString(R.string.shortcut_index_short)
        LauncherMode.KEYBOARD -> R.drawable.ic_keyboard to context.getString(R.string.shortcut_keyboard_short)
        LauncherMode.VOICE -> R.drawable.ic_microphone to context.getString(R.string.shortcut_voice_short)
    }
}

private fun createModeIntent(context: Context, mode: LauncherMode): Intent {
    return Intent(context, AssistActivity::class.java).apply {
        putExtra("launcher_mode", mode.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}
