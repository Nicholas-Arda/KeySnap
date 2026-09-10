package com.example.service.adb

import com.example.bridge.KeyEventMapping

/** A parsed Linux input event produced by `getevent -l`. */
data class ParsedGetEventKey(
    val device: String?,
    val kernelName: String,
    val linuxCode: Int?,
    val androidKeyCode: Int,
    val action: Action,
    /** True for contact/stylus switches emitted by a touchscreen, not a mappable hardware button. */
    val isTouchContact: Boolean,
) {
    enum class Action { DOWN, UP, REPEAT }
}

/**
 * Parser kept independent from the ADB transport so kernel output can be unit tested.
 *
 * The decoding itself lives in [KeyEventMapping] in the `:bridge` module: the detached bridge
 * process resolves key codes with that same class. A key recorded here and matched there must
 * produce the same code — especially the synthetic codes for unlabelled OEM buttons — so the two
 * sides share one implementation rather than keeping parallel copies.
 */
object GetEventParser {

    fun parse(line: String): ParsedGetEventKey? {
        val parsed = KeyEventMapping.parse(line) ?: return null
        val action = when (parsed.action) {
            KeyEventMapping.ACTION_DOWN -> ParsedGetEventKey.Action.DOWN
            KeyEventMapping.ACTION_UP -> ParsedGetEventKey.Action.UP
            KeyEventMapping.ACTION_REPEAT -> ParsedGetEventKey.Action.REPEAT
            else -> return null
        }
        return ParsedGetEventKey(
            device = parsed.device,
            kernelName = parsed.kernelName,
            linuxCode = parsed.linuxCode,
            androidKeyCode = parsed.androidKeyCode,
            action = action,
            isTouchContact = parsed.touchContact,
        )
    }
}
