package com.dubcast.shared.data.local.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun createDubCastDatabase(): DubCastDatabase =
    getDatabaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*ALL_MIGRATIONS)
        .build()
