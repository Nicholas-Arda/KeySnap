package com.example.service.bridge

import android.content.Context
import android.util.Log
import com.example.bridge.BridgeProtocol
import com.example.data.KeyMappingConfig
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
 * the app — so the bridge never writes here. It only reads, and reports back over the loopback
 * socket whose port and token are handed to it in this config.
 */
class BridgePayload(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "BridgePayload"
        private const val PREFS_NAME = "arda_mapper_bridge_prefs"
        private const val PREF_PORT = "control_port"
        private const val PREF_TOKEN = "control_token"

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
            prefs.edit().putString(PREF_TOKEN, generated).commit()
            generated
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
        val base = BridgeProtocol.BASE_DIR
        val script = buildString {
            append("#!/system/bin/sh\n")
            append("BASE=").append(base).append('\n')
            append("mkdir -p \"\$BASE\" || exit 1\n")
            append("chmod 755 \"\$BASE\"\n")
            append("cp \"").append(dexSource.absolutePath).append("\" \"\$BASE/")
                .append(BridgeProtocol.DEX_NAME).append("\" || exit 1\n")
            append("chmod 644 \"\$BASE/").append(BridgeProtocol.DEX_NAME).append("\"\n")
            append("export CLASSPATH=\"\$BASE/").append(BridgeProtocol.DEX_NAME).append("\"\n")
            append("exec app_process /system/bin --nice-name=").append(BridgeProtocol.NICE_NAME)
                .append(' ').append(BridgeProtocol.MAIN_CLASS)
                .append(" --config \"").append(configPath).append("\"\n")
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
            val root = JSONObject()
            root.put("version", BridgeProtocol.VERSION)
            root.put("packageName", appContext.packageName)
            root.put("port", controlPort)
            root.put("token", controlToken)
            root.put("globallyEnabled", globallyEnabled)
            root.put("doublePressWindowMs", config.doublePressWindowMs)
            root.put("longPressThresholdMs", config.longPressThresholdMs)

            val scriptArray = JSONArray()
            scripts.forEach { script ->
                val triggers = JSONArray()
                script.triggers.forEach { trigger ->
                    val triggerJson = JSONObject()
                        .put("keyCodes", JSONArray().apply { trigger.keyCodes.forEach(::put) })
                        .put("pressType", trigger.pressType.name)
                    if (trigger.doublePressWindowMs != null) triggerJson.put("doublePressWindowMs", trigger.doublePressWindowMs)
                    if (trigger.longPressThresholdMs != null) triggerJson.put("longPressThresholdMs", trigger.longPressThresholdMs)
                    triggers.put(triggerJson)
                }
                val actions = JSONArray()
                script.actions.forEach { action ->
                    val paramsJson = JSONObject()
                    action.params.forEach { (key, value) -> paramsJson.put(key, value) }
                    actions.put(JSONObject().put("type", action.type).put("params", paramsJson))
                }
                val constraints = JSONArray()
                script.constraints.forEach { constraint ->
                    val paramsJson = JSONObject()
                    constraint.params.forEach { (key, value) -> paramsJson.put(key, value) }
                    constraints.put(JSONObject().put("type", constraint.type).put("params", paramsJson))
                }
                scriptArray.put(
                    JSONObject()
                        .put("id", script.id)
                        .put("name", script.name)
                        .put("enabled", script.enabled)
                        .put("triggers", triggers)
                        .put("actions", actions)
                        .put("constraints", constraints),
                )
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
        val base = BridgeProtocol.BASE_DIR
        return "mkdir -p $base; " +
            "nohup setsid sh '${script.absolutePath}' </dev/null >$base/spawn.log 2>&1 & " +
            "sleep 1; " +
            "echo ${BridgeProtocol.SPAWN_MARKER} spawned"
    }
}
