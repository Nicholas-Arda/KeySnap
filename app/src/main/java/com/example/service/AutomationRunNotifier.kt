package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.RunNotice
import com.example.data.RunRecord
import com.example.data.ShortcutScript
import io.github.nicholasarda.keysnap.R

/**
 * An automation's own "Notifications" (off by default): one quiet notice when it runs and one when
 * "Undo when it ends" puts settings back. Only the user's words or the shortcut's name go in, never
 * what triggered it (a notification's text, a contact). Each replaces the last for that automation.
 * A run with a failed action is left to [RunFailureNotifier].
 */
object AutomationRunNotifier {
    const val CHANNEL_ID = "automation_runs"
    private const val RUN_ID = 3002
    private const val UNDO_ID = 3003

    fun onRun(context: Context, script: ShortcutScript, record: RunRecord) {
        if (!script.automation || !script.notice.onRun || record.actions.any { !it.ok }) return
        if (!NotificationPrefs.enabled(context, NotificationPrefs.Kind.AUTOMATION_RUNS)) return
        val words = runWords(context, script.name, script.notice, record.actions.size)
        post(context, script, "auto_run:${script.id}", RUN_ID, words)
    }

    fun onUndo(context: Context, script: ShortcutScript) {
        if (!script.automation || !script.notice.onUndo) return
        if (!NotificationPrefs.enabled(context, NotificationPrefs.Kind.AUTOMATION_RUNS)) return
        post(context, script, "auto_undo:${script.id}", UNDO_ID, undoWords(context, script.name, script.notice))
    }

    /**
     * Title and text of the run notice: the shortcut's own words, else the Settings → Notifications
     * default, else the built-in one. Blank words follow the shortcut's current name.
     */
    fun runWords(context: Context, name: String, notice: RunNotice, actions: Int): Pair<String, String> =
        notice.runTitle.ifBlank { global(context, NotificationPrefs.RUN_TITLE, name) ?: context.getString(R.string.notice_run_title_default, displayName(context, name)) } to
            notice.runText.ifBlank { global(context, NotificationPrefs.RUN_TEXT, name) ?: context.resources.getQuantityString(R.plurals.notice_run_text_default, actions, actions) }

    fun undoWords(context: Context, name: String, notice: RunNotice): Pair<String, String> =
        notice.undoTitle.ifBlank { global(context, NotificationPrefs.UNDO_TITLE, name) ?: context.getString(R.string.notice_undo_title_default, displayName(context, name)) } to
            notice.undoText.ifBlank { global(context, NotificationPrefs.UNDO_TEXT, name) ?: context.getString(R.string.notice_undo_text_default) }

    private fun global(context: Context, key: String, name: String): String? =
        NotificationPrefs.text(context, key).takeIf(String::isNotEmpty)?.replace(NotificationPrefs.NAME, displayName(context, name))

    private fun displayName(context: Context, name: String) = name.ifBlank { context.getString(R.string.scripts_unnamed) }

    private fun post(context: Context, script: ShortcutScript, tag: String, id: Int, words: Pair<String, String>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Blocked, or Android 13+ without POST_NOTIFICATIONS: nothing to show.
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_automation_runs), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(RunFailureNotifier.EXTRA_SCRIPT_ID, script.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(words.first)
            .setContentText(words.second)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(tag, id, notification)
    }
}
