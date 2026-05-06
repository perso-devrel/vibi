package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SeparationDirective
import com.dubcast.shared.domain.model.TextOverlay
import com.dubcast.shared.domain.usecase.render.RenderKind

/**
 * 자막/더빙/분리 흐름이 "편집 영상" 을 source 로 사용해야 할 때 BFF 에 render 잡을 1번 보내고
 * jobId 만 회수하기 위한 인터페이스. 결과 파일은 다운로드하지 않으며, BFF 가 디스크에 보관하고
 * `editedRenderJobId` 로 재참조한다.
 *
 * 본 인터페이스는 `SaveAllVariantsUseCase` 의 multi-variant 갤러리 저장 흐름과는 분리:
 * 본인은 ALWAYS 단일 jobId 1개만, 자막/dub/오버레이/오디오오버라이드 모두 제외 ("순수 편집 영상").
 */
interface RenderRepository {
    /**
     * 편집 영상 render 잡을 BFF 에 제출하고 COMPLETED 까지 폴링 후 jobId 반환.
     *
     * 빌더는 `subtitleClips / textOverlays / dubClips / audioOverridePath` 등 자막/더빙/오버레이
     * 관련 입력은 제외해서 "순수 편집 영상" 만 만든다 (자막/더빙은 BFF 측에서 본 jobId 위에 합성).
     *
     * @param kind RenderKind.AUDIO 면 audio-only m4a 출력 (자막/STT/분리용, 5–10x 빠름).
     *             RenderKind.VIDEO 면 풀 mp4 mux (자동 더빙용).
     */
    suspend fun submitForEditedSource(
        project: EditProject,
        segments: List<Segment>,
        imageClips: List<ImageClip>,
        bgmClips: List<BgmClip>,
        textOverlays: List<TextOverlay>,
        separationDirectives: List<SeparationDirective>,
        kind: RenderKind,
        onProgress: (percent: Int) -> Unit,
    ): Result<String>
}
