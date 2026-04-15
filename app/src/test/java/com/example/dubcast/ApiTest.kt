package com.example.dubcast

/**
 * Marker interface for tests that call real external APIs (BFF/ElevenLabs).
 * These tests are excluded by default and only run with: ./gradlew testDebugUnitTest -Pinclude.api.tests
 */
interface ApiTest
