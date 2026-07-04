package com.joyal.swyplauncher.domain.model

data class CustomGesture(
    val id: String,
    val template: List<NormalizedPoint>,
    val previewStrokes: List<List<NormalizedPoint>>,
    val appIds: Set<String>
)

data class NormalizedPoint(val x: Float, val y: Float)
