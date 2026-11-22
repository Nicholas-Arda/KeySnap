package com.example.service.bridge

import android.content.Context
import android.util.Log
import com.example.bridge.BridgeConfig
import com.example.bridge.BridgeProtocol
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * Owns everything the detached bridge reads from disk.
 *
 * The payload lives in the app's external files directory because that is the one location both
 * UIDs can reach: the app owns it, and the shell UID is a member of `ext_data_rw`. The reverse
 * direction is not usable — a file created by the shell there is shell-owned and unreadable by
 * the app — so the bridge never creates files here. The mode file is an app-created inode
 * the bridge only opens; other results are reported back over the loopback
 * socket whose port and token are handed to it in this config.
 */
class BridgePayload(context: Context) {

    private val appContext = context.applicationContext

    /** This build's shell-side directory; see [BridgeProtocol.baseDirFor]. */
    val baseDir: String = BridgeProtocol.baseDirFor(appContext.packageName)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Every process of ours reaches the bridge through a payload, cold widget, tile, alarm and place
        // runs included, so a pre-63 bridge loses its config here first. Nothing writes that name again.
        val legacy = appContext.getExternalFilesDir(null)?.let { File(File(it, PAYLOAD_DIR), BridgeProtocol.LEGACY_CONFIG_NAME) }
        if (legacy?.delete() == true) BridgeController.staleReplaced.set(true)
    }

    companion object {
        private const val TAG = "BridgePayload"
        private const val PREFS_NAME = "arda_mapper_bridge_prefs"
        private const val PREF_PORT = "control_port"
        private const val PREF_TOKEN = "control_token_v63"
        private const val PREF_SENSITIVE_RESTORE = "sensitive_restore_nonce"

        /**
         * The token of app versions before protocol 63, which sent it in clear on every connection, so
         * it may have been captured. Never read again; deleted when its replacement is generated.
         */
        private const val PREF_TOKEN_CLEARTEXT = "control_token"

        private const val PAYLOAD_DIR = "bridge"
        private const val ASSET_DEX = "bridge.dex"

        /** Ephemeral-range port picked once and reused so a restarted app finds its own bridge. */
        private const val PORT_MIN = 38_200
        private const val PORT_RANGE = 500

        /**
         * Guards first-time generation of the port and token.
         *
         * These are read from several coroutines at once during startup (liveness probe, config
         * write, spawn). With an asynchronous `apply()` each caller would still see the default
         * and mint its own value, so the app would write one port into the config and then probe
         * a different one. The lock plus a synchronous `commit()` makes the first generated value
         * immediately visible to every subsequent reader.
         */
        private val credentialLock = Any()

        /**
         * start.sh's step before the dex copy: kills the pre-63 bridge the legacy pid file names, waits
         * up to 2 s for it to go, then deletes the file. Only pre-63 bridges wrote it, so this can never
         * hit a bridge another start.sh just spawned: the respawn after an update lands while the old
         * one still holds the port, and bridges before 2026-10-15 never exit on their own. A pid that
         * no longer names a bridge is left alone, and a missing, empty or non-numeric file (never `-1`,
         * which would signal every shell process) does nothing.
         */
        internal val KILL_LEFTOVER_BRIDGE =
            "PID=\$(cat \"\$BASE/${BridgeProtocol.LEGACY_PID_NAME}\" 2>/dev/null)\n" +
                "case \"\$PID\" in ''|*[!0-9]*) PID= ;; esac\n" +
                "i=0\n" +
                "while [ -n \"\$PID\" ] && [ \$i -lt 20 ] && grep -qF ${BridgeProtocol.NICE_NAME} \"/proc/\$PID/cmdline\" 2>/dev/null; do\n" +
                "    kill -9 \"\$PID\" 2>/dev/null\n" +
                "    sleep 0.1\n" +
                "    i=\$((i + 1))\n" +
                "done\n" +
                "rm -f \"\$BASE/${BridgeProtocol.LEGACY_PID_NAME}\"\n"
    }

    /** Stable for the lifetime of the install; regenerated only if the prefs are cleared. */
    val controlPort: Int
        get() = synchronized(credentialLock) {
            val stored = prefs.getInt(PREF_PORT, 0)
            if (stored in PORT_MIN until (PORT_MIN + PORT_RANGE)) {
                return@synchronized stored
            }
            val generated = PORT_MIN + SecureRandom().nextInt(PORT_RANGE)
            prefs.edit().putInt(PREF_PORT, generated).commit()
            generated
        }

    val controlToken: String
        get() = synchronized(credentialLock) {
            val stored = prefs.getString(PREF_TOKEN, null)
            if (!stored.isNullOrBlank()) {
                return@synchronized stored
            }
            val generated = generateToken()
            prefs.edit().remove(PREF_TOKEN_CLEARTEXT).putString(PREF_TOKEN, generated).commit()
            generated
        }

    /**
     * Sensitive apps' "Restore now": the bridge acts on a change of this number in the config, so it
     * is never cleared, and a fresh process rewriting the config does not read as a new tap.
     */
    fun requestSensitiveRestore() {
        prefs.edit().putLong(PREF_SENSITIVE_RESTORE, prefs.getLong(PREF_SENSITIVE_RESTORE, 0L) + 1).commit()
    }

    private fun generateToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Null when external storage is unavailable, which makes the bridge unreachable. */
    fun payloadDir(): File? {
        val external = appContext.getExternalFilesDir(null) ?: return null
        val dir = File(external, PAYLOAD_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create payload dir $dir")
            return null
        }
        return dir
    }

    fun configFile(): File? = payloadDir()?.let { File(it, BridgeProtocol.CONFIG_NAME) }

    /** Whether a config is on disk, without creating the directory to find out. */
    fun hasConfig(): Boolean =
        appContext.getExternalFilesDir(null)?.let { File(File(it, PAYLOAD_DIR), BridgeProtocol.CONFIG_NAME).exists() } == true

    fun startScriptFile(): File? = payloadDir()?.let { File(it, BridgeProtocol.START_SCRIPT_NAME) }

    /**
     * Copies the dex out of assets and (re)writes the launcher script. Both are rewritten on
     * every install so an app update always ships a matching payload to the shell side.
     */
    fun install(): Boolean {
        val dir = payloadDir() ?: return false
        return try {
            val dexTarget = File(dir, BridgeProtocol.DEX_NAME)
            appContext.assets.open(ASSET_DEX).use { input ->
                dexTarget.outputStream().use(input::copyTo)
            }
            writeStartScript(dir, dexTarget)
            Log.d(TAG, "Bridge payload installed at $dir (${dexTarget.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install bridge payload", e)
            false
        }
    }

    /**
     * Removes everything the app wrote for the bridge: the dex, the launcher script and the
     * config with its control token. The bridge clears its own /data/local/tmp directory as it
     * exits, which is the half the app UID cannot reach.
     */
    fun uninstall() {
        payloadDir()?.listFiles()?.forEach { it.delete() }
    }

    /**
     * The dex is copied to /data/local/tmp before launching: app_process must load it as the
     * shell UID, and executing code straight off the FUSE-backed external volume is not reliable.
     */
    private fun writeStartScript(dir: File, dexSource: File) {
        val configPath = File(dir, BridgeProtocol.CONFIG_NAME).absolutePath
        val base = baseDir
        val script = buildString {
            append("#!/system/bin/sh\n")
            append("BASE=").append(base).append('\n')
            append("mkdir -p \"\$BASE\" || exit 1\n")
            // Owner only: the log and SendButton's window dump must not be readable by other apps.
            append("chmod 700 \"\$BASE\"\n")
            append(KILL_LEFTOVER_BRIDGE)
            append("cp \"").append(dexSource.absolutePath).append("\" \"\$BASE/")
                .append(BridgeProtocol.DEX_NAME).append("\" || exit 1\n")
            append("chmod 644 \"\$BASE/").append(BridgeProtocol.DEX_NAME).append("\"\n")
            append("export CLASSPATH=\"\$BASE/").append(BridgeProtocol.DEX_NAME).append("\"\n")
            append("exec app_process /system/bin --nice-name=").append(BridgeProtocol.NICE_NAME)
                .append(' ').append(BridgeProtocol.MAIN_CLASS)
                .append(" --config \"").append(configPath).append("\" --base \"\$BASE\"\n")
        }
        File(dir, BridgeProtocol.START_SCRIPT_NAME).writeText(script)
    }

    /**
     * Rewrites the config the bridge polls. Written to a temp file in the same directory and
     * renamed so the bridge never observes a half-written document.
     */
    fun writeConfig(
        scripts: List<ShortcutScript>,
        globallyEnabled: Boolean,
        config: KeyMappingConfig,
    ): Boolean {
        val target = configFile() ?: return false
        return try {
            com.example.mode.ModeHooks.ensureState(appContext)
            val root = JSONObject()
            root.put("version", BridgeProtocol.VERSION)
            root.put("packageName", appContext.packageName)
            root.put("port", controlPort)
            root.put("token", controlToken)
            root.put("globallyEnabled", globallyEnabled)
            root.put("foregroundReadable", com.example.service.hasUsageAccess(appContext))
            root.put("capsuleFeedbackEnabled", com.example.capsule.CapsuleHooks.isEnabled(appContext) &&
                com.example.data.EntitlementManager.getInstance(appContext).isPro.value)
            root.put("doublePressWindowMs", config.doublePressWindowMs)
            root.put("longPressThresholdMs", config.longPressThresholdMs)
            root.put("vibrateOnTrigger", config.vibrateOnTrigger)
            root.put("hapticDurationMs", config.hapticDurationMs)
            // The bridge enforces Settings → Sensitive apps; the app only lists them (none while the master is off).
            val sensitiveApps = JSONArray()
            com.example.service.SensitiveAppsGate.activeApps(appContext).forEach { app ->
                sensitiveApps.put(
                    JSONObject()
                        .put("packageName", app.packageName)
                        .put("hideDeveloperOptions", app.hideDeveloperOptions)
                        .put("hideAccessibility", app.hideAccessibility)
                        .put("hideWirelessDebugging", app.hideWirelessDebugging),
                )
            }
            root.put("sensitiveApps", sensitiveApps)
            root.put("sensitiveRestoreNonce", prefs.getLong(PREF_SENSITIVE_RESTORE, 0L))

            val scriptArray = JSONArray()
            // Only key shortcuts: the bridge drops a trigger-less script anyway, and an automation's
            // conditions (a feed address, a calendar name) have no business in a shell-readable file.
            // Automations and widget/tile runs reach the bridge as RUN requests carrying their actions.
            scripts.forEach { script ->
                // Assistant invocations originate in the app, never in getevent. Mixed scripts
                // retain their physical triggers; the assistant's actions arrive through RUN.
                val physicalTriggers = script.triggers.filterNot {
                    com.example.bridge.KeyEventMapping.ASSISTANT_GESTURE in it.keyCodes
                }
                if (physicalTriggers.isEmpty()) return@forEach
                val triggers = JSONArray()
                physicalTriggers.forEach { trigger ->
                    val triggerJson = JSONObject()
                        .put("keyCodes", JSONArray().apply { trigger.keyCodes.forEach(::put) })
                        .put("pressType", trigger.pressType.name)
                    if (trigger.doublePressWindowMs != null) triggerJson.put("doublePressWindowMs", trigger.doublePressWindowMs)
                    if (trigger.longPressThresholdMs != null) triggerJson.put("longPressThresholdMs", trigger.longPressThresholdMs)
                    triggers.put(triggerJson)
                }
                val actions = JSONArray()
                script.actions.forEach { action -> actions.put(action.toBridgeJson()) }
                val constraints = JSONArray()
                script.constraints.forEach { constraint ->
                    val paramsJson = JSONObject()
                    // A place goes in as its side alone: the shell can read this file, and where
                    // someone's home is has no business in it. Rewritten on every side change.
                    val params = if (constraint.type == ScriptConstraintCatalog.AT_PLACE) {
                        val inside = com.example.service.PlaceCondition.knownInside(appContext, constraint.params)
                        constraint.params.filterKeys { it == com.example.bridge.ConstraintNegation.PARAM } +
                            (inside?.let { mapOf(BridgeConfig.PARAM_INSIDE to it.toString()) } ?: emptyMap())
                    } else {
                        constraint.params
                    }
                    params.forEach { (key, value) -> paramsJson.put(key, value) }
                    constraints.put(JSONObject().put("type", constraint.type).put("params", paramsJson))
                }
                val scriptJson = JSONObject()
                    .put("id", script.id)
                    .put("name", script.name)
                    .put("enabled", script.enabled)
                    .put("triggers", triggers)
                    .put("actions", actions)
                    .put("constraints", constraints)
                // Absent = defer to the global default, like the null override in the app.
                script.vibrateOnTrigger?.let { scriptJson.put("vibrateOnTrigger", it) }
                script.hapticDurationMs?.let { scriptJson.put("hapticDurationMs", it) }
                script.hapticPattern?.let { scriptJson.put("hapticPattern", it) }
                if (script.matchAny) scriptJson.put("matchAny", true)
                scriptArray.put(scriptJson)
            }
            root.put("scripts", scriptArray)

            val temp = File(target.parentFile, "${BridgeProtocol.CONFIG_NAME}.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(target)) {
                // renameTo can fail on the FUSE-backed volume; fall back to a direct rewrite.
                target.writeText(root.toString())
                temp.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write bridge config", e)
            false
        }
    }

    /**
     * The command sent over the authorized ADB shell. `setsid` moves the bridge into its own
     * session so closing the ADB stream cannot deliver SIGHUP, and redirecting all three standard
     * streams detaches it from the transport entirely. Verified on device: the process reparents
     * to init and survives both the ADB stream closing and the app being force-stopped.
     */
    fun spawnCommand(): String? {
        val script = startScriptFile() ?: return null
        val base = baseDir
        return "mkdir -p $base; chmod 700 $base; " +
            "nohup setsid sh '${script.absolutePath}' </dev/null >$base/spawn.log 2>&1 & " +
            "sleep 1; " +
            "echo ${BridgeProtocol.SPAWN_MARKER} spawned"
    }
}

/**
 * The action as the bridge receives it, in the config file and in a Run-once request. Types the
 * bridge never runs keep their slot (results are reported by position) but carry no params, so
 * message text, numbers and reply text are not copied into a file the shell UID can read.
 */
internal fun ScriptAction.toBridgeJson(): JSONObject {
    val paramsJson = JSONObject()
    if (type !in ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE) {
        params.forEach { (key, value) -> paramsJson.put(key, value) }
    }
    return JSONObject().put("type", type).put("params", paramsJson)
}
