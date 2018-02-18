package com.exchangerate.core.di.module

import com.exchangerate.core.data.repository.remote.RemoteExchangeRepository
import dagger.Module
import dagger.Provides
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
class RepositoryModule {

    @Provides
    @Singleton
    fun provideUsageRepository(retrofit: Retrofit): RemoteExchangeRepository {
        return retrofit.create(RemoteExchangeRepository::class.java)
    }

}