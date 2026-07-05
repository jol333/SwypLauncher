package com.joyal.swyplauncher.ui.components

import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem

sealed class SelectableItem {
    abstract val id: String
    abstract val label: String

    data class App(val appInfo: AppInfo) : SelectableItem() {
        override val id = appInfo.getIdentifier()
        override val label = appInfo.label
    }

    data class Shortcut(val searchItem: ShortcutSearchItem) : SelectableItem() {
        override val id = searchItem.identifier()
        override val label = searchItem.label
    }
}
