package com.joyal.swyplauncher.ui.state

import com.joyal.swyplauncher.domain.model.AppInfo

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    // Separate filtered results for each mode
    val handwritingFilteredApps: List<AppInfo> = emptyList(),
    val handwritingSmartApps: List<AppInfo> = emptyList(),
    val indexFilteredApps: List<AppInfo> = emptyList(),
    val indexSmartApps: List<AppInfo> = emptyList(),
    val keyboardFilteredApps: List<AppInfo> = emptyList(),
    val keyboardSmartApps: List<AppInfo> = emptyList(),
    val voiceFilteredApps: List<AppInfo> = emptyList(),
    val voiceSmartApps: List<AppInfo> = emptyList(),
    val hiddenApps: List<AppInfo> = emptyList(),
    val newlyInstalledAppPackage: String? = null, // Package name of app that should show badge
    val isLoading: Boolean = false,
    val error: String? = null,
    // Calculator results for each mode
    val handwritingCalculatorResult: String? = null,
    val keyboardCalculatorResult: String? = null,
    val voiceCalculatorResult: String? = null,
    // Tooltip visibility
    val showHideAppTooltip: Boolean = false
)
