package com.example.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.widget.Toast
import io.github.nicholasarda.keysnap.R

object AssistantIntegration {
    // Platform SystemApi intent (not part of the public SDK constants).
    private const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"
    internal val invocations = AssistantInvocationGate()

    fun isSelected(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true ||
            VoiceInteractionService.isActiveService(context, ComponentName(context, KeySnapVoiceService::class.java))

    /** ROLE_ASSISTANT is selected in Settings; do not request it through createRequestRoleIntent. */
    fun openSettings(context: Context) {
        for (action in listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            if (start(context, Intent(action))) return
        }
    }

    fun launch(context: Context, action: String = Intent.ACTION_ASSIST): Boolean {
        // Mark BEFORE startActivity: the framework can synchronously start our voice session.
        invocations.markOutgoing(SystemClock.elapsedRealtime())
        if (!isSelected(context)) return start(context, Intent(action))
        for (target in discoverTargets(context)) {
            val intent = Intent(target.action).setComponent(ComponentName(target.packageName, target.className))
            if (target.kind == AssistantTarget.Kind.LAUNCHER) intent.addCategory(Intent.CATEGORY_LAUNCHER)
            if (start(context, intent)) return true
        }
        Toast.makeText(context, R.string.assistant_no_other_app, Toast.LENGTH_SHORT).show()
        return false
    }

    @Suppress("DEPRECATION")
    private fun discoverTargets(context: Context): List<AssistantTarget> {
        val pm = context.packageManager
        val candidates = mutableListOf<AssistantTarget>()
        val packages = assistantPackagePriority.toMutableSet()
        pm.queryIntentServices(Intent(VoiceInteractionService.SERVICE_INTERFACE), 0).forEach {
            it.serviceInfo?.takeIf { info -> info.enabled && info.exported }?.let { info -> packages += info.packageName }
        }
        // VOICE_ASSIST is the platform's voice-assistant entry intent. Resolve it to an exported
        // component instead of relying on a private activity name that changes between app builds.
        listOf(
            ACTION_VOICE_ASSIST to AssistantTarget.Kind.OVERLAY,
            Intent.ACTION_ASSIST to AssistantTarget.Kind.ASSIST,
            Intent.ACTION_VOICE_COMMAND to AssistantTarget.Kind.VOICE_COMMAND,
        ).forEach { (action, kind) ->
            // Explicit components do not need CATEGORY_DEFAULT; some OEM overlays omit it.
            pm.queryIntentActivities(Intent(action), 0).forEach activity@{ result ->
                val info = result.activityInfo ?: return@activity
                if (info.enabled && info.exported && info.applicationInfo.enabled &&
                    (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
                ) {
                    packages += info.packageName
                    candidates += AssistantTarget(info.packageName, info.name, action, kind)
                }
            }
        }
        packages.filter { it != context.packageName }.forEach { packageName ->
            pm.getLaunchIntentForPackage(packageName)?.component?.let { component ->
                candidates += AssistantTarget(packageName, component.className, Intent.ACTION_MAIN, AssistantTarget.Kind.LAUNCHER)
            }
        }
        return orderedAssistantTargets(candidates, context.packageName)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        // Installed packages, component names and intent extras never enter logs.
        false
    }
}
