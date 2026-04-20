package com.example.dubcast.ui.timeline

import androidx.lifecycle.SavedStateHandle
import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.repository.TtsResult
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.timeline.DeleteDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.MoveDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.SplitDubTextToSubtitlesUseCase
import com.example.dubcast.domain.usecase.tts.GetVoiceListUseCase
import com.example.dubcast.domain.usecase.tts.SynthesizeDubClipUseCase
import com.example.dubcast.fake.FakeDubClipRepository
import com.example.dubcast.fake.FakeEditProjectRepository
import com.example.dubcast.fake.FakeSubtitleClipRepository
import com.example.dubcast.fake.FakeTtsRepository
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
    private lateinit var ttsRepo: FakeTtsRepository
    private lateinit var vm: TimelineViewModel

    private val projectId = "proj-1"

    @Before
    fun setup() {
        dubRepo = FakeDubClipRepository()
        subRepo = FakeSubtitleClipRepository()
        ttsRepo = FakeTtsRepository()
        vm = TimelineViewModel(
            savedStateHandle = SavedStateHandle(mapOf("projectId" to projectId)),
            editProjectRepository = FakeEditProjectRepository(),
            dubClipRepository = dubRepo,
            subtitleClipRepository = subRepo,
            ttsRepository = ttsRepo,
            synthesizeDubClip = SynthesizeDubClipUseCase(ttsRepo, dubRepo),
            getVoiceList = GetVoiceListUseCase(ttsRepo),
            moveDubClip = MoveDubClipUseCase(dubRepo),
            deleteDubClip = DeleteDubClipUseCase(dubRepo),
            addSubtitleClip = AddSubtitleClipUseCase(subRepo),
            deleteSubtitleClip = DeleteSubtitleClipUseCase(subRepo),
            splitDubTextToSubtitles = SplitDubTextToSubtitlesUseCase()
        )
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
