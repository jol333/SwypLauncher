package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAppChangesUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    operator fun invoke(): Flow<AppChangeEvent> = appRepository.observeAppChanges()
}
