package com.joyal.swyplauncher.service

import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import com.joyal.swyplauncher.ui.AssistActivity

class SwypLauncherTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        getSharedPreferences("swyplauncher_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("qs_tile_added", true).apply()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        getSharedPreferences("swyplauncher_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("qs_tile_added", false).apply()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, AssistActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityAndCollapse(
            android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}
