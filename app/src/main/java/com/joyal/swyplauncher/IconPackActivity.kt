package com.joyal.swyplauncher

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.AppSortOrder
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.domain.usecase.RecordAppUsageUseCase
import com.joyal.swyplauncher.ui.screens.IconPackScreen
import com.joyal.swyplauncher.ui.theme.SwypLauncherTheme
import com.joyal.swyplauncher.ui.util.sortApps
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Settings > Icon pack: choose the icon pack applied to every app. */
@AndroidEntryPoint
class IconPackActivity : AppCompatActivity() {

    @Inject
    lateinit var iconPackRepository: IconPackRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    @Inject
    lateinit var recordAppUsageUseCase: RecordAppUsageUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwypLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    IconPackOrchestrator(
                        onBack = { finish() },
                        iconPackRepository = iconPackRepository,
                        preferencesRepository = preferencesRepository,
                        getInstalledAppsUseCase = getInstalledAppsUseCase,
                        recordAppUsageUseCase = recordAppUsageUseCase
                    )
                }
            }
        }
    }
}

// The installed apps, and the ones worth previewing a pack on: the user's most used first.
private class IconPackApps(val all: List<AppInfo>, val preview: List<AppInfo>)

@Composable
private fun IconPackOrchestrator(
    onBack: () -> Unit,
    iconPackRepository: IconPackRepository,
    preferencesRepository: PreferencesRepository,
    getInstalledAppsUseCase: GetInstalledAppsUseCase,
    recordAppUsageUseCase: RecordAppUsageUseCase
) {
    // Slides in from the right and back out, like the other screens opened from settings
    var isFinishing by remember { mutableStateOf(false) }
    BackHandler(enabled = !isFinishing) { isFinishing = true }

    val gridSize = remember { preferencesRepository.getGridSize() }
    val cornerRadius = remember { preferencesRepository.getCornerRadius() }

    val apps by produceState(initialValue = IconPackApps(emptyList(), emptyList())) {
        value = withContext(Dispatchers.IO) {
            val hidden = preferencesRepository.getHiddenApps()
            // Hidden apps stay out of sight here too
            val visible = getInstalledAppsUseCase().filter { it.getIdentifier() !in hidden }
            val usage = runCatching { recordAppUsageUseCase.getUsageMap() }.getOrDefault(emptyMap())
            IconPackApps(
                all = visible,
                // Enough for the largest grid the stage shows (6 columns x 5 rows)
                preview = sortApps(visible, AppSortOrder.USAGE, usage).take(30)
            )
        }
    }

    SwipeDismissContainer(
        orientation = Orientation.Horizontal,
        onDismiss = { if (isFinishing) onBack() else isFinishing = true },
        startFromEdge = true,
        manualDismissTrigger = isFinishing
    ) {
        IconPackScreen(
            iconPackRepository = iconPackRepository,
            allApps = apps.all,
            previewApps = apps.preview,
            gridSize = gridSize,
            cornerRadius = cornerRadius,
            onBack = { isFinishing = true }
        )
    }
}
