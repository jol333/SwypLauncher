package com.joyal.swyplauncher.domain.model

sealed class AppChangeEvent {
    data class AppInstalled(val packageName: String) : AppChangeEvent()
    data class AppUninstalled(val packageName: String) : AppChangeEvent()
}
