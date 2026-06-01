package com.vibi.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "separation_directives")
data class SeparationDirectiveEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    /** JSON encoded `List<{stemId, volume, audioUrl?}>`. */
    val selectionsJson: String,
    val createdAt: Long,
    /** 이 directive 를 만든 BFF 분리 잡 id. 같은 잡 중복 commit dedup 키. legacy row 는 null. */
    val jobId: String? = null,
    /** Stem audio 파일 안의 시작 offset (ms). split piece 가 stem 의 중간부터 재생할 때 사용. */
    val sourceOffsetMs: Long = 0L,
)
