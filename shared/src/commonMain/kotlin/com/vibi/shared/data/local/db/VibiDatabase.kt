package com.vibi.shared.data.local.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.vibi.shared.data.local.db.dao.BgmClipDao
import com.vibi.shared.data.local.db.dao.DubClipDao
import com.vibi.shared.data.local.db.dao.EditProjectDao
import com.vibi.shared.data.local.db.dao.ImageClipDao
import com.vibi.shared.data.local.db.dao.SegmentDao
import com.vibi.shared.data.local.db.dao.SeparationDirectiveDao
import com.vibi.shared.data.local.db.dao.SubtitleClipDao
import com.vibi.shared.data.local.db.dao.TextOverlayDao
import com.vibi.shared.data.local.db.entity.BgmClipEntity
import com.vibi.shared.data.local.db.entity.DubClipEntity
import com.vibi.shared.data.local.db.entity.EditProjectEntity
import com.vibi.shared.data.local.db.entity.ImageClipEntity
import com.vibi.shared.data.local.db.entity.SegmentEntity
import com.vibi.shared.data.local.db.entity.SeparationDirectiveEntity
import com.vibi.shared.data.local.db.entity.SubtitleClipEntity
import com.vibi.shared.data.local.db.entity.TextOverlayEntity

@Database(
    entities = [
        EditProjectEntity::class,
        DubClipEntity::class,
        SubtitleClipEntity::class,
        ImageClipEntity::class,
        SegmentEntity::class,
        TextOverlayEntity::class,
        BgmClipEntity::class,
        SeparationDirectiveEntity::class
    ],
    version = 3,
    exportSchema = true
)
@ConstructedBy(VibiDatabaseConstructor::class)
abstract class VibiDatabase : RoomDatabase() {
    abstract fun editProjectDao(): EditProjectDao
    abstract fun dubClipDao(): DubClipDao
    abstract fun subtitleClipDao(): SubtitleClipDao
    abstract fun imageClipDao(): ImageClipDao
    abstract fun segmentDao(): SegmentDao
    abstract fun textOverlayDao(): TextOverlayDao
    abstract fun bgmClipDao(): BgmClipDao
    abstract fun separationDirectiveDao(): SeparationDirectiveDao

    companion object {
        const val DB_FILE_NAME = "vibi.db"
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object VibiDatabaseConstructor : RoomDatabaseConstructor<VibiDatabase> {
    override fun initialize(): VibiDatabase
}
