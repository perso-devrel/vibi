package com.example.dubcast.ui.timeline

import androidx.lifecycle.SavedStateHandle
import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.ImageInfo
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.Stem
import com.example.dubcast.domain.model.StemKind
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.repository.MixStatus
import com.example.dubcast.domain.repository.SeparationStatus
import com.example.dubcast.domain.repository.TtsResult
import com.example.dubcast.domain.usecase.input.AudioInfo
import com.example.dubcast.domain.usecase.image.AddImageClipUseCase
import com.example.dubcast.domain.usecase.image.DeleteImageClipUseCase
import com.example.dubcast.domain.usecase.image.UpdateImageClipUseCase
import com.example.dubcast.domain.usecase.input.SetProjectFrameUseCase
import com.example.dubcast.domain.usecase.bgm.AddBgmClipUseCase
import com.example.dubcast.domain.usecase.bgm.DeleteBgmClipUseCase
import com.example.dubcast.domain.usecase.bgm.UpdateBgmClipUseCase
import com.example.dubcast.domain.usecase.separation.ApplyMixAsBgmUseCase
import com.example.dubcast.domain.usecase.separation.PollMixUseCase
import com.example.dubcast.domain.usecase.separation.PollSeparationUseCase
import com.example.dubcast.domain.usecase.separation.RequestStemMixUseCase
import com.example.dubcast.domain.usecase.separation.StartAudioSeparationUseCase
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.GenerateAutoDubUseCase
import com.example.dubcast.domain.usecase.subtitle.GenerateAutoSubtitlesUseCase
import com.example.dubcast.fake.FakeAutoDubRepository
import com.example.dubcast.fake.FakeAutoSubtitleRepository
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
import com.example.dubcast.domain.usecase.timeline.SplitSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentSpeedUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentVolumeUseCase
import com.example.dubcast.domain.usecase.tts.GetVoiceListUseCase
import com.example.dubcast.domain.usecase.tts.SynthesizeDubClipUseCase
import com.example.dubcast.fake.FakeAudioMetadataExtractor
import com.example.dubcast.fake.FakeAudioSeparationRepository
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
    private lateinit var separationRepo: FakeAudioSeparationRepository
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
        separationRepo = FakeAudioSeparationRepository()
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
            audioMetadataExtractor = audioExtractor,
            startAudioSeparation = StartAudioSeparationUseCase(separationRepo),
            pollSeparation = PollSeparationUseCase(separationRepo),
            requestStemMix = RequestStemMixUseCase(separationRepo),
            pollMix = PollMixUseCase(separationRepo),
            applyMixAsBgm = ApplyMixAsBgmUseCase(
                separationRepository = separationRepo,
                audioMetadataExtractor = audioExtractor,
                addBgmClipUseCase = AddBgmClipUseCase(bgmRepo)
            ),
            generateAutoSubtitles = GenerateAutoSubtitlesUseCase(
                FakeAutoSubtitleRepository(),
                subRepo,
                projectRepo
            ),
            generateAutoDub = GenerateAutoDubUseCase(
                FakeAutoDubRepository(),
                projectRepo
            )
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
    fun `onInsertPreviewClip drops dub clip and never creates subtitles`() = runTest {
        ttsRepo.synthesizeResult = Result.success(TtsResult("/fake/audio.mp3", 3000L))

        vm.onSynthesize("Some text", "voice-1", "Rachel")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.previewClip)

        vm.onInsertPreviewClip()
        advanceUntilIdle()

        assertEquals(1, dubRepo.observeClips(projectId).first().size)
        assertEquals(0, subRepo.observeClips(projectId).first().size)
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
        // Initial state is intentionally unselected — the user must tap a
        // segment to enter the inline edit panel. (See loadSegments.)
        assertEquals(null, state.selectedSegmentId)
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
    fun `onEnterRangeMode activates range state for VIDEO segment`() = runTest {
        seedSegment(id = "seg-1", durationMs = 10_000L)
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-1")
        val s = vm.uiState.value
        assertTrue(s.isRangeSelecting)
        assertEquals("seg-1", s.rangeTargetSegmentId)
        // Global timeline ms: single 10s segment starts at 0, ends at 10_000.
        assertEquals(0L, s.pendingRangeStartMs)
        assertEquals(10_000L, s.pendingRangeEndMs)
    }

    @Test
    fun `onEnterRangeMode on second segment uses its global timeline offset`() = runTest {
        // After a delete/duplicate, later segments live at non-zero global ms.
        // Tapping one should open the range handles over that clip, not at
        // the start of the timeline.
        seedSegment(id = "seg-1", durationMs = 10_000L)
        segmentRepo.addSegment(
            Segment(
                id = "seg-2",
                projectId = projectId,
                type = SegmentType.VIDEO,
                order = 1,
                sourceUri = "content://video2",
                durationMs = 5_000L,
                width = 1920,
                height = 1080
            )
        )
        advanceUntilIdle()

        vm.onEnterRangeMode("seg-2")
        val s = vm.uiState.value
        assertEquals("seg-2", s.rangeTargetSegmentId)
        assertEquals(10_000L, s.pendingRangeStartMs)
        assertEquals(15_000L, s.pendingRangeEndMs)
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
    fun `onDuplicateTextOverlay places duplicate at same time on next free lane`() = runTest {
        seedSegment(durationMs = 30_000L)
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "x",
                startMs = 1000L, endMs = 3000L, lane = 0
            )
        )
        advanceUntilIdle()

        vm.onDuplicateTextOverlay("t1")
        advanceUntilIdle()

        val all = textOverlayRepo.all()
        assertEquals(2, all.size)
        // Duplicate retains the original time window — auto-lane drops it on
        // the next free row instead of pushing it forward in time.
        val dup = all.first { it.id != "t1" }
        assertEquals(1000L, dup.startMs)
        assertEquals(3000L, dup.endMs)
        assertEquals(1, dup.lane)
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

    @Test
    fun `selecting an image clip clears every other selection`() = runTest {
        seedSegment(durationMs = 10_000L)
        imageRepo.addClip(
            com.example.dubcast.domain.model.ImageClip(
                id = "i1", projectId = projectId,
                imageUri = "content://x", startMs = 0L, endMs = 1_000L
            )
        )
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "x",
                startMs = 0L, endMs = 1_000L
            )
        )
        bgmRepo.addClip(
            com.example.dubcast.domain.model.BgmClip(
                id = "b1", projectId = projectId,
                sourceUri = "content://m.mp3", sourceDurationMs = 1_000L, startMs = 0L
            )
        )
        seedProject()
        advanceUntilIdle()
        vm.onSelectTextOverlay("t1")
        vm.onSelectBgmClip("b1")
        advanceUntilIdle()

        vm.onSelectImageClip("i1")
        val s = vm.uiState.value
        assertEquals("i1", s.selectedImageClipId)
        assertEquals(null, s.selectedTextOverlayId)
        assertEquals(null, s.selectedBgmClipId)
        assertEquals(null, s.selectedSegmentId)
        assertEquals(null, s.selectedDubClipId)
        assertEquals(null, s.selectedSubtitleClipId)
    }

    @Test
    fun `adding image lands on a lane already free of text overlay collisions`() = runTest {
        seedSegment(durationMs = 30_000L)
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "x",
                startMs = 0L, endMs = 2_000L, lane = 0
            )
        )
        seedProject()
        advanceUntilIdle()

        vm.onInsertImage("content://img.jpg", defaultDurationMs = 1_000L)
        advanceUntilIdle()

        val img = imageRepo.observeClips(projectId).first().first()
        assertEquals(1, img.lane)
    }

    @Test
    fun `onChangeImageClipLane clamps below zero`() = runTest {
        seedSegment(durationMs = 10_000L)
        imageRepo.addClip(
            com.example.dubcast.domain.model.ImageClip(
                id = "i1", projectId = projectId,
                imageUri = "content://x", startMs = 0L, endMs = 1_000L, lane = 0
            )
        )
        seedProject()
        advanceUntilIdle()

        vm.onChangeImageClipLane("i1", -3)
        advanceUntilIdle()

        assertEquals(0, imageRepo.observeClips(projectId).first().first().lane)
    }

    @Test
    fun `onDuplicateImageClip places copy at same time on next free lane`() = runTest {
        seedSegment(durationMs = 30_000L)
        imageRepo.addClip(
            com.example.dubcast.domain.model.ImageClip(
                id = "i1", projectId = projectId,
                imageUri = "content://x", startMs = 1_000L, endMs = 3_000L,
                xPct = 60f, yPct = 40f, widthPct = 25f, heightPct = 25f, lane = 0
            )
        )
        seedProject()
        advanceUntilIdle()

        vm.onDuplicateImageClip("i1")
        advanceUntilIdle()

        val all = imageRepo.observeClips(projectId).first()
        assertEquals(2, all.size)
        val dup = all.first { it.id != "i1" }
        assertEquals(1_000L, dup.startMs)
        assertEquals(3_000L, dup.endMs)
        assertEquals(1, dup.lane)
        assertEquals(60f, dup.xPct)
        assertEquals(25f, dup.widthPct)
    }

    @Test
    fun `onChangeTextOverlayLane increments persisted lane`() = runTest {
        seedSegment(durationMs = 10_000L)
        textOverlayRepo.addOverlay(
            com.example.dubcast.domain.model.TextOverlay(
                id = "t1", projectId = projectId, text = "x",
                startMs = 0L, endMs = 1_000L, lane = 0
            )
        )
        seedProject()
        advanceUntilIdle()

        vm.onChangeTextOverlayLane("t1", 2)
        advanceUntilIdle()

        assertEquals(2, textOverlayRepo.getOverlay("t1")!!.lane)
    }

    @Test
    fun `audio separation reaches PICK_STEMS after polling`() = runTest {
        seedSegment()
        advanceUntilIdle()
        separationRepo.startResult = Result.success("sep-1")
        val stems = listOf(
            Stem("background", "배경음", "/u/bg", StemKind.BACKGROUND),
            Stem("voice_all", "모든 화자", "/u/v", StemKind.VOICE_ALL)
        )
        separationRepo.statusResults = mutableListOf(
            Result.success(SeparationStatus.Processing("sep-1", 40, "Transcribing")),
            Result.success(SeparationStatus.Ready("sep-1", stems))
        )

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()

        val ui = vm.uiState.value.audioSeparation
        assertNotNull(ui)
        assertEquals(AudioSeparationStep.PICK_STEMS, ui!!.step)
        assertEquals(2, ui.stems.size)
        assertEquals(2, ui.selections.size)
        assertTrue(ui.selections.values.none { it.selected })
    }

    @Test
    fun `confirming mix adds a BGM clip at segment offset`() = runTest {
        seedSegment(durationMs = 20_000L)
        advanceUntilIdle()
        separationRepo.startResult = Result.success("sep-2")
        val stems = listOf(Stem("background", "배경음", "/u/bg", StemKind.BACKGROUND))
        separationRepo.statusResults = mutableListOf(
            Result.success(SeparationStatus.Ready("sep-2", stems))
        )
        separationRepo.mixRequestResult = Result.success("mix-2")
        separationRepo.mixStatusResults = mutableListOf(
            Result.success(MixStatus.Completed("mix-2", "/dl/mix?token=x"))
        )
        separationRepo.mixDownloadResult = Result.success("/cache/mix-2.mp3")
        audioExtractor.nextInfo = AudioInfo(uri = "placeholder", durationMs = 12_000L)

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()
        vm.onToggleStemSelection("background")
        vm.onConfirmStemMix()
        advanceUntilIdle()

        val ui = vm.uiState.value.audioSeparation!!
        assertEquals(AudioSeparationStep.DONE, ui.step)
        val bgms = bgmRepo.all()
        assertEquals(1, bgms.size)
        assertEquals("/cache/mix-2.mp3", bgms[0].sourceUri)
        // Mute flag defaults to true — the segment's volume should be 0 so the
        // original audio does not play over the mixed BGM.
        assertEquals(0f, segmentRepo.getSegment("seg-1")!!.volumeScale, 0.0001f)
    }

    @Test
    fun `mix keeps original audio when mute flag is disabled`() = runTest {
        seedSegment(durationMs = 20_000L)
        advanceUntilIdle()
        separationRepo.startResult = Result.success("sep-3")
        separationRepo.statusResults = mutableListOf(
            Result.success(
                SeparationStatus.Ready(
                    "sep-3",
                    listOf(Stem("voice_all", "모든 화자", "/u/v", StemKind.VOICE_ALL))
                )
            )
        )
        separationRepo.mixRequestResult = Result.success("mix-3")
        separationRepo.mixStatusResults = mutableListOf(
            Result.success(MixStatus.Completed("mix-3", "/dl/mix?token=y"))
        )
        separationRepo.mixDownloadResult = Result.success("/cache/mix-3.mp3")
        audioExtractor.nextInfo = AudioInfo(uri = "placeholder", durationMs = 8_000L)

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()
        vm.onToggleMuteOriginalSegmentAudio() // flip default-on → off
        vm.onToggleStemSelection("voice_all")
        vm.onConfirmStemMix()
        advanceUntilIdle()

        assertEquals(1f, segmentRepo.getSegment("seg-1")!!.volumeScale, 0.0001f)
    }

    @Test
    fun `separation failure surfaces error`() = runTest {
        seedSegment()
        advanceUntilIdle()
        separationRepo.startResult = Result.failure(RuntimeException("402 payment required"))

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()

        val ui = vm.uiState.value.audioSeparation!!
        assertEquals(AudioSeparationStep.FAILED, ui.step)
        assertTrue(ui.errorMessage!!.contains("402"))
    }

    @Test
    fun `starting separation forwards segment trim range to repository`() = runTest {
        segmentRepo.addSegment(
            Segment(
                id = "seg-1",
                projectId = projectId,
                type = SegmentType.VIDEO,
                order = 0,
                sourceUri = "content://video.mp4",
                durationMs = 60_000L,
                width = 1920,
                height = 1080,
                trimStartMs = 2_000L,
                trimEndMs = 8_500L
            )
        )
        advanceUntilIdle()
        separationRepo.startResult = Result.success("sep-trim")
        separationRepo.statusResults = mutableListOf(
            Result.success(SeparationStatus.Ready("sep-trim", emptyList()))
        )

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()

        assertEquals(2_000L, separationRepo.lastStartArgs?.trimStartMs)
        assertEquals(8_500L, separationRepo.lastStartArgs?.trimEndMs)
    }

    @Test
    fun `starting separation forwards effective end when only end is trimmed`() = runTest {
        segmentRepo.addSegment(
            Segment(
                id = "seg-1",
                projectId = projectId,
                type = SegmentType.VIDEO,
                order = 0,
                sourceUri = "content://video.mp4",
                durationMs = 60_000L,
                width = 1920,
                height = 1080,
                trimStartMs = 0L,
                trimEndMs = 12_000L
            )
        )
        advanceUntilIdle()
        separationRepo.startResult = Result.success("sep-end-only")
        separationRepo.statusResults = mutableListOf(
            Result.success(SeparationStatus.Ready("sep-end-only", emptyList()))
        )

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()

        assertEquals(0L, separationRepo.lastStartArgs?.trimStartMs)
        assertEquals(12_000L, separationRepo.lastStartArgs?.trimEndMs)
    }

    @Test
    fun `starting separation omits trim range when segment is untrimmed`() = runTest {
        seedSegment()
        advanceUntilIdle()
        separationRepo.startResult = Result.success("sep-full")
        separationRepo.statusResults = mutableListOf(
            Result.success(SeparationStatus.Ready("sep-full", emptyList()))
        )

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()

        assertEquals(null, separationRepo.lastStartArgs?.trimStartMs)
        assertEquals(null, separationRepo.lastStartArgs?.trimEndMs)
    }

    @Test
    fun `confirming mix clears undo history so prior edits cannot be undone`() = runTest {
        seedSegment(durationMs = 20_000L)
        seedProject()
        advanceUntilIdle()

        // Two edits that would normally be undoable
        vm.onEnterTrimMode()
        vm.onSetPendingTrimStart(1_000L)
        vm.onSetPendingTrimEnd(10_000L)
        vm.onConfirmTrim()
        advanceUntilIdle()
        vm.onUpdatePlaybackPosition(2_000L)
        vm.onInsertImage("content://x.jpg")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canUndo)

        // Run a complete separation -> mix flow
        separationRepo.startResult = Result.success("sep-commit")
        separationRepo.statusResults = mutableListOf(
            Result.success(
                SeparationStatus.Ready(
                    "sep-commit",
                    listOf(Stem("background", "배경음", "/u/bg", StemKind.BACKGROUND))
                )
            )
        )
        separationRepo.mixRequestResult = Result.success("mix-commit")
        separationRepo.mixStatusResults = mutableListOf(
            Result.success(MixStatus.Completed("mix-commit", "/dl/mix?token=z"))
        )
        separationRepo.mixDownloadResult = Result.success("/cache/mix-commit.mp3")
        audioExtractor.nextInfo = AudioInfo(uri = "placeholder", durationMs = 9_000L)

        vm.onShowAudioSeparationSheet("seg-1")
        vm.onStartSeparation()
        advanceUntilIdle()
        vm.onToggleStemSelection("background")
        vm.onConfirmStemMix()
        advanceUntilIdle()

        // Undo history has been cleared; the single baseline snapshot is the
        // post-separation state, so nothing can be rolled back from here.
        assertEquals(false, vm.uiState.value.canUndo)

        // Calling undo is a no-op: image clip and trim changes stay put
        vm.onUndo()
        advanceUntilIdle()
        assertEquals(1, imageRepo.observeClips(projectId).first().size)
        val segAfterUndo = segmentRepo.getSegment("seg-1")!!
        assertEquals(1_000L, segAfterUndo.trimStartMs)
        assertEquals(10_000L, segAfterUndo.trimEndMs)
    }
}
