package com.joyal.swyplauncher.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.domain.model.ShortcutIcon
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem
import com.joyal.swyplauncher.domain.repository.CurrencyRepository
import com.joyal.swyplauncher.domain.repository.ShortcutSearchRepository
import com.joyal.swyplauncher.domain.usecase.GetCachedInstalledAppsUseCase
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.domain.usecase.GetSmartAppListUseCase
import com.joyal.swyplauncher.domain.usecase.LaunchAppUseCase
import com.joyal.swyplauncher.domain.usecase.ObserveAppChangesUseCase
import com.joyal.swyplauncher.domain.usecase.RecordAppUsageUseCase
import com.joyal.swyplauncher.ui.state.CurrencyResultState
import com.joyal.swyplauncher.ui.state.LauncherUiState
import com.joyal.swyplauncher.ui.state.TimeZoneResultState
import com.joyal.swyplauncher.ui.state.UnitResultState
import com.joyal.swyplauncher.util.CurrencyUtil
import com.joyal.swyplauncher.util.TimeZoneUtil
import com.joyal.swyplauncher.util.UnitData
import com.joyal.swyplauncher.util.UnitUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
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
    private val shortcutSearchRepository: ShortcutSearchRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    enum class CurrencyMode { KEYBOARD, VOICE, HANDWRITING }

    // Region for unit-conversion preferences (imperial vs metric, Indian numbering)
    private val unitRegion: UnitUtil.Region by lazy { UnitUtil.detectRegion(appContext) }

    // Auto-detected home time zone (no GPS) — the default side for time-zone conversion.
    private val homeTimeZone: TimeZoneUtil.Ref.ZoneRef by lazy { TimeZoneUtil.detectHome(appContext) }

    private var keyboardCurrencyJob: Job? = null
    private var voiceCurrencyJob: Job? = null
    private var handwritingCurrencyJob: Job? = null

    private var keyboardFilterJob: Job? = null
    private var voiceFilterJob: Job? = null
    private var handwritingFilterJob: Job? = null

    /** Cancels [mode]'s running currency conversion, then tracks the one [start] launches. Main thread. */
    private inline fun replaceCurrencyJob(mode: CurrencyMode, start: () -> Job) {
        when (mode) {
            CurrencyMode.KEYBOARD -> { keyboardCurrencyJob?.cancel(); keyboardCurrencyJob = start() }
            CurrencyMode.VOICE -> { voiceCurrencyJob?.cancel(); voiceCurrencyJob = start() }
            CurrencyMode.HANDWRITING -> { handwritingCurrencyJob?.cancel(); handwritingCurrencyJob = start() }
        }
    }

    // Each search mode keeps its own converter results in LauncherUiState; these pick the
    // fields that belong to a mode.
    private fun LauncherUiState.currencyResult(mode: CurrencyMode) = when (mode) {
        CurrencyMode.KEYBOARD -> keyboardCurrencyResult
        CurrencyMode.VOICE -> voiceCurrencyResult
        CurrencyMode.HANDWRITING -> handwritingCurrencyResult
    }

    private fun LauncherUiState.withCurrencyResult(mode: CurrencyMode, result: CurrencyResultState?) = when (mode) {
        CurrencyMode.KEYBOARD -> copy(keyboardCurrencyResult = result)
        CurrencyMode.VOICE -> copy(voiceCurrencyResult = result)
        CurrencyMode.HANDWRITING -> copy(handwritingCurrencyResult = result)
    }

    private fun LauncherUiState.unitResult(mode: CurrencyMode) = when (mode) {
        CurrencyMode.KEYBOARD -> keyboardUnitResult
        CurrencyMode.VOICE -> voiceUnitResult
        CurrencyMode.HANDWRITING -> handwritingUnitResult
    }

    private fun LauncherUiState.withUnitResult(mode: CurrencyMode, result: UnitResultState?) = when (mode) {
        CurrencyMode.KEYBOARD -> copy(keyboardUnitResult = result)
        CurrencyMode.VOICE -> copy(voiceUnitResult = result)
        CurrencyMode.HANDWRITING -> copy(handwritingUnitResult = result)
    }

    private fun LauncherUiState.timeZoneResult(mode: CurrencyMode) = when (mode) {
        CurrencyMode.KEYBOARD -> keyboardTimeZoneResult
        CurrencyMode.VOICE -> voiceTimeZoneResult
        CurrencyMode.HANDWRITING -> handwritingTimeZoneResult
    }

    private fun LauncherUiState.withTimeZoneResult(mode: CurrencyMode, result: TimeZoneResultState?) = when (mode) {
        CurrencyMode.KEYBOARD -> copy(keyboardTimeZoneResult = result)
        CurrencyMode.VOICE -> copy(voiceTimeZoneResult = result)
        CurrencyMode.HANDWRITING -> copy(handwritingTimeZoneResult = result)
    }

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _gridSize = MutableStateFlow(preferencesRepository.getGridSize())
    val gridSize: StateFlow<Int> = _gridSize.asStateFlow()

    private val _cornerRadius = MutableStateFlow(preferencesRepository.getCornerRadius())
    val cornerRadius: StateFlow<Float> = _cornerRadius.asStateFlow()

    private val _autoOpenSingleResult = MutableStateFlow(preferencesRepository.isAutoOpenSingleResultEnabled())
    val autoOpenSingleResult: StateFlow<Boolean> = _autoOpenSingleResult.asStateFlow()

    // User preference: load the entire app list as soon as the assistant opens (default true).
    private val _loadAllAppsOnOpen = MutableStateFlow(preferencesRepository.isLoadAllAppsOnOpenEnabled())
    val loadAllAppsOnOpen: StateFlow<Boolean> = _loadAllAppsOnOpen.asStateFlow()

    // Transient, per-assistant-session flag. Becomes true once the user reveals the full app
    // list (via the "Show all apps" button or by expanding the sheet). Only meaningful when
    // [loadAllAppsOnOpen] is false. Resets on each open since the activity/ViewModel is recreated.
    private val _allAppsRevealed = MutableStateFlow(false)
    val allAppsRevealed: StateFlow<Boolean> = _allAppsRevealed.asStateFlow()

    fun revealAllApps() {
        if (!_allAppsRevealed.value) _allAppsRevealed.value = true
    }

    private val _appSortOrder = MutableStateFlow(preferencesRepository.getAppSortOrder())
    val appSortOrder: StateFlow<com.joyal.swyplauncher.domain.repository.AppSortOrder> = _appSortOrder.asStateFlow()

    private var loadJob: Job? = null
    private var cachedUsageMap: Map<String, Int> = emptyMap()

    // Active per-mode filters, re-applied whenever a background app refresh lands so a refresh
    // that completes mid-search never replaces the user's results with the full app list.
    // Written on the main thread only (all filter entry points run there). Held as one immutable
    // value so a refresh can read a consistent snapshot from whichever thread it applies on.
    private data class ActiveFilters(
        val keyboardQuery: String = "",
        val voiceQuery: String = "",
        val handwritingPrefix: String = "",
        val handwritingGestureAppIds: Set<String>? = null,
        val handwritingGestureShortcutIds: Set<String>? = null,
        val indexLetter: Char? = null
    )

    @Volatile
    private var activeFilters = ActiveFilters()

    private data class AppLoadResult(
        val apps: List<AppInfo>,
        // Hidden apps that text searches also match; empty unless the preference is on
        val searchableHiddenApps: List<AppInfo>,
        val smartApps: List<AppInfo>,
        val newlyInstalledPackage: String?,
        val usageMap: Map<String, Int>
    )

    init {
        loadApps()
        observeAppChanges()
        prewarmShortcutSearch()
    }

    // Builds the shortcut index off the critical path so neither assistant opening nor the
    // first search keystroke pays for it. Skipped entirely while the preference is off.
    private fun prewarmShortcutSearch() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { shortcutSearchRepository.prewarm() }
        }
    }
    
    fun setAppSortOrder(order: com.joyal.swyplauncher.domain.repository.AppSortOrder) {
        preferencesRepository.setAppSortOrder(order)
        _appSortOrder.value = order
        loadApps() // Reload to apply new sort order
    }

    private fun loadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // In a fresh process the in-memory cache is empty; fall back to the list persisted
            // by the previous scan so apps show immediately while the real scan runs below.
            val cachedApps = getCachedInstalledAppsUseCase().ifEmpty {
                getCachedInstalledAppsUseCase.restore()
            }
            val hasCached = cachedApps.isNotEmpty()
            // Rank the cached list with the last known usage map (this ViewModel's, else the
            // repository's from an earlier session) instead of waiting for a fresh query.
            val cachedUsage = cachedUsageMap.takeIf { it.isNotEmpty() }
                ?: recordAppUsageUseCase.peekUsageMap()

            if (hasCached) {
                val cachedDispatcher = if (cachedUsage != null) Dispatchers.Default else Dispatchers.IO
                val cachedResult = withContext(cachedDispatcher) {
                    val result = buildAppLoadResult(cachedApps, cachedUsage)
                    // Published from this thread rather than after hopping back to main: when
                    // the assistant is opening, the main thread is busy until its first frame is
                    // drawn, and the apps should be in that frame instead of popping in after it.
                    applyAppLoadResult(result, isLoading = false)
                    result
                }
                cachedUsageMap = cachedResult.usageMap
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
                        error = e.message ?: appContext.getString(R.string.error_failed_to_load_apps)
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
        val (hiddenList, filteredApps) = allApps.partition { it.getIdentifier() in hiddenApps }

        val usageMap = usageMapOverride ?: recordAppUsageUseCase.getUsageMap()
        val sortOrder = preferencesRepository.getAppSortOrder()
        val apps = com.joyal.swyplauncher.ui.util.sortApps(filteredApps, sortOrder, usageMap)
        val searchableHiddenApps =
            if (hiddenList.isEmpty() || !preferencesRepository.isShowHiddenAppsInSearchEnabled()) emptyList()
            else com.joyal.swyplauncher.ui.util.sortApps(hiddenList, sortOrder, usageMap)

        val gridSize = preferencesRepository.getGridSize()
        val smart = getSmartAppListUseCase(apps, gridSize, usageMap)
        val newlyInstalled = getNewlyInstalledPackage(smart)

        return AppLoadResult(
            apps = apps,
            searchableHiddenApps = searchableHiddenApps,
            smartApps = smart,
            newlyInstalledPackage = newlyInstalled,
            usageMap = usageMap
        )
    }

    private suspend fun applyAppLoadResult(result: AppLoadResult, isLoading: Boolean) {
        // Pre-compute available letters for Index mode performance
        val letters = result.apps.map { it.firstLetter }.distinct().sorted()

        // Snapshot the active filters before recomputing on Default.
        val filters = activeFilters
        val keyboardQuery = filters.keyboardQuery
        val voiceQuery = filters.voiceQuery
        val handwritingPrefix = filters.handwritingPrefix
        val handwritingGestureIds = filters.handwritingGestureAppIds
        val handwritingGestureShortcutIds = filters.handwritingGestureShortcutIds
        val indexLetter = filters.indexLetter

        // Searches and gesture matches also include hidden apps when that preference is on
        val searchPool = searchPool(result.apps, result.searchableHiddenApps)
        // Null when no gesture is active, or the active one has nothing visible to show (then
        // the handwriting mode falls back to its recognized text, like a fresh gesture match)
        val gestureResults = handwritingGestureIds?.let { ids ->
            withContext(Dispatchers.Default) {
                gestureResults(searchPool, ids, handwritingGestureShortcutIds)
            }
        }

        val (keyboard, voice, handwriting, index) = withContext(Dispatchers.Default) {
            val shortcuts =
                if (keyboardQuery.isNotBlank() || voiceQuery.isNotBlank() || handwritingPrefix.isNotBlank()) {
                    preferencesRepository.getAppShortcuts()
                } else emptyMap()

            val keyboard = refilterForRefresh(result) { _ ->
                if (keyboardQuery.isBlank()) null
                else searchPool.filter { matchesQueryOrShortcut(it, keyboardQuery, shortcuts, prefixMatch = false) }
            }
            val voice = refilterForRefresh(result) { _ ->
                if (voiceQuery.isBlank()) null
                else searchPool.filter { matchesQueryOrShortcut(it, voiceQuery, shortcuts, prefixMatch = false) }
            }
            val handwriting = refilterForRefresh(result) { apps ->
                when {
                    gestureResults != null -> gestureResults.first
                    handwritingPrefix.isNotBlank() ->
                        searchPool.filter { matchesQueryOrShortcut(it, handwritingPrefix, shortcuts, prefixMatch = true) }
                    else -> null
                }
            }
            val index = refilterForRefresh(result) { apps ->
                indexLetter?.let { letter -> apps.filter { it.firstLetter == letter } }
            }
            listOf(keyboard, voice, handwriting, index)
        }

        // Recompute app-shortcut results for the active queries too, so a refresh commits apps
        // and shortcuts together. Otherwise a refresh landing mid-search could publish a single
        // filtered app while the shortcut list is momentarily empty, tripping auto-open.
        // Off the main thread; each search is a cheap in-memory scan once the index is warm,
        // and blank queries short-circuit to empty (the common non-searching refresh).
        val (keyboardShortcuts, voiceShortcuts, handwritingShortcuts) =
            withContext(Dispatchers.Default) {
                suspend fun shortcutsFor(query: String, prefixMatch: Boolean) =
                    if (query.isBlank()) emptyList()
                    else runCatching { shortcutSearchRepository.search(query, prefixMatch) }
                        .getOrDefault(emptyList())

                Triple(
                    shortcutsFor(keyboardQuery, prefixMatch = false),
                    shortcutsFor(voiceQuery, prefixMatch = false),
                    gestureResults?.second ?: shortcutsFor(handwritingPrefix, prefixMatch = true)
                )
            }

        // May run off the main thread (cached pass), where a superseded load isn't stopped by a
        // dispatch back to main, so check before publishing.
        currentCoroutineContext().ensureActive()
        _uiState.update {
            it.copy(
                apps = result.apps,
                searchableHiddenApps = result.searchableHiddenApps,
                availableLetters = letters,
                handwritingFilteredApps = handwriting.first,
                handwritingSmartApps = handwriting.second,
                handwritingShortcutResults = handwritingShortcuts,
                handwritingGestureMatched = gestureResults != null,
                indexFilteredApps = index.first,
                indexSmartApps = index.second,
                keyboardFilteredApps = keyboard.first,
                keyboardSmartApps = keyboard.second,
                keyboardShortcutResults = keyboardShortcuts,
                voiceFilteredApps = voice.first,
                voiceSmartApps = voice.second,
                voiceShortcutResults = voiceShortcuts,
                newlyInstalledAppPackage = result.newlyInstalledPackage,
                isLoading = isLoading,
                error = null
            )
        }
    }

    /**
     * (filtered, smart) lists for one mode after a refresh. [filter] returns null when the mode
     * has no active filter, in which case the full refreshed lists are used as-is.
     */
    private suspend fun refilterForRefresh(
        result: AppLoadResult,
        filter: (List<AppInfo>) -> List<AppInfo>?
    ): Pair<List<AppInfo>, List<AppInfo>> {
        val filtered = filter(result.apps) ?: return result.apps to result.smartApps
        if (filtered.isEmpty()) return filtered to emptyList()
        val gridSize = preferencesRepository.getGridSize()
        return filtered to getSmartAppListUseCase(filtered, gridSize, result.usageMap)
    }

    private fun observeAppChanges() {
        viewModelScope.launch {
            observeAppChangesUseCase().collect {
                // Reload apps when changes detected; the shortcut index is stale too
                shortcutSearchRepository.invalidate()
                loadApps()
            }
        }
    }

    // Keyboard mode filter
    fun filterAppsKeyboard(query: String) {
        activeFilters = activeFilters.copy(keyboardQuery = query)
        keyboardFilterJob?.cancel()
        keyboardCurrencyJob?.cancel()
        keyboardFilterJob = launchModeFilter(query, CurrencyMode.KEYBOARD, prefixMatch = false)
    }

    // Voice mode filter
    fun filterAppsVoice(query: String) {
        activeFilters = activeFilters.copy(voiceQuery = query)
        voiceFilterJob?.cancel()
        voiceCurrencyJob?.cancel()
        voiceFilterJob = launchModeFilter(query, CurrencyMode.VOICE, prefixMatch = false)
    }

    // Handwriting mode filter by prefix
    fun filterAppsByPrefixHandwriting(prefix: String) {
        activeFilters = activeFilters.copy(
            handwritingPrefix = prefix,
            handwritingGestureAppIds = null,
            handwritingGestureShortcutIds = null
        )
        handwritingFilterJob?.cancel()
        handwritingCurrencyJob?.cancel()
        handwritingFilterJob = launchModeFilter(prefix, CurrencyMode.HANDWRITING, prefixMatch = true)
    }

    // Runs parse + filter + smart-app compute on Default so keystrokes aren't blocked.
    private fun launchModeFilter(query: String, mode: CurrencyMode, prefixMatch: Boolean): Job =
        viewModelScope.launch(Dispatchers.Default) { runModeFilter(query, mode, prefixMatch) }

    private suspend fun runModeFilter(query: String, mode: CurrencyMode, prefixMatch: Boolean) {
        coroutineScope {
            val parseResult = computeCalcOrCurrency(query)
            ensureActive()

            val state = _uiState.value
            val filtered = if (query.isBlank()) {
                state.apps
            } else {
                val shortcuts = preferencesRepository.getAppShortcuts()
                searchPool(state.apps, state.searchableHiddenApps)
                    .filter { matchesQueryOrShortcut(it, query, shortcuts, prefixMatch) }
            }

            val (smart, newlyInstalled) = if (filtered.isEmpty()) {
                emptyList<AppInfo>() to null
            } else {
                val gridSize = preferencesRepository.getGridSize()
                val smartList = getSmartAppListUseCase(filtered, gridSize)
                smartList to getNewlyInstalledPackage(smartList)
            }

            // In-memory match against the prewarmed index; empty when the feature is off.
            val shortcutResults = if (query.isBlank()) {
                emptyList()
            } else {
                runCatching { shortcutSearchRepository.search(query, prefixMatch) }
                    .getOrDefault(emptyList())
            }

            ensureActive()

            _uiState.update { s ->
                when (mode) {
                    CurrencyMode.KEYBOARD -> s.copy(
                        keyboardFilteredApps = filtered,
                        keyboardSmartApps = smart,
                        keyboardShortcutResults = shortcutResults,
                        newlyInstalledAppPackage = newlyInstalled,
                        keyboardCalculatorResult = parseResult.calc,
                        keyboardCurrencyResult = parseResult.currency,
                        keyboardUnitResult = parseResult.unit,
                        keyboardTimeZoneResult = parseResult.timeZone
                    )
                    CurrencyMode.VOICE -> s.copy(
                        voiceFilteredApps = filtered,
                        voiceSmartApps = smart,
                        voiceShortcutResults = shortcutResults,
                        newlyInstalledAppPackage = newlyInstalled,
                        voiceCalculatorResult = parseResult.calc,
                        voiceCurrencyResult = parseResult.currency,
                        voiceUnitResult = parseResult.unit,
                        voiceTimeZoneResult = parseResult.timeZone
                    )
                    CurrencyMode.HANDWRITING -> s.copy(
                        handwritingFilteredApps = filtered,
                        handwritingSmartApps = smart,
                        handwritingShortcutResults = shortcutResults,
                        handwritingGestureMatched = false,
                        newlyInstalledAppPackage = newlyInstalled,
                        handwritingCalculatorResult = parseResult.calc,
                        handwritingCurrencyResult = parseResult.currency,
                        handwritingUnitResult = parseResult.unit,
                        handwritingTimeZoneResult = parseResult.timeZone
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
        val unit: UnitResultState? = null,
        val timeZone: TimeZoneResultState? = null
    )

    // Enabled conversion categories — loaded once lazily from preferences.
    // null means "all enabled" (first launch / no preference saved yet).
    private val enabledConversionCategories: Set<String>? by lazy {
        preferencesRepository.getEnabledConversionCategories()
    }

    private fun isCategoryEnabled(key: String): Boolean {
        val enabled = enabledConversionCategories ?: return true // null = all enabled
        return key in enabled
    }

    // Detect calc vs currency vs time-zone vs unit.
    // Currency first ("$5+5"), then time-zone ("US time now"), then unit ("5 km"), then calc.
    // Time-zone precedes unit so "9 pm IST" isn't mis-read as picometers.
    private fun computeCalcOrCurrency(input: String): ParseResult {
        val parsed = CurrencyUtil.tryParse(input, currencyRepository.getNativeCurrencyCode())
        if (parsed != null && isCategoryEnabled("CURRENCY")) {
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
        if (isCategoryEnabled("TIMEZONE")) {
            TimeZoneUtil.tryParse(input, homeTimeZone)?.let { p ->
                return ParseResult(calc = null, currency = null, parsed = null, timeZone = buildTimeZoneState(p))
            }
        }
        UnitUtil.tryParse(input, unitRegion)?.let { p ->
            if (isCategoryEnabled(p.category.name)) {
                return ParseResult(null, null, null, buildUnitState(p))
            }
        }
        val calc = com.joyal.swyplauncher.util.CalculatorUtil.evaluate(input)
        return ParseResult(calc, null, null)
    }

    private fun buildTimeZoneState(p: TimeZoneUtil.Parsed): TimeZoneResultState {
        val built = TimeZoneUtil.build(p.primary, p.secondary, p.epochMillis, p.formatPref)
        return TimeZoneResultState(
            epochMillis = p.epochMillis,
            primaryRef = p.primary,
            secondaryRef = p.secondary,
            formatPref = p.formatPref,
            primaryRows = built.primaryRows,
            secondaryRows = built.secondaryRows
        )
    }

    private fun activeTimeZone(mode: CurrencyMode): TimeZoneResultState? =
        _uiState.value.timeZoneResult(mode)

    private fun commitTimeZone(state: TimeZoneResultState, mode: CurrencyMode) {
        val built = TimeZoneUtil.build(state.primaryRef, state.secondaryRef, state.epochMillis, state.formatPref)
        val rebuilt = state.copy(primaryRows = built.primaryRows, secondaryRows = built.secondaryRows)
        _uiState.update { it.withTimeZoneResult(mode, rebuilt) }
    }

    /**
     * User edited the clock on a row — anchor the new instant to that row's zone.
     * [pref] (non-null when the typed value signalled 24h or am/pm) switches the format
     * for every row, matching the main search box; null keeps the current format.
     */
    fun updateTimeZoneTime(
        zoneId: String,
        hour: Int,
        minute: Int,
        pref: TimeZoneUtil.FormatPref?,
        mode: CurrencyMode
    ) {
        val active = activeTimeZone(mode) ?: return
        val newEpoch = TimeZoneUtil.applyEditedTime(zoneId, hour, minute, active.epochMillis)
        commitTimeZone(active.copy(epochMillis = newEpoch, formatPref = pref ?: active.formatPref), mode)
    }

    /** Swap which country/zone is primary (on top) and which is the target. */
    fun swapTimeZones(mode: CurrencyMode) {
        val active = activeTimeZone(mode) ?: return
        commitTimeZone(active.copy(primaryRef = active.secondaryRef, secondaryRef = active.primaryRef), mode)
    }

    /** Change one side to a different country (from the dropdown). */
    fun changeTimeZoneCountry(isPrimary: Boolean, iso: String, mode: CurrencyMode) {
        val active = activeTimeZone(mode) ?: return
        val ref = TimeZoneUtil.Ref.CountryRef(iso)
        var primary = if (isPrimary) ref else active.primaryRef
        var secondary = if (isPrimary) active.secondaryRef else ref
        // Don't let both sides become the same zone — push the untouched side to home/UTC.
        if (TimeZoneUtil.sameZones(primary, secondary)) {
            val alt: TimeZoneUtil.Ref =
                if (!TimeZoneUtil.sameZones(ref, homeTimeZone)) homeTimeZone
                else TimeZoneUtil.Ref.ZoneRef("Etc/UTC", null)
            if (isPrimary) secondary = alt else primary = alt
        }
        commitTimeZone(active.copy(primaryRef = primary, secondaryRef = secondary), mode)
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
        val human = UnitUtil.humanReadable(appContext, value, unitRegion.indian) ?: return null
        val unitName = spec?.aliases?.getOrNull(1) ?: spec?.aliases?.firstOrNull() ?: spec?.symbol ?: return human
        return "$human $unitName"
    }

    // Failed-conversion message, tailored for shoe sizes (out-of-range / negative input)
    private fun convError(category: UnitData.Category): String =
        appContext.getString(
            if (category == UnitData.Category.SHOE_SIZE) R.string.unit_invalid_shoe_size
            else R.string.unit_cannot_convert
        )

    // Interactive unit edits (typing a value or changing a unit). Synchronous recompute.
    fun updateUnitConversion(amount: Double, fromId: String, toId: String, isSourceChanged: Boolean, mode: CurrencyMode) {
        val active = _uiState.value.unitResult(mode) ?: return

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
        _uiState.update { it.withUnitResult(mode, newState) }
    }

    fun updateCurrencyConversion(amount: Double, fromCode: String, toCode: String, isSourceChanged: Boolean, mode: CurrencyMode) {
        val active = _uiState.value.currencyResult(mode) ?: return

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
        
        _uiState.update { it.withCurrencyResult(mode, pendingState) }
        
        launchCurrencyConversion(parsed, mode, isSourceChanged)
    }

    /**
     * Fetches rates (memory, disk or network) and commits the converted amount for [mode].
     * [isSourceChanged] is false when the user edited the target field: [parsed] then runs
     * target -> source and the result is mapped back onto the source side.
     */
    private fun launchCurrencyConversion(
        parsed: CurrencyUtil.Parsed,
        mode: CurrencyMode,
        isSourceChanged: Boolean = true
    ) {
        val targetSourceAmount = parsed.amount
        val targetSourceCode = parsed.from
        replaceCurrencyJob(mode) {
            viewModelScope.launch(Dispatchers.Default) {
                val result = currencyRepository.getRates()

                // Build state, but carefully map it back since we might be doing inverse conversion
                val newState = when (result) {
                    is CurrencyRepository.RatesResult.Success -> {
                        val converted = CurrencyUtil.convert(parsed.amount, parsed.from, parsed.to, result.base, result.rates)
                        if (converted == null) {
                            CurrencyResultState(
                                sourceAmount = if (isSourceChanged) parsed.amount else 0.0, // Error state
                                targetAmount = if (!isSourceChanged) parsed.amount else null,
                                error = appContext.getString(R.string.currency_unsupported),
                                fromCode = if (isSourceChanged) parsed.from else parsed.to,
                                toCode = if (isSourceChanged) parsed.to else parsed.from
                            )
                        } else {
                            CurrencyResultState(
                                sourceAmount = if (isSourceChanged) parsed.amount else converted,
                                targetAmount = if (!isSourceChanged) parsed.amount else converted,
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

                // Only commit if the user's current input still matches what was just converted
                _uiState.update { s ->
                    val active = s.currencyResult(mode)
                    val currentCheckingAmount = if (isSourceChanged) active?.sourceAmount else active?.targetAmount
                    val currentCheckingCode = if (isSourceChanged) active?.fromCode else active?.toCode

                    if (currentCheckingAmount != targetSourceAmount || currentCheckingCode != targetSourceCode) return@update s

                    s.withCurrencyResult(mode, newState)
                }
            }
        }
    }

    /**
     * Handwriting mode filter by matched custom gesture's assigned apps and app shortcuts.
     * When none of them can be shown (hidden, uninstalled, or shortcut search off), the ink is
     * treated as plain handwriting and [recognizedText] is searched instead.
     */
    fun filterAppsByCustomGestureHandwriting(
        appIds: Set<String>,
        shortcutIds: Set<String>,
        recognizedText: String
    ) {
        activeFilters = activeFilters.copy(
            handwritingPrefix = recognizedText,
            handwritingGestureAppIds = appIds,
            handwritingGestureShortcutIds = shortcutIds
        )
        handwritingFilterJob?.cancel()
        handwritingCurrencyJob?.cancel()
        handwritingFilterJob = viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val results = gestureResults(
                searchPool(state.apps, state.searchableHiddenApps), appIds, shortcutIds
            )
            if (results == null) {
                if (recognizedText.isNotBlank()) {
                    runModeFilter(recognizedText, CurrencyMode.HANDWRITING, prefixMatch = true)
                } else {
                    val (smart, newlyInstalled) = computeSmartApps(state.apps)
                    _uiState.update {
                        it.copy(
                            handwritingFilteredApps = state.apps,
                            handwritingSmartApps = smart,
                            handwritingShortcutResults = emptyList(),
                            handwritingGestureMatched = false,
                            newlyInstalledAppPackage = newlyInstalled,
                            handwritingCalculatorResult = null,
                            handwritingCurrencyResult = null,
                            handwritingUnitResult = null,
                            handwritingTimeZoneResult = null
                        )
                    }
                }
                return@launch
            }
            val (filtered, shortcuts) = results
            val (smart, newlyInstalled) = computeSmartApps(filtered)
            _uiState.update {
                it.copy(
                    handwritingFilteredApps = filtered,
                    handwritingSmartApps = smart,
                    handwritingShortcutResults = shortcuts,
                    handwritingGestureMatched = true,
                    newlyInstalledAppPackage = newlyInstalled,
                    handwritingCalculatorResult = null,
                    handwritingCurrencyResult = null,
                    handwritingUnitResult = null,
                    handwritingTimeZoneResult = null
                )
            }
        }
    }

    /** A gesture's visible apps (from [pool]) and shortcuts, or null when it has neither. */
    private suspend fun gestureResults(
        pool: List<AppInfo>,
        appIds: Set<String>,
        shortcutIds: Set<String>?
    ): Pair<List<AppInfo>, List<ShortcutSearchItem>>? {
        val apps = pool.filter { it.getIdentifier() in appIds }
        val shortcuts = resolveGestureShortcuts(shortcutIds)
        return if (apps.isEmpty() && shortcuts.isEmpty()) null else apps to shortcuts
    }

    /**
     * Resolves a gesture's assigned app-shortcut identifiers to live [ShortcutSearchItem]s.
     * Returns empty when the feature is disabled or the assistant role is missing (so a gesture
     * created while shortcuts were enabled degrades gracefully to just its apps).
     */
    private suspend fun resolveGestureShortcuts(shortcutIds: Set<String>?): List<ShortcutSearchItem> {
        if (shortcutIds.isNullOrEmpty()) return emptyList()
        return runCatching { shortcutSearchRepository.getAllShortcuts(forResults = true) }
            .getOrDefault(emptyList())
            .filter { it.identifier() in shortcutIds }
    }

    /** Apps a search or gesture matches against: the visible apps, then any searchable hidden ones. */
    private fun searchPool(apps: List<AppInfo>, searchableHiddenApps: List<AppInfo>): List<AppInfo> =
        if (searchableHiddenApps.isEmpty()) apps else apps + searchableHiddenApps

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
        activeFilters = activeFilters.copy(indexLetter = letter.uppercaseChar())
        viewModelScope.launch(Dispatchers.Default) {
            val filtered = _uiState.value.apps.filter { it.firstLetter == letter.uppercaseChar() }
            val (smart, newlyInstalled) = computeSmartApps(filtered)
            _uiState.update { it.copy(indexFilteredApps = filtered, indexSmartApps = smart, newlyInstalledAppPackage = newlyInstalled) }
        }
    }

    /**
     * Precomputes the smart-app ordering for every first-letter bucket so single-mode Index
     * letter-paging can render each page (and slide smoothly between adjacent letters) without
     * recomputing on every swipe. A single usage-map lookup is shared across all letters.
     */
    suspend fun computeSmartAppsByLetter(): Map<Char, List<AppInfo>> = withContext(Dispatchers.Default) {
        val apps = _uiState.value.apps
        if (apps.isEmpty()) return@withContext emptyMap()
        val gridSize = preferencesRepository.getGridSize()
        val usageMap = recordAppUsageUseCase.getUsageMap()
        apps.groupBy { it.firstLetter }
            .mapValues { (_, letterApps) -> getSmartAppListUseCase(letterApps, gridSize, usageMap) }
    }

    // Reset filters for a specific mode
    fun resetFilterHandwriting() {
        activeFilters = activeFilters.copy(
            handwritingPrefix = "",
            handwritingGestureAppIds = null,
            handwritingGestureShortcutIds = null
        )
        handwritingFilterJob?.cancel()
        handwritingCurrencyJob?.cancel()
        resetFilter { copy(handwritingFilteredApps = apps, handwritingSmartApps = it.first, handwritingShortcutResults = emptyList(), handwritingGestureMatched = false, newlyInstalledAppPackage = it.second, handwritingCalculatorResult = null, handwritingCurrencyResult = null, handwritingUnitResult = null, handwritingTimeZoneResult = null) }
    }
    fun resetFilterIndex() {
        activeFilters = activeFilters.copy(indexLetter = null)
        resetFilter { copy(indexFilteredApps = apps, indexSmartApps = it.first, newlyInstalledAppPackage = it.second) }
    }
    fun resetFilterKeyboard() {
        activeFilters = activeFilters.copy(keyboardQuery = "")
        keyboardFilterJob?.cancel()
        keyboardCurrencyJob?.cancel()
        resetFilter { copy(keyboardFilteredApps = apps, keyboardSmartApps = it.first, keyboardShortcutResults = emptyList(), newlyInstalledAppPackage = it.second, keyboardCalculatorResult = null, keyboardCurrencyResult = null, keyboardUnitResult = null, keyboardTimeZoneResult = null) }
    }
    fun resetFilterVoice() {
        activeFilters = activeFilters.copy(voiceQuery = "")
        voiceFilterJob?.cancel()
        voiceCurrencyJob?.cancel()
        resetFilter { copy(voiceFilteredApps = apps, voiceSmartApps = it.first, voiceShortcutResults = emptyList(), newlyInstalledAppPackage = it.second, voiceCalculatorResult = null, voiceCurrencyResult = null, voiceUnitResult = null, voiceTimeZoneResult = null) }
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
            // Start the app first: recording usage is a disk write and must not delay the launch
            val result = launchAppUseCase(packageName, activityName)

            // Record usage (with activity name for proper tracking). NonCancellable because the
            // sheet dismisses right after a launch, which clears this ViewModel mid-write.
            withContext(NonCancellable) {
                recordAppUsageUseCase(packageName, activityName)
            }

            if (result.isFailure) {
                _uiState.update {
                    it.copy(error = result.exceptionOrNull()?.message ?: appContext.getString(R.string.error_failed_to_launch_app))
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

    /** Launches an app-shortcut search result and counts it as usage of the parent app. */
    fun launchShortcut(shortcut: ShortcutSearchItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // Launch first; the usage record is a disk write (see launchApp)
            val launched = shortcutSearchRepository.launchShortcut(shortcut)
            withContext(NonCancellable) {
                recordAppUsageUseCase(shortcut.packageName, null)
            }
            if (!launched) {
                _uiState.update {
                    it.copy(error = appContext.getString(R.string.error_failed_to_launch_app))
                }
            }
        }
    }

    /** Icon for a shortcut search result (cached in the repository). Null while unavailable. */
    suspend fun loadShortcutIcon(shortcut: ShortcutSearchItem): ShortcutIcon? =
        runCatching { shortcutSearchRepository.getIcon(shortcut) }.getOrNull()

    /** Hides a single app shortcut from search results and drops it from the current lists. */
    fun hideShortcut(shortcut: ShortcutSearchItem) {
        preferencesRepository.addHiddenShortcut(shortcut.identifier())
        val id = shortcut.identifier()
        _uiState.update {
            it.copy(
                keyboardShortcutResults = it.keyboardShortcutResults.filterNot { s -> s.identifier() == id },
                voiceShortcutResults = it.voiceShortcutResults.filterNot { s -> s.identifier() == id },
                handwritingShortcutResults = it.handwritingShortcutResults.filterNot { s -> s.identifier() == id }
            )
        }
    }

    /** Maps a magic word to an app shortcut so typing that word surfaces it in search. */
    fun addShortcutAlias(word: String, shortcut: ShortcutSearchItem) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val current = preferencesRepository.getShortcutSearchAliases().toMutableMap()
        val ids = current[trimmed]?.toMutableSet() ?: mutableSetOf()
        ids.add(shortcut.identifier())
        current[trimmed] = ids
        preferencesRepository.setShortcutSearchAliases(current)
    }

    fun hideApp(identifier: String, mode: LauncherMode, searchQuery: String = "") {
        preferencesRepository.addHiddenApp(identifier)
        val hiddenPackage = identifier.substringBefore('/')
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Same filter/sort/smart-apps pipeline as loadApps(), now without the hidden app
                val result = withContext(Dispatchers.IO) {
                    buildAppLoadResult(getInstalledAppsUseCase())
                }
                val apps = result.apps

                // The mode the app was hidden from re-applies its search (or shows everything);
                // the other modes keep their current results minus the hidden app.
                suspend fun refilter(
                    targetMode: LauncherMode,
                    hasQuery: Boolean,
                    previous: List<AppInfo>,
                    matches: (AppInfo) -> Boolean
                ): Triple<List<AppInfo>, List<AppInfo>, String?> {
                    val unfiltered = Triple(apps, result.smartApps, result.newlyInstalledPackage)
                    if (mode == targetMode && !hasQuery) return unfiltered
                    val filtered =
                        if (mode == targetMode) apps.filter(matches)
                        else previous.filter { it.getIdentifier() != identifier }
                    if (mode != targetMode && filtered.isEmpty()) {
                        return Triple(filtered, unfiltered.second, unfiltered.third)
                    }
                    val (smart, newlyInstalled) = computeSmartApps(filtered)
                    return Triple(filtered, smart, newlyInstalled)
                }

                val keyboard = refilter(
                    LauncherMode.KEYBOARD, searchQuery.isNotBlank(), currentState.keyboardFilteredApps
                ) { it.label.contains(searchQuery, ignoreCase = true) }
                val voice = refilter(
                    LauncherMode.VOICE, searchQuery.isNotBlank(), currentState.voiceFilteredApps
                ) { it.label.contains(searchQuery, ignoreCase = true) }
                val handwriting = refilter(
                    LauncherMode.HANDWRITING, searchQuery.isNotBlank(), currentState.handwritingFilteredApps
                ) { it.label.startsWith(searchQuery, ignoreCase = true) }
                val index = refilter(
                    LauncherMode.INDEX, searchQuery.length == 1, currentState.indexFilteredApps
                ) { it.firstLetter == searchQuery[0].uppercaseChar() }

                _uiState.update {
                    it.copy(
                        apps = apps,
                        searchableHiddenApps = result.searchableHiddenApps,
                        handwritingFilteredApps = handwriting.first,
                        handwritingSmartApps = handwriting.second,
                        indexFilteredApps = index.first,
                        indexSmartApps = index.second,
                        keyboardFilteredApps = keyboard.first,
                        keyboardSmartApps = keyboard.second,
                        voiceFilteredApps = voice.first,
                        voiceSmartApps = voice.second,
                        newlyInstalledAppPackage = when (mode) {
                            LauncherMode.KEYBOARD -> keyboard.third
                            LauncherMode.VOICE -> voice.third
                            LauncherMode.HANDWRITING -> handwriting.third
                            LauncherMode.INDEX -> index.third
                        },
                        // Hidden apps are excluded from shortcut search, so drop the
                        // hidden app's shortcuts from the currently displayed results too
                        keyboardShortcutResults = it.keyboardShortcutResults
                            .filterNot { s -> s.packageName == hiddenPackage },
                        voiceShortcutResults = it.voiceShortcutResults
                            .filterNot { s -> s.packageName == hiddenPackage },
                        handwritingShortcutResults = it.handwritingShortcutResults
                            .filterNot { s -> s.packageName == hiddenPackage },
                        isLoading = false,
                        showHideAppTooltip = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: appContext.getString(R.string.error_failed_to_load_apps)
                    )
                }
            }
        }
    }

    fun unhideApp(identifier: String) {
        preferencesRepository.removeHiddenApp(identifier)
        loadApps()
    }

    /** Loads the currently-hidden apps into state so they can be un-hidden. */
    fun getHiddenApps() {
        viewModelScope.launch {
            val allApps = getInstalledAppsUseCase()
            val hiddenIds = preferencesRepository.getHiddenApps()
            _uiState.update { it.copy(hiddenApps = allApps.filter { app -> app.getIdentifier() in hiddenIds }) }
        }
    }

    /** Loads the currently-hidden app shortcuts into state so they can be un-hidden. */
    fun getHiddenShortcuts() {
        viewModelScope.launch {
            val hidden = shortcutSearchRepository.getHiddenShortcuts()
            _uiState.update { it.copy(hiddenShortcuts = hidden) }
        }
    }

    /**
     * Un-hides a single app shortcut, drops it from the hidden-shortcuts list, and re-runs the
     * active per-mode searches so it reappears in an already-open results page.
     */
    fun unhideShortcut(identifier: String) {
        preferencesRepository.removeHiddenShortcut(identifier)
        _uiState.update {
            it.copy(hiddenShortcuts = it.hiddenShortcuts.filterNot { s -> s.identifier() == identifier })
        }
        // loadApps() -> applyAppLoadResult re-runs the shortcut search against the now-updated
        // hidden set, so the just-unhidden shortcut is restored to the current results (mirrors
        // unhideApp, which already refreshes via loadApps()).
        loadApps()
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