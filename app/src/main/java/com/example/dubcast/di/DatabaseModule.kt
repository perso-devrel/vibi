package com.example.dubcast.di

import android.content.Context
import androidx.room.Room
import com.example.dubcast.data.local.db.DubCastDatabase
import com.example.dubcast.data.local.db.dao.BgmClipDao
import com.example.dubcast.data.local.db.dao.DubClipDao
import com.example.dubcast.data.local.db.dao.EditProjectDao
import com.example.dubcast.data.local.db.dao.ImageClipDao
import com.example.dubcast.data.local.db.dao.SegmentDao
import com.example.dubcast.data.local.db.dao.SubtitleClipDao
import com.example.dubcast.data.local.db.dao.TextOverlayDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DubCastDatabase {
        return Room.databaseBuilder(
            context,
            DubCastDatabase::class.java,
            "dubcast.db"
        )
            .addMigrations(
                DubCastDatabase.MIGRATION_1_2,
                DubCastDatabase.MIGRATION_2_3,
                DubCastDatabase.MIGRATION_3_4,
                DubCastDatabase.MIGRATION_4_5,
                DubCastDatabase.MIGRATION_5_6,
                DubCastDatabase.MIGRATION_6_7,
                DubCastDatabase.MIGRATION_7_8,
                DubCastDatabase.MIGRATION_8_9,
                DubCastDatabase.MIGRATION_9_10,
                DubCastDatabase.MIGRATION_10_11,
                DubCastDatabase.MIGRATION_11_12,
                DubCastDatabase.MIGRATION_12_13,
                DubCastDatabase.MIGRATION_13_14,
                DubCastDatabase.MIGRATION_14_15
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideEditProjectDao(db: DubCastDatabase): EditProjectDao = db.editProjectDao()

    @Provides
    fun provideDubClipDao(db: DubCastDatabase): DubClipDao = db.dubClipDao()

    @Provides
    fun provideSubtitleClipDao(db: DubCastDatabase): SubtitleClipDao = db.subtitleClipDao()

    @Provides
    fun provideImageClipDao(db: DubCastDatabase): ImageClipDao = db.imageClipDao()

    @Provides
    fun provideSegmentDao(db: DubCastDatabase): SegmentDao = db.segmentDao()

    @Provides
    fun provideTextOverlayDao(db: DubCastDatabase): TextOverlayDao = db.textOverlayDao()

    @Provides
    fun provideBgmClipDao(db: DubCastDatabase): BgmClipDao = db.bgmClipDao()
}
