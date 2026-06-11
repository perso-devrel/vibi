package com.vibi.shared.domain.repository

import com.vibi.shared.domain.model.SeparationCost
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.platform.AudioSourceKind

sealed class SeparationStatus {
    abstract val jobId: String

    data class Processing(
        override val jobId: String,
        val progress: Int,
        val progressReason: String?
    ) : SeparationStatus()

    data class Ready(
        override val jobId: String,
        val stems: List<Stem>,
        /**
         * BFF 가 측정한 stem FLAC 실측 길이(ms). TimelineViewModel 이 SeparationDirective 의
         * rangeEndMs 를 사용자 선택값 대신 이 값으로 보정해 UI 막대 길이와 stem 길이를 1:1
         * 매칭. null 이면 측정 누락 → 클라이언트가 사용자 선택값 fallback.
         */
        val actualDurationMs: Long? = null
    ) : SeparationStatus()

    data class Failed(
        override val jobId: String,
        val progressReason: String?
    ) : SeparationStatus()
}

data class StemSelection(
    val stemId: String,
    val volume: Float = 1.0f,
    /**
     * 분리된 stem 의 절대 다운로드 URL. directive 영속화 후 export render 가
     * 이 URL 들을 받아 amix 합성하기 때문에 stemId 만으론 부족.
     */
    val audioUrl: String? = null,
    /**
     * 사용자가 mix 에 포함시킬지 여부. false 라도 directive 에 보존돼 사용자가 sheet 재진입 시
     * 다시 토글 가능. preview/render 양쪽에서 false 인 stem 은 음 미포함 (preview 는 volume 0,
     * render 는 사전 필터). 기본 true — 신규 분리/legacy 데이터의 backward compat.
     */
    val selected: Boolean = true,
    /**
     * 분리 완료 직후 (아직 온라인) [audioUrl] 을 영구 디렉터리에 받아둔 로컬 파일 절대 경로.
     * 존재하면 preview/mixer 가 원격 URL 대신 이 파일을 재생 — 서버 연결이 끊겨도, tokenized URL
     * 이 만료돼도 볼륨/복제/삭제/재생이 동작. null 이면 아직 미캐시 (online 스트리밍 fallback).
     * export render 는 여전히 서버가 [audioUrl] 로 합성하므로 URL 은 별도 보존.
     */
    val localPath: String? = null
)

interface AudioSeparationRepository {
    /**
     * 분리 시작 전 비용 견적. AudioSeparationSheet 가 SETUP 단계에 표시 + Start 버튼 분기.
     * BFF 권위 잔액도 같이 받아 [com.vibi.shared.data.local.CreditStore] 동기화 가능 (구현에 위임).
     */
    suspend fun getCost(durationMs: Long): Result<SeparationCost>

    /**
     * 분리 시작. 모바일이 trim + audio extract 까지 끝낸 audio 를 BFF 로 송신.
     *
     * @param sourceUri 영상/오디오 source URI
     * @param sourceKind 입력 종류 (VIDEO / AUDIO_COMPATIBLE / AUDIO_INCOMPATIBLE)
     * @param trimStartMs / [trimEndMs] trim 윈도우. null/null 이면 전체 구간.
     */
    suspend fun startSeparation(
        sourceUri: String,
        sourceKind: AudioSourceKind,
        sourceLanguageCode: String = "auto",
        trimStartMs: Long? = null,
        trimEndMs: Long? = null,
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<SeparationStatus>

    suspend fun downloadStem(stemUrl: String, outputFileName: String): Result<String>
}
