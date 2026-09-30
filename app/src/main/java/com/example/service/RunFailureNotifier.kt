package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.RunFailure
import com.example.data.RunRecord
import com.example.data.ScriptActionCatalog
import com.example.data.ShortcutScript
import io.github.nicholasarda.keysnap.R

/**
 * Says when a shortcut's action could not run, and why, so nothing stops silently. One notification
 * per shortcut: a repeat failure updates it without alerting again, and a clean run clears it.
 * Tapping it opens that shortcut in the editor, whose "Recent runs" card has the details.
 */
object RunFailureNotifier {
    const val CHANNEL_ID = "run_failures"
    const val EXTRA_SCRIPT_ID = "io.github.nicholasarda.keysnap.extra.OPEN_SCRIPT_ID"
    private const val NOTIFICATION_ID = 3001

    fun onRun(context: Context, script: ShortcutScript, record: RunRecord) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val tag = "run_failure:${script.id}"
        val failures = record.actions.filterNot { it.ok }
        if (failures.isEmpty()) {
            manager.cancel(tag, NOTIFICATION_ID)
            return
        }
        // Blocked, or Android 13+ without POST_NOTIFICATIONS: the run log still has it.
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_run_failures),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_run_failures_description) },
        )
        val lines = failures.map { failure ->
            val title = ScriptActionCatalog.descriptor(failure.type)?.let { context.getString(it.titleRes) } ?: failure.type
            "$title · ${context.getString(RunFailure.from(failure.reason).labelRes)}"
        }
        val open = PendingIntent.getActivity(
            context,
            script.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SCRIPT_ID, script.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val name = script.name.ifBlank { context.getString(R.string.scripts_unnamed) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_run_failed_title, name))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        manager.notify(tag, NOTIFICATION_ID, notification)
    }
}
