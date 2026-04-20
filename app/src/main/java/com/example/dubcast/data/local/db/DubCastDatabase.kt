package com.example.dubcast.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dubcast.data.local.db.dao.DubClipDao
import com.example.dubcast.data.local.db.dao.EditProjectDao
import com.example.dubcast.data.local.db.dao.ImageClipDao
import com.example.dubcast.data.local.db.dao.SubtitleClipDao
import com.example.dubcast.data.local.db.entity.DubClipEntity
import com.example.dubcast.data.local.db.entity.EditProjectEntity
import com.example.dubcast.data.local.db.entity.ImageClipEntity
import com.example.dubcast.data.local.db.entity.SubtitleClipEntity

@Database(
    entities = [
        EditProjectEntity::class,
        DubClipEntity::class,
        SubtitleClipEntity::class,
        ImageClipEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class DubCastDatabase : RoomDatabase() {
    abstract fun editProjectDao(): EditProjectDao
    abstract fun dubClipDao(): DubClipDao
    abstract fun subtitleClipDao(): SubtitleClipDao
    abstract fun imageClipDao(): ImageClipDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dub_jobs ADD COLUMN withDubbing INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS edit_projects (
                        projectId TEXT NOT NULL PRIMARY KEY,
                        videoUri TEXT NOT NULL,
                        videoDurationMs INTEGER NOT NULL,
                        videoWidth INTEGER NOT NULL,
                        videoHeight INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS dub_clips (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        voiceId TEXT NOT NULL,
                        voiceName TEXT NOT NULL,
                        audioFilePath TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        volume REAL NOT NULL DEFAULT 1.0
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS subtitle_clips (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        anchor TEXT NOT NULL DEFAULT 'bottom',
                        yOffsetPct REAL NOT NULL DEFAULT 90.0
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS dub_jobs")
                db.execSQL("DROP TABLE IF EXISTS subtitle_segments")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN trimStartMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN trimEndMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subtitle_clips ADD COLUMN sourceDubClipId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE subtitle_clips ADD COLUMN xPct REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE subtitle_clips ADD COLUMN yPct REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE subtitle_clips ADD COLUMN widthPct REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE subtitle_clips ADD COLUMN heightPct REAL DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS image_clips (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        imageUri TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        xPct REAL NOT NULL DEFAULT 50.0,
                        yPct REAL NOT NULL DEFAULT 50.0,
                        widthPct REAL NOT NULL DEFAULT 30.0,
                        heightPct REAL NOT NULL DEFAULT 30.0
                    )"""
                )
            }
        }
    }
}
