package com.dubcast.shared.domain.usecase.separation

import com.dubcast.shared.domain.model.SeparationMediaType
import com.dubcast.shared.domain.repository.AudioSeparationRepository

class StartAudioSeparationUseCase constructor(
    private val repository: AudioSeparationRepository
) {
    /**
     * @param editedRenderJobId non-null 이면 BFF 가 본 jobId 의 render output 을 source 로 사용 →
     *   multipart `file` part 자체를 송신하지 않음. ViewModel 이 프로젝트 segment 분리 흐름에서만
     *   주입; BGM (단독 audio 파일) 분리 흐름은 null.
     */
    suspend operator fun invoke(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String = "auto",
        trimStartMs: Long? = null,
        trimEndMs: Long? = null,
        editedRenderJobId: String? = null,
    ): Result<String> {
        if (numberOfSpeakers !in 1..10) {
            return Result.failure(IllegalArgumentException("numberOfSpeakers must be in 1..10"))
        }
        if (trimStartMs != null || trimEndMs != null) {
            if (trimStartMs == null || trimEndMs == null) {
                return Result.failure(IllegalArgumentException("trimStartMs and trimEndMs must be set together"))
            }
            if (trimStartMs < 0L || trimEndMs <= trimStartMs) {
                return Result.failure(IllegalArgumentException("invalid trim range"))
            }
        }
        // editedRenderJobId 가 있으면 BFF 의 audio-only render 결과 (m4a) 를 source 로 쓰므로
        // mediaType 을 AUDIO 로 강제. BFF 분리 서비스가 mediaType 기준으로 `-vn` 추출을 분기하므로
        // 일치 필수. caller (TimelineViewModel) 가 영상 segment 흐름에서 VIDEO 로 호출해도 자동 보정.
        val effectiveMediaType = if (editedRenderJobId != null) SeparationMediaType.AUDIO else mediaType
        return repository.startSeparation(
            sourceUri = sourceUri,
            mediaType = effectiveMediaType,
            numberOfSpeakers = numberOfSpeakers,
            sourceLanguageCode = sourceLanguageCode,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            editedRenderJobId = editedRenderJobId,
        )
    }
}
