package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import com.example.data.AppLocale
import io.github.nicholasarda.keysnap.R
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.data.KeyMappingRepository
import com.example.data.LiveKeyEvent
import com.example.data.ScriptRepository
import com.example.service.adb.WirelessAdbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyMapperAccessibilityService : AccessibilityService() {
    // Services get their own resources from the system, so the in-app language picker has to be
    // applied here too or their notifications and overlays stay in the system language.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))


    companion object {
        private const val TAG = "KeyMapperService"

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        val latestKeyEvent: SharedFlow<LiveKeyEvent>
            get() = HardwareKeyTriggerCoordinator.latestKeyEvent

        val triggerCount: StateFlow<Int>
            get() = HardwareKeyTriggerCoordinator.triggerCount

        @Volatile
        private var instance: KeyMapperAccessibilityService? = null

        /**
         * Navigation-category actions (back/home/recents/...) can only run through a live
         * AccessibilityService instance; ScriptActionExecutor routes to this from the app/
         * Accessibility path. Unreachable while Advanced Mode owns execution, same as every other
         * action there, since HardwareKeyTriggerCoordinator suppresses in-app dispatch then.
         */
        fun performGlobalAction(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        /**
         * Whether an input-method window is on screen, for the `keyboard_showing` constraints.
         * Null when no service instance is reachable, so the caller can treat it as unknown.
         * Window metadata only; this reads no window content.
         */
        fun isKeyboardShowing(): Boolean? = instance?.let { service ->
            runCatching { service.windows.orEmpty().any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }.getOrNull()
        }

        /**
         * Runs an [AccessibilityNodeInfo] action (cut/copy/paste) on whichever node currently has
         * input focus. False when nothing is focused, same as when no field can accept the action.
         */
        fun performFocusedEditAction(action: Int): Boolean {
            val node = instance?.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                node.performAction(action)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }

        /**
         * Dispatches a tap/swipe/pinch gesture through the live AccessibilityService. Returns
         * whether dispatch was accepted, not whether the gesture finished — matches the
         * fire-and-forget semantics every other action here uses.
         */
        fun performGesture(gesture: GestureDescription): Boolean = instance?.dispatchGesture(gesture, null, null) ?: false

        /**
         * Sets text directly on whichever node currently has input focus, via
         * [AccessibilityNodeInfo.ACTION_SET_TEXT]. False when nothing editable is focused.
         */
        fun setFocusedText(text: String): Boolean {
            val node = instance?.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }

        /**
         * Collapses the selection of whichever node has input focus to its end, via
         * [AccessibilityNodeInfo.ACTION_SET_SELECTION]. False when nothing editable is focused.
         */
        fun moveFocusedCursorToEnd(): Boolean {
            val node = instance?.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                val length = node.text?.length ?: 0
                val arguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }

        /**
         * Selects the word touching the cursor in whichever node has input focus, via
         * [wordBoundsAtCursor]. False when nothing editable is focused or the cursor sits in
         * whitespace.
         */
        fun selectFocusedWordAtCursor(): Boolean {
            val node = instance?.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                val text = node.text?.toString() ?: return false
                val cursor = node.textSelectionEnd.takeIf { it >= 0 } ?: text.length
                val bounds = wordBoundsAtCursor(text, cursor) ?: return false
                val arguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, bounds.first)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, bounds.last + 1)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }

        private val SIX_DIGIT_REGEX = Regex("""\b(\d{6})\b""")
        private val IP_PORT_REGEX = Regex("""\b((?:\d{1,3}\.){3}\d{1,3}):(\d{2,5})\b""")
        private val PORT_ONLY_REGEX = Regex(""":(\d{4,5})\b""")
    }

    private lateinit var repository: KeyMappingRepository
    private val deviceEventReceiver = DeviceEventReceiver()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        repository = KeyMappingRepository.getInstance(applicationContext)
        HardwareKeyTriggerCoordinator.initialize(applicationContext, repository)
        WirelessAdbManager.initialize(applicationContext)
        DeviceEventReceiver.register(this, deviceEventReceiver)
        Log.d(TAG, "KeyMapperAccessibilityService connected & coordinator initialized")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !WirelessAdbManager.isSearchingMdnsAndScreen()) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            scanAllWindowsForPairingInfo(event)
        }
    }

    private fun scanAllWindowsForPairingInfo(event: AccessibilityEvent) {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(roots::add)
        windows.orEmpty().forEach { window ->
            window.root?.let { root -> if (roots.none { it === root }) roots.add(root) }
        }
        if (roots.isEmpty()) {
            event.source?.let(roots::add)
        }
        val combinedTexts = mutableListOf<String>()
        try {
            roots.forEach { collectNodeTexts(it, combinedTexts) }
            extractPairingInfo(combinedTexts)
        } catch (e: Exception) {
            Log.w(TAG, "Error while scanning windows for pairing credentials", e)
        } finally {
            roots.forEach { runCatching { it.recycle() } }
        }
    }

    private fun extractPairingInfo(allTexts: List<String>) {
        var extractedCode: String? = null
        var extractedIp: String? = null
        var extractedPort: Int? = null

        for (text in allTexts) {
            IP_PORT_REGEX.find(text)?.let {
                extractedIp = it.groupValues[1]
                extractedPort = it.groupValues[2].toIntOrNull()
            }
            if (extractedPort == null) {
                PORT_ONLY_REGEX.find(text)?.let { extractedPort = it.groupValues[1].toIntOrNull() }
            }
            SIX_DIGIT_REGEX.find(text)?.let { match ->
                val candidate = match.groupValues[1]
                if (!text.contains(Regex("(?:\\d{1,3}\\.){3}\\d{1,3}"))) extractedCode = candidate
            }
        }

        if (extractedCode != null) {
            Log.d(TAG, "Pairing credentials detected from accessibility hierarchy")
            WirelessAdbManager.onCredentialsDetectedFromScreen(extractedIp, extractedPort, extractedCode)
        }
    }


    private fun collectNodeTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            list.add(text)
        }
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            list.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectNodeTexts(child, list)
            try {
                child?.recycle()
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {
        instance = null
        _isServiceActive.value = false
        Log.d(TAG, "KeyMapperAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        DeviceEventReceiver.unregister(this, deviceEventReceiver)
        instance = null
        _isServiceActive.value = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::repository.isInitialized) {
            repository = KeyMappingRepository.getInstance(applicationContext)
            HardwareKeyTriggerCoordinator.initialize(applicationContext, repository)
        }

        val config = repository.config.value
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN

        val isMatched = HardwareKeyTriggerCoordinator.dispatchKeyEvent(
            keyCode = keyCode,
            isDown = isDown,
            source = getString(R.string.source_accessibility)
        )

        val shouldConsume = ScriptRepository.getInstance(applicationContext).shouldConsume(keyCode)
        return if (shouldConsume) true else super.onKeyEvent(event)
    }
}
