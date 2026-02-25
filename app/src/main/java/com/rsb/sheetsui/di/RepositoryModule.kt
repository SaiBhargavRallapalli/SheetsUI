package com.rsb.sheetsui.di

import com.rsb.sheetsui.data.repository.ExportRepositoryImpl
import com.rsb.sheetsui.data.repository.FormRepositoryImpl
import com.rsb.sheetsui.data.repository.SpreadsheetRepositoryImpl
import com.rsb.sheetsui.domain.repository.ExportRepository
import com.rsb.sheetsui.domain.repository.FormRepository
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
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
    abstract fun bindSpreadsheetRepository(
        impl: SpreadsheetRepositoryImpl
    ): SpreadsheetRepository

    @Binds
    @Singleton
    abstract fun bindFormRepository(
        impl: FormRepositoryImpl
    ): FormRepository

    @Binds
    @Singleton
    abstract fun bindExportRepository(
        impl: ExportRepositoryImpl
    ): ExportRepository
}
