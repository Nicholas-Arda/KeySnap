package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptOrderTest {
    private fun scripts(vararg ids: String) = ids.map { ShortcutScript(id = it, name = it, triggers = emptyList(), actions = emptyList()) }

    @Test fun movesForwardAndBackward() {
        assertEquals(listOf("b", "c", "a"), moveShortcut(listOf("a", "b", "c"), "a", "c"))
        assertEquals(listOf("c", "a", "b"), moveShortcut(listOf("a", "b", "c"), "c", "a"))
    }

    @Test fun sameOrMissingItemDoesNotMove() {
        val order = listOf("a", "b")
        assertEquals(order, moveShortcut(order, "a", "a"))
        assertEquals(order, moveShortcut(order, "x", "b"))
        assertEquals(order, moveShortcut(order, "a", "x"))
    }

    @Test fun filteredMoveKeepsHiddenSlotsAndRelativeOrder() {
        val full = scripts("hidden0", "a", "hidden1", "b", "hidden2", "c", "hidden3")
        val merged = mergeShortcutOrder(full, listOf("c", "a", "b"))
        assertEquals(listOf("hidden0", "c", "hidden1", "a", "hidden2", "b", "hidden3"), merged.map { it.id })
    }

    @Test fun allVisibleCanBeReversed() {
        assertEquals(listOf("c", "b", "a"), mergeShortcutOrder(scripts("a", "b", "c"), listOf("c", "b", "a")).map { it.id })
    }

    @Test fun preservesCurrentContentsAndNewHiddenScripts() {
        val current = scripts("a", "new", "b").map { it.copy(name = "latest ${it.id}", enabled = false) }
        assertEquals(listOf(current[2], current[1], current[0]), mergeShortcutOrder(current, listOf("b", "a")))
    }

    @Test fun staleAndDuplicateIdsCannotResurrectOrDuplicateScripts() {
        val current = scripts("a", "hidden", "b")
        assertEquals(listOf(current[2], current[1], current[0]), mergeShortcutOrder(current, listOf("b", "deleted", "b", "a")))
    }

    @Test fun emptyAndSingleSubsetAreUnchanged() {
        val current = scripts("a", "b")
        assertEquals(current, mergeShortcutOrder(current, emptyList()))
        assertEquals(current, mergeShortcutOrder(current, listOf("b")))
        assertEquals(emptyList<ShortcutScript>(), mergeShortcutOrder(emptyList(), listOf("a")))
    }
}
