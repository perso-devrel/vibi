package com.dubcast.shared.domain.model

import com.dubcast.shared.domain.repository.StemSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentTest {
    @Test
    fun `video with trimEndMs zero falls back to durationMs`() {
        val segment = Segment(
            id = "s1",
            projectId = "p1",
            type = SegmentType.VIDEO,
            order = 0,
            sourceUri = "content://video",
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            trimStartMs = 1_000L,
            trimEndMs = 0L
        )
        assertEquals(10_000L, segment.effectiveTrimEndMs)
        assertEquals(9_000L, segment.effectiveDurationMs)
    }

    @Test
    fun `image segment uses durationMs regardless of trim`() {
        val segment = Segment(
            id = "s1",
            projectId = "p1",
            type = SegmentType.IMAGE,
            order = 0,
            sourceUri = "content://image",
            durationMs = 3_000L,
            width = 1920,
            height = 1080
        )
        assertEquals(3_000L, segment.effectiveDurationMs)
    }

    private fun pristineProject(projectId: String = "p1") = EditProject(
        projectId = projectId,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun pristineSegment(projectId: String = "p1") = Segment(
        id = "s1",
        projectId = projectId,
        type = SegmentType.VIDEO,
        order = 0,
        sourceUri = "content://video",
        durationMs = 10_000L,
        width = 1920,
        height = 1080,
    )

    @Test
    fun `isProjectEdited false when single segment all defaults`() {
        assertFalse(
            isProjectEdited(
                project = pristineProject(),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited true when bgm clip added`() {
        val bgm = BgmClip(
            id = "b1",
            projectId = "p1",
            sourceUri = "content://bgm",
            sourceDurationMs = 10_000L,
            startMs = 0L,
        )
        assertTrue(
            isProjectEdited(
                project = pristineProject(),
                segments = listOf(pristineSegment()),
                bgmClips = listOf(bgm),
            )
        )
    }

    @Test
    fun `isProjectEdited true when text overlay added`() {
        val overlay = TextOverlay(
            id = "t1",
            projectId = "p1",
            text = "hi",
            startMs = 0L,
            endMs = 1_000L,
        )
        assertTrue(
            isProjectEdited(
                project = pristineProject(),
                segments = listOf(pristineSegment()),
                textOverlays = listOf(overlay),
            )
        )
    }

    @Test
    fun `isProjectEdited true when image overlay added`() {
        val image = ImageClip(
            id = "i1",
            projectId = "p1",
            imageUri = "content://image",
            startMs = 0L,
            endMs = 1_000L,
        )
        assertTrue(
            isProjectEdited(
                project = pristineProject(),
                segments = listOf(pristineSegment()),
                imageClips = listOf(image),
            )
        )
    }

    @Test
    fun `isProjectEdited true when separation directive added`() {
        val directive = SeparationDirective(
            id = "d1",
            projectId = "p1",
            rangeStartMs = 0L,
            rangeEndMs = 1_000L,
            numberOfSpeakers = 2,
            muteOriginalSegmentAudio = true,
            selections = listOf(
                StemSelection(stemId = "s", volume = 1f, audioUrl = "u"),
            ),
            createdAt = 0L,
        )
        assertTrue(
            isProjectEdited(
                project = pristineProject(),
                segments = listOf(pristineSegment()),
                separationDirectives = listOf(directive),
            )
        )
    }

    @Test
    fun `isProjectEdited true when frame width set`() {
        assertTrue(
            isProjectEdited(
                project = pristineProject().copy(frameWidth = 1080),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited false when frame matches first segment native size`() {
        // CreateProjectWithInitialVideoSegmentUseCase 시뮬레이션 — 신규 프로젝트는
        // 첫 segment 의 native size 가 그대로 frameWidth/Height 로 영속화됨. 사용자가
        // 명시적으로 frame 을 바꾼 게 아니므로 default 로 인정 → false.
        assertFalse(
            isProjectEdited(
                project = pristineProject().copy(frameWidth = 1920, frameHeight = 1080),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited true when frame differs from first segment native size`() {
        // 사용자가 frame 을 1280x720 으로 바꿨고 segment 는 1920x1080 — 명시적 편집 → true.
        assertTrue(
            isProjectEdited(
                project = pristineProject().copy(frameWidth = 1280, frameHeight = 720),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited false when frame unset sentinel and segments present`() {
        // legacy 미설정 sentinel (0/0) — 첫 segment 의 native size 사용. default 로 인정 → false.
        assertFalse(
            isProjectEdited(
                project = pristineProject().copy(frameWidth = 0, frameHeight = 0),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited true when video scale changed`() {
        assertTrue(
            isProjectEdited(
                project = pristineProject().copy(videoScale = 1.5f),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited true when background color changed`() {
        assertTrue(
            isProjectEdited(
                project = pristineProject().copy(backgroundColorHex = "#FFFFFF"),
                segments = listOf(pristineSegment()),
            )
        )
    }

    @Test
    fun `isProjectEdited true when video offset changed`() {
        assertTrue(
            isProjectEdited(
                project = pristineProject().copy(videoOffsetXPct = 5f),
                segments = listOf(pristineSegment()),
            )
        )
    }
}
