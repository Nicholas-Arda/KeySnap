package com.example.service

import com.example.bridge.ActionTestResult
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SingleActionTest {
    @Test fun runsOneActionAndReturnsOutcomeAndDuration() = runBlocking {
        val calls = mutableListOf<ScriptAction>()
        val action = ScriptAction("shell_command", mapOf("command" to "literal-secret"))
        val executor = ScriptActionExecutor { calls += it; true }
        val result = executor.runSingleActionForTest(action)
        assertEquals(listOf(action), calls)
        assertTrue(result.ok)
        assertTrue(result.durationMs >= 0)
        assertTrue(result.output.isEmpty())
    }

    @Test fun detailedTestResultIsNotExecutedAgainByBooleanHandler() = runBlocking {
        val expected = ActionTestResult(false, 7, null, 123, false, listOf("private output"))
        val executor = ScriptActionExecutor(singleActionTest = { expected }) { error("Must not execute twice") }
        assertSame(expected, executor.runSingleActionForTest(ScriptAction("shell_command")))
    }

    @Test fun exceptionsNeverExposePayload() = runBlocking {
        val executor = ScriptActionExecutor { throw IllegalArgumentException("secret") }
        val result = executor.runSingleActionForTest(ScriptAction("shell_command"))
        assertFalse(result.ok)
        assertTrue(result.output.isEmpty())
        assertFalse(result.toString().contains("secret"))
    }
}
