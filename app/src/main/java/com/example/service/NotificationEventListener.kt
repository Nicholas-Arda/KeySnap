package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository

/**
 * Notification access, used only by the "Notification received" condition and the "Reply to
 * notification" action. A notification's text is read only for an app some condition names,
 * matched in memory and dropped; [NotificationMatches] keeps the time of a match and nothing else.
 * Nothing here may log a title, text, sender, chat or reply.
 */
class NotificationEventListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        // Ongoing (media, progress) and group summaries re-post constantly and carry no message.
        if (sbn.isOngoing || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        onPosted(this, sbn.packageName) {
            val extras = notification.extras
            listOfNotNull(
                extras.getCharSequence(Notification.EXTRA_TITLE),
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            ).joinToString("\n")
        }
    }

    companion object {
        private const val TAG = "NotificationListener"

        /** Null while notification access is off or the system has unbound the listener. */
        @Volatile private var instance: NotificationEventListener? = null

        /**
         * Sends `text` through a chat notification's own Reply button, like replying from the
         * shade. False when access is off, no notification from `packageName` has a Reply
         * button, or `chat` matches none. Never logs the text, the chat or the app.
         */
        fun reply(context: Context, params: Map<String, String>): Boolean {
            val listener = instance ?: return false
            val packageName = params["packageName"]?.trim().orEmpty()
            val text = params["text"].orEmpty()
            if (packageName.isEmpty() || text.isBlank()) return false
            val active = try {
                listener.activeNotifications.orEmpty()
            } catch (_: SecurityException) {
                return false
            }
            val candidates = active.filter { it.packageName == packageName }.mapNotNull { sbn ->
                val action = replyActionOf(sbn.notification) ?: return@mapNotNull null
                val extras = sbn.notification.extras
                val title = listOfNotNull(
                    extras.getCharSequence(Notification.EXTRA_TITLE),
                    extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
                ).joinToString(" ")
                ReplyCandidate(title, sbn.postTime, action)
            }
            val target = pickReplyTarget(candidates, params["chat"]) ?: return false
            return send(context, target.action, text)
        }

        private fun replyActionOf(notification: Notification): Notification.Action? =
            (notification.actions.orEmpty().toList() + Notification.WearableExtender(notification).actions)
                .firstOrNull { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }

        private fun send(context: Context, action: Notification.Action, text: String): Boolean {
            val inputs = action.remoteInputs ?: return false
            val pendingIntent = action.actionIntent ?: return false
            val fillIn = Intent()
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            RemoteInput.addResultsToIntent(inputs, fillIn, results)
            return try {
                pendingIntent.send(context, 0, fillIn)
                true
            } catch (_: PendingIntent.CanceledException) {
                Log.w(TAG, "Reply target was cancelled before sending")
                false
            }
        }

        fun isAccessGranted(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(ComponentName(context, NotificationEventListener::class.java)) == true

        /** KeySnap's own toggle; some OEM builds lack this screen, so callers fall back to the list. */
        fun accessSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NotificationEventListener::class.java).flattenToString(),
            )

        fun openAccessSettings(context: Context) {
            try {
                context.startActivity(accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        /** Split from the callback so the edge behaviour is testable without a StatusBarNotification. */
        internal fun onPosted(context: Context, packageName: String, readContent: () -> String) {
            // KeySnap's own notifications (show_notification, run-failure alerts) never count, or a
            // shortcut that shows one could trigger itself.
            if (packageName == context.packageName) return
            val conditions = ScriptRepository.getInstance(context).scripts.value
                .flatMap { it.constraints }
                .filter { it.type == ScriptConstraintCatalog.NOTIFICATION_RECEIVED }
            if (NotificationMatches.record(conditions, packageName, SystemClock.elapsedRealtime(), readContent)) {
                HardwareKeyTriggerCoordinator.reevaluateAutomations()
                // Nothing announces the window closing; without this re-check the condition would
                // still read true at the next match, and that match would not be an edge.
                HardwareKeyTriggerCoordinator.reevaluateAutomationsAfter(NotificationMatches.WINDOW_MS + 50)
            }
        }
    }
}
