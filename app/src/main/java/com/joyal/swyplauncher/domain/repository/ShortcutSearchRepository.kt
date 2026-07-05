package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.domain.model.ShortcutIcon
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem

/**
 * Searches app shortcuts published by other apps (dynamic + manifest) for the assistant's
 * search modes. Shortcut access is only granted while Swyp Launcher holds the assistant
 * role, so every entry point degrades to "no results" — never a crash — when the role or
 * the user preference is missing.
 */
interface ShortcutSearchRepository {

    /**
     * Builds the in-memory shortcut index in the background (refreshing a stale one) so the
     * first search keystroke doesn't pay the load cost. No-op when the feature is disabled.
     */
    suspend fun prewarm()

    /**
     * Shortcuts whose label matches [query]. Returns empty when the feature is disabled,
     * the assistant role isn't held, or shortcut access fails. If the role has been
     * revoked, the preference is switched off as a side effect.
     */
    suspend fun search(query: String, prefixMatch: Boolean): List<ShortcutSearchItem>

    /**
     * All shortcuts available in the system. Returns empty when the feature is disabled,
     * or the assistant role isn't held.
     */
    suspend fun getAllShortcuts(): List<ShortcutSearchItem>

    /**
     * The currently-hidden shortcuts that are still resolvable in the system (so they can be
     * un-hidden from the hidden-apps screen). Independent of the feature toggle — only the
     * assistant role is required to resolve them. Entries whose parent app was uninstalled
     * are omitted, since they can no longer be rendered or launched.
     */
    suspend fun getHiddenShortcuts(): List<ShortcutSearchItem>

    /** Loads (and caches) the icon for a shortcut, with dark-icon detection. Null on failure. */
    suspend fun getIcon(item: ShortcutSearchItem): ShortcutIcon?

    /** Launches the shortcut. Returns false when it no longer exists or access is denied. */
    fun launchShortcut(item: ShortcutSearchItem): Boolean

    /** Drops the cached index (called when apps are installed/removed/updated). */
    fun invalidate()
}
