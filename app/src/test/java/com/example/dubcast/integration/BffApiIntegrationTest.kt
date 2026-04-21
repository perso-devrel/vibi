package com.example.dubcast.integration

import com.example.dubcast.ApiTest
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.RenderBgmClip
import com.example.dubcast.data.remote.dto.RenderConfig
import com.example.dubcast.data.remote.dto.RenderFrame
import com.example.dubcast.data.remote.dto.RenderSegment
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Integration tests that call the real BFF API (v2 endpoints).
 *
 * Excluded from default test runs to avoid API costs.
 * Run explicitly with: ./gradlew testDebugUnitTest -Pinclude.api.tests
 *
 * Reads BFF_BASE_URL from local.properties or env variables.
 */
@Category(ApiTest::class)
class BffApiIntegrationTest {

    private lateinit var api: BffApiService
    private lateinit var moshi: Moshi

    private fun loadProperty(key: String): String? {
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
        val propsFile = File("../local.properties")
        if (propsFile.exists()) {
            val props = Properties().apply { load(propsFile.inputStream()) }
            props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    @Before
    fun setup() {
        val baseUrl = loadProperty("BFF_BASE_URL")
        assumeTrue(
            "Skipping: BFF_BASE_URL not configured",
            baseUrl != null && !baseUrl.contains("example.com")
        )

        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl!!)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BffApiService::class.java)
    }

    @Test
    fun `voices endpoint returns non-empty list`() = runTest {
        val response = api.getVoices()
        assertNotNull(response.voices)
        assertTrue(response.voices.isNotEmpty())
    }

    @Test
    fun `render with bare segment (no frame no bgm) accepts submission`() = runTest(
        timeout = kotlin.time.Duration.parse("30s")
    ) {
        val bffStorage = File("C:/Users/EST-INFRA/Desktop/est/dubcast-bff/dubcast-bff/storage")
        val videoFile = File(bffStorage, "render").listFiles { f -> f.extension == "mp4" }
            ?.firstOrNull()
        assumeTrue("no test mp4", videoFile != null)
        val bareConfig = RenderConfig(
            dubClips = emptyList(),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "video_0",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 3_000L,
                    trimStartMs = 0L,
                    trimEndMs = 3_000L,
                    width = 1920,
                    height = 1080
                )
            )
        )
        val json = moshi.adapter(RenderConfig::class.java).toJson(bareConfig)
        val configBody = json.toRequestBody("application/json".toMediaType())
        val videoPart = MultipartBody.Part.createFormData(
            "video_0", videoFile!!.name,
            videoFile.asRequestBody("video/mp4".toMediaType())
        )
        try {
            val r = api.submitRenderJob(
                videoFiles = listOf(videoPart),
                audioFiles = emptyList(),
                subtitles = null,
                imageFiles = emptyList(),
                segmentImageFiles = emptyList(),
                bgmFiles = emptyList(),
                config = configBody
            )
            assertTrue(r.jobId.startsWith("render-"))
        } catch (e: retrofit2.HttpException) {
            throw AssertionError("bare submit ${e.code()}: ${e.response()?.errorBody()?.string()}\nconfig=$json", e)
        }
    }

    /**
     * End-to-end render with all newly added fields:
     *  - frame (1080x1920 portrait + non-default bg color)
     *  - segment volumeScale + speedScale
     *  - bgmClips with start offset + custom volume
     *
     * Reuses files already present under BFF storage/ to avoid bundling
     * binary assets. Skipped if those files are absent.
     */
    @Test
    fun `render with frame bgm and segment volume_speed completes successfully`() = runTest(
        timeout = kotlin.time.Duration.parse("3m")
    ) {
        val bffStorage = loadProperty("BFF_STORAGE_DIR")
            ?.let { File(it) }
            ?: File("C:/Users/EST-INFRA/Desktop/est/dubcast-bff/dubcast-bff/storage")
        val videoFile = File(bffStorage, "render").listFiles { f -> f.extension == "mp4" }
            ?.firstOrNull()
        val audioFile = File(bffStorage, "tts").listFiles { f -> f.extension == "mp3" }
            ?.firstOrNull()
        assumeTrue("Skipping: no test mp4 in BFF storage/render", videoFile != null)
        assumeTrue("Skipping: no test mp3 in BFF storage/tts", audioFile != null)

        val renderConfig = RenderConfig(
            dubClips = emptyList(),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "video_0",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 3_000L,
                    trimStartMs = 0L,
                    trimEndMs = 3_000L,
                    width = 1920,
                    height = 1080,
                    volumeScale = 0.5f,
                    speedScale = 1.5f
                )
            ),
            frame = RenderFrame(width = 1080, height = 1920, backgroundColorHex = "#112233"),
            bgmClips = listOf(
                RenderBgmClip(audioFileKey = "bgm_0", startMs = 500L, volume = 0.4f)
            )
        )
        val configJson = moshi.adapter(RenderConfig::class.java).toJson(renderConfig)
        val configBody = configJson.toRequestBody("application/json".toMediaType())

        val videoPart = MultipartBody.Part.createFormData(
            "video_0", videoFile!!.name,
            videoFile.asRequestBody("video/mp4".toMediaType())
        )
        val bgmPart = MultipartBody.Part.createFormData(
            "bgm_0", audioFile!!.name,
            audioFile.asRequestBody("audio/mpeg".toMediaType())
        )

        val submit = try {
            api.submitRenderJob(
                videoFiles = listOf(videoPart),
                audioFiles = emptyList(),
                subtitles = null,
                imageFiles = emptyList(),
                segmentImageFiles = emptyList(),
                bgmFiles = listOf(bgmPart),
                config = configBody
            )
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string()
            throw AssertionError("submitRenderJob ${e.code()}: $body\nconfig=$configJson", e)
        }
        val jobId = submit.jobId
        assertNotNull(jobId)
        assertTrue(jobId.startsWith("render-"))

        // Poll until COMPLETED or FAILED, max 2 minutes.
        val deadline = System.currentTimeMillis() + 120_000L
        var lastStatus = "PENDING"
        var lastProgress: Int? = null
        var error: String? = null
        while (System.currentTimeMillis() < deadline) {
            val s = api.getRenderStatus(jobId)
            lastStatus = s.status
            lastProgress = s.progress
            error = s.error
            if (s.status == "COMPLETED" || s.status == "FAILED") break
            delay(1_500)
        }
        assertEquals(
            "Render did not complete (last=$lastStatus, progress=$lastProgress, error=$error)",
            "COMPLETED", lastStatus
        )

        // Download and sanity-check the output.
        val body = api.downloadRenderResult(jobId)
        val bytes = body.byteStream().use { it.readBytes() }
        assertTrue("Output file too small: ${bytes.size} bytes", bytes.size > 1024)
        // mp4 files start with an `ftyp` box at offset 4.
        val ftypMarker = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val hasFtyp = (4..16).any { off ->
            off + 4 <= bytes.size && bytes.sliceArray(off until off + 4).contentEquals(ftypMarker)
        }
        assertTrue("Output does not look like an mp4 (no ftyp marker found)", hasFtyp)
    }
}
