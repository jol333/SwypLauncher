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
    // Unit conversion results for each mode
    val handwritingUnitResult: UnitResultState? = null,
    val keyboardUnitResult: UnitResultState? = null,
    val voiceUnitResult: UnitResultState? = null,
    // Tooltip visibility
    val showHideAppTooltip: Boolean = false
)

// Unit conversion display state
data class UnitResultState(
    val category: com.joyal.swyplauncher.util.UnitData.Category,
    val sourceAmount: Double,
    val targetAmount: Double? = null,
    val fromId: String,
    val toId: String,
    val sourceApprox: String? = null,  // human-readable subtext (large/small values)
    val targetApprox: String? = null,
    val error: String? = null
)

// Currency conversion display state
data class CurrencyResultState(
    val sourceAmount: Double,
    val targetAmount: Double? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val ratesTimestamp: Long? = null,  // non-null = using locally cached rates
    val fromCode: String = "",
    val toCode: String = ""
)
