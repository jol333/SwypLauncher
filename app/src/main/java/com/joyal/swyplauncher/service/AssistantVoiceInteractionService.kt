package com.joyal.swyplauncher.service

import android.service.voice.VoiceInteractionService

import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AssistantVoiceInteractionService : VoiceInteractionService() {

    @Inject
    lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReady() {
        super.onReady()
        // Pre-warm the app list when the service is ready
        serviceScope.launch {
            try {
                getInstalledAppsUseCase()
            } catch (e: Exception) {
                // Ignore errors during pre-warm
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
