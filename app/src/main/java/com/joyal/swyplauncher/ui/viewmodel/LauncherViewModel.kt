package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.domain.usecase.GetSmartAppListUseCase
import com.joyal.swyplauncher.domain.usecase.LaunchAppUseCase
import com.joyal.swyplauncher.domain.usecase.ObserveAppChangesUseCase
import com.joyal.swyplauncher.domain.usecase.RecordAppUsageUseCase
import com.joyal.swyplauncher.ui.state.LauncherUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val launchAppUseCase: LaunchAppUseCase,
    private val observeAppChangesUseCase: ObserveAppChangesUseCase,
    private val getSmartAppListUseCase: GetSmartAppListUseCase,
    private val recordAppUsageUseCase: RecordAppUsageUseCase,
    private val preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _gridSize = MutableStateFlow(preferencesRepository.getGridSize())
    val gridSize: StateFlow<Int> = _gridSize.asStateFlow()

    private val _cornerRadius = MutableStateFlow(preferencesRepository.getCornerRadius())
    val cornerRadius: StateFlow<Float> = _cornerRadius.asStateFlow()

    private val _autoOpenSingleResult = MutableStateFlow(preferencesRepository.isAutoOpenSingleResultEnabled())
    val autoOpenSingleResult: StateFlow<Boolean> = _autoOpenSingleResult.asStateFlow()

    private val _appSortOrder = MutableStateFlow(preferencesRepository.getAppSortOrder())
    val appSortOrder: StateFlow<com.joyal.swyplauncher.domain.repository.AppSortOrder> = _appSortOrder.asStateFlow()

    init {
        loadApps()
        observeAppChanges()
    }
    
    fun setAppSortOrder(order: com.joyal.swyplauncher.domain.repository.AppSortOrder) {
        preferencesRepository.setAppSortOrder(order)
        _appSortOrder.value = order
        loadApps() // Reload to apply new sort order
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (apps, smartApps, newlyInstalledPackage) = withContext(Dispatchers.IO) {
                    val allApps = getInstalledAppsUseCase()
                    val hiddenApps = preferencesRepository.getHiddenApps()
                    val filteredApps = allApps.filter { it.getIdentifier() !in hiddenApps }
                    
                    // Apply sort order
                    val sortOrder = preferencesRepository.getAppSortOrder()
                    val usageMap = if (sortOrder == com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE) {
                        recordAppUsageUseCase.getUsageMap()
                    } else emptyMap()
                    val apps = com.joyal.swyplauncher.ui.util.sortApps(filteredApps, sortOrder, usageMap)
                    
                    val gridSize = preferencesRepository.getGridSize()
                    val smart = getSmartAppListUseCase(apps, gridSize)
                    
                    // Check if first smart app is newly installed (within 24 hours and not opened)
                    val now = System.currentTimeMillis()
                    val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000)
                    val newlyInstalled = smart.firstOrNull()?.let { firstApp ->
                        if (firstApp.installTime > twentyFourHoursAgo && 
                            !recordAppUsageUseCase.hasBeenOpened(firstApp.packageName, firstApp.activityName)) {
                            firstApp.getIdentifier()
                        } else null
                    }
                    
                    Triple(apps, smart, newlyInstalled)
                }
                _uiState.update {
                    it.copy(
                        apps = apps,
                        handwritingFilteredApps = apps,
                        handwritingSmartApps = smartApps,
                        indexFilteredApps = apps,
                        indexSmartApps = smartApps,
                        keyboardFilteredApps = apps,
                        keyboardSmartApps = smartApps,
                        voiceFilteredApps = apps,
                        voiceSmartApps = smartApps,
                        newlyInstalledAppPackage = newlyInstalledPackage,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load apps"
                    )
                }
            }
        }
    }

    private fun observeAppChanges() {
        viewModelScope.launch {
            observeAppChangesUseCase().collect {
                // Reload apps when changes detected
                loadApps()
            }
        }
    }

    // Keyboard mode filter
    fun filterAppsKeyboard(query: String) {
        val calculatorResult = com.joyal.swyplauncher.util.CalculatorUtil.evaluate(query)
        filterApps(query, { app, q -> matchesQueryOrShortcut(app, q) }) { filtered, smart, newlyInstalled ->
            copy(
                keyboardFilteredApps = filtered, 
                keyboardSmartApps = smart, 
                newlyInstalledAppPackage = newlyInstalled,
                keyboardCalculatorResult = calculatorResult
            )
        }
    }

    // Voice mode filter
    fun filterAppsVoice(query: String) {
        val calculatorResult = com.joyal.swyplauncher.util.CalculatorUtil.evaluate(query)
        filterApps(query, { app, q -> matchesQueryOrShortcut(app, q) }) { filtered, smart, newlyInstalled ->
            copy(
                voiceFilteredApps = filtered, 
                voiceSmartApps = smart, 
                newlyInstalledAppPackage = newlyInstalled,
                voiceCalculatorResult = calculatorResult
            )
        }
    }

    // Handwriting mode filter by prefix
    fun filterAppsByPrefixHandwriting(prefix: String) {
        val calculatorResult = com.joyal.swyplauncher.util.CalculatorUtil.evaluate(prefix)
        filterApps(prefix, { app, p -> matchesQueryOrShortcut(app, p, prefixMatch = true) }) { filtered, smart, newlyInstalled ->
            copy(
                handwritingFilteredApps = filtered, 
                handwritingSmartApps = smart, 
                newlyInstalledAppPackage = newlyInstalled,
                handwritingCalculatorResult = calculatorResult
            )
        }
    }
    
    private fun matchesQueryOrShortcut(app: AppInfo, query: String, prefixMatch: Boolean = false): Boolean {
        val shortcuts = preferencesRepository.getAppShortcuts()
        val matchesShortcut = shortcuts.any { (shortcut, appIds) ->
            appIds.contains(app.getIdentifier()) && shortcut.equals(query, ignoreCase = true)
        }
        return matchesShortcut || if (prefixMatch) {
            app.label.startsWith(query, ignoreCase = true)
        } else {
            app.label.contains(query, ignoreCase = true)
        }
    }

    // Index mode filter by first letter
    fun filterAppsByFirstLetterIndex(letter: Char) {
        viewModelScope.launch {
            val filtered = _uiState.value.apps.filter { it.firstLetter == letter.uppercaseChar() }
            val (smart, newlyInstalled) = computeSmartApps(filtered)
            _uiState.update { it.copy(indexFilteredApps = filtered, indexSmartApps = smart, newlyInstalledAppPackage = newlyInstalled) }
        }
    }

    // Reset filters for a specific mode
    fun resetFilterHandwriting() = resetFilter { copy(handwritingFilteredApps = apps, handwritingSmartApps = it.first, newlyInstalledAppPackage = it.second, handwritingCalculatorResult = null) }
    fun resetFilterIndex() = resetFilter { copy(indexFilteredApps = apps, indexSmartApps = it.first, newlyInstalledAppPackage = it.second) }
    fun resetFilterKeyboard() = resetFilter { copy(keyboardFilteredApps = apps, keyboardSmartApps = it.first, newlyInstalledAppPackage = it.second, keyboardCalculatorResult = null) }
    fun resetFilterVoice() = resetFilter { copy(voiceFilteredApps = apps, voiceSmartApps = it.first, newlyInstalledAppPackage = it.second, voiceCalculatorResult = null) }
    
    // Helper: Generic filter function
    private fun filterApps(
        query: String,
        predicate: (AppInfo, String) -> Boolean,
        updateState: LauncherUiState.(List<AppInfo>, List<AppInfo>, String?) -> LauncherUiState
    ) {
        viewModelScope.launch {
            val currentApps = _uiState.value.apps
            val filtered = if (query.isBlank()) currentApps else currentApps.filter { predicate(it, query) }
            val (smart, newlyInstalled) = computeSmartApps(filtered)
            _uiState.update { it.updateState(filtered, smart, newlyInstalled) }
        }
    }
    
    // Helper: Reset filter for a mode
    private fun resetFilter(updateState: LauncherUiState.(Pair<List<AppInfo>, String?>) -> LauncherUiState) {
        viewModelScope.launch {
            val smartData = computeSmartApps(_uiState.value.apps)
            _uiState.update { it.updateState(smartData) }
        }
    }
    
    // Helper: Compute smart apps and newly installed package
    private suspend fun computeSmartApps(apps: List<AppInfo>): Pair<List<AppInfo>, String?> {
        return if (apps.isEmpty()) {
            emptyList<AppInfo>() to null
        } else {
            withContext(Dispatchers.Default) {
                val gridSize = preferencesRepository.getGridSize()
                val smart = getSmartAppListUseCase(apps, gridSize)
                val newlyInstalled = getNewlyInstalledPackage(smart)
                smart to newlyInstalled
            }
        }
    }
    
    private suspend fun getNewlyInstalledPackage(smartApps: List<AppInfo>): String? {
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000)
        return smartApps.firstOrNull()?.let { firstApp ->
            if (firstApp.installTime > twentyFourHoursAgo && 
                !recordAppUsageUseCase.hasBeenOpened(firstApp.packageName, firstApp.activityName)) {
                firstApp.getIdentifier()
            } else null
        }
    }

    fun launchApp(packageName: String, activityName: String? = null) {
        viewModelScope.launch {
            // Record usage before launching (with activity name for proper tracking)
            recordAppUsageUseCase(packageName, activityName)
            
            val result = launchAppUseCase(packageName, activityName)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(error = result.exceptionOrNull()?.message ?: "Failed to launch app")
                }
            } else {
                // Refresh smart apps after successful launch for all modes
                val gridSize = preferencesRepository.getGridSize()
                val smartApps = getSmartAppListUseCase(_uiState.value.apps, gridSize)
                val newlyInstalledPackage = getNewlyInstalledPackage(smartApps)
                _uiState.update { 
                    it.copy(
                        handwritingSmartApps = smartApps,
                        indexSmartApps = smartApps,
                        keyboardSmartApps = smartApps,
                        voiceSmartApps = smartApps,
                        newlyInstalledAppPackage = newlyInstalledPackage
                    ) 
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getAvailableLetters(): List<Char> {
        return _uiState.value.apps
            .map { it.firstLetter }
            .distinct()
            .sorted()
    }

    enum class LauncherMode {
        KEYBOARD, VOICE, HANDWRITING, INDEX
    }

    fun hideApp(identifier: String, mode: LauncherMode, searchQuery: String = "") {
        preferencesRepository.addHiddenApp(identifier)
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (apps, smartApps, newlyInstalledPackage) = withContext(Dispatchers.IO) {
                    val allApps = getInstalledAppsUseCase()
                    val hiddenApps = preferencesRepository.getHiddenApps()
                    val apps = allApps.filter { it.getIdentifier() !in hiddenApps }
                    val gridSize = preferencesRepository.getGridSize()
                    val smart = getSmartAppListUseCase(apps, gridSize)
                    
                    // Check if first smart app is newly installed (within 24 hours and not opened)
                    val now = System.currentTimeMillis()
                    val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000)
                    val newlyInstalled = smart.firstOrNull()?.let { firstApp ->
                        if (firstApp.installTime > twentyFourHoursAgo && 
                            !recordAppUsageUseCase.hasBeenOpened(firstApp.packageName, firstApp.activityName)) {
                            firstApp.getIdentifier()
                        } else null
                    }
                    
                    Triple(apps, smart, newlyInstalled)
                }
                
                // Only update the active mode's filter, preserve others by removing the hidden app
                val (keyboardFiltered, keyboardSmart, keyboardNewlyInstalled) = when (mode) {
                    LauncherMode.KEYBOARD -> {
                        if (searchQuery.isNotBlank()) {
                            val filtered = apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
                            val (smart, newlyInstalled) = computeSmartApps(filtered)
                            Triple(filtered, smart, newlyInstalled)
                        } else Triple(apps, smartApps, newlyInstalledPackage)
                    }
                    else -> {
                        // Preserve existing filter, just remove the hidden app
                        val filtered = currentState.keyboardFilteredApps.filter { it.getIdentifier() != identifier }
                        val (smart, newlyInstalled) = if (filtered.isNotEmpty()) computeSmartApps(filtered) else (smartApps to newlyInstalledPackage)
                        Triple(filtered, smart, newlyInstalled)
                    }
                }
                
                val (voiceFiltered, voiceSmart, voiceNewlyInstalled) = when (mode) {
                    LauncherMode.VOICE -> {
                        if (searchQuery.isNotBlank()) {
                            val filtered = apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
                            val (smart, newlyInstalled) = computeSmartApps(filtered)
                            Triple(filtered, smart, newlyInstalled)
                        } else Triple(apps, smartApps, newlyInstalledPackage)
                    }
                    else -> {
                        // Preserve existing filter, just remove the hidden app
                        val filtered = currentState.voiceFilteredApps.filter { it.getIdentifier() != identifier }
                        val (smart, newlyInstalled) = if (filtered.isNotEmpty()) computeSmartApps(filtered) else (smartApps to newlyInstalledPackage)
                        Triple(filtered, smart, newlyInstalled)
                    }
                }
                
                val (handwritingFiltered, handwritingSmart, handwritingNewlyInstalled) = when (mode) {
                    LauncherMode.HANDWRITING -> {
                        if (searchQuery.isNotBlank()) {
                            val filtered = apps.filter { it.label.startsWith(searchQuery, ignoreCase = true) }
                            val (smart, newlyInstalled) = computeSmartApps(filtered)
                            Triple(filtered, smart, newlyInstalled)
                        } else Triple(apps, smartApps, newlyInstalledPackage)
                    }
                    else -> {
                        // Preserve existing filter, just remove the hidden app
                        val filtered = currentState.handwritingFilteredApps.filter { it.getIdentifier() != identifier }
                        val (smart, newlyInstalled) = if (filtered.isNotEmpty()) computeSmartApps(filtered) else (smartApps to newlyInstalledPackage)
                        Triple(filtered, smart, newlyInstalled)
                    }
                }
                
                val (indexFiltered, indexSmart, indexNewlyInstalled) = when (mode) {
                    LauncherMode.INDEX -> {
                        if (searchQuery.length == 1) {
                            val filtered = apps.filter { it.firstLetter == searchQuery[0].uppercaseChar() }
                            val (smart, newlyInstalled) = computeSmartApps(filtered)
                            Triple(filtered, smart, newlyInstalled)
                        } else Triple(apps, smartApps, newlyInstalledPackage)
                    }
                    else -> {
                        // Preserve existing filter, just remove the hidden app
                        val filtered = currentState.indexFilteredApps.filter { it.getIdentifier() != identifier }
                        val (smart, newlyInstalled) = if (filtered.isNotEmpty()) computeSmartApps(filtered) else (smartApps to newlyInstalledPackage)
                        Triple(filtered, smart, newlyInstalled)
                    }
                }
                
                _uiState.update {
                    it.copy(
                        apps = apps,
                        handwritingFilteredApps = handwritingFiltered,
                        handwritingSmartApps = handwritingSmart,
                        indexFilteredApps = indexFiltered,
                        indexSmartApps = indexSmart,
                        keyboardFilteredApps = keyboardFiltered,
                        keyboardSmartApps = keyboardSmart,
                        voiceFilteredApps = voiceFiltered,
                        voiceSmartApps = voiceSmart,
                        newlyInstalledAppPackage = when (mode) {
                            LauncherMode.KEYBOARD -> keyboardNewlyInstalled
                            LauncherMode.VOICE -> voiceNewlyInstalled
                            LauncherMode.HANDWRITING -> handwritingNewlyInstalled
                            LauncherMode.INDEX -> indexNewlyInstalled
                        },
                        isLoading = false,
                        showHideAppTooltip = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load apps"
                    )
                }
            }
        }
    }

    fun unhideApp(identifier: String) {
        preferencesRepository.removeHiddenApp(identifier)
        loadApps()
    }

    fun getHiddenApps(): List<AppInfo> {
        viewModelScope.launch {
            val allApps = getInstalledAppsUseCase()
            val hiddenIds = preferencesRepository.getHiddenApps()
            _uiState.update { it.copy(hiddenApps = allApps.filter { app -> app.getIdentifier() in hiddenIds }) }
        }
        return _uiState.value.hiddenApps
    }
    
    fun shouldPromptForUsageStatsPermission(): Boolean {
        return !preferencesRepository.hasPromptedForUsageStatsPermission() &&
               !recordAppUsageUseCase.hasUsageStatsPermission()
    }
    
    fun markUsageStatsPermissionPrompted() {
        preferencesRepository.setUsageStatsPermissionPrompted()
    }
    
    fun openUsageStatsSettings() {
        recordAppUsageUseCase.openUsageAccessSettings()
    }
    
    fun dismissHideAppTooltip() {
        _uiState.update { it.copy(showHideAppTooltip = false) }
    }
}
