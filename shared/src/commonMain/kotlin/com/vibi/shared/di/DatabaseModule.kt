package com.vibi.shared.di

import com.vibi.shared.data.local.db.VibiDatabase
import com.vibi.shared.data.local.db.createVibiDatabase
import org.koin.dsl.module

val databaseModule = module {
    single<VibiDatabase> { createVibiDatabase() }
    single { get<VibiDatabase>().editProjectDao() }
    single { get<VibiDatabase>().segmentDao() }
    single { get<VibiDatabase>().bgmClipDao() }
    single { get<VibiDatabase>().separationDirectiveDao() }
}
