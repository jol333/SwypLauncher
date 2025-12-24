package com.joyal.swyplauncher.di

import com.joyal.swyplauncher.data.repository.AppRepositoryImpl
import com.joyal.swyplauncher.data.repository.MLKitRepositoryImpl
import com.joyal.swyplauncher.data.repository.PreferencesRepositoryImpl
import com.joyal.swyplauncher.domain.repository.AppRepository
import com.joyal.swyplauncher.domain.repository.MLKitRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(
        appRepositoryImpl: AppRepositoryImpl
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindMLKitRepository(
        mlKitRepositoryImpl: MLKitRepositoryImpl
    ): MLKitRepository
}
