package com.joyal.swyplauncher.ui.state

import com.joyal.swyplauncher.domain.model.AppInfo

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val availableLetters: List<Char> = emptyList(), // Pre-computed for Index mode performance
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
    // Currency conversion results for each mode
    val handwritingCurrencyResult: CurrencyResultState? = null,
    val keyboardCurrencyResult: CurrencyResultState? = null,
    val voiceCurrencyResult: CurrencyResultState? = null,
    // Tooltip visibility
    val showHideAppTooltip: Boolean = false
)

// Currency conversion display state
data class CurrencyResultState(
    val inputDisplay: String,        // formatted source amount, e.g. "$100"
    val outputDisplay: String? = null, // formatted target amount, null while loading
    val isLoading: Boolean = false,
    val error: String? = null,
    val ratesTimestamp: Long? = null,  // non-null = using locally cached rates
    val fromCode: String = "",
    val toCode: String = ""
)
