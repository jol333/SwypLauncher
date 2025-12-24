package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.AppRepository
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(): List<AppInfo> = appRepository.getInstalledApps()
}
