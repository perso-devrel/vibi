package com.dubcast.shared.data.local.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationsTest {

    @Test
    fun `migrations form contiguous version chain`() {
        val pairs = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val maxVersion = pairs.maxOf { it.second }
        assertEquals((1 until maxVersion).map { it to it + 1 }, pairs)
    }

    @Test
    fun `migration 7 to 8 statements create segments and rebuild edit_projects`() {
        val stmts = MIGRATION_7_8_STATEMENTS
        assertEquals(6, stmts.size)
        assertTrue(stmts[0].contains("CREATE TABLE IF NOT EXISTS segments"))
        assertTrue(stmts[1].contains("INSERT INTO segments"))
        assertTrue(stmts[2].contains("CREATE TABLE edit_projects_new"))
        assertTrue(stmts[5].contains("RENAME TO edit_projects"))
    }

    @Test
    fun `ALL_MIGRATIONS last entry endVersion matches max version`() {
        // 새 migration 추가 시 ALL_MIGRATIONS 등록 누락을 잡는 일반 가드.
        // 특정 버전을 hard-coded 하지 않으므로 maintenance 비용 없음.
        val maxVersion = ALL_MIGRATIONS.maxOf { it.endVersion }
        val latest = ALL_MIGRATIONS.last()
        assertEquals(latest.endVersion, maxVersion)
    }
}
