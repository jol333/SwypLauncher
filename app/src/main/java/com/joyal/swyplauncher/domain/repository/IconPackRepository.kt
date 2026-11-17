package com.joyal.swyplauncher.domain.repository

import android.graphics.drawable.Drawable
import com.joyal.swyplauncher.domain.model.AppIconChoice
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackFallbackStyle
import com.joyal.swyplauncher.domain.model.IconPackIcon
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.model.IconPackMatch
import kotlinx.coroutines.flow.StateFlow

/**
 * Icon packs installed on the device, which one is applied, and the icons picked for individual
 * apps. Packs are other people's APKs and are often sloppily built, so nothing here throws for a
 * missing, half-written or unreadable pack: lookups just come back empty and the caller falls
 * back to the app's own icon.
 */
interface IconPackRepository {

    /**
     * Goes up whenever app icons may now render differently (pack applied, icon picked, pack
     * updated or removed). Icons on screen reload when it changes.
     */
    val iconVersion: StateFlow<Int>

    suspend fun getIconPacks(): List<IconPackInfo>

    /** The pack list if it was already loaded in this process; never touches PackageManager. */
    fun peekIconPacks(): List<IconPackInfo>?

    /** Package of the pack chosen in settings, whether or not it is still installed. */
    fun getSelectedIconPack(): String?

    /** [getSelectedIconPack], but null once that pack has been uninstalled. */
    fun getActiveIconPack(): String?

    fun setSelectedIconPack(packageName: String?, clearChoices: Boolean)

    fun cornerRadiusFor(packageName: String?): Float

    /** Individually picked icons, keyed by [AppInfo.getIdentifier]. */
    fun getIconChoices(): Map<String, AppIconChoice>

    /** Null removes the choice, so the app follows the applied pack again. */
    fun setIconChoice(appIdentifier: String, choice: AppIconChoice?)

    fun iconStyleToken(app: AppInfo): String

    // The calls below read the pack's APK and block: IO threads only.

    /** The icon [packPackage] assigns to [app], if it has one. */
    fun findIcon(packPackage: String, app: AppInfo): IconPackMatch?

    fun loadIconDrawable(icon: IconPackIcon, density: Int): Drawable?

    fun getFallbackStyle(packPackage: String): IconPackFallbackStyle?

    /** Every icon the pack offers for manual picking. Not checked for loadability. */
    suspend fun getAllIcons(packPackage: String): List<IconPackIcon>

    /** How many of [apps] the pack has its own icon for. */
    suspend fun countThemedApps(packPackage: String, apps: List<AppInfo>): Int

    /** A package was installed, updated or removed; drops whatever was cached about it. */
    fun onPackageChanged(packageName: String)

    /** Releases parsed packs and their open APK resources; they are reloaded on demand. */
    fun trimMemory()
}
