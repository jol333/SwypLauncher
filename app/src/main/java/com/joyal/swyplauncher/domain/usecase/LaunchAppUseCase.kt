package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.domain.repository.AppRepository
import javax.inject.Inject

class LaunchAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, activityName: String? = null): Result<Unit> = 
        appRepository.launchApp(packageName, activityName)
}
