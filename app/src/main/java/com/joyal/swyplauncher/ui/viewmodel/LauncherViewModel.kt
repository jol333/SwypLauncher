package com.joyal.swyplauncher.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.CurrencyRepository
import com.joyal.swyplauncher.domain.usecase.GetCachedInstalledAppsUseCase
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.domain.usecase.GetSmartAppListUseCase
import com.joyal.swyplauncher.domain.usecase.LaunchAppUseCase
import com.joyal.swyplauncher.domain.usecase.ObserveAppChangesUseCase
import com.joyal.swyplauncher.domain.usecase.RecordAppUsageUseCase
import com.joyal.swyplauncher.ui.state.CurrencyResultState
import com.joyal.swyplauncher.ui.state.LauncherUiState
import com.joyal.swyplauncher.ui.state.UnitResultState
import com.joyal.swyplauncher.util.CurrencyUtil
import com.joyal.swyplauncher.util.UnitData
import com.joyal.swyplauncher.util.UnitUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val getCachedInstalledAppsUseCase: GetCachedInstalledAppsUseCase,
    private val launchAppUseCase: LaunchAppUseCase,
    private val observeAppChangesUseCase: ObserveAppChangesUseCase,
    private val getSmartAppListUseCase: GetSmartAppListUseCase,
    private val recordAppUsageUseCase: RecordAppUsageUseCase,
    private val preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository,
    private val currencyRepository: CurrencyRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    enum class CurrencyMode { KEYBOARD, VOICE, HANDWRITING }

    // Region for unit-conversion preferences (imperial vs metric, Indian numbering)
    private val unitRegion: UnitUtil.Region by lazy { UnitUtil.detectRegion(appContext) }

    private var keyboardCurrencyJob: Job? = null
    private var voiceCurrencyJob: Job? = null
    private var handwritingCurrencyJob: Job? = null

    private var keyboardFilterJob: Job? = null
    private var voiceFilterJob: Job? = null
    private var handwritingFilterJob: Job? = null

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

    private var loadJob: Job? = null
    private var cachedUsageMap: Map<String, Int> = emptyMap()

    private data class AppLoadResult(
        val apps: List<AppInfo>,
        val smartApps: List<AppInfo>,
        val newlyInstalledPackage: String?,
        val usageMap: Map<String, Int>
    )

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
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cachedApps = getCachedInstalledAppsUseCase()
            val hasCached = cachedApps.isNotEmpty()
            val cachedUsage = cachedUsageMap.takeIf { it.isNotEmpty() }

            if (hasCached) {
                val cachedDispatcher = if (cachedUsage != null) Dispatchers.Default else Dispatchers.IO
                val cachedResult = withContext(cachedDispatcher) {
                    buildAppLoadResult(cachedApps, cachedUsage)
                }
                cachedUsageMap = cachedResult.usageMap
                applyAppLoadResult(cachedResult, isLoading = false)
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                val freshResult = withContext(Dispatchers.IO) {
                    val allApps = getInstalledAppsUseCase()
                    buildAppLoadResult(allApps, usageMapOverride = null)
                }
                cachedUsageMap = freshResult.usageMap
                applyAppLoadResult(freshResult, isLoading = false)
            } catch (e: CancellationException) {
                // Ignore cancellation when a newer load supersedes this one
                return@launch
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

    private suspend fun buildAppLoadResult(
        allApps: List<AppInfo>,
        usageMapOverride: Map<String, Int>? = null
    ): AppLoadResult {
        val hiddenApps = preferencesRepository.getHiddenApps()
        val filteredApps = allApps.filter { it.getIdentifier() !in hiddenApps }

        val usageMap = usageMapOverride ?: recordAppUsageUseCase.getUsageMap()
        val sortOrder = preferencesRepository.getAppSortOrder()
        val apps = com.joyal.swyplauncher.ui.util.sortApps(filteredApps, sortOrder, usageMap)

        val gridSize = preferencesRepository.getGridSize()
        val smart = getSmartAppListUseCase(apps, gridSize, usageMap)
        val newlyInstalled = getNewlyInstalledPackage(smart)

        return AppLoadResult(
            apps = apps,
            smartApps = smart,
            newlyInstalledPackage = newlyInstalled,
            usageMap = usageMap
        )
    }

    private fun applyAppLoadResult(result: AppLoadResult, isLoading: Boolean) {
        // Pre-compute available letters for Index mode performance
        val letters = result.apps.map { it.firstLetter }.distinct().sorted()
        _uiState.update {
            it.copy(
                apps = result.apps,
                availableLetters = letters,
                handwritingFilteredApps = result.apps,
                handwritingSmartApps = result.smartApps,
                indexFilteredApps = result.apps,
                indexSmartApps = result.smartApps,
                keyboardFilteredApps = result.apps,
                keyboardSmartApps = result.smartApps,
                voiceFilteredApps = result.apps,
                voiceSmartApps = result.smartApps,
                newlyInstalledAppPackage = result.newlyInstalledPackage,
                isLoading = isLoading,
                error = null
            )
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
        keyboardFilterJob?.cancel()
        keyboardCurrencyJob?.cancel()
        keyboardFilterJob = launchModeFilter(query, CurrencyMode.KEYBOARD, prefixMatch = false)
    }

    // Voice mode filter
    fun filterAppsVoice(query: String) {
        voiceFilterJob?.cancel()
        voiceCurrencyJob?.cancel()
        voiceFilterJob = launchModeFilter(query, CurrencyMode.VOICE, prefixMatch = false)
    }

    // Handwriting mode filter by prefix
    fun filterAppsByPrefixHandwriting(prefix: String) {
        handwritingFilterJob?.cancel()
        handwritingCurrencyJob?.cancel()
        handwritingFilterJob = launchModeFilter(prefix, CurrencyMode.HANDWRITING, prefixMatch = true)
    }

    // Runs parse + filter + smart-app compute on Default so keystrokes aren't blocked.
    private fun launchModeFilter(query: String, mode: CurrencyMode, prefixMatch: Boolean): Job {
        return viewModelScope.launch(Dispatchers.Default) {
            val parseResult = computeCalcOrCurrency(query)
            ensureActive()

            val currentApps = _uiState.value.apps
            val filtered = if (query.isBlank()) {
                currentApps
            } else {
                val shortcuts = preferencesRepository.getAppShortcuts()
                currentApps.filter { matchesQueryOrShortcut(it, query, shortcuts, prefixMatch) }
            }

            val (smart, newlyInstalled) = if (filtered.isEmpty()) {
                emptyList<AppInfo>() to null
            } else {
                val gridSize = preferencesRepository.getGridSize()
                val smartList = getSmartAppListUseCase(filtered, gridSize)
                smartList to getNewlyInstalledPackage(smartList)
            }

            ensureActive()

            _uiState.update { s ->
                when (mode) {
                    CurrencyMode.KEYBOARD -> s.copy(
                        keyboardFilteredApps = filtered,
                        keyboardSmartApps = smart,
                        newlyInstalledAppPackage = newlyInstalled,
                        keyboardCalculatorResult = parseResult.calc,
                        keyboardCurrencyResult = parseResult.currency,
                        keyboardUnitResult = parseResult.unit
                    )
                    CurrencyMode.VOICE -> s.copy(
                        voiceFilteredApps = filtered,
                        voiceSmartApps = smart,
                        newlyInstalledAppPackage = newlyInstalled,
                        voiceCalculatorResult = parseResult.calc,
                        voiceCurrencyResult = parseResult.currency,
                        voiceUnitResult = parseResult.unit
                    )
                    CurrencyMode.HANDWRITING -> s.copy(
                        handwritingFilteredApps = filtered,
                        handwritingSmartApps = smart,
                        newlyInstalledAppPackage = newlyInstalled,
                        handwritingCalculatorResult = parseResult.calc,
                        handwritingCurrencyResult = parseResult.currency,
                        handwritingUnitResult = parseResult.unit
                    )
                }
            }

            // Job-var access stays on Main to match other cancel sites
            parseResult.parsed?.let {
                withContext(Dispatchers.Main.immediate) {
                    launchCurrencyConversion(it, mode)
                }
            }
        }
    }

    private data class ParseResult(
        val calc: String?,
        val currency: CurrencyResultState?,
        val parsed: CurrencyUtil.Parsed?,
        val unit: UnitResultState? = null
    )

    // Detect calc vs currency vs unit. Currency first ("$5+5"), then unit ("5 km"), then calc.
    private fun computeCalcOrCurrency(input: String): ParseResult {
        val parsed = CurrencyUtil.tryParse(input, currencyRepository.getNativeCurrencyCode())
        if (parsed != null) {
            val needsLoad = !currencyRepository.hasRatesInMemory()
            return ParseResult(
                calc = null,
                currency = CurrencyResultState(
                    sourceAmount = parsed.amount,
                    targetAmount = null,
                    isLoading = needsLoad,
                    fromCode = parsed.from,
                    toCode = parsed.to
                ),
                parsed = parsed
            )
        }
        UnitUtil.tryParse(input, unitRegion)?.let { return ParseResult(null, null, null, buildUnitState(it)) }
        val calc = com.joyal.swyplauncher.util.CalculatorUtil.evaluate(input)
        return ParseResult(calc, null, null)
    }

    // Build a complete unit-conversion state (synchronous — no network needed).
    private fun buildUnitState(p: UnitUtil.Parsed): UnitResultState {
        val target = UnitUtil.convert(p.amount, p.from, p.to)
        return UnitResultState(
            category = p.category,
            sourceAmount = p.amount,
            targetAmount = target,
            fromId = p.from,
            toId = p.to,
            sourceApprox = approxFor(p.amount, p.from),
            targetApprox = target?.let { approxFor(it, p.to) },
            error = if (target == null) convError(p.category) else null
        )
    }

    // "~9.46 trillion kilometers" style subtext, null when value is in a normal range.
    private fun approxFor(value: Double, unitId: String): String? {
        val spec = UnitData.byId(unitId)
        if (spec?.category == UnitData.Category.NUMERAL || spec?.category == UnitData.Category.SHOE_SIZE || spec?.category == UnitData.Category.DATA) {
            return null
        }
        val human = UnitUtil.humanReadable(value, unitRegion.indian) ?: return null
        val unitName = spec?.aliases?.getOrNull(1) ?: spec?.aliases?.firstOrNull() ?: spec?.symbol ?: return human
        return "$human $unitName"
    }

    // Failed-conversion message, tailored for shoe sizes (out-of-range / negative input)
    private fun convError(category: UnitData.Category): String =
        if (category == UnitData.Category.SHOE_SIZE) "Enter a valid shoe size" else "Cannot convert"

    // Interactive unit edits (typing a value or changing a unit). Synchronous recompute.
    fun updateUnitConversion(amount: Double, fromId: String, toId: String, isSourceChanged: Boolean, mode: CurrencyMode) {
        val active = when (mode) {
            CurrencyMode.KEYBOARD -> _uiState.value.keyboardUnitResult
            CurrencyMode.VOICE -> _uiState.value.voiceUnitResult
            CurrencyMode.HANDWRITING -> _uiState.value.handwritingUnitResult
        } ?: return

        val newState = if (isSourceChanged) {
            val converted = UnitUtil.convert(amount, fromId, toId)
            active.copy(
                sourceAmount = amount, targetAmount = converted, fromId = fromId, toId = toId,
                sourceApprox = approxFor(amount, fromId),
                targetApprox = converted?.let { approxFor(it, toId) },
                error = if (converted == null) convError(active.category) else null
            )
        } else {
            val converted = UnitUtil.convert(amount, toId, fromId)
            active.copy(
                sourceAmount = converted ?: active.sourceAmount, targetAmount = amount, fromId = fromId, toId = toId,
                sourceApprox = converted?.let { approxFor(it, fromId) },
                targetApprox = approxFor(amount, toId),
                error = if (converted == null) convError(active.category) else null
            )
        }
        _uiState.update { s ->
            when (mode) {
                CurrencyMode.KEYBOARD -> s.copy(keyboardUnitResult = newState)
                CurrencyMode.VOICE -> s.copy(voiceUnitResult = newState)
                CurrencyMode.HANDWRITING -> s.copy(handwritingUnitResult = newState)
            }
        }
    }

    private fun launchCurrencyConversion(parsed: CurrencyUtil.Parsed, mode: CurrencyMode) {
        when (mode) {
            CurrencyMode.KEYBOARD -> keyboardCurrencyJob?.cancel()
            CurrencyMode.VOICE -> voiceCurrencyJob?.cancel()
            CurrencyMode.HANDWRITING -> handwritingCurrencyJob?.cancel()
        }
        val targetSourceAmount = parsed.amount
        val targetSourceCode = parsed.from
        val job = viewModelScope.launch(Dispatchers.Default) {
            val result = currencyRepository.getRates()
            val newState = buildCurrencyState(parsed, result)
            // Only commit if the user's current input still matches what we just converted
            _uiState.update { s ->
                val active = when (mode) {
                    CurrencyMode.KEYBOARD -> s.keyboardCurrencyResult
                    CurrencyMode.VOICE -> s.voiceCurrencyResult
                    CurrencyMode.HANDWRITING -> s.handwritingCurrencyResult
                }
                if (active?.sourceAmount != targetSourceAmount || active.fromCode != targetSourceCode) return@update s
                when (mode) {
                    CurrencyMode.KEYBOARD -> s.copy(keyboardCurrencyResult = newState)
                    CurrencyMode.VOICE -> s.copy(voiceCurrencyResult = newState)
                    CurrencyMode.HANDWRITING -> s.copy(handwritingCurrencyResult = newState)
                }
            }
        }
        when (mode) {
            CurrencyMode.KEYBOARD -> keyboardCurrencyJob = job
            CurrencyMode.VOICE -> voiceCurrencyJob = job
            CurrencyMode.HANDWRITING -> handwritingCurrencyJob = job
        }
    }

    private fun buildCurrencyState(parsed: CurrencyUtil.Parsed, result: CurrencyRepository.RatesResult): CurrencyResultState {
        return when (result) {
            is CurrencyRepository.RatesResult.Success -> {
                val converted = CurrencyUtil.convert(parsed.amount, parsed.from, parsed.to, result.base, result.rates)
                if (converted == null) {
                    CurrencyResultState(
                        sourceAmount = parsed.amount,
                        error = "Unsupported currency",
                        fromCode = parsed.from,
                        toCode = parsed.to
                    )
                } else {
                    CurrencyResultState(
                        sourceAmount = parsed.amount,
                        targetAmount = converted,
                        ratesTimestamp = if (result.fromCache) result.timestamp else null,
                        fromCode = parsed.from,
                        toCode = parsed.to
                    )
                }
            }
            is CurrencyRepository.RatesResult.Error -> CurrencyResultState(
                sourceAmount = parsed.amount,
                error = result.message,
                fromCode = parsed.from,
                toCode = parsed.to
            )
        }
    }
    
    fun updateCurrencyConversion(amount: Double, fromCode: String, toCode: String, isSourceChanged: Boolean, mode: CurrencyMode) {
        val active = when (mode) {
            CurrencyMode.KEYBOARD -> _uiState.value.keyboardCurrencyResult
            CurrencyMode.VOICE -> _uiState.value.voiceCurrencyResult
            CurrencyMode.HANDWRITING -> _uiState.value.handwritingCurrencyResult
        } ?: return

        // Set immediate loading state if rates are not in memory, else we can compute immediately
        val needsLoad = !currencyRepository.hasRatesInMemory()
        
        val parsed = if (isSourceChanged) {
            CurrencyUtil.Parsed(amount, fromCode, toCode)
        } else {
            // Amount is the new target amount, so we treat it as the source for inverse conversion
            CurrencyUtil.Parsed(amount, toCode, fromCode)
        }
        
        val pendingState = CurrencyResultState(
            sourceAmount = if (isSourceChanged) amount else active.sourceAmount,
            targetAmount = if (!isSourceChanged) amount else active.targetAmount,
            isLoading = needsLoad,
            fromCode = fromCode,
            toCode = toCode
        )
        
        _uiState.update { s ->
            when (mode) {
                CurrencyMode.KEYBOARD -> s.copy(keyboardCurrencyResult = pendingState)
                CurrencyMode.VOICE -> s.copy(voiceCurrencyResult = pendingState)
                CurrencyMode.HANDWRITING -> s.copy(handwritingCurrencyResult = pendingState)
            }
        }
        
        launchCurrencyConversion(parsed, mode, isSourceChanged)
    }

    private fun launchCurrencyConversion(parsed: CurrencyUtil.Parsed, mode: CurrencyMode, isSourceChanged: Boolean) {
        when (mode) {
            CurrencyMode.KEYBOARD -> keyboardCurrencyJob?.cancel()
            CurrencyMode.VOICE -> voiceCurrencyJob?.cancel()
            CurrencyMode.HANDWRITING -> handwritingCurrencyJob?.cancel()
        }
        val targetSourceAmount = parsed.amount
        val targetSourceCode = parsed.from
        val job = viewModelScope.launch(Dispatchers.Default) {
            val result = currencyRepository.getRates()
            
            // Build state, but carefully map it back since we might be doing inverse conversion
            val newState = when (result) {
                is CurrencyRepository.RatesResult.Success -> {
                    val converted = CurrencyUtil.convert(parsed.amount, parsed.from, parsed.to, result.base, result.rates)
                    if (converted == null) {
                        CurrencyResultState(
                            sourceAmount = if (isSourceChanged) parsed.amount else 0.0, // Error state
                            targetAmount = if (!isSourceChanged) parsed.amount else null,
                            error = "Unsupported currency",
                            fromCode = if (isSourceChanged) parsed.from else parsed.to,
                            toCode = if (isSourceChanged) parsed.to else parsed.from
                        )
                    } else {
                        CurrencyResultState(
                            sourceAmount = if (isSourceChanged) parsed.amount else converted,
                            targetAmount = if (!isSourceChanged) parsed.amount else converted,
                            ratesTimestamp = if (result.fromCache) result.timestamp else null,
                            fromCode = if (isSourceChanged) parsed.from else parsed.to,
                            toCode = if (isSourceChanged) parsed.to else parsed.from
                        )
                    }
                }
                is CurrencyRepository.RatesResult.Error -> CurrencyResultState(
                    sourceAmount = if (isSourceChanged) parsed.amount else 0.0,
                    targetAmount = if (!isSourceChanged) parsed.amount else null,
                    error = result.message,
                    fromCode = if (isSourceChanged) parsed.from else parsed.to,
                    toCode = if (isSourceChanged) parsed.to else parsed.from
                )
            }
            
            _uiState.update { s ->
                val active = when (mode) {
                    CurrencyMode.KEYBOARD -> s.keyboardCurrencyResult
                    CurrencyMode.VOICE -> s.voiceCurrencyResult
                    CurrencyMode.HANDWRITING -> s.handwritingCurrencyResult
                }
                
                // We check against the active state's current values which should have been set by updateCurrencyConversion
                val currentCheckingAmount = if (isSourceChanged) active?.sourceAmount else active?.targetAmount
                val currentCheckingCode = if (isSourceChanged) active?.fromCode else active?.toCode
                
                if (currentCheckingAmount != targetSourceAmount || currentCheckingCode != targetSourceCode) return@update s
                
                when (mode) {
                    CurrencyMode.KEYBOARD -> s.copy(keyboardCurrencyResult = newState)
                    CurrencyMode.VOICE -> s.copy(voiceCurrencyResult = newState)
                    CurrencyMode.HANDWRITING -> s.copy(handwritingCurrencyResult = newState)
                }
            }
        }
        when (mode) {
            CurrencyMode.KEYBOARD -> keyboardCurrencyJob = job
            CurrencyMode.VOICE -> voiceCurrencyJob = job
            CurrencyMode.HANDWRITING -> handwritingCurrencyJob = job
        }
    }

    // Handwriting mode filter by matched custom gesture's assigned apps
    fun filterAppsByCustomGestureHandwriting(appIds: Set<String>) {
        handwritingFilterJob?.cancel()
        handwritingCurrencyJob?.cancel()
        handwritingFilterJob = viewModelScope.launch(Dispatchers.Default) {
            val currentApps = _uiState.value.apps
            val filtered = currentApps.filter { it.getIdentifier() in appIds }
            val (smart, newlyInstalled) = computeSmartApps(filtered)
            _uiState.update {
                it.copy(
                    handwritingFilteredApps = filtered,
                    handwritingSmartApps = smart,
                    newlyInstalledAppPackage = newlyInstalled,
                    handwritingCalculatorResult = null,
                    handwritingCurrencyResult = null,
                    handwritingUnitResult = null
                )
            }
        }
    }

    private fun matchesQueryOrShortcut(
        app: AppInfo,
        query: String,
        shortcuts: Map<String, Set<String>>,
        prefixMatch: Boolean = false
    ): Boolean {
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
        viewModelScope.launch(Dispatchers.Default) {
            val filtered = _uiState.value.apps.filter { it.firstLetter == letter.uppercaseChar() }
            val (smart, newlyInstalled) = computeSmartApps(filtered)
            _uiState.update { it.copy(indexFilteredApps = filtered, indexSmartApps = smart, newlyInstalledAppPackage = newlyInstalled) }
        }
    }

    // Reset filters for a specific mode
    fun resetFilterHandwriting() {
        handwritingFilterJob?.cancel()
        handwritingCurrencyJob?.cancel()
        resetFilter { copy(handwritingFilteredApps = apps, handwritingSmartApps = it.first, newlyInstalledAppPackage = it.second, handwritingCalculatorResult = null, handwritingCurrencyResult = null, handwritingUnitResult = null) }
    }
    fun resetFilterIndex() = resetFilter { copy(indexFilteredApps = apps, indexSmartApps = it.first, newlyInstalledAppPackage = it.second) }
    fun resetFilterKeyboard() {
        keyboardFilterJob?.cancel()
        keyboardCurrencyJob?.cancel()
        resetFilter { copy(keyboardFilteredApps = apps, keyboardSmartApps = it.first, newlyInstalledAppPackage = it.second, keyboardCalculatorResult = null, keyboardCurrencyResult = null, keyboardUnitResult = null) }
    }
    fun resetFilterVoice() {
        voiceFilterJob?.cancel()
        voiceCurrencyJob?.cancel()
        resetFilter { copy(voiceFilteredApps = apps, voiceSmartApps = it.first, newlyInstalledAppPackage = it.second, voiceCalculatorResult = null, voiceCurrencyResult = null, voiceUnitResult = null) }
    }
    
    // Helper: Reset filter for a mode
    private fun resetFilter(updateState: LauncherUiState.(Pair<List<AppInfo>, String?>) -> LauncherUiState) {
        viewModelScope.launch(Dispatchers.Default) {
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
        return _uiState.value.availableLetters
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