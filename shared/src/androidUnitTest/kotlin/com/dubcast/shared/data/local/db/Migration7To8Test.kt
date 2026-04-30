package com.dubcast.shared.data.local.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * v7→v8 마이그레이션 단위 검증.
 *
 * Migrations.kt 의 [MIGRATION_7_8_STATEMENTS] 가:
 *  - segments 테이블을 신규 생성하고
 *  - 기존 edit_projects 의 비디오 메타를 single VIDEO segment 로 옮기고
 *  - edit_projects 를 슬림화 (projectId/createdAt/updatedAt 만 남김)
 *  - 자식 클립 테이블의 projectId 참조 무결성을 유지
 * 함을 보장한다.
 *
 * Android 런타임/MigrationTestHelper 없이 sqlite-jdbc 의 in-memory SQLite 로 실행 — JVM 단위 테스트.
 * legacy-android 의 동등 테스트(Migration7To8Test)와 동일 패턴.
 */
class Migration7To8Test {

    private lateinit var conn: Connection

    @Before
    fun setup() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV7Schema()
        seedV7Data()
    }

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `segments table is created`() {
        applyMigration()
        val cols = columnNames("segments")
        assertTrue(
            cols.containsAll(
                listOf(
                    "id", "projectId", "type", "order", "sourceUri",
                    "durationMs", "width", "height", "trimStartMs", "trimEndMs",
                    "imageXPct", "imageYPct", "imageWidthPct", "imageHeightPct"
                )
            )
        )
    }

    @Test
    fun `each project gets a single VIDEO segment with original metadata`() {
        applyMigration()
        val rows = conn.createStatement().executeQuery(
            """SELECT id, projectId, type, `order`, sourceUri, durationMs,
                   width, height, trimStartMs, trimEndMs
                   FROM segments ORDER BY projectId, `order`"""
        ).asList { rs ->
            mapOf(
                "id" to rs.getString("id"),
                "projectId" to rs.getString("projectId"),
                "type" to rs.getString("type"),
                "order" to rs.getInt("order"),
                "sourceUri" to rs.getString("sourceUri"),
                "durationMs" to rs.getLong("durationMs"),
                "width" to rs.getInt("width"),
                "height" to rs.getInt("height"),
                "trimStartMs" to rs.getLong("trimStartMs"),
                "trimEndMs" to rs.getLong("trimEndMs")
            )
        }
        assertEquals(2, rows.size)

        val pA = rows.first { it["projectId"] == "proj-a" }
        assertEquals("proj-a_seg0", pA["id"])
        assertEquals("VIDEO", pA["type"])
        assertEquals(0, pA["order"])
        assertEquals("content://a.mp4", pA["sourceUri"])
        assertEquals(30_000L, pA["durationMs"])
        assertEquals(1920, pA["width"])
        assertEquals(1080, pA["height"])
        assertEquals(0L, pA["trimStartMs"])
        assertEquals(0L, pA["trimEndMs"])

        val pB = rows.first { it["projectId"] == "proj-b" }
        assertEquals("proj-b_seg0", pB["id"])
        assertEquals("content://b.mp4", pB["sourceUri"])
        assertEquals(60_000L, pB["durationMs"])
        assertEquals(2_000L, pB["trimStartMs"])
        assertEquals(55_000L, pB["trimEndMs"])
    }

    @Test
    fun `edit_projects is slimmed to projectId, createdAt, updatedAt`() {
        applyMigration()
        val cols = columnNames("edit_projects")
        assertEquals(setOf("projectId", "createdAt", "updatedAt"), cols.toSet())

        val rows = conn.createStatement().executeQuery(
            "SELECT projectId, createdAt, updatedAt FROM edit_projects ORDER BY projectId"
        ).asList { rs ->
            Triple(rs.getString("projectId"), rs.getLong("createdAt"), rs.getLong("updatedAt"))
        }
        assertEquals(2, rows.size)
        assertEquals(Triple("proj-a", 1000L, 2000L), rows[0])
        assertEquals(Triple("proj-b", 3000L, 4000L), rows[1])
    }

    @Test
    fun `existing child clips still match project ids after migration`() {
        applyMigration()
        val projectIds = conn.createStatement().executeQuery(
            "SELECT projectId FROM edit_projects"
        ).asList { it.getString(1) }.toSet()

        for (table in listOf("dub_clips", "subtitle_clips", "image_clips")) {
            val clipProjects = conn.createStatement().executeQuery(
                "SELECT DISTINCT projectId FROM $table"
            ).asList { it.getString(1) }.toSet()
            assertTrue(
                "$table should reference existing project ids, unknown=${clipProjects - projectIds}",
                projectIds.containsAll(clipProjects)
            )
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun applyMigration() {
        conn.autoCommit = false
        try {
            for (sql in MIGRATION_7_8_STATEMENTS) {
                conn.createStatement().use { it.execute(sql) }
            }
            conn.commit()
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    private fun createV7Schema() {
        val ddl = listOf(
            """CREATE TABLE edit_projects (
                projectId TEXT NOT NULL PRIMARY KEY,
                videoUri TEXT NOT NULL,
                videoDurationMs INTEGER NOT NULL,
                videoWidth INTEGER NOT NULL,
                videoHeight INTEGER NOT NULL,
                trimStartMs INTEGER NOT NULL DEFAULT 0,
                trimEndMs INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""",
            """CREATE TABLE dub_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                text TEXT NOT NULL,
                voiceId TEXT NOT NULL,
                voiceName TEXT NOT NULL,
                audioFilePath TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                volume REAL NOT NULL DEFAULT 1.0
            )""",
            """CREATE TABLE subtitle_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                text TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL,
                anchor TEXT NOT NULL DEFAULT 'bottom',
                yOffsetPct REAL NOT NULL DEFAULT 90.0,
                sourceDubClipId TEXT,
                xPct REAL,
                yPct REAL,
                widthPct REAL,
                heightPct REAL
            )""",
            """CREATE TABLE image_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                imageUri TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL,
                xPct REAL NOT NULL DEFAULT 50.0,
                yPct REAL NOT NULL DEFAULT 50.0,
                widthPct REAL NOT NULL DEFAULT 30.0,
                heightPct REAL NOT NULL DEFAULT 30.0
            )"""
        )
        for (sql in ddl) conn.createStatement().use { it.execute(sql) }
    }

    private fun seedV7Data() {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """INSERT INTO edit_projects VALUES(
                    'proj-a', 'content://a.mp4', 30000, 1920, 1080, 0, 0, 1000, 2000)"""
            )
            stmt.execute(
                """INSERT INTO edit_projects VALUES(
                    'proj-b', 'content://b.mp4', 60000, 1280, 720, 2000, 55000, 3000, 4000)"""
            )
            for (p in listOf("proj-a", "proj-b")) {
                for (n in 0..1) {
                    stmt.execute(
                        """INSERT INTO dub_clips VALUES(
                            '$p-dub-$n', '$p', 'hello', 'v', 'V', '/path.mp3', 0, 1000, 1.0)"""
                    )
                    stmt.execute(
                        """INSERT INTO subtitle_clips(id, projectId, text, startMs, endMs)
                            VALUES('$p-sub-$n', '$p', 'hi', 0, 1000)"""
                    )
                    stmt.execute(
                        """INSERT INTO image_clips(id, projectId, imageUri, startMs, endMs)
                            VALUES('$p-img-$n', '$p', 'content://img', 0, 1000)"""
                    )
                }
            }
        }
    }

    private fun columnNames(table: String): List<String> {
        val rs = conn.createStatement().executeQuery("PRAGMA table_info('$table')")
        return rs.asList { it.getString("name") }
    }

    private fun <T> ResultSet.asList(block: (ResultSet) -> T): List<T> {
        val list = mutableListOf<T>()
        use {
            while (it.next()) list += block(it)
        }
        return list
    }
}
