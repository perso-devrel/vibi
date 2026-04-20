package com.example.dubcast.ui.timeline

import androidx.lifecycle.SavedStateHandle
import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.ImageInfo
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.repository.TtsResult
import com.example.dubcast.domain.usecase.image.AddImageClipUseCase
import com.example.dubcast.domain.usecase.image.DeleteImageClipUseCase
import com.example.dubcast.domain.usecase.image.UpdateImageClipUseCase
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.timeline.AddImageSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.AddVideoSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.DeleteDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.MoveDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.RemoveSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.SplitDubTextToSubtitlesUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.example.dubcast.domain.usecase.tts.GetVoiceListUseCase
import com.example.dubcast.domain.usecase.tts.SynthesizeDubClipUseCase
import com.example.dubcast.fake.FakeDubClipRepository
import com.example.dubcast.fake.FakeImageClipRepository
import com.example.dubcast.fake.FakeImageMetadataExtractor
import com.example.dubcast.fake.FakeSegmentRepository
import com.example.dubcast.fake.FakeSubtitleClipRepository
import com.example.dubcast.fake.FakeTtsRepository
import com.example.dubcast.fake.FakeVideoMetadataExtractor
import com.example.dubcast.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var dubRepo: FakeDubClipRepository
    private lateinit var subRepo: FakeSubtitleClipRepository
    private lateinit var imageRepo: FakeImageClipRepository
    private lateinit var segmentRepo: FakeSegmentRepository
    private lateinit var ttsRepo: FakeTtsRepository
    private lateinit var videoExtractor: FakeVideoMetadataExtractor
    private lateinit var imageExtractor: FakeImageMetadataExtractor
    private lateinit var vm: TimelineViewModel

    private val projectId = "proj-1"

    @Before
    fun setup() {
        dubRepo = FakeDubClipRepository()
        subRepo = FakeSubtitleClipRepository()
        imageRepo = FakeImageClipRepository()
        segmentRepo = FakeSegmentRepository()
        ttsRepo = FakeTtsRepository()
        videoExtractor = FakeVideoMetadataExtractor()
        imageExtractor = FakeImageMetadataExtractor()
        vm = TimelineViewModel(
            savedStateHandle = SavedStateHandle(mapOf("projectId" to projectId)),
            segmentRepository = segmentRepo,
            dubClipRepository = dubRepo,
            subtitleClipRepository = subRepo,
            imageClipRepository = imageRepo,
            ttsRepository = ttsRepo,
            synthesizeDubClip = SynthesizeDubClipUseCase(ttsRepo, dubRepo),
            getVoiceList = GetVoiceListUseCase(ttsRepo),
            moveDubClip = MoveDubClipUseCase(dubRepo),
            deleteDubClip = DeleteDubClipUseCase(dubRepo),
            addSubtitleClip = AddSubtitleClipUseCase(subRepo),
            deleteSubtitleClip = DeleteSubtitleClipUseCase(subRepo),
            splitDubTextToSubtitles = SplitDubTextToSubtitlesUseCase(),
            addImageClip = AddImageClipUseCase(imageRepo),
            updateImageClip = UpdateImageClipUseCase(imageRepo),
            deleteImageClip = DeleteImageClipUseCase(imageRepo),
            updateSegmentTrim = UpdateSegmentTrimUseCase(segmentRepo),
            addVideoSegment = AddVideoSegmentUseCase(segmentRepo),
            addImageSegment = AddImageSegmentUseCase(segmentRepo),
            removeSegment = RemoveSegmentUseCase(segmentRepo),
            updateImageSegmentDuration = UpdateImageSegmentDurationUseCase(segmentRepo),
            updateImageSegmentPosition = UpdateImageSegmentPositionUseCase(segmentRepo),
            videoMetadataExtractor = videoExtractor,
            imageMetadataExtractor = imageExtractor
        )
    }

    private suspend fun seedSegment(
        id: String = "seg-1",
        durationMs: Long = 60_000L
    ): Segment {
        val segment = Segment(
            id = id,
            projectId = projectId,
            type = SegmentType.VIDEO,
            order = 0,
            sourceUri = "content://video.mp4",
            durationMs = durationMs,
            width = 1920,
            height = 1080
        )
        segmentRepo.addSegment(segment)
        return segment
    }

    @Test
    fun `showOnScreen true creates auto subtitles for each line`() = runTest {
        ttsRepo.synthesizeResult = Result.success(TtsResult("/fake/audio.mp3", 6000L))

        vm.onSynthesize("Line one\nLine two", "voice-1", "Rachel")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.previewClip)

        vm.onInsertPreviewClip(showOnScreen = true)
        advanceUntilIdle()

        val subs = subRepo.observeClips(projectId).first()
        assertEquals(2, subs.size)
        assertTrue(subs.all { it.isAuto })
        assertTrue(subs.all { it.sourceDubClipId != null })
    }

    @Test
    fun `showOnScreen false creates no subtitles`() = runTest {
        ttsRepo.synthesizeResult = Result.success(TtsResult("/fake/audio.mp3", 3000L))

        vm.onSynthesize("Some text", "voice-1", "Rachel")
        advanceUntilIdle()

        vm.onInsertPreviewClip(showOnScreen = false)
        advanceUntilIdle()

        val subs = subRepo.observeClips(projectId).first()
        assertEquals(0, subs.size)
    }

    @Test
    fun `appending a video segment extends total duration`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        videoExtractor.result = VideoInfo(
            uri = "content://new.mp4",
            fileName = "new.mp4",
            mimeType = "video/mp4",
            durationMs = 5_000L,
            width = 1280,
            height = 720,
            sizeBytes = 0L
        )
        vm.onAppendVideoSegment("content://new.mp4")
        advanceUntilIdle()

        val segments = segmentRepo.getByProjectId(projectId)
        assertEquals(2, segments.size)
        assertEquals(SegmentType.VIDEO, segments[1].type)
        assertEquals(1, segments[1].order)
        assertEquals(15_000L, vm.uiState.value.videoDurationMs)
    }

    @Test
    fun `appending an image segment adds a 3-second clip`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        imageExtractor.result = ImageInfo(uri = "content://pic.jpg", width = 800, height = 600)
        vm.onAppendImageSegment("content://pic.jpg")
        advanceUntilIdle()

        val segments = segmentRepo.getByProjectId(projectId)
        assertEquals(2, segments.size)
        assertEquals(SegmentType.IMAGE, segments[1].type)
        assertEquals(3_000L, segments[1].durationMs)
        assertEquals(13_000L, vm.uiState.value.videoDurationMs)
    }

    @Test
    fun `onDeleteSelectedSegment removes selected segment and compacts order`() = runTest {
        seedSegment(id = "seg-1", durationMs = 10_000L)
        segmentRepo.addSegment(
            Segment(
                id = "seg-2",
                projectId = projectId,
                type = SegmentType.VIDEO,
                order = 1,
                sourceUri = "content://b.mp4",
                durationMs = 5_000L,
                width = 1280,
                height = 720
            )
        )
        advanceUntilIdle()

        vm.onSelectSegment("seg-2")
        vm.onDeleteSelectedSegment()
        advanceUntilIdle()

        val segments = segmentRepo.getByProjectId(projectId)
        assertEquals(1, segments.size)
        assertEquals("seg-1", segments[0].id)
    }

    @Test
    fun `onDeleteSelectedSegment refuses to delete the only remaining segment`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onSelectSegment("seg-1")
        vm.onDeleteSelectedSegment()
        advanceUntilIdle()

        assertEquals(1, segmentRepo.getByProjectId(projectId).size)
    }

    @Test
    fun `segment-driven video metadata populates UI state`() = runTest {
        seedSegment(durationMs = 45_000L)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(45_000L, state.videoDurationMs)
        assertEquals(1920, state.videoWidth)
        assertEquals(1080, state.videoHeight)
        assertEquals("content://video.mp4", state.videoUri)
        assertEquals("seg-1", state.selectedSegmentId)
    }

    @Test
    fun `onConfirmTrim persists trim to the selected segment`() = runTest {
        seedSegment(durationMs = 20_000L)
        advanceUntilIdle()

        vm.onEnterTrimMode()
        vm.onSetPendingTrimStart(2_000L)
        vm.onSetPendingTrimEnd(15_000L)
        vm.onConfirmTrim()
        advanceUntilIdle()

        val updated = segmentRepo.getSegment("seg-1")!!
        assertEquals(2_000L, updated.trimStartMs)
        assertEquals(15_000L, updated.trimEndMs)
    }

    @Test
    fun `onInsertImage persists clip with default 3 second duration`() = runTest {
        vm.onUpdatePlaybackPosition(2000L)
        vm.onInsertImage("content://sample.jpg")
        advanceUntilIdle()

        val clips = imageRepo.observeClips(projectId).first()
        assertEquals(1, clips.size)
        assertEquals(2000L, clips.first().startMs)
        assertEquals(5000L, clips.first().endMs)
    }

    @Test
    fun `onUpdateImageClipPosition updates coordinates`() = runTest {
        vm.onInsertImage("content://sample.jpg")
        advanceUntilIdle()

        val clip = imageRepo.observeClips(projectId).first().first()
        vm.onUpdateImageClipPosition(clip.id, xPct = 25f, yPct = 75f, widthPct = 40f, heightPct = 40f)
        advanceUntilIdle()

        val updated = imageRepo.observeClips(projectId).first().first()
        assertEquals(25f, updated.xPct)
        assertEquals(75f, updated.yPct)
        assertEquals(40f, updated.widthPct)
    }

    @Test
    fun `onDeleteSelectedClip deletes selected image clip`() = runTest {
        vm.onInsertImage("content://sample.jpg")
        advanceUntilIdle()
        val clip = imageRepo.observeClips(projectId).first().first()

        vm.onSelectImageClip(clip.id)
        vm.onDeleteSelectedClip()
        advanceUntilIdle()

        assertEquals(0, imageRepo.observeClips(projectId).first().size)
    }

    @Test
    fun `deleting dub clip removes its auto subtitles but keeps manual subtitle`() = runTest {
        // Pre-existing manual subtitle
        val manualSub = SubtitleClip(
            id = "manual-1",
            projectId = projectId,
            text = "Manual caption",
            startMs = 0L,
            endMs = 1000L,
            position = SubtitlePosition(anchor = Anchor.BOTTOM, yOffsetPct = 90f)
        )
        subRepo.addClip(manualSub)

        // Synthesize a 2-line dub with showOnScreen=true
        ttsRepo.synthesizeResult = Result.success(TtsResult("/fake/audio.mp3", 6000L))
        vm.onSynthesize("Line one\nLine two", "voice-1", "Rachel")
        advanceUntilIdle()

        vm.onInsertPreviewClip(showOnScreen = true)
        advanceUntilIdle()

        // Verify 2 auto-subtitles were created
        val subsAfterInsert = subRepo.observeClips(projectId).first()
        assertEquals(3, subsAfterInsert.size)  // 1 manual + 2 auto

        // Get the dub clip ID
        val dubs = dubRepo.observeClips(projectId).first()
        assertEquals(1, dubs.size)
        val dubClipId = dubs.first().id

        // Select and delete the dub clip
        vm.onSelectDubClip(dubClipId)
        vm.onDeleteSelectedClip()
        advanceUntilIdle()

        // Only manual subtitle should remain
        val subsAfterDelete = subRepo.observeClips(projectId).first()
        assertEquals(1, subsAfterDelete.size)
        assertEquals("manual-1", subsAfterDelete.first().id)
    }
}
