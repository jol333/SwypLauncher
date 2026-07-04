package com.joyal.swyplauncher.ui.model

import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem

sealed class AppListItem {
    data class CategoryHeader(val category: String) : AppListItem()
    data class App(val appInfo: AppInfo) : AppListItem()
    data class Shortcut(val shortcut: ShortcutSearchItem) : AppListItem()
    data object Divider : AppListItem()
}
