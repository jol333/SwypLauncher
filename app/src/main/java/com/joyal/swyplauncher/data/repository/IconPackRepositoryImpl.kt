package com.joyal.swyplauncher.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.joyal.swyplauncher.domain.model.AppIconChoice
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackFallbackStyle
import com.joyal.swyplauncher.domain.model.IconPackIcon
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.model.IconPackMatch
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.util.IconPackParser
import com.joyal.swyplauncher.util.ParsedIconPack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconPackRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : IconPackRepository {

    private val packageManager: PackageManager = context.packageManager

    private val _iconVersion = MutableStateFlow(0)
    override val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    @Volatile
    private var cachedPacks: List<IconPackInfo>? = null

    // The prefs value is JSON; parsed once and kept, since every icon load consults it.
    @Volatile
    private var cachedChoices: Map<String, AppIconChoice>? = null

    // Last-update time per pack package, NOT_INSTALLED when the package is gone. One binder call
    // per pack per process instead of one per icon.
    private val packUpdateTimes = ConcurrentHashMap<String, Long>()

    // Other apps' resources, kept open while their icons are being drawn.
    private val packResources = ConcurrentHashMap<String, Resources>()

    // Parsed appfilter.xml files, least recently used first. A big pack maps several thousand
    // components, so only a few are kept. Guarded by itself; parsing happens under the lock so
    // twenty icons requested at once parse the pack once.
    private val parsedPacks = object : LinkedHashMap<String, ParsedIconPack>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParsedIconPack>?) =
            size > MAX_PARSED_PACKS
    }

    override suspend fun getIconPacks(): List<IconPackInfo> =
        cachedPacks ?: withContext(Dispatchers.IO) { queryIconPacks().also { cachedPacks = it } }

    override fun peekIconPacks(): List<IconPackInfo>? = cachedPacks

    private fun queryIconPacks(): List<IconPackInfo> {
        val intents = THEME_ACTIONS.map { Intent(it) } +
            THEME_CATEGORIES.map { Intent(Intent.ACTION_MAIN).addCategory(it) }
        val packs = LinkedHashMap<String, IconPackInfo>()
        for (intent in intents) {
            val resolved = try {
                packageManager.queryIntentActivities(intent, 0)
            } catch (_: Exception) {
                continue
            }
            for (info in resolved) {
                val packageName = info.activityInfo?.packageName ?: continue
                if (packageName == context.packageName || packageName in packs) continue
                // Theme intents are also answered by apps that are not icon packs (and by packs
                // that ship no icon mapping); without an appfilter there is nothing to apply.
                // Not through resourcesFor(): candidates that turn out not to be packs shouldn't
                // stay open in the cache.
                val resources = try {
                    packageManager.getResourcesForApplication(packageName)
                } catch (_: Exception) {
                    continue
                }
                if (!IconPackParser.hasAppFilter(resources, packageName)) continue
                val label = try {
                    info.activityInfo.applicationInfo.loadLabel(packageManager).toString()
                } catch (_: Exception) {
                    packageName
                }
                packs[packageName] = IconPackInfo(packageName, label.ifBlank { packageName })
            }
        }
        return packs.values.sortedBy { it.label.lowercase() }
    }

    override fun getSelectedIconPack(): String? = preferencesRepository.getIconPack()

    override fun getActiveIconPack(): String? =
        getSelectedIconPack()?.takeIf { packUpdateTime(it) != NOT_INSTALLED }

    override fun setSelectedIconPack(packageName: String?, clearChoices: Boolean) {
        preferencesRepository.setIconPack(packageName)
        if (clearChoices) {
            preferencesRepository.setAppIconChoices(emptyMap())
            cachedChoices = emptyMap()
        }
        _iconVersion.update { it + 1 }
    }

    override fun getIconChoices(): Map<String, AppIconChoice> =
        cachedChoices ?: preferencesRepository.getAppIconChoices().also { cachedChoices = it }

    override fun setIconChoice(appIdentifier: String, choice: AppIconChoice?) {
        val updated = getIconChoices().toMutableMap()
        if (choice != null) updated[appIdentifier] = choice else updated.remove(appIdentifier)
        cachedChoices = updated
        preferencesRepository.setAppIconChoices(updated)
        _iconVersion.update { it + 1 }
    }

    override fun iconStyleToken(app: AppInfo): String {
        val picked = when (val choice = getIconChoices()[app.getIdentifier()]) {
            AppIconChoice.Original -> return ""
            is AppIconChoice.FromPack -> {
                val updated = packUpdateTime(choice.icon.packPackage)
                // A pick from a pack that is gone falls through to the applied pack
                if (updated == NOT_INSTALLED) null
                else "${choice.icon.packPackage}/${choice.icon.drawableName}@$updated"
            }
            null -> null
        }
        val source = picked ?: run {
            val pack = getActiveIconPack() ?: return ""
            "$pack@${packUpdateTime(pack)}"
        }
        return "p" + Integer.toHexString(source.hashCode())
    }

    private fun packUpdateTime(packPackage: String): Long =
        packUpdateTimes.getOrPut(packPackage) {
            try {
                packageManager.getPackageInfo(packPackage, 0).lastUpdateTime
            } catch (_: Exception) {
                NOT_INSTALLED
            }
        }

    private fun resourcesFor(packPackage: String): Resources? {
        packResources[packPackage]?.let { return it }
        return try {
            packageManager.getResourcesForApplication(packPackage).also {
                packResources[packPackage] = it
            }
        } catch (_: Exception) {
            // Uninstalled, disabled, or an APK the system can't open.
            null
        }
    }

    private fun parsedPack(packPackage: String): ParsedIconPack? {
        if (packUpdateTime(packPackage) == NOT_INSTALLED) return null
        synchronized(parsedPacks) {
            parsedPacks[packPackage]?.let { return it }
            val resources = resourcesFor(packPackage) ?: return null
            return IconPackParser.parseAppFilter(resources, packPackage).also {
                parsedPacks[packPackage] = it
            }
        }
    }

    override fun findIcon(packPackage: String, app: AppInfo): IconPackMatch? {
        val pack = parsedPack(packPackage) ?: return null
        val component = app.getIdentifier()

        // Dynamic calendar: "<prefix><day of month>", when the pack really has today's drawable
        pack.calendars[component]?.let { prefix ->
            val today = IconPackIcon(
                packPackage,
                prefix + Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            )
            if (drawableId(today) != 0) return IconPackMatch(today, changesDaily = true)
        }

        val name = pack.icons[component]
            // The app renamed its launcher activity since the pack was made: still use the
            // pack's icon as long as the pack has only one for that package.
            ?: pack.packageIcons[app.packageName]?.takeIf { it.isNotEmpty() }
            ?: return null
        return IconPackMatch(IconPackIcon(packPackage, name))
    }

    @SuppressLint("DiscouragedApi") // the pack's resources can only be looked up by name
    private fun drawableId(icon: IconPackIcon): Int {
        val resources = resourcesFor(icon.packPackage) ?: return 0
        return try {
            resources.getIdentifier(icon.drawableName, "drawable", icon.packPackage)
                .takeIf { it != 0 }
                ?: resources.getIdentifier(icon.drawableName, "mipmap", icon.packPackage)
        } catch (_: Exception) {
            0
        }
    }

    override fun loadIconDrawable(icon: IconPackIcon, density: Int): Drawable? {
        val id = drawableId(icon)
        if (id == 0) return null
        val resources = resourcesFor(icon.packPackage) ?: return null
        return try {
            ResourcesCompat.getDrawableForDensity(resources, id, density, null)
        } catch (_: Exception) {
            // Corrupt image, or an XML drawable that references something the pack doesn't have.
            null
        } catch (_: OutOfMemoryError) {
            // An absurdly large image; the caller falls back to another icon.
            null
        }
    }

    override fun getFallbackStyle(packPackage: String): IconPackFallbackStyle? =
        parsedPack(packPackage)?.fallbackStyle

    override suspend fun getAllIcons(packPackage: String): List<IconPackIcon> =
        withContext(Dispatchers.IO) {
            val resources = resourcesFor(packPackage) ?: return@withContext emptyList()
            val listed = IconPackParser.parseDrawableList(resources, packPackage)
            // Packs without a drawable.xml still have the icons their appfilter points at
            val names = listed.ifEmpty {
                parsedPack(packPackage)?.icons?.values?.distinct().orEmpty()
            }
            names.map { IconPackIcon(packPackage, it) }
        }

    override suspend fun countThemedApps(packPackage: String, apps: List<AppInfo>): Int =
        withContext(Dispatchers.IO) {
            val pack = parsedPack(packPackage) ?: return@withContext 0
            apps.count { app ->
                val component = app.getIdentifier()
                component in pack.icons || component in pack.calendars ||
                    !pack.packageIcons[app.packageName].isNullOrEmpty()
            }
        }

    override fun onPackageChanged(packageName: String) {
        val wasKnown = packUpdateTimes.remove(packageName) != null
        packResources.remove(packageName)
        synchronized(parsedPacks) { parsedPacks.remove(packageName) }
        // Any package change can add or remove a pack; the next reader queries again.
        cachedPacks = null

        val inUse = packageName == getSelectedIconPack() || getIconChoices().values.any {
            it is AppIconChoice.FromPack && it.icon.packPackage == packageName
        }
        // Icons drawn from this pack are now out of date (or have nothing to draw from)
        if (wasKnown && inUse) _iconVersion.update { it + 1 }
    }

    override fun trimMemory() {
        synchronized(parsedPacks) { parsedPacks.clear() }
        packResources.clear()
    }

    private companion object {
        const val NOT_INSTALLED = -1L
        const val MAX_PARSED_PACKS = 4

        // How launchers have discovered icon packs since ADW; a pack declares at least one.
        val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.fede.launcher.THEME_ICONPACK"
        )
        val THEME_CATEGORIES = listOf(
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
        )
    }
}
