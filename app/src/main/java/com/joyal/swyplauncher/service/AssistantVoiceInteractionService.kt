package com.joyal.swyplauncher.service

import android.service.voice.VoiceInteractionService

import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Voice interaction service that the system binds to when the user invokes the
 * assistant (navbar gesture / long-press power). Kept deliberately lightweight:
 *
 *  - No `@AndroidEntryPoint` annotation, so Hilt does NOT do field injection
 *    synchronously on the bind path. On a cold start (post-Doze / process death)
 *    every millisecond between bind and `onReady()` widens the window in which
 *    the system can drop the show request.
 *  - The pre-warm dependency is resolved lazily off the main thread via
 *    `EntryPointAccessors`, so `onReady()` returns immediately and the system
 *    can move on to creating the session.
 */
class AssistantVoiceInteractionService : VoiceInteractionService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrewarmEntryPoint {
        fun getInstalledAppsUseCase(): GetInstalledAppsUseCase
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReady() {
        super.onReady()
        // Pre-warm the installed-apps cache off the main thread. Resolving the
        // Hilt dependency here (instead of via @Inject) keeps service bind fast.
        serviceScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    PrewarmEntryPoint::class.java
                )
                entryPoint.getInstalledAppsUseCase().invoke()
            } catch (_: Exception) {
                // Best-effort prewarm; ignore failures.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
