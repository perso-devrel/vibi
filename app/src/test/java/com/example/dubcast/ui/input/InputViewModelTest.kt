package com.example.dubcast.ui.input

import com.example.dubcast.domain.model.ValidationError
import com.example.dubcast.domain.model.ValidationResult
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.usecase.input.ValidateVideoUseCase
import com.example.dubcast.fake.FakeEditProjectRepository
import com.example.dubcast.fake.FakeVideoMetadataExtractor
import com.example.dubcast.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InputViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var extractor: FakeVideoMetadataExtractor
    private lateinit var viewModel: InputViewModel

    private val validVideo = VideoInfo(
        uri = "content://test/video.mp4",
        fileName = "video.mp4",
        mimeType = "video/mp4",
        durationMs = 120_000L,
        width = 1280,
        height = 720,
        sizeBytes = 5_000_000L
    )

    @Before
    fun setup() {
        extractor = FakeVideoMetadataExtractor()
        viewModel = InputViewModel(extractor, ValidateVideoUseCase(), FakeEditProjectRepository())
    }

    @Test
    fun `initial state has no selection`() {
        val state = viewModel.uiState.value
        assertNull(state.selectedVideo)
        assertNull(state.validationResult)
        assertFalse(state.isExtracting)
    }

    @Test
    fun `onVideoPicked with valid video extracts and validates`() = runTest {
        extractor.result = validVideo
        viewModel.onVideoPicked("content://test/video.mp4")

        val state = viewModel.uiState.value
        assertEquals(validVideo, state.selectedVideo)
        assertEquals(ValidationResult.Valid, state.validationResult)
        assertFalse(state.isExtracting)
    }

    @Test
    fun `onVideoPicked with oversized video shows invalid`() = runTest {
        extractor.result = validVideo.copy(width = 3840, height = 2160)
        viewModel.onVideoPicked("content://test/video.mp4")

        val state = viewModel.uiState.value
        assertEquals(
            ValidationResult.Invalid(ValidationError.RESOLUTION_EXCEEDS_LIMIT),
            state.validationResult
        )
    }

    @Test
    fun `onVideoPicked with too long video shows invalid`() = runTest {
        extractor.result = validVideo.copy(durationMs = 700_000L)
        viewModel.onVideoPicked("content://test/video.mp4")

        val state = viewModel.uiState.value
        assertEquals(
            ValidationResult.Invalid(ValidationError.DURATION_EXCEEDS_LIMIT),
            state.validationResult
        )
    }

    @Test
    fun `onVideoPicked with null extraction shows metadata error`() = runTest {
        extractor.result = null
        viewModel.onVideoPicked("content://test/video.mp4")

        val state = viewModel.uiState.value
        assertEquals(
            ValidationResult.Invalid(ValidationError.METADATA_UNREADABLE),
            state.validationResult
        )
        assertNull(state.selectedVideo)
    }

    @Test
    fun `canProceed is true only when valid`() = runTest {
        extractor.result = validVideo
        viewModel.onVideoPicked("content://test/video.mp4")
        assertEquals(ValidationResult.Valid, viewModel.uiState.value.validationResult)

        extractor.result = validVideo.copy(durationMs = 999_999L)
        viewModel.onVideoPicked("content://test/video2.mp4")
        assertTrue(viewModel.uiState.value.validationResult is ValidationResult.Invalid)
    }
}
