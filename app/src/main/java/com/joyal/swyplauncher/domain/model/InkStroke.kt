package com.joyal.swyplauncher.domain.model

data class InkStroke(
    val points: List<InkPoint>
)

data class InkPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long
)
