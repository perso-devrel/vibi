package com.example.dubcast.domain.usecase.subtitle

class UndoRedoManager<T>(private val maxHistory: Int = 50) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushState(state: T) {
        undoStack.addLast(state)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(): T? {
        if (!canUndo) return null
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        return undoStack.last()
    }

    fun redo(): T? {
        if (!canRedo) return null
        val state = redoStack.removeLast()
        undoStack.addLast(state)
        return state
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
