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
    /** 앵커 세그먼트 id. 빈 문자열 = legacy/미앵커 (글로벌 range 를 그대로 사용). */
    val segmentId: String = "",
    /** 앵커 세그먼트 source(trim) 좌표 내 분리 구간. segmentId 비면 무의미. */
    val localStartMs: Long = 0L,
    val localEndMs: Long = 0L,
)
