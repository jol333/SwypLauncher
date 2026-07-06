package com.joyal.swyplauncher.ui.util

import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.AppSortOrder
import com.joyal.swyplauncher.ui.model.AppListItem

/**
 * Sorts apps based on the specified sort order.
 * For CATEGORY sort, returns a flat list with "Other" category at the bottom.
 */
fun sortApps(
    apps: List<AppInfo>,
    sortOrder: AppSortOrder,
    usageMap: Map<String, Int> = emptyMap()
): List<AppInfo> {
    return when (sortOrder) {
        AppSortOrder.NAME -> apps.sortedBy { it.label.lowercase() }
        AppSortOrder.USAGE -> apps.sortedByDescending {
            // Try full identifier first, then fall back to package name only
            usageMap[it.getIdentifier()] ?: usageMap[it.packageName] ?: 0
        }
        AppSortOrder.CATEGORY -> apps.sortedWith(
            compareBy<AppInfo> { if (it.category == "Other") "zzz" else it.category }
                .thenBy { it.label.lowercase() }
        )
    }
}

/**
 * Converts app list to AppListItem list with category headers when sort order is CATEGORY.
 * Smart apps appear first without category headers, then remaining apps grouped by category.
 * "Other" category is always shown at the bottom.
 *
 * @param isSearching When true, category headers are hidden even in CATEGORY sort mode
 * @param gridSize Number of columns in the grid (used to determine if divider should be shown)
 */
fun combineAppListsWithHeaders(
    smartApps: List<AppInfo>,
    allApps: List<AppInfo>,
    sortOrder: AppSortOrder,
    isSearching: Boolean = false,
    gridSize: Int = 4
): List<AppListItem> {
    val smartIdentifiers = smartApps.map { it.getIdentifier() }.toSet()
    var remainingApps = allApps.filter { it.getIdentifier() !in smartIdentifiers }

    val result = mutableListOf<AppListItem>()

    // Add smart apps first (without headers)
    smartApps.forEach { result.add(AppListItem.App(it)) }

    // Fill first row with alphabetical apps if smart apps don't fill it
    if (smartApps.size < gridSize && remainingApps.isNotEmpty()) {
        val slotsToFill = gridSize - smartApps.size
        // Get fillers sorted alphabetically
        val fillers = remainingApps
            .sortedBy { it.label.lowercase() }
            .take(slotsToFill)

        fillers.forEach { result.add(AppListItem.App(it)) }

        // Remove fillers from remaining apps for subsequent processing
        val fillerIds = fillers.map { it.getIdentifier() }.toSet()
        remainingApps = remainingApps.filter { it.getIdentifier() !in fillerIds }

        // No divider since we filled with regular apps
    } else if (smartApps.isNotEmpty() && remainingApps.isNotEmpty()) {
        // Smart apps filled the row (or more), and there are more apps, show divider
        result.add(AppListItem.Divider)
    }

    // Add remaining apps with category headers if CATEGORY sort AND not searching
    if (sortOrder == AppSortOrder.CATEGORY && remainingApps.isNotEmpty() && !isSearching) {
        val groupedByCategory = remainingApps
            .sortedWith(compareBy<AppInfo> { it.category }.thenBy { it.label.lowercase() })
            .groupBy { it.category }

        // Sort categories alphabetically, but put "Other" at the end
        val sortedCategories = groupedByCategory.keys.sortedWith(compareBy {
            if (it == "Other") "zzz" else it // "zzz" ensures "Other" comes last
        })

        sortedCategories.forEach { category ->
            val apps = groupedByCategory[category] ?: emptyList()
            result.add(AppListItem.CategoryHeader(category))
            apps.forEach { result.add(AppListItem.App(it)) }
        }
    } else {
        // Non-CATEGORY sort (or searching): no category headers. remainingApps preserves the
        // caller's ordering (allApps is pre-sorted); re-sort by name only for NAME sort or search.
        val sortedRemaining = if (sortOrder == AppSortOrder.NAME || isSearching) {
             remainingApps.sortedBy { it.label.lowercase() }
        } else {
             remainingApps
        }

        sortedRemaining.forEach { result.add(AppListItem.App(it)) }
    }

    return result
}
