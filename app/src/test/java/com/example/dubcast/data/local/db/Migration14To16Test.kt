package com.example.dubcast.data.local.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Covers the two most recent ALTER-style migrations:
 *  - 14→15 adds `segments.duplicatedFromId` (nullable TEXT)
 *  - 15→16 adds `edit_projects.videoScale/videoOffsetXPct/videoOffsetYPct`
 *
 * Uses sqlite-jdbc for schema-level verification without Android runtime.
 * MIGRATION_X_Y's `migrate` delegates to `db.execSQL`, so we re-issue the
 * same SQL strings here (there's no MIGRATION_14_15_STATEMENTS list yet,
 * and the ALTER commands are short enough to inline).
 */
class Migration14To16Test {

    private lateinit var conn: Connection

    @Before
    fun setup() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV14Schema()
    }

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `14 to 15 adds duplicatedFromId as nullable TEXT with default null`() {
        conn.createStatement().use {
            it.execute("ALTER TABLE segments ADD COLUMN duplicatedFromId TEXT")
        }
        val (name, type, notNull) = columnInfo("segments", "duplicatedFromId")
        assertEquals("duplicatedFromId", name)
        assertEquals("TEXT", type)
        assertEquals(0, notNull)

        // Pre-existing rows get NULL so they are not treated as duplicates.
        seedSegment("seg-1")
        val stored = conn.createStatement().executeQuery(
            "SELECT duplicatedFromId FROM segments WHERE id = 'seg-1'"
        ).use { rs -> if (rs.next()) rs.getString(1) else "MISSING" }
        assertNull(stored)
    }

    @Test
    fun `15 to 16 adds video placement columns with sensible defaults`() {
        // Apply 14→15 then 15→16 in order so the test mirrors production.
        conn.createStatement().use {
            it.execute("ALTER TABLE segments ADD COLUMN duplicatedFromId TEXT")
        }
        conn.createStatement().use {
            it.execute("ALTER TABLE edit_projects ADD COLUMN videoScale REAL NOT NULL DEFAULT 1.0")
            it.execute("ALTER TABLE edit_projects ADD COLUMN videoOffsetXPct REAL NOT NULL DEFAULT 0.0")
            it.execute("ALTER TABLE edit_projects ADD COLUMN videoOffsetYPct REAL NOT NULL DEFAULT 0.0")
        }
        val cols = columnNames("edit_projects")
        assertTrue(cols.containsAll(listOf("videoScale", "videoOffsetXPct", "videoOffsetYPct")))

        seedProject("proj-a")
        val row = conn.createStatement().executeQuery(
            "SELECT videoScale, videoOffsetXPct, videoOffsetYPct FROM edit_projects WHERE projectId = 'proj-a'"
        ).use { rs ->
            if (rs.next()) Triple(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)) else null
        }
        assertEquals(Triple(1.0, 0.0, 0.0), row)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun createV14Schema() {
        val ddl = listOf(
            """CREATE TABLE edit_projects (
                projectId TEXT NOT NULL PRIMARY KEY,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                frameWidth INTEGER NOT NULL DEFAULT 0,
                frameHeight INTEGER NOT NULL DEFAULT 0,
                backgroundColorHex TEXT NOT NULL DEFAULT '#000000'
            )""",
            """CREATE TABLE segments (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                type TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                sourceUri TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                trimStartMs INTEGER NOT NULL DEFAULT 0,
                trimEndMs INTEGER NOT NULL DEFAULT 0,
                imageXPct REAL NOT NULL DEFAULT 50.0,
                imageYPct REAL NOT NULL DEFAULT 50.0,
                imageWidthPct REAL NOT NULL DEFAULT 50.0,
                imageHeightPct REAL NOT NULL DEFAULT 50.0,
                volumeScale REAL NOT NULL DEFAULT 1.0,
                speedScale REAL NOT NULL DEFAULT 1.0
            )"""
        )
        for (sql in ddl) conn.createStatement().use { it.execute(sql) }
    }

    private fun seedSegment(id: String) {
        conn.createStatement().use {
            it.execute(
                """INSERT INTO segments
                    (id, projectId, type, `order`, sourceUri, durationMs, width, height)
                    VALUES ('$id', 'p', 'VIDEO', 0, 'content://x', 1000, 1920, 1080)"""
            )
        }
    }

    private fun seedProject(id: String) {
        conn.createStatement().use {
            it.execute(
                """INSERT INTO edit_projects (projectId, createdAt, updatedAt)
                    VALUES ('$id', 1, 2)"""
            )
        }
    }

    private fun columnNames(table: String): List<String> =
        conn.createStatement().executeQuery("PRAGMA table_info('$table')")
            .asList { it.getString("name") }

    private data class ColumnInfo(val name: String, val type: String, val notNull: Int)

    private fun columnInfo(table: String, column: String): Triple<String, String, Int> {
        val rs = conn.createStatement().executeQuery("PRAGMA table_info('$table')")
        rs.use {
            while (it.next()) {
                if (it.getString("name") == column) {
                    return Triple(it.getString("name"), it.getString("type"), it.getInt("notnull"))
                }
            }
        }
        error("column $column not found on $table")
    }

    private fun <T> ResultSet.asList(block: (ResultSet) -> T): List<T> {
        val list = mutableListOf<T>()
        use {
            while (it.next()) list += block(it)
        }
        return list
    }
}
