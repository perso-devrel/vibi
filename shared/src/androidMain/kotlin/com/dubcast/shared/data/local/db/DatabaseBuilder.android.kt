package com.dubcast.shared.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

lateinit var applicationContext: Context
    internal set

actual fun getDatabaseBuilder(): RoomDatabase.Builder<DubCastDatabase> {
    check(::applicationContext.isInitialized) {
        "DubCast DB: call DubCastDatabaseInitializer.init(context) before building the database."
    }
    val dbFile = applicationContext.getDatabasePath(DubCastDatabase.DB_FILE_NAME)
    return Room.databaseBuilder<DubCastDatabase>(
        context = applicationContext,
        name = dbFile.absolutePath
    )
}

object DubCastDatabaseInitializer {
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}
