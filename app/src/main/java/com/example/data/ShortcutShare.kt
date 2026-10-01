package com.example.data

import androidx.annotation.StringRes
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.github.nicholasarda.keysnap.R
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The shared form of a shortcut. Field order is the JSON order, so `keysnap` leads and a person
 * reading the text can tell what it is. No id, key, enabled flag, timing, run history or widget and
 * tile bindings: those belong to one phone.
 */
@JsonClass(generateAdapter = true)
internal data class SharedShortcut(
    val keysnap: Int,
    val name: String,
    val automation: Boolean = false,
    /** Key-less and not an automation: runs from a widget or tile; see [ShortcutScript.isSurfaceOnly]. */
    val surfaceOnly: Boolean = false,
    val pressType: String? = null,
    val actions: List<PersistedScriptAction>,
    val constraints: List<PersistedScriptConstraint> = emptyList(),
)

/** A shortcut decoded from shared text and validated. Nothing about it is saved yet. */
data class ImportedShortcut(
    val name: String,
    val automation: Boolean,
    val pressType: TriggerPressType,
    val actions: List<ScriptAction>,
    val constraints: List<ScriptConstraint>,
    val surfaceOnly: Boolean = false,
) {
    /** CLAUDE.md: an imported shortcut that runs anything privileged is never switched on for the user. */
    val startsEnabled: Boolean get() = actions.none { it.type in ScriptActionCatalog.PRIVILEGED }
}

sealed interface ImportResult {
    data class Ok(val shortcut: ImportedShortcut) : ImportResult
    data class Error(@StringRes val messageRes: Int) : ImportResult
}

/**
 * Turns a shortcut into shareable text and back. [decode] is a trust boundary — its input can come
 * from any app through the share sheet — so it rejects rather than repairs, and it never drops a step
 * or a condition it does not understand: that would change what the shortcut does.
 */
object ShortcutShare {

    /** Bumped only when an older KeySnap could no longer read what a newer one writes. */
    const val FORMAT_VERSION = 1

    /** Far above any real shortcut (the 36-step SOS recipe is about 2 KB); bounds memory for a picked file. */
    const val MAX_LENGTH = 64 * 1024

    private const val MAX_NAME_LENGTH = 80

    /** Bidi embeddings, overrides and isolates: they can make a command display as something it is not. */
    private val HIDDEN = setOf('‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩')

    /**
     * Also refused inside parameters, which the review prints "as written": direction marks and
     * zero-width characters make two different payloads look identical, and CR, NEL and the Unicode
     * line/paragraph separators split a displayed command. Names may keep them (emoji use ZWJ).
     */
    private val HIDDEN_IN_PARAMS = HIDDEN + setOf(
        '\u200E', '\u200F', '\u061C', '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u2028', '\u2029',
    )

    private fun hiddenInParam(c: Char) = c in HIDDEN_IN_PARAMS || (c.isISOControl() && c != '\n' && c != '\t')

    /**
     * Action parameters that point at something on this phone only: the wallpaper image is a
     * content:// read grant held by this install, and a sound is a picked file or ringtone here, or a
     * URL a crafted share could use to make every press contact a host the user never typed. The
     * recipient's editor asks for their own. A message's contact is a third party's name and number:
     * personal data that means nothing on another phone, so it never leaves this one.
     */
    private val PHONE_BOUND_PARAMS: Map<String, Set<String>> = mapOf(
        "set_wallpaper" to setOf("imageUri"),
        "play_sound" to setOf("uri"),
        "send_message" to setOf("contactName", "number"),
        // A chat is a third party's name too. Left empty, a reply answers whichever chat triggered
        // it; PRIVILEGED keeps such an import switched off and the review shows the every-chat warning.
        "reply_to_notification" to setOf("chat"),
    )

    private val adapter = Moshi.Builder().build().adapter(SharedShortcut::class.java)
    private val invalid = ImportResult.Error(R.string.import_error_invalid)
    private val unsupported = ImportResult.Error(R.string.import_error_unsupported)

    fun encode(
        name: String,
        automation: Boolean,
        pressType: TriggerPressType,
        actions: List<ScriptAction>,
        constraints: List<ScriptConstraint>,
        surfaceOnly: Boolean = false,
    ): String = adapter.toJson(
        SharedShortcut(
            keysnap = FORMAT_VERSION,
            name = name,
            automation = automation,
            surfaceOnly = surfaceOnly,
            pressType = pressType.name,
            actions = actions.map { PersistedScriptAction(it.type, portableParams(it.type, it.params)) },
            // A Bluetooth device's address and name identify this phone's pairing and mean nothing on
            // another; the recipient's editor asks for their own device instead.
            constraints = constraints.map {
                PersistedScriptConstraint(
                    it.type,
                    if (it.type.startsWith("bluetooth_device_")) it.params - setOf("address", "name") else portableConstraintParams(it.type, it.params),
                )
            },
        ),
    )

    fun decode(text: String): ImportResult {
        if (text.length > MAX_LENGTH) return ImportResult.Error(R.string.import_error_too_large)
        // The share intro and chat apps wrap the JSON in text of their own.
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) return invalid
        val shared = runCatching { adapter.fromJson(text.substring(start, end + 1)) }.getOrNull() ?: return invalid
        if (shared.keysnap > FORMAT_VERSION) return ImportResult.Error(R.string.import_error_newer)
        if (shared.keysnap != FORMAT_VERSION || shared.actions.isEmpty() || hasHiddenNull(shared)) return invalid
        if (shared.automation && shared.surfaceOnly) return invalid
        // The editor refuses to save these on anything but an automation (MainViewModel.commitDraft);
        // an import must not be a way around that.
        if (!shared.automation && shared.constraints.any { it.type in ScriptConstraintCatalog.AUTOMATION_ONLY }) return invalid
        val paramTexts = (shared.actions.map { it.params } + shared.constraints.map { it.params }).flatMap { it.keys + it.values }
        if (shared.name.any { it in HIDDEN } || paramTexts.any { value -> value.any(::hiddenInParam) }) {
            return ImportResult.Error(R.string.import_error_hidden)
        }
        val pressType = shared.pressType?.let { name ->
            TriggerPressType.entries.firstOrNull { it.name == name } ?: return invalid
        } ?: TriggerPressType.SINGLE_PRESS
        val actions = shared.actions.map { action ->
            val descriptor = ScriptActionCatalog.descriptor(action.type)
                ?.takeIf { it.availability == ActionAvailability.AVAILABLE }
                ?: return unsupported
            val specs = descriptor.parameters.associateBy { it.key }
            // Undeclared keys are inert today but would be invisible in the review, so they stay out.
            val params = portableParams(action.type, action.params.filterKeys { it in specs })
            if (params.any { (key, value) -> !isValid(specs.getValue(key), value) }) return invalid
            ScriptAction(action.type, params)
        }
        val constraints = shared.constraints.map {
            if (!ScriptConstraintCatalog.isAvailable(it.type)) return unsupported
            ScriptConstraint(it.type, portableConstraintParams(it.type, it.params))
        }
        val name = shared.name.filterNot { it.isISOControl() }.trim().take(MAX_NAME_LENGTH)
        return ImportResult.Ok(ImportedShortcut(name, shared.automation, pressType, actions, constraints, shared.surfaceOnly))
    }

    /**
     * Reads at most [MAX_LENGTH] + 1 bytes, so a huge or binary file costs nothing to turn away;
     * [decode] rejects anything that long. `InputStream.readNBytes` needs API 33 and minSdk is 30.
     */
    fun readCapped(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (out.size() <= MAX_LENGTH) {
            val read = input.read(chunk, 0, minOf(chunk.size, MAX_LENGTH + 1 - out.size()))
            if (read < 0) break
            out.write(chunk, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun portableParams(type: String, params: Map<String, String>): Map<String, String> =
        PHONE_BOUND_PARAMS[type]?.let { params - it } ?: params

    /**
     * A feed address can carry a private token, and from a crafted share it would make this phone
     * poll a host the user never typed every 15 minutes. A calendar's name is often its owner's
     * email address. Neither means anything on another phone; the recipient's editor asks for
     * their own. An event-title filter is the shortcut's own logic and travels.
     */
    private fun portableConstraintParams(type: String, params: Map<String, String>): Map<String, String> = when (type) {
        ScriptConstraintCatalog.RSS_NEW_ITEM -> params - "url"
        ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE -> params - "calendar"
        // A contact's name is a third party's data, like a message's recipient.
        ScriptConstraintCatalog.NOTIFICATION_RECEIVED -> params - "contact"
        else -> params
    }

    /** [action] as it travels in a share, so the share review shows what actually leaves the phone. */
    fun portable(action: ScriptAction): ScriptAction = action.copy(params = portableParams(action.type, action.params))

    /** Moshi does not enforce nullability inside lists and maps, so a crafted file can hide a null there. */
    private fun hasHiddenNull(shared: SharedShortcut): Boolean {
        val items: List<Any?> = shared.actions + shared.constraints
        if (null in items) return true
        val values: List<Any?> = shared.actions.flatMap { it.params.values } + shared.constraints.flatMap { it.params.values }
        return null in values
    }

    /** Blank is allowed (the editor then shows "Tap to configure"); anything else must fit its kind. */
    private fun isValid(spec: ActionParameterSpec, value: String): Boolean = value.isBlank() || when (spec.kind) {
        ActionParameterKind.CHOICE -> value in spec.choices
        ActionParameterKind.INTEGER, ActionParameterKind.MILLISECONDS, ActionParameterKind.KEY_CODE ->
            value.trim().toLongOrNull() != null
        else -> true
    }
}
