package com.example.assistant

/** Activity candidates only. A VoiceInteractionService is discoverable but cannot be started
 * as another app's active assistant; its package may instead expose an assist activity/launcher. */
internal data class AssistantTarget(
    val packageName: String,
    val className: String,
    val action: String,
    val kind: Kind,
) {
    enum class Kind { OVERLAY, ASSIST, VOICE_COMMAND, LAUNCHER }
}

// Gemini's own app first: while KeySnap holds the assistant role, the Google app's assist
// entry points open the old Google Assistant instead of Gemini (POCO/HyperOS, 2026-10-24).
internal val assistantPackagePriority = listOf(
    "com.google.android.apps.bard",
    "com.google.android.googlequicksearchbox",
    "com.openai.chatgpt",
    "com.anthropic.claude",
    "com.heytap.speechassist",
    "com.coloros.speechassist",
    "com.samsung.android.bixby.agent",
)

internal fun orderedAssistantTargets(targets: List<AssistantTarget>, ownPackage: String): List<AssistantTarget> =
    targets.filter { it.packageName != ownPackage }.distinct().sortedWith(
        compareBy<AssistantTarget> {
            assistantPackagePriority.indexOf(it.packageName).takeIf { rank -> rank >= 0 } ?: Int.MAX_VALUE
        }.thenBy { it.kind.ordinal }
            .thenBy { it.packageName }.thenBy { it.className },
    )
