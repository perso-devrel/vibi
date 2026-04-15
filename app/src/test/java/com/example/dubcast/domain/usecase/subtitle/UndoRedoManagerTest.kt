package com.example.dubcast.domain.usecase.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoRedoManagerTest {

    private lateinit var manager: UndoRedoManager<String>

    @Before
    fun setup() {
        manager = UndoRedoManager(maxHistory = 50)
    }

    @Test
    fun `initial state cannot undo or redo`() {
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
    }

    @Test
    fun `push then undo returns previous`() {
        manager.pushState("A")
        manager.pushState("B")
        val result = manager.undo()
        assertEquals("A", result)
    }

    @Test
    fun `undo then redo returns forward state`() {
        manager.pushState("A")
        manager.pushState("B")
        manager.undo()
        val result = manager.redo()
        assertEquals("B", result)
    }

    @Test
    fun `redo cleared after new push`() {
        manager.pushState("A")
        manager.pushState("B")
        manager.undo()
        manager.pushState("C")
        assertFalse(manager.canRedo)
    }

    @Test
    fun `max history evicts oldest`() {
        val mgr = UndoRedoManager<Int>(maxHistory = 3)
        mgr.pushState(1)
        mgr.pushState(2)
        mgr.pushState(3)
        mgr.pushState(4) // evicts 1

        val third = mgr.undo() // 3
        val second = mgr.undo() // 2
        val first = mgr.undo() // null - 1 was evicted
        assertEquals(3, third)
        assertEquals(2, second)
        assertNull(first)
    }

    @Test
    fun `clear resets everything`() {
        manager.pushState("A")
        manager.pushState("B")
        manager.clear()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
    }

    @Test
    fun `undo from single state returns null`() {
        manager.pushState("A")
        val result = manager.undo()
        assertNull(result)
        assertFalse(manager.canUndo)
    }

    @Test
    fun `redo without undo returns null`() {
        manager.pushState("A")
        val result = manager.redo()
        assertNull(result)
    }

    @Test
    fun `multiple undos and redos`() {
        manager.pushState("A")
        manager.pushState("B")
        manager.pushState("C")

        assertEquals("B", manager.undo())
        assertEquals("A", manager.undo())
        assertEquals("B", manager.redo())
        assertEquals("C", manager.redo())
        assertNull(manager.redo()) // no more redo
    }

    @Test
    fun `canUndo is true after two pushes`() {
        manager.pushState("A")
        assertFalse(manager.canUndo) // only one state, nothing to undo to
        manager.pushState("B")
        assertTrue(manager.canUndo)
    }

    @Test
    fun `canRedo is true after undo`() {
        manager.pushState("A")
        manager.pushState("B")
        assertFalse(manager.canRedo)
        manager.undo()
        assertTrue(manager.canRedo)
    }
}
