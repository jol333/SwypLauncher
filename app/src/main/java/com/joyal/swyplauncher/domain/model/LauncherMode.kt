package com.joyal.swyplauncher.domain.model

enum class LauncherMode {
    CONTROL_CENTER,
    HANDWRITING,
    INDEX,
    KEYBOARD,
    VOICE
}

/** Human-readable name for settings UI ("CONTROL_CENTER" -> "Control Center"). */
fun LauncherMode.displayName(): String =
    name.lowercase().split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }
