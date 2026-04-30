package com.dubcast.shared.di

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.createBffHttpClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

val BffBaseUrlKey = named("bffBaseUrl")

val networkModule = module {
    single<HttpClient> {
        val baseUrl = getProperty<String>("bffBaseUrl")
        createBffHttpClient(baseUrl = baseUrl)
    }
    single { BffApi(client = get()) }
}
