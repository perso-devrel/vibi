package com.example.dubcast.di

import android.content.Context
import androidx.room.Room
import com.example.dubcast.data.local.db.DubCastDatabase
import com.example.dubcast.data.local.db.dao.DubClipDao
import com.example.dubcast.data.local.db.dao.EditProjectDao
import com.example.dubcast.data.local.db.dao.ImageClipDao
import com.example.dubcast.data.local.db.dao.SegmentDao
import com.example.dubcast.data.local.db.dao.SubtitleClipDao
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
                DubCastDatabase.MIGRATION_7_8
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
}
