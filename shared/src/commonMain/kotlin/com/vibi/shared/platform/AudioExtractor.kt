package com.vibi.shared.platform

/**
 * 영상/오디오 source 에서 분리에 보낼 audio (m4a/mp3/wav) 를 준비.
 * 모바일이 BFF 로 보내기 전 trim + audio extract 까지 책임진다.
 *
 * - **VIDEO** 입력 → m4a (AAC) 추출 + trim. iOS `AVAssetExportPresetAppleM4A` 한 줄.
 *   재인코딩 없는 sample-copy 경로 (mp4 안의 AAC 트랙을 새 m4a container 로 mux).
 * - **AUDIO_COMPATIBLE** 입력 (m4a/mp3/wav) → 같은 포맷 유지 sample-copy trim. trim 없으면
 *   원본 path 그대로 (재인코딩 0회).
 * - **AUDIO_INCOMPATIBLE** 입력 (flac/ogg 등) → m4a (AAC 192k) 재인코딩.
 *
 * 실패는 [AudioExtractException] 의 sealed 변형으로 던져 UI 가 사용자 메시지로 매핑.
 */
interface AudioExtractor {
    /** Platform 별 분리 기능 지원 여부. UI 가 entry 버튼 disable + Repository / ViewModel
     * 진입 가드에 사용. iOS=true, Android=false (iOS 우선 출시). */
    val isSupported: Boolean

    /**
     * @param sourceUri 영상/오디오 source URI / 절대 경로
     * @param sourceKind caller 가 결정한 입력 종류
     * @param startMs trim 시작 (null 이면 처음부터)
     * @param endMs trim 끝 (null 이면 끝까지). null/null 이면 전체 구간.
     * @return BFF 로 송신 가능한 audio 파일 메타 (path + mimeType + ext)
     */
    suspend fun prepareSeparationAudio(
        sourceUri: String,
        sourceKind: AudioSourceKind,
        startMs: Long?,
        endMs: Long?,
    ): PreparedAudio
}

enum class AudioSourceKind {
    /** mp4/mov 등 영상. audio track 추출 + trim → m4a. */
    VIDEO,

    /** Perso 가 받는 audio (m4a/mp3/wav). trim 만 (필요 시) sample-copy. */
    AUDIO_COMPATIBLE,

    /** flac/ogg/aiff 등 Perso 비호환 audio. m4a 로 재인코딩. */
    AUDIO_INCOMPATIBLE,
}

data class PreparedAudio(
    val path: String,
    val mimeType: String,
    /** filename 의 확장자. BFF 가 화이트리스트 체크에 사용. */
    val ext: String,
)

sealed class AudioExtractException(message: String) : Exception(message) {
    object DiskFull : AudioExtractException("disk full")
    object CodecUnsupported : AudioExtractException("codec unsupported")
    object SourceCorrupt : AudioExtractException("source corrupt")
    object Cancelled : AudioExtractException("cancelled")
    data class Unknown(val reason: String) : AudioExtractException("unknown: $reason")
}
