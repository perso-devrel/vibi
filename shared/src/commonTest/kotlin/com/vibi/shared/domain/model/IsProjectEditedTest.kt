package com.vibi.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SaveAllVariantsUseCase 의 bypass 분기 정확성을 담보하는 도메인 SSOT.
 * 여기서 회귀하면 무편집 영상이 BFF render 로 들어가거나, 편집된 영상이 원본 그대로 저장됨.
 */
class IsProjectEditedTest {

    private fun seg(
        durationMs: Long = 10_000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        volumeScale: Float = 1.0f,
        speedScale: Float = 1.0f,
        width: Int = 1920,
        height: Int = 1080,
        type: SegmentType = SegmentType.VIDEO,
    ) = Segment(
        id = "s", projectId = "p", type = type, order = 0,
        sourceUri = "file:///v.mp4", durationMs = durationMs,
        width = width, height = height,
        trimStartMs = trimStartMs, trimEndMs = trimEndMs,
        volumeScale = volumeScale, speedScale = speedScale,
    )

    private fun project(
        frameWidth: Int = 0, frameHeight: Int = 0,
        backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
        videoScale: Float = EditProject.DEFAULT_VIDEO_SCALE,
        videoOffsetXPct: Float = 0f, videoOffsetYPct: Float = 0f,
    ) = EditProject(
        projectId = "p", createdAt = 0L, updatedAt = 0L,
        frameWidth = frameWidth, frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct, videoOffsetYPct = videoOffsetYPct,
    )

    @Test fun `default project + 단일 VIDEO 는 무편집`() {
        assertFalse(isProjectEdited(project(), listOf(seg())))
    }

    @Test fun `frame native size 매칭은 무편집으로 인정`() {
        val s = seg(width = 1920, height = 1080)
        assertFalse(isProjectEdited(project(frameWidth = 1920, frameHeight = 1080), listOf(s)))
    }

    @Test fun `frame 다른 size 면 편집`() {
        val s = seg(width = 1920, height = 1080)
        assertTrue(isProjectEdited(project(frameWidth = 1080, frameHeight = 1920), listOf(s)))
    }

    @Test fun `trimEndMs equals durationMs 는 untrimmed sentinel`() {
        val s = seg(durationMs = 10_000L, trimEndMs = 10_000L)
        assertFalse(s.hasNonTrivialEdits(), "trimEndMs == durationMs 미트림")
        assertFalse(isProjectEdited(project(), listOf(s)))
    }

    @Test fun `trimEndMs 가 durationMs 보다 짧으면 편집`() {
        val s = seg(durationMs = 10_000L, trimEndMs = 5_000L)
        assertTrue(s.hasNonTrivialEdits())
    }

    @Test fun `trimStartMs 0 이상이면 편집`() {
        val s = seg(trimStartMs = 1_000L)
        assertTrue(s.hasNonTrivialEdits())
    }

    @Test fun `slider tolerance — volumeScale 0_99999는 무편집`() {
        val s = seg(volumeScale = 0.99999f)
        assertFalse(s.hasNonTrivialEdits(), "slider quantize 오차 흡수")
    }

    @Test fun `slider tolerance — volumeScale 0_99는 편집 사용자 의도`() {
        val s = seg(volumeScale = 0.99f)
        assertTrue(s.hasNonTrivialEdits(), "0.01 차이는 사용자 의도로 간주")
    }

    @Test fun `slider tolerance — speedScale 1_00001는 무편집`() {
        val s = seg(speedScale = 1.00001f)
        assertFalse(s.hasNonTrivialEdits())
    }

    @Test fun `slider tolerance — videoScale 1_00005는 무편집`() {
        // EDIT_TOLERANCE = 1e-4 = 0.0001. 1.00005 - 1 = 5e-5 < 1e-4.
        val p = project(videoScale = 1.00005f)
        assertFalse(isProjectEdited(p, listOf(seg())))
    }

    @Test fun `videoOffsetXPct 1_0 이면 편집`() {
        val p = project(videoOffsetXPct = 1.0f)
        assertTrue(isProjectEdited(p, listOf(seg())))
    }

    @Test fun `다중 segment 는 편집`() {
        assertTrue(isProjectEdited(project(), listOf(seg(), seg().copy(id = "s2", order = 1))))
    }

    @Test fun `BGM 있으면 편집`() {
        val bgm = BgmClip(
            id = "b", projectId = "p", sourceUri = "/x.mp3",
            startMs = 0L, sourceDurationMs = 5_000L, lane = 0,
        )
        assertTrue(isProjectEdited(project(), listOf(seg()), bgmClips = listOf(bgm)))
    }

    @Test fun `separation directive 있으면 편집`() {
        val d = SeparationDirective(
            id = "d", projectId = "p",
            rangeStartMs = 0L, rangeEndMs = 1_000L,
            numberOfSpeakers = 1, muteOriginalSegmentAudio = true,
            selections = emptyList(), createdAt = 0L,
        )
        assertTrue(isProjectEdited(project(), listOf(seg()), separationDirectives = listOf(d)))
    }

    @Test fun `backgroundColorHex 변경은 편집`() {
        val p = project(backgroundColorHex = "#FFFFFF")
        assertTrue(isProjectEdited(p, listOf(seg())))
    }

    @Test fun `IMAGE segment 의 volume_speed 는 검사 안 함`() {
        // IMAGE 는 trim/volume/speed 자체가 무의미하므로 volumeScale=0.5 라도 무편집.
        val s = seg(type = SegmentType.IMAGE, volumeScale = 0.5f, speedScale = 2.0f)
        assertFalse(s.hasNonTrivialEdits())
    }
}
