package com.dubcast.shared.di

import com.dubcast.shared.data.local.db.DubCastDatabase
import com.dubcast.shared.data.local.db.createDubCastDatabase
import org.koin.dsl.module

val databaseModule = module {
    single<DubCastDatabase> { createDubCastDatabase() }
    single { get<DubCastDatabase>().editProjectDao() }
    single { get<DubCastDatabase>().dubClipDao() }
    single { get<DubCastDatabase>().subtitleClipDao() }
    single { get<DubCastDatabase>().imageClipDao() }
    single { get<DubCastDatabase>().segmentDao() }
    single { get<DubCastDatabase>().textOverlayDao() }
    single { get<DubCastDatabase>().bgmClipDao() }
    single { get<DubCastDatabase>().separationDirectiveDao() }
}
