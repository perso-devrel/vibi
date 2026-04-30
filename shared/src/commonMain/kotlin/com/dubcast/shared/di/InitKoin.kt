package com.dubcast.shared.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initKoin(
    bffBaseUrl: String,
    platformModules: List<org.koin.core.module.Module> = emptyList(),
    extraConfig: KoinApplication.() -> Unit = {}
): KoinApplication = startKoin {
    properties(mapOf("bffBaseUrl" to bffBaseUrl))
    modules(
        listOf(
            databaseModule,
            networkModule,
            repositoryModule,
            useCaseModule,
            viewModelModule
        ) + platformModules
    )
    extraConfig()
}
