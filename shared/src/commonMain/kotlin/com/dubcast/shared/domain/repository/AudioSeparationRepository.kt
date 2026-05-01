package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.SeparationMediaType
import com.dubcast.shared.domain.model.Stem

sealed class SeparationStatus {
    abstract val jobId: String

    data class Processing(
        override val jobId: String,
        val progress: Int,
        val progressReason: String?
    ) : SeparationStatus()

    data class Ready(
        override val jobId: String,
        val stems: List<Stem>
    ) : SeparationStatus()

    data class Consumed(
        override val jobId: String,
        val mixJobId: String
    ) : SeparationStatus()

    data class Failed(
        override val jobId: String,
        val progressReason: String?
    ) : SeparationStatus()
}

sealed class MixStatus {
    abstract val mixJobId: String

    data class Processing(
        override val mixJobId: String,
        val progress: Int
    ) : MixStatus()

    data class Completed(
        override val mixJobId: String,
        val downloadUrl: String
    ) : MixStatus()

    data class Failed(
        override val mixJobId: String
    ) : MixStatus()
}

data class StemSelection(
    val stemId: String,
    val volume: Float = 1.0f,
    /**
     * 분리된 stem 의 절대 다운로드 URL. directive 영속화 후 export render 가
     * 이 URL 들을 받아 amix 합성하기 때문에 stemId 만으론 부족.
     * legacy 경로(BFF mix mp3)에서는 비어 있을 수 있음.
     */
    val audioUrl: String? = null,
    /**
     * 사용자가 mix 에 포함시킬지 여부. false 라도 directive 에 보존돼 사용자가 sheet 재진입 시
     * 다시 토글 가능. preview/render 양쪽에서 false 인 stem 은 음 미포함 (preview 는 volume 0,
     * render 는 사전 필터). 기본 true — 신규 분리/legacy 데이터의 backward compat.
     */
    val selected: Boolean = true
)

interface AudioSeparationRepository {
    suspend fun startSeparation(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String = "auto",
        trimStartMs: Long? = null,
        trimEndMs: Long? = null
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<SeparationStatus>

    suspend fun downloadStem(stemUrl: String, outputFileName: String): Result<String>

    suspend fun requestMix(jobId: String, selections: List<StemSelection>): Result<String>

    suspend fun pollMixStatus(mixJobId: String): Result<MixStatus>

    suspend fun downloadMix(
        mixJobId: String,
        downloadUrl: String,
        outputFileName: String
    ): Result<String>
}
