package com.joyal.swyplauncher.domain.model

import android.graphics.drawable.Drawable

data class AppShortcut(
    val id: String,
    val shortLabel: CharSequence,
    val longLabel: CharSequence?,
    val icon: Drawable?,
    val packageName: String
)
