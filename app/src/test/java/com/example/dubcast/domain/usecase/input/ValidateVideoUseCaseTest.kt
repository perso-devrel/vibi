package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.ValidationError
import com.example.dubcast.domain.model.ValidationResult
import com.example.dubcast.domain.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateVideoUseCaseTest {

    private val useCase = ValidateVideoUseCase()

    private fun videoInfo(
        mimeType: String = "video/mp4",
        durationMs: Long = 300_000L,
        width: Int = 1280,
        height: Int = 720
    ) = VideoInfo(
        uri = "content://test",
        fileName = "test.mp4",
        mimeType = mimeType,
        durationMs = durationMs,
        width = width,
        height = height,
        sizeBytes = 10_000_000L
    )

    @Test
    fun `valid mp4 under limits`() {
        val result = useCase(videoInfo())
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `valid mov at boundary duration`() {
        val result = useCase(videoInfo(mimeType = "video/quicktime", durationMs = 600_000L))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `valid webm`() {
        val result = useCase(videoInfo(mimeType = "video/webm", durationMs = 60_000L, width = 640, height = 480))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `valid 1080p landscape`() {
        val result = useCase(videoInfo(width = 1920, height = 1080))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `valid 1080p portrait`() {
        val result = useCase(videoInfo(width = 1080, height = 1920))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `rejects avi format`() {
        val result = useCase(videoInfo(mimeType = "video/x-msvideo"))
        assertEquals(ValidationResult.Invalid(ValidationError.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun `rejects mkv format`() {
        val result = useCase(videoInfo(mimeType = "video/x-matroska"))
        assertEquals(ValidationResult.Invalid(ValidationError.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun `rejects empty mime type`() {
        val result = useCase(videoInfo(mimeType = ""))
        assertEquals(ValidationResult.Invalid(ValidationError.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun `rejects over 10 minutes`() {
        val result = useCase(videoInfo(durationMs = 600_001L))
        assertEquals(ValidationResult.Invalid(ValidationError.DURATION_EXCEEDS_LIMIT), result)
    }

    @Test
    fun `rejects 4K resolution`() {
        val result = useCase(videoInfo(width = 3840, height = 2160))
        assertEquals(ValidationResult.Invalid(ValidationError.RESOLUTION_EXCEEDS_LIMIT), result)
    }

    @Test
    fun `rejects 1440p resolution`() {
        val result = useCase(videoInfo(width = 2560, height = 1440))
        assertEquals(ValidationResult.Invalid(ValidationError.RESOLUTION_EXCEEDS_LIMIT), result)
    }

    @Test
    fun `format checked before duration`() {
        val result = useCase(videoInfo(mimeType = "video/avi", durationMs = 999_999L))
        assertEquals(ValidationResult.Invalid(ValidationError.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun `duration checked before resolution`() {
        val result = useCase(videoInfo(durationMs = 999_999L, width = 3840, height = 2160))
        assertEquals(ValidationResult.Invalid(ValidationError.DURATION_EXCEEDS_LIMIT), result)
    }
}
