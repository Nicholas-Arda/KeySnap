package com.example.viewmodel

import com.example.data.ActionEditorForms
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyEveryChatTest {

    private val anyChat = ScriptConstraint("notification_received", mapOf("packageName" to "com.example.chat"))
    private val mom = ScriptConstraint("notification_received", mapOf("packageName" to "com.example.chat", "contact" to "annem"))
    private val reply = ScriptAction("reply_to_notification", mapOf("packageName" to "com.example.chat", "text" to "busy"))
    private val replyMom = ScriptAction("reply_to_notification", mapOf("chat" to "annem", "text" to "busy"))
    private val cooldown = ScriptConstraint("cooldown", mapOf("minutes" to "10"))

    private fun draft(constraints: List<ScriptConstraint>, actions: List<ScriptAction>, automation: Boolean = true) =
        ScriptDraft(id = "s", name = "", enabled = true, trigger = null, automation = automation, actions = actions, constraints = constraints, isNew = true)

    @Test
    fun everyChatOnlyWhenBothContactAndChatAreEmpty() {
        assertEquals(1, ActionEditorForms.everyChatReplyIndex(true, listOf(anyChat), listOf(ScriptAction("vibrate"), reply)))
        assertFalse(ActionEditorForms.repliesToEveryChat(true, listOf(mom), listOf(reply)))
        assertFalse(ActionEditorForms.repliesToEveryChat(true, listOf(anyChat), listOf(replyMom)))
        assertFalse(ActionEditorForms.repliesToEveryChat(false, listOf(anyChat), listOf(reply)))
        assertFalse(ActionEditorForms.repliesToEveryChat(true, listOf(ScriptConstraint("charging")), listOf(reply)))
        assertTrue(ActionEditorForms.repliesToEveryChat(true, listOf(anyChat.copy(params = anyChat.params + ("contact" to " "))), listOf(reply)))
        // A word filter still answers anyone who sends that word.
        assertTrue(ActionEditorForms.repliesToEveryChat(true, listOf(anyChat.copy(params = anyChat.params + ("contains" to "acil"))), listOf(reply)))
    }

    @Test
    fun addingTheReplyAddsATenMinuteCooldown() {
        val before = draft(listOf(anyChat), emptyList())
        val after = draft(listOf(anyChat), listOf(reply)).withReplyCooldown(before)
        assertEquals(listOf(anyChat, cooldown), after.constraints)
    }

    @Test
    fun emptyingTheContactAddsIt() {
        val before = draft(listOf(mom), listOf(reply))
        assertEquals(listOf(anyChat, cooldown), draft(listOf(anyChat), listOf(reply)).withReplyCooldown(before).constraints)
    }

    @Test
    fun anExistingCooldownIsKeptAsTheUserSetIt() {
        val five = ScriptConstraint("cooldown", mapOf("minutes" to "5"))
        val before = draft(listOf(mom, five), listOf(reply))
        assertEquals(listOf(anyChat, five), draft(listOf(anyChat, five), listOf(reply)).withReplyCooldown(before).constraints)
    }

    @Test
    fun aRemovedCooldownIsNotAddedBack() {
        val before = draft(listOf(mom), listOf(reply))
        val after = draft(listOf(anyChat), listOf(reply)).copy(cooldownRemoved = true).withReplyCooldown(before)
        assertEquals(listOf(anyChat), after.constraints)
    }

    @Test
    fun aDraftThatAlreadyRepliedToEveryoneIsLeftAlone() {
        // A saved shortcut opened as it is: no edge, so nothing is added behind the user's back.
        val saved = draft(listOf(anyChat), listOf(reply))
        assertEquals(listOf(anyChat), saved.copy(name = "x").withReplyCooldown(saved).constraints)
    }
}
