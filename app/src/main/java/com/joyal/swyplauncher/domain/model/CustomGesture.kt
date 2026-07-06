package com.joyal.swyplauncher.domain.model

data class CustomGesture(
    val id: String,
    val template: List<NormalizedPoint>,
    val previewStrokes: List<List<NormalizedPoint>>,
    val appIds: Set<String>,
    // App-shortcut identifiers ("packageName/shortcutId") assigned to this gesture. Only ever
    // populated when the "Search app shortcuts" feature is enabled; empty for older gestures.
    val shortcutIds: Set<String> = emptySet()
)

data class NormalizedPoint(val x: Float, val y: Float)
