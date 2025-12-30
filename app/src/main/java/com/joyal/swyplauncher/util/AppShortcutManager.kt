package com.joyal.swyplauncher.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.LauncherMode

object AppShortcutManager {
    
    /**
     * Updates the dynamic shortcuts based on the enabled modes.
     * This should be called whenever the user changes their mode preferences.
     */
    fun updateShortcuts(context: Context, enabledModes: List<LauncherMode>) {
        val shortcuts = enabledModes.map { mode ->
            createShortcutForMode(context, mode)
        }
        
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
    
    private fun createShortcutForMode(context: Context, mode: LauncherMode): ShortcutInfoCompat {
        val (shortLabel, longLabel, icon) = when (mode) {
            LauncherMode.HANDWRITING -> Triple(
                context.getString(R.string.shortcut_handwriting_short),
                context.getString(R.string.shortcut_handwriting_long),
                R.drawable.ic_handwriting
            )
            LauncherMode.INDEX -> Triple(
                context.getString(R.string.shortcut_index_short),
                context.getString(R.string.shortcut_index_long),
                R.drawable.ic_index
            )
            LauncherMode.KEYBOARD -> Triple(
                context.getString(R.string.shortcut_keyboard_short),
                context.getString(R.string.shortcut_keyboard_long),
                R.drawable.ic_keyboard
            )
            LauncherMode.VOICE -> Triple(
                context.getString(R.string.shortcut_voice_short),
                context.getString(R.string.shortcut_voice_long),
                R.drawable.ic_microphone
            )
        }
        
        val intent = Intent(context, com.joyal.swyplauncher.ui.AssistActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("launcher_mode", mode.name)
        }
        
        return ShortcutInfoCompat.Builder(context, "${mode.name.lowercase()}_mode")
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(createTintedIcon(context, icon, Color.GRAY))
            .setIntent(intent)
            .build()
    }

    private fun createTintedIcon(context: Context, iconResId: Int, color: Int): IconCompat {
        val drawable = ContextCompat.getDrawable(context, iconResId)
            ?: return IconCompat.createWithResource(context, iconResId)

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val wrapped = DrawableCompat.wrap(drawable).mutate()
        
        wrapped.setBounds(0, 0, canvas.width, canvas.height)
        DrawableCompat.setTint(wrapped, color)
        wrapped.draw(canvas)

        return IconCompat.createWithBitmap(bitmap)
    }
}
