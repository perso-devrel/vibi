package com.example.dubcast.ui.share

import androidx.lifecycle.SavedStateHandle
import com.example.dubcast.fake.FakeGallerySaver
import com.example.dubcast.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var gallerySaver: FakeGallerySaver
    private lateinit var viewModel: ShareViewModel

    @Before
    fun setup() {
        gallerySaver = FakeGallerySaver()
        val savedStateHandle = SavedStateHandle(mapOf("outputPath" to "/path/video.mp4"))
        viewModel = ShareViewModel(savedStateHandle, gallerySaver)
    }

    @Test
    fun `saveToGallery success updates state`() = runTest {
        viewModel.saveToGallery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.savedToGallery)
        assertNotNull(state.galleryUri)
        assertNull(state.error)
    }

    @Test
    fun `saveToGallery failure shows error`() = runTest {
        gallerySaver.result = Result.failure(java.io.IOException("Storage full"))

        viewModel.saveToGallery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Storage full"))
    }
}
