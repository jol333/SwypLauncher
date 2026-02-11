package com.joyal.swyplauncher.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.compose.ui.graphics.Color
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.ui.AssistActivity

class SwypLauncherWidget : GlanceAppWidget() {

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
            WidgetContent(context, enabledModes)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, enabledModes: List<LauncherMode>) {
    val backgroundColor = ColorProvider(Color(0xFF1A1A1D))
    val textColor = ColorProvider(Color.White)
    val accentColor = ColorProvider(Color(0xFF00BC7D))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            enabledModes.forEachIndexed { index, mode ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                val (iconRes, label) = when (mode) {
                    LauncherMode.HANDWRITING -> R.drawable.ic_handwriting to context.getString(R.string.shortcut_handwriting_short)
                    LauncherMode.INDEX -> R.drawable.ic_index to context.getString(R.string.shortcut_index_short)
                    LauncherMode.KEYBOARD -> R.drawable.ic_keyboard to context.getString(R.string.shortcut_keyboard_short)
                    LauncherMode.VOICE -> R.drawable.ic_microphone to context.getString(R.string.shortcut_voice_short)
                }

                val intent = Intent(context, AssistActivity::class.java).apply {
                    putExtra("launcher_mode", mode.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                Column(
                    modifier = GlanceModifier
                        .clickable(actionStartActivityIntent(intent))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = label,
                        modifier = GlanceModifier.size(28.dp)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = label,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
