package com.example.dubcast.integration

import com.example.dubcast.ApiTest
import com.example.dubcast.data.remote.api.BffApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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

        val moshi = Moshi.Builder()
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
}
