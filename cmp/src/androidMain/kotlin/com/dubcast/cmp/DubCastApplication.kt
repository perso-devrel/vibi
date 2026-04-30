package com.dubcast.cmp

import android.app.Application
import com.dubcast.shared.data.local.db.DubCastDatabaseInitializer
import com.dubcast.shared.di.androidPlatformModule
import com.dubcast.shared.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class DubCastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DubCastDatabaseInitializer.init(this)
        initKoin(
            bffBaseUrl = BuildConfig.BFF_BASE_URL,
            platformModules = listOf(androidPlatformModule)
        ) {
            androidLogger(Level.INFO)
            androidContext(this@DubCastApplication)
        }
    }
}
