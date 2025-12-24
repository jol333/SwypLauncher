package com.joyal.swyplauncher.ui.model

import com.joyal.swyplauncher.domain.model.AppInfo

sealed class AppListItem {
    data class CategoryHeader(val category: String) : AppListItem()
    data class App(val appInfo: AppInfo) : AppListItem()
    data object Divider : AppListItem()
}
