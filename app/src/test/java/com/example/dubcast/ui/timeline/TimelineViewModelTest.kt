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
import com.example.dubcast.domain.usecase.input.SetProjectFrameUseCase
import com.example.dubcast.domain.usecase.bgm.AddBgmClipUseCase
import com.example.dubcast.domain.usecase.bgm.DeleteBgmClipUseCase
import com.example.dubcast.domain.usecase.bgm.UpdateBgmClipUseCase
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.text.AddTextOverlayUseCase
import com.example.dubcast.domain.usecase.text.DeleteTextOverlayUseCase
import com.example.dubcast.domain.usecase.text.DuplicateTextOverlayUseCase
import com.example.dubcast.domain.usecase.text.UpdateTextOverlayUseCase
import com.example.dubcast.domain.usecase.timeline.AddImageSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.AddVideoSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.DeleteDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.DuplicateSegmentRangeUseCase
import com.example.dubcast.domain.usecase.timeline.MoveDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.RemoveSegmentRangeUseCase
import com.example.dubcast.domain.usecase.timeline.RemoveSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.SplitDubTextToSubtitlesUseCase
import com.example.dubcast.domain.usecase.timeline.SplitSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentSpeedUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentVolumeUseCase
import com.example.dubcast.domain.usecase.tts.GetVoiceListUseCase
import com.example.dubcast.domain.usecase.tts.SynthesizeDubClipUseCase
import com.example.dubcast.fake.FakeAudioMetadataExtractor
import com.example.dubcast.fake.FakeBgmClipRepository
import com.example.dubcast.fake.FakeDubClipRepository
import com.example.dubcast.fake.FakeEditProjectRepository
import com.example.dubcast.fake.FakeImageClipRepository
import com.example.dubcast.fake.FakeImageMetadataExtractor
import com.example.dubcast.fake.FakeSegmentRepository
import com.example.dubcast.fake.FakeSubtitleClipRepository
import com.example.dubcast.fake.FakeTextOverlayRepository
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
    private lateinit var projectRepo: FakeEditProjectRepository
    private lateinit var textOverlayRepo: FakeTextOverlayRepository
    private lateinit var bgmRepo: FakeBgmClipRepository
    private lateinit var ttsRepo: FakeTtsRepository
    private lateinit var videoExtractor: FakeVideoMetadataExtractor
    private lateinit var imageExtractor: FakeImageMetadataExtractor
    private lateinit var audioExtractor: FakeAudioMetadataExtractor
    private lateinit var vm: TimelineViewModel

    private val projectId = "proj-1"

    @Before
    fun setup() {
        dubRepo = FakeDubClipRepository()
        subRepo = FakeSubtitleClipRepository()
        imageRepo = FakeImageClipRepository()
        segmentRepo = FakeSegmentRepository()
        projectRepo = FakeEditProjectRepository(segmentRepo)
        textOverlayRepo = FakeTextOverlayRepository()
        bgmRepo = FakeBgmClipRepository()
        ttsRepo = FakeTtsRepository()
        videoExtractor = FakeVideoMetadataExtractor()
        imageExtractor = FakeImageMetadataExtractor()
        audioExtractor = FakeAudioMetadataExtractor()
        vm = TimelineViewModel(
            savedStateHandle = SavedStateHandle(mapOf("projectId" to projectId)),
            segmentRepository = segmentRepo,
            dubClipRepository = dubRepo,
            subtitleClipRepository = subRepo,
            imageClipRepository = imageRepo,
            editProjectRepository = projectRepo,
            textOverlayRepository = textOverlayRepo,
            bgmClipRepository = bgmRepo,
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
            splitSegment = SplitSegmentUseCase(segmentRepo),
            duplicateSegmentRange = DuplicateSegmentRangeUseCase(segmentRepo, SplitSegmentUseCase(segmentRepo)),
            removeSegmentRange = RemoveSegmentRangeUseCase(segmentRepo, SplitSegmentUseCase(segmentRepo), RemoveSegmentUseCase(segmentRepo)),
            updateSegmentVolume = UpdateSegmentVolumeUseCase(segmentRepo),
            updateSegmentSpeed = UpdateSegmentSpeedUseCase(segmentRepo),
            setProjectFrame = SetProjectFrameUseCase(projectRepo),
            addTextOverlay = AddTextOverlayUseCase(textOverlayRepo),
            updateTextOverlay = UpdateTextOverlayUseCase(textOverlayRepo),
            deleteTextOverlay = DeleteTextOverlayUseCase(textOverlayRepo),
            duplicateTextOverlay = DuplicateTextOverlayUseCase(textOverlayRepo),
            addBgmClip = AddBgmClipUseCase(bgmRepo),
            updateBgmClip = UpdateBgmClipUseCase(bgmRepo),
            deleteBgmClip = DeleteBgmClipUseCase(bgmRepo),
            videoMetadataExtractor = videoExtractor,
            imageMetadataExtractor = imageExtractor,
            audioMetadataExtractor = audioExtractor
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

    @Test
    fun `onEnterRangeMode activates range state for VIDEO segment`() = runTest {
        seedSegment(id = "seg-1", durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        val s = vm.uiState.value
        assertTrue(s.isRangeSelecting)
        assertEquals("seg-1", s.rangeTargetSegmentId)
        assertEquals(0L, s.pendingRangeStartMs)
        assertEquals(1_000L, s.pendingRangeEndMs)
    }

    @Test
    fun `onEnterRangeMode is a no-op for IMAGE segment`() = runTest {
        segmentRepo.addSegment(
            Segment(
                id = "img-1",
                projectId = projectId,
                type = SegmentType.IMAGE,
                order = 0,
                sourceUri = "content://x",
                durationMs = 3_000L,
                width = 100,
                height = 100
            )
        )
        advanceUntilIdle()

        vm.onEnterRangeMode("img-1")
        assertTrue(!vm.uiState.value.isRangeSelecting)
    }

    @Test
    fun `onSetPendingRangeStart clamps to trim bounds and keeps 100ms gap`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        vm.onSetPendingRangeEnd(5_000L)
        vm.onSetPendingRangeStart(-2_000L) // below trimStart
        assertEquals(0L, vm.uiState.value.pendingRangeStartMs)

        vm.onSetPendingRangeStart(4_950L) // closer than 100ms to end
        assertEquals(4_900L, vm.uiState.value.pendingRangeStartMs)
    }

    @Test
    fun `onDuplicateRange creates a duplicate middle segment`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        vm.onSetPendingRangeEnd(7_000L)
        vm.onSetPendingRangeStart(3_000L)
        vm.onDuplicateRange()
        advanceUntilIdle()

        val segs = segmentRepo.getByProjectId(projectId)
        assertEquals(4, segs.size) // pre, middle, duplicate, post
        assertTrue(!vm.uiState.value.isRangeSelecting)
    }

    @Test
    fun `onDeleteRange removes middle piece`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        vm.onSetPendingRangeEnd(7_000L)
        vm.onSetPendingRangeStart(3_000L)
        vm.onDeleteRange()
        advanceUntilIdle()

        val segs = segmentRepo.getByProjectId(projectId)
        assertEquals(2, segs.size) // pre + post
    }

    @Test
    fun `onApplyRangeVolume sets volumeScale on middle split piece`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        vm.onSetPendingRangeEnd(6_000L)
        vm.onSetPendingRangeStart(2_000L)
        vm.onApplyRangeVolume(0.5f)
        advanceUntilIdle()

        val segs = segmentRepo.getByProjectId(projectId)
        val middle = segs.first { it.trimStartMs == 2_000L && it.trimEndMs == 6_000L }
        assertEquals(0.5f, middle.volumeScale)
    }

    @Test
    fun `onApplyRangeSpeed sets speedScale on middle split piece`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        vm.onSetPendingRangeEnd(6_000L)
        vm.onSetPendingRangeStart(2_000L)
        vm.onApplyRangeSpeed(2f)
        advanceUntilIdle()

        val segs = segmentRepo.getByProjectId(projectId)
        val middle = segs.first { it.trimStartMs == 2_000L && it.trimEndMs == 6_000L }
        assertEquals(2f, middle.speedScale)
    }

    @Test
    fun `onCancelRangeMode clears range state`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        assertTrue(vm.uiState.value.isRangeSelecting)
        vm.onCancelRangeMode()
        assertTrue(!vm.uiState.value.isRangeSelecting)
    }

    private suspend fun seedImageSegment(
        id: String = "img-1",
        durationMs: Long = 3_000L
    ): Segment {
        val segment = Segment(
            id = id,
            projectId = projectId,
            type = SegmentType.IMAGE,
            order = 0,
            sourceUri = "content://photo.jpg",
            durationMs = durationMs,
            width = 1024,
            height = 768
        )
        segmentRepo.addSegment(segment)
        return segment
    }

    @Test
    fun `onResizeImageSegmentByDrag clamps below 500ms to 500ms`() = runTest {
        seedImageSegment()
        advanceUntilIdle()

        vm.onResizeImageSegmentByDrag("img-1", 100L)
        advanceUntilIdle()

        assertEquals(500L, segmentRepo.getSegment("img-1")!!.durationMs)
    }

    @Test
    fun `onResizeImageSegmentByDrag clamps above 30000ms to 30000ms`() = runTest {
        seedImageSegment()
        advanceUntilIdle()

        vm.onResizeImageSegmentByDrag("img-1", 50_000L)
        advanceUntilIdle()

        assertEquals(30_000L, segmentRepo.getSegment("img-1")!!.durationMs)
    }

    @Test
    fun `onResizeImageSegmentByDrag updates within range`() = runTest {
        seedImageSegment()
        advanceUntilIdle()

        vm.onResizeImageSegmentByDrag("img-1", 4_500L)
        advanceUntilIdle()

        assertEquals(4_500L, segmentRepo.getSegment("img-1")!!.durationMs)
    }

    @Test
    fun `onResizeImageSegmentByDrag is a no-op for VIDEO segment`() = runTest {
        seedSegment(id = "vid-1", durationMs = 10_000L)
        advanceUntilIdle()

        vm.onResizeImageSegmentByDrag("vid-1", 5_000L)
        advanceUntilIdle()

        assertEquals(10_000L, segmentRepo.getSegment("vid-1")!!.durationMs)
    }

    private suspend fun seedProject(
        frameWidth: Int = 1920,
        frameHeight: Int = 1080
    ): com.example.dubcast.domain.model.EditProject {
        val project = com.example.dubcast.domain.model.EditProject(
            projectId = projectId,
            createdAt = 0L,
            updatedAt = 0L,
            frameWidth = frameWidth,
            frameHeight = frameHeight
        )
        projectRepo.createProject(project)
        return project
    }

    @Test
    fun `observes project frame on init`() = runTest {
        seedProject(frameWidth = 1080, frameHeight = 1920)
        advanceUntilIdle()

        assertEquals(1080, vm.uiState.value.frameWidth)
        assertEquals(1920, vm.uiState.value.frameHeight)
    }

    @Test
    fun `onShowFrameSheet pre-fills pending values from current frame`() = runTest {
        seedProject(frameWidth = 1280, frameHeight = 720)
        advanceUntilIdle()

        vm.onShowFrameSheet()
        val s = vm.uiState.value
        assertTrue(s.showFrameSheet)
        assertEquals("1280", s.pendingFrameWidth)
        assertEquals("720", s.pendingFrameHeight)
        assertEquals("#000000", s.pendingBackgroundColorHex)
    }

    @Test
    fun `onApplyFramePreset 9_16 fills portrait dimensions`() = runTest {
        seedProject(frameWidth = 1920, frameHeight = 1080)
        advanceUntilIdle()

        vm.onShowFrameSheet()
        vm.onApplyFramePreset(FramePreset.PORTRAIT_9_16)
        val s = vm.uiState.value
        val w = s.pendingFrameWidth.toInt()
        val h = s.pendingFrameHeight.toInt()
        assertTrue(h > w)
        // ratio 9:16 → w/h ≈ 0.5625
        val ratio = w.toFloat() / h.toFloat()
        assertTrue(kotlin.math.abs(ratio - 9f / 16f) < 0.01f)
    }

    @Test
    fun `onConfirmFrame persists frame and closes sheet`() = runTest {
        seedProject(frameWidth = 1920, frameHeight = 1080)
        advanceUntilIdle()

        vm.onShowFrameSheet()
        vm.onFrameWidthInputChanged("1080")
        vm.onFrameHeightInputChanged("1920")
        vm.onFrameBackgroundColorChanged("#FF00FF")
        vm.onConfirmFrame()
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.showFrameSheet)
        val updated = projectRepo.getProject(projectId)!!
        assertEquals(1080, updated.frameWidth)
        assertEquals(1920, updated.frameHeight)
        assertEquals("#FF00FF", updated.backgroundColorHex)
    }

    @Test
    fun `onConfirmFrame rejects malformed color and keeps sheet open with error`() = runTest {
        seedProject(frameWidth = 1920, frameHeight = 1080)
        advanceUntilIdle()

        vm.onShowFrameSheet()
        vm.onFrameWidthInputChanged("1080")
        vm.onFrameHeightInputChanged("1920")
        vm.onFrameBackgroundColorChanged("not-a-color")
        vm.onConfirmFrame()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.showFrameSheet)
        assertNotNull(s.frameError)
        // Project not updated
        assertEquals(1920, projectRepo.getProject(projectId)!!.frameWidth)
    }

    @Test
    fun `onConfirmFrame rejects non-positive dimensions`() = runTest {
        seedProject(frameWidth = 1920, frameHeight = 1080)
        advanceUntilIdle()

        vm.onShowFrameSheet()
        vm.onFrameWidthInputChanged("0")
        vm.onFrameHeightInputChanged("1920")
        vm.onConfirmFrame()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.showFrameSheet)
        assertNotNull(s.frameError)
    }

    @Test
    fun `onShowTextOverlaySheetForNew clears editing id and seeds time window from playhead`() = runTest {
        seedSegment(durationMs = 30_000L)
        advanceUntilIdle()
        vm.onUpdatePlaybackPosition(5_000L)

        vm.onShowTextOverlaySheetForNew()
        val s = vm.uiState.value
        assertTrue(s.showTextOverlaySheet)
        assertEquals(null, s.editingTextOverlayId)
        assertEquals(5_000L, s.pendingOverlayStartMs)
        assertEquals(8_000L, s.pendingOverlayEndMs)
        assertEquals("", s.pendingOverlayText)
    }

    @Test
    fun `onConfirmTextOverlay rejects blank text and keeps sheet open`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onShowTextOverlaySheetForNew()
        vm.onTextOverlayTextChanged("   ")
        vm.onConfirmTextOverlay()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.showTextOverlaySheet)
        assertNotNull(s.textOverlayError)
        assertTrue(textOverlayRepo.all().isEmpty())
    }

    @Test
    fun `onConfirmTextOverlay creates overlay when sheet is in new mode`() = runTest {
        seedSegment(durationMs = 10_000L)
        advanceUntilIdle()

        vm.onShowTextOverlaySheetForNew()
        vm.onTextOverlayTextChanged("Hello")
        vm.onTextOverlayFontFamilyChanged("noto_serif_kr")
        vm.onTextOverlayFontSizeChanged(40f)
        vm.onTextOverlayColorChanged("#FFAABBCC")
        vm.onConfirmTextOverlay()
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.showTextOverlaySheet)
        val all = textOverlayRepo.all()
        assertEquals(1, all.size)
        assertEquals("Hello", all[0].text)
        assertEquals("noto_serif_kr", all[0].fontFamily)
        assertEquals(40f, all[0].fontSizeSp)
        assertEquals("#FFAABBCC", all[0].colorHex)
    }

    @Test
    fun `onConfirmTextOverlay updates overlay when sheet is in edit mode`() = runTest {
        seedSegment(durationMs = 10_000L)
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "old",
                startMs = 0L, endMs = 1000L
            )
        )
        advanceUntilIdle()

        vm.onShowTextOverlaySheetForEdit("t1")
        vm.onTextOverlayTextChanged("new")
        vm.onConfirmTextOverlay()
        advanceUntilIdle()

        assertEquals("new", textOverlayRepo.getOverlay("t1")!!.text)
    }

    @Test
    fun `onDuplicateTextOverlay places duplicate after source`() = runTest {
        seedSegment(durationMs = 30_000L)
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "x",
                startMs = 1000L, endMs = 3000L
            )
        )
        advanceUntilIdle()

        vm.onDuplicateTextOverlay("t1")
        advanceUntilIdle()

        val all = textOverlayRepo.all().sortedBy { it.startMs }
        assertEquals(2, all.size)
        assertEquals(3000L, all[1].startMs)
        assertEquals(5000L, all[1].endMs)
    }

    @Test
    fun `onDeleteTextOverlay removes selected overlay and clears selection`() = runTest {
        seedSegment(durationMs = 10_000L)
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "x",
                startMs = 0L, endMs = 1000L
            )
        )
        advanceUntilIdle()
        vm.onSelectTextOverlay("t1")

        vm.onDeleteTextOverlay("t1")
        advanceUntilIdle()

        assertEquals(0, textOverlayRepo.all().size)
        assertEquals(null, vm.uiState.value.selectedTextOverlayId)
    }

    @Test
    fun `onPickBgmAudio extracts duration and adds clip at current playhead`() = runTest {
        seedSegment(durationMs = 30_000L)
        advanceUntilIdle()
        vm.onUpdatePlaybackPosition(7_500L)
        audioExtractor.nextInfo = com.example.dubcast.domain.usecase.input.AudioInfo(
            uri = "content://song.mp3", durationMs = 90_000L
        )

        vm.onPickBgmAudio("content://song.mp3")
        advanceUntilIdle()

        val all = bgmRepo.all()
        assertEquals(1, all.size)
        assertEquals("content://song.mp3", all[0].sourceUri)
        assertEquals(90_000L, all[0].sourceDurationMs)
        assertEquals(7_500L, all[0].startMs)
        assertEquals(false, vm.uiState.value.isAddingBgm)
    }

    @Test
    fun `onPickBgmAudio sets error when extractor returns null`() = runTest {
        seedSegment(durationMs = 30_000L)
        advanceUntilIdle()
        audioExtractor.nextInfo = null

        vm.onPickBgmAudio("content://broken.mp3")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.bgmError)
        assertEquals(0, bgmRepo.all().size)
    }

    @Test
    fun `onUpdateBgmStartMs persists clamped non-negative start`() = runTest {
        seedSegment(durationMs = 10_000L)
        bgmRepo.addClip(
            com.example.dubcast.domain.model.BgmClip(
                "b1", projectId, "content://x", 60_000L, 0L
            )
        )
        advanceUntilIdle()

        vm.onUpdateBgmStartMs("b1", -100L)
        advanceUntilIdle()

        assertEquals(0L, bgmRepo.getClip("b1")!!.startMs)
    }

    @Test
    fun `onUpdateBgmVolume clamps to allowed range`() = runTest {
        seedSegment(durationMs = 10_000L)
        bgmRepo.addClip(
            com.example.dubcast.domain.model.BgmClip(
                "b1", projectId, "content://x", 60_000L, 0L
            )
        )
        advanceUntilIdle()

        vm.onUpdateBgmVolume("b1", 5f)
        advanceUntilIdle()

        assertEquals(2f, bgmRepo.getClip("b1")!!.volumeScale)
    }

    @Test
    fun `onDeleteBgmClip removes clip and clears selection`() = runTest {
        seedSegment(durationMs = 10_000L)
        bgmRepo.addClip(
            com.example.dubcast.domain.model.BgmClip(
                "b1", projectId, "content://x", 60_000L, 0L
            )
        )
        advanceUntilIdle()
        vm.onSelectBgmClip("b1")

        vm.onDeleteBgmClip("b1")
        advanceUntilIdle()

        assertEquals(0, bgmRepo.all().size)
        assertEquals(null, vm.uiState.value.selectedBgmClipId)
    }

    @Test
    fun `undo restores frame after onConfirmFrame change`() = runTest {
        seedSegment(durationMs = 10_000L)
        seedProject(frameWidth = 1920, frameHeight = 1080)
        advanceUntilIdle()

        vm.onShowFrameSheet()
        vm.onFrameWidthInputChanged("1080")
        vm.onFrameHeightInputChanged("1920")
        vm.onFrameBackgroundColorChanged("#FF112233")
        vm.onConfirmFrame()
        advanceUntilIdle()

        // Confirm change took effect
        assertEquals(1080, projectRepo.getProject(projectId)!!.frameWidth)

        vm.onUndo()
        advanceUntilIdle()

        val restored = projectRepo.getProject(projectId)!!
        assertEquals(1920, restored.frameWidth)
        assertEquals(1080, restored.frameHeight)
        assertEquals("#000000", restored.backgroundColorHex)
    }

    @Test
    fun `undo removes added text overlay`() = runTest {
        seedSegment(durationMs = 10_000L)
        seedProject()
        advanceUntilIdle()

        vm.onShowTextOverlaySheetForNew()
        vm.onTextOverlayTextChanged("Hello")
        vm.onConfirmTextOverlay()
        advanceUntilIdle()
        assertEquals(1, textOverlayRepo.all().size)

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(0, textOverlayRepo.all().size)
    }

    @Test
    fun `undo removes added bgm clip`() = runTest {
        seedSegment(durationMs = 10_000L)
        seedProject()
        audioExtractor.nextInfo = com.example.dubcast.domain.usecase.input.AudioInfo(
            uri = "content://song.mp3", durationMs = 30_000L
        )
        advanceUntilIdle()

        vm.onPickBgmAudio("content://song.mp3")
        advanceUntilIdle()
        assertEquals(1, bgmRepo.all().size)

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(0, bgmRepo.all().size)
    }

    @Test
    fun `undo restores segment list after delete`() = runTest {
        seedSegment(id = "seg-a", durationMs = 5_000L)
        segmentRepo.addSegment(
            com.example.dubcast.domain.model.Segment(
                id = "seg-b",
                projectId = projectId,
                type = SegmentType.VIDEO,
                order = 1,
                sourceUri = "content://b.mp4",
                durationMs = 5_000L,
                width = 1920,
                height = 1080
            )
        )
        seedProject()
        advanceUntilIdle()
        vm.onSelectSegment("seg-b")
        advanceUntilIdle()
        vm.onDeleteSelectedSegment()
        advanceUntilIdle()
        assertEquals(1, segmentRepo.getByProjectId(projectId).size)

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(2, segmentRepo.getByProjectId(projectId).size)
    }

    @Test
    fun `undo restores prior dub volume`() = runTest {
        seedSegment(durationMs = 10_000L)
        // Seed the clip before the project so the initial undo snapshot
        // (taken when observeProject first emits) includes it.
        dubRepo.addClip(
            com.example.dubcast.domain.model.DubClip(
                id = "d1",
                projectId = projectId,
                text = "hi",
                voiceId = "v1",
                voiceName = "v",
                audioFilePath = "/x.mp3",
                startMs = 0L,
                durationMs = 1000L,
                volume = 1f
            )
        )
        seedProject()
        advanceUntilIdle()

        vm.onUpdateDubClipVolume("d1", 0.3f)
        advanceUntilIdle()

        vm.onUndo()
        advanceUntilIdle()

        val first = dubRepo.observeClips(projectId).first().first()
        assertEquals(1f, first.volume)
    }
}
