package com.rivaisdolar.app.di

import android.content.Context
import androidx.room.Room
import com.rivaisdolar.app.data.local.AcaoPendenteDao
import com.rivaisdolar.app.data.local.BancoLocal
import com.rivaisdolar.app.data.local.CacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EscopoApp

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun banco(@ApplicationContext context: Context): BancoLocal =
        Room.databaseBuilder(context, BancoLocal::class.java, "rivais.db")
            // O banco local é só cache: se o esquema mudar, basta baixar de novo.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun cacheDao(banco: BancoLocal): CacheDao = banco.cache()

    @Provides
    fun acaoDao(banco: BancoLocal): AcaoPendenteDao = banco.acoes()

    @Provides
    @Singleton
    @EscopoApp
    fun escopo(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
