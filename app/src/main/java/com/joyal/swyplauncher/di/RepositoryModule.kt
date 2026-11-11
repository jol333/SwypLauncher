package com.joyal.swyplauncher.di

import com.joyal.swyplauncher.data.repository.CurrencyRepositoryImpl
import com.joyal.swyplauncher.data.repository.PreferencesRepositoryImpl
import com.joyal.swyplauncher.data.repository.ShortcutSearchRepositoryImpl
import com.joyal.swyplauncher.data.source.AppDataSource
import com.joyal.swyplauncher.data.source.MLKitDataSource
import com.joyal.swyplauncher.domain.repository.AppRepository
import com.joyal.swyplauncher.domain.repository.CurrencyRepository
import com.joyal.swyplauncher.domain.repository.MLKitRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.repository.ShortcutSearchRepository
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
        appDataSource: AppDataSource
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindMLKitRepository(
        mlKitDataSource: MLKitDataSource
    ): MLKitRepository

    @Binds
    @Singleton
    abstract fun bindCurrencyRepository(
        currencyRepositoryImpl: CurrencyRepositoryImpl
    ): CurrencyRepository

    @Binds
    @Singleton
    abstract fun bindShortcutSearchRepository(
        shortcutSearchRepositoryImpl: ShortcutSearchRepositoryImpl
    ): ShortcutSearchRepository
}
