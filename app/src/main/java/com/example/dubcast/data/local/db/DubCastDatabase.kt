package com.example.dubcast.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dubcast.data.local.db.dao.BgmClipDao
import com.example.dubcast.data.local.db.dao.DubClipDao
import com.example.dubcast.data.local.db.dao.EditProjectDao
import com.example.dubcast.data.local.db.dao.ImageClipDao
import com.example.dubcast.data.local.db.dao.SegmentDao
import com.example.dubcast.data.local.db.dao.SubtitleClipDao
import com.example.dubcast.data.local.db.dao.TextOverlayDao
import com.example.dubcast.data.local.db.entity.BgmClipEntity
import com.example.dubcast.data.local.db.entity.DubClipEntity
import com.example.dubcast.data.local.db.entity.EditProjectEntity
import com.example.dubcast.data.local.db.entity.ImageClipEntity
import com.example.dubcast.data.local.db.entity.SegmentEntity
import com.example.dubcast.data.local.db.entity.SubtitleClipEntity
import com.example.dubcast.data.local.db.entity.TextOverlayEntity

@Database(
    entities = [
        EditProjectEntity::class,
        DubClipEntity::class,
        SubtitleClipEntity::class,
        ImageClipEntity::class,
        SegmentEntity::class,
        TextOverlayEntity::class,
        BgmClipEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class DubCastDatabase : RoomDatabase() {
    abstract fun editProjectDao(): EditProjectDao
    abstract fun dubClipDao(): DubClipDao
    abstract fun subtitleClipDao(): SubtitleClipDao
    abstract fun imageClipDao(): ImageClipDao
    abstract fun segmentDao(): SegmentDao
    abstract fun textOverlayDao(): TextOverlayDao
    abstract fun bgmClipDao(): BgmClipDao

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

        val MIGRATION_7_8_STATEMENTS: List<String> = listOf(
            // 1) Create segments table
            """CREATE TABLE IF NOT EXISTS segments (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                type TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                sourceUri TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                trimStartMs INTEGER NOT NULL DEFAULT 0,
                trimEndMs INTEGER NOT NULL DEFAULT 0,
                imageXPct REAL NOT NULL DEFAULT 50.0,
                imageYPct REAL NOT NULL DEFAULT 50.0,
                imageWidthPct REAL NOT NULL DEFAULT 50.0,
                imageHeightPct REAL NOT NULL DEFAULT 50.0
            )""",
            // 2) Migrate each existing project's video into segment 0
            """INSERT INTO segments(
                id, projectId, type, `order`, sourceUri,
                durationMs, width, height, trimStartMs, trimEndMs,
                imageXPct, imageYPct, imageWidthPct, imageHeightPct
            )
            SELECT projectId || '_seg0', projectId, 'VIDEO', 0, videoUri,
                videoDurationMs, videoWidth, videoHeight, trimStartMs, trimEndMs,
                50.0, 50.0, 50.0, 50.0
            FROM edit_projects""",
            // 3) Rebuild edit_projects with only projectId/createdAt/updatedAt
            """CREATE TABLE edit_projects_new (
                projectId TEXT NOT NULL PRIMARY KEY,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""",
            """INSERT INTO edit_projects_new(projectId, createdAt, updatedAt)
            SELECT projectId, createdAt, updatedAt FROM edit_projects""",
            "DROP TABLE edit_projects",
            "ALTER TABLE edit_projects_new RENAME TO edit_projects"
        )

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8_STATEMENTS.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE segments ADD COLUMN volumeScale REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE segments ADD COLUMN speedScale REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN frameWidth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN frameHeight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN backgroundColorHex TEXT NOT NULL DEFAULT '#000000'")
                // Backfill frame dimensions from each project's first VIDEO segment.
                db.execSQL(
                    """UPDATE edit_projects SET
                        frameWidth = COALESCE((
                            SELECT s.width FROM segments s
                            WHERE s.projectId = edit_projects.projectId AND s.type = 'VIDEO'
                            ORDER BY s.`order` ASC LIMIT 1
                        ), 0),
                        frameHeight = COALESCE((
                            SELECT s.height FROM segments s
                            WHERE s.projectId = edit_projects.projectId AND s.type = 'VIDEO'
                            ORDER BY s.`order` ASC LIMIT 1
                        ), 0)"""
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS text_overlays (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        fontFamily TEXT NOT NULL DEFAULT 'noto_sans_kr',
                        fontSizeSp REAL NOT NULL DEFAULT 24.0,
                        colorHex TEXT NOT NULL DEFAULT '#FFFFFFFF',
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        xPct REAL NOT NULL DEFAULT 50.0,
                        yPct REAL NOT NULL DEFAULT 50.0
                    )"""
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS bgm_clips (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        sourceUri TEXT NOT NULL,
                        sourceDurationMs INTEGER NOT NULL,
                        startMs INTEGER NOT NULL,
                        volumeScale REAL NOT NULL DEFAULT 1.0
                    )"""
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE image_clips ADD COLUMN lane INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE text_overlays ADD COLUMN lane INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE segments ADD COLUMN duplicatedFromId TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN videoScale REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN videoOffsetXPct REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE edit_projects ADD COLUMN videoOffsetYPct REAL NOT NULL DEFAULT 0.0")
            }
        }
    }
}
