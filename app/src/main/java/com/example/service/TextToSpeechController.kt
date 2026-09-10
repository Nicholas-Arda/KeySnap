package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Owns a single lazily-initialized [TextToSpeech] engine so repeated `text_to_speech` actions
 * don't pay the async engine-startup cost every time. Bridge-side has no equivalent: the detached
 * process would need to bind to the user's chosen TTS engine app, which is the same kind of
 * cross-app audio binding already documented as unreliable for uid 2000 elsewhere in this app.
 */
object TextToSpeechController {
    private const val TAG = "TextToSpeechController"

    @Volatile
    private var engine: TextToSpeech? = null

    private suspend fun ensureInitialized(context: Context): TextToSpeech? {
        engine?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            var created: TextToSpeech? = null
            created = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engine = created
                    continuation.resume(created)
                } else {
                    Log.e(TAG, "TextToSpeech init failed: status=$status")
                    continuation.resume(null)
                }
            }
        }
    }

    suspend fun speak(context: Context, text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val tts = ensureInitialized(context) ?: return false
        return try {
            tts.setLanguage(Locale.getDefault())
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "arda_mapper_${System.currentTimeMillis()}") == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "speak() failed", e)
            false
        }
    }

    fun stop(): Boolean = try {
        engine?.stop() == TextToSpeech.SUCCESS
    } catch (e: Exception) {
        Log.e(TAG, "stop() failed", e)
        false
    }
}
