package com.example.ui.scripts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bridge.ActionTestResult
import com.example.data.ActionEditorForms
import com.example.data.AppLabelResolver
import com.example.data.ExtraRow
import com.example.data.HeaderRow
import com.example.data.InstalledApp
import com.example.data.PrivilegedEditor
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.SettingProbe
import com.example.data.UrlIssue
import com.example.data.previewCommand
import com.example.data.shellRunsIn
import com.example.data.ShellContext
import com.example.service.NotificationEventListener
import com.example.service.ScriptActionExecutor
import com.example.service.SettingAccess
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.theme.MonoBody
import com.example.ui.theme.SectionLabel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.backgroundFor
import com.example.ui.theme.cautionOn
import com.example.ui.theme.successOn
import io.github.nicholasarda.keysnap.R
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Enter = fadeIn(tween(180)) + expandVertically(tween(180))
private val Exit = fadeOut(tween(180)) + shrinkVertically(tween(180))

/**
 * Result shown after ▶ Test. [toString] stays free of output, URLs and setting values.
 * Shell lines live only in [output] for the open sheet.
 */
private class Shown(
    val ok: Boolean,
    val kind: String,
    val status: Int?,
    val exit: Int?,
    val durationMs: Long,
    val output: List<String>,
) {
    override fun toString(): String = if (ok) "ok" else "fail"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PrivilegedEditorPage(
    actionType: String,
    initialParams: Map<String, String>,
    startsFromNotification: Boolean,
    installedApps: List<InstalledApp>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    advancedActive: Boolean = false,
    onSetUpAdvancedMode: () -> Unit = {},
    everyChatWarning: Boolean = false,
    cooldownRemoved: Boolean = false,
    existingCooldownMinutes: String? = null,
    onRemoveReplyCooldown: () -> Unit = {},
    notificationAccessGranted: Boolean = true,
    canWriteSettings: Boolean = true,
    onRequestWriteSettings: () -> Unit = {},
    runTest: (suspend (ScriptAction) -> ActionTestResult)? = null,
    readSetting: (suspend (String, String) -> String?)? = null,
    writeSystemSetting: (suspend (String, String) -> ActionTestResult)? = null,
    visibleChats: ((String) -> List<String>)? = null,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val form = ActionEditorForms.forType(actionType) ?: return
    val title = ScriptActionCatalog.descriptor(actionType)?.let { stringResource(it.titleRes) } ?: actionType
    val values = remember(actionType, initialParams) {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            putAll(ActionEditorForms.initialValues(form, initialParams))
        }
    }
    val headerRows = remember(actionType, initialParams) {
        mutableStateListOf<HeaderRow>().apply { addAll(PrivilegedEditor.headerRows(initialParams["headers"])) }
    }
    val extraRows = remember(actionType, initialParams) {
        mutableStateListOf<ExtraRow>().apply { addAll(PrivilegedEditor.extraRows(initialParams["extras"])) }
    }
    val runner: suspend (ScriptAction) -> ActionTestResult = runTest ?: { action ->
        ScriptActionExecutor.android(context).runSingleActionForTest(action)
    }
    val reader: suspend (String, String) -> String? = readSetting ?: { namespace, key ->
        SettingAccess.read(context, namespace, key, advancedActive, runner)
    }
    val writer: suspend (String, String) -> ActionTestResult = writeSystemSetting ?: { key, value ->
        SettingAccess.writeSystem(context, key, value)
    }
    val chatSource = visibleChats ?: { packageName ->
        runCatching { NotificationEventListener.visibleChatTitles(packageName) }.getOrDefault(emptyList())
    }
    var generation by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Shown?>(null) }
    var undo by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var picking by remember { mutableStateOf(false) }
    var settingQuery by remember { mutableStateOf("") }
    var probed by remember { mutableStateOf<String?>(null) }
    var probeEpoch by remember { mutableIntStateOf(0) }
    var liveChats by remember { mutableStateOf<List<String>>(emptyList()) }
    var typeName by remember { mutableStateOf(false) }
    var appPickerTried by remember { mutableStateOf(false) }
    var cooldownDismissed by remember { mutableStateOf(false) }

    fun touch(block: () -> Unit) {
        block()
        shown = null
        generation++
    }

    val namespace = values["namespace"].orEmpty().ifEmpty { "system" }
    val settingKey = values["key"].orEmpty()
    if (actionType == PrivilegedEditor.SETTING) {
        LaunchedEffect(namespace, settingKey, advancedActive, probeEpoch) {
            probed = null
            if (settingKey.isEmpty() || PrivilegedEditor.settingProbe(namespace, advancedActive) == SettingProbe.BLOCKED) return@LaunchedEffect
            delay(300)
            probed = reader(namespace, settingKey)
        }
    }
    val replyPackage = values["packageName"].orEmpty()
    if (actionType == PrivilegedEditor.REPLY) {
        LaunchedEffect(replyPackage) {
            liveChats = if (replyPackage.isEmpty()) emptyList() else runCatching { chatSource(replyPackage) }.getOrDefault(emptyList())
            val chat = values["chat"].orEmpty()
            if (chat.isNotEmpty() && chat !in liveChats) typeName = true
            if (chat.isNotEmpty() && chat in liveChats) typeName = false
        }
    }

    val saveOn = PrivilegedEditor.saveEnabled(
        actionType,
        values.toMap(),
        headerRows.toList(),
        extraRows.toList(),
        startsFromNotification,
    )
    val preview = previewCommand(
        ScriptAction(actionType, PrivilegedEditor.previewParams(actionType, values.toMap(), headerRows.toList(), extraRows.toList())),
    )
    val cap = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    val caution = cautionOn(colors.surface)
    val success = successOn(colors.surface)

    fun launchTest() {
        if (testing) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val gen = generation
        val snapshot = values.toMap()
        val headers = headerRows.toList()
        val extras = extraRows.toList()
        val known = probed
        testing = true
        scope.launch {
            try {
                val result = executeTest(actionType, snapshot, headers, extras, initialParams, known, advancedActive, reader, writer, runner)
                if (gen == generation) {
                    shown = result.first
                    if (result.second != null) undo = result.second
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (gen == generation) shown = Shown(false, "fail", null, null, 0, emptyList())
            } finally {
                testing = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .heightIn(max = cap)
            .nestedScroll(rememberSheetContentScroll())
            .verticalScroll(rememberScrollState())
            .testTag("action_config_sheet")
            .animateContentSize()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(colors.primary.copy(alpha = .12f), InnerShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(actionIcon(actionType), null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("action_config_cancel")) {
                Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = colors.onSurfaceVariant)
            }
        }

        when (actionType) {
            PrivilegedEditor.HTTP -> HttpFields(values, headerRows, revealed, { revealed = it }, ::touch)
            PrivilegedEditor.SHELL -> ShellFields(values, advancedActive, caution, success, onSetUpAdvancedMode, ::touch)
            PrivilegedEditor.INTENT -> IntentFields(values, extraRows, { picking = true }, ::touch)
            PrivilegedEditor.REPLY -> ReplyFields(
                values = values,
                chats = liveChats,
                typeName = typeName,
                everyChatWarning = everyChatWarning,
                showCooldown = everyChatWarning && values["chat"].isNullOrBlank() && !cooldownRemoved && !cooldownDismissed &&
                    (existingCooldownMinutes == null || existingCooldownMinutes == "10"),
                startsFromNotification = startsFromNotification,
                accessGranted = notificationAccessGranted,
                onPickApp = { picking = true },
                onTypeName = {
                    touch {
                        typeName = true
                        values["chat"] = ""
                    }
                },
                onChat = { chat ->
                    touch {
                        typeName = false
                        values["chat"] = chat
                    }
                },
                onChatText = { touch { values["chat"] = it } },
                onText = { touch { values["text"] = it } },
                onDismissCooldown = {
                    cooldownDismissed = true
                    onRemoveReplyCooldown()
                },
                onOpenAccess = { NotificationEventListener.openAccessSettings(context) },
                pristine = !appPickerTried,
            )
            PrivilegedEditor.SETTING -> SettingFields(
                values = values,
                query = settingQuery,
                onQuery = { settingQuery = it },
                probed = probed,
                advancedActive = advancedActive,
                canWrite = canWriteSettings,
                caution = caution,
                onAllow = onRequestWriteSettings,
                onEdit = ::touch,
            )
        }

        AnimatedVisibility(visible = preview.isNotEmpty(), enter = Enter, exit = Exit) {
            Column(
                Modifier.fillMaxWidth().background(backgroundFor(ThemeMode.DARK), InnerShape).padding(10.dp),
            ) {
                Text(stringResource(R.string.editor_will_run), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(preview, Modifier.testTag("config_what_will_run"), style = MonoBody, color = TextPrimary)
            }
        }
        AnimatedContent(targetState = shown, label = "test-result") { current ->
            if (current != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultLine(current, success, if (current.kind == "written" && undo != null) {
                        {
                            val restore = undo ?: return@ResultLine
                            if (testing) return@ResultLine
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            testing = true
                            scope.launch {
                                try {
                                    val ns = values["namespace"].orEmpty()
                                    val key = values["key"].orEmpty()
                                    val result = if (!advancedActive && ns == "system") {
                                        writer(key, restore)
                                    } else {
                                        runner(ScriptAction(PrivilegedEditor.SETTING, mapOf("namespace" to ns, "key" to key, "value" to restore)))
                                    }
                                    if (result.ok) {
                                        undo = null
                                        shown = null
                                        probeEpoch++
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    shown = Shown(false, "fail", null, null, 0, emptyList())
                                } finally {
                                    testing = false
                                }
                            }
                        }
                    } else {
                        null
                    })
                    if (current.kind == "shell") {
                        Text(
                            if (current.output.isEmpty()) stringResource(R.string.editor_no_output) else current.output.joinToString("\n"),
                            Modifier
                                .fillMaxWidth()
                                .background(backgroundFor(ThemeMode.DARK), InnerShape)
                                .padding(10.dp)
                                .testTag("config_shell_output"),
                            style = MonoBody,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (actionType != PrivilegedEditor.REPLY) {
                OutlinedButton(
                    onClick = { launchTest() },
                    modifier = Modifier.width(112.dp).heightIn(min = MinTouchTarget).testTag("action_test_button"),
                    shape = InnerShape,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.editor_test))
                }
            }
            Button(
                onClick = {
                    onConfirm(PrivilegedEditor.savedParams(actionType, values.toMap(), headerRows.toList(), extraRows.toList()))
                },
                enabled = saveOn,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("action_config_confirm"),
                shape = InnerShape,
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }

    if (picking) {
        val key = if (actionType == PrivilegedEditor.REPLY) "packageName" else "package"
        AppPickerSheet(
            apps = installedApps,
            selected = values[key],
            onSelect = { picked ->
                touch { values[key] = picked }
                picking = false
            },
            onDismiss = {
                picking = false
                appPickerTried = true
            },
        )
    }
}

private suspend fun executeTest(
    type: String,
    values: Map<String, String>,
    headers: List<HeaderRow>,
    extras: List<ExtraRow>,
    initialParams: Map<String, String>,
    known: String?,
    advancedActive: Boolean,
    reader: suspend (String, String) -> String?,
    writer: suspend (String, String) -> ActionTestResult,
    runner: suspend (ScriptAction) -> ActionTestResult,
): Pair<Shown, String?> {
    if (type == PrivilegedEditor.SETTING && !advancedActive && values["namespace"] == "system") {
        val key = values["key"].orEmpty()
        val previous = known ?: reader(values["namespace"].orEmpty(), key)
        val result = writer(key, values["value"].orEmpty())
        val restore = if (result.ok) previous else null
        return toShown(type, result) to restore
    }
    val action = PrivilegedEditor.testAction(
        type,
        values,
        headers,
        extras,
        initialParams["headers"],
        initialParams["extras"],
    ) ?: return Shown(false, "fail", null, null, 0, emptyList()) to null
    var restore: String? = null
    if (type == PrivilegedEditor.SETTING) {
        val ns = values["namespace"].orEmpty()
        val key = values["key"].orEmpty()
        val previous = if (PrivilegedEditor.settingProbe(ns, advancedActive) == SettingProbe.BLOCKED) {
            null
        } else {
            known ?: reader(ns, key)
        }
        val result = runner(action)
        if (result.ok) restore = previous
        return toShown(type, result) to restore
    }
    return toShown(type, runner(action)) to null
}

private fun toShown(type: String, result: ActionTestResult): Shown {
    if (result.timedOut) return Shown(false, "timeout", result.httpStatus, result.exitCode, result.durationMs, result.output)
    return when (type) {
        PrivilegedEditor.HTTP -> if (result.httpStatus != null) {
            Shown(result.ok, "http", result.httpStatus, null, result.durationMs, emptyList())
        } else {
            Shown(false, "fail", null, null, result.durationMs, emptyList())
        }
        PrivilegedEditor.SHELL -> if (result.exitCode != null) {
            Shown(result.ok, "shell", null, result.exitCode, result.durationMs, result.output)
        } else {
            Shown(false, "fail", null, null, result.durationMs, result.output)
        }
        PrivilegedEditor.SETTING -> if (result.ok) {
            Shown(true, "written", null, null, result.durationMs, emptyList())
        } else {
            Shown(false, "fail", null, null, result.durationMs, emptyList())
        }
        else -> if (result.ok) {
            Shown(true, "done", null, null, result.durationMs, emptyList())
        } else {
            Shown(false, "fail", null, null, result.durationMs, emptyList())
        }
    }
}

@Composable
private fun HttpFields(
    values: MutableMap<String, String>,
    rows: MutableList<HeaderRow>,
    revealed: Set<Int>,
    onRevealed: (Set<Int>) -> Unit,
    touch: (() -> Unit) -> Unit,
) {
    val method = values["method"].orEmpty().ifEmpty { "GET" }
    Segment(
        "method",
        listOf(
            "GET" to R.string.choice_http_get,
            "POST" to R.string.choice_http_post,
            "PUT" to R.string.choice_http_put,
            "DELETE" to R.string.choice_http_delete,
        ),
        method,
    ) { touch { values["method"] = it } }
    val url = values["url"].orEmpty()
    val issue = PrivilegedEditor.urlIssue(url)
    Entry(
        stringResource(R.string.param_url),
        url,
        { touch { values["url"] = it } },
        "config_field_url",
        when (issue) {
            UrlIssue.EMPTY -> stringResource(R.string.editor_url_empty)
            UrlIssue.NOT_WEB -> stringResource(R.string.editor_url_not_web)
            else -> null
        },
        mono = true,
    )
    AnimatedVisibility(visible = issue == UrlIssue.PLAIN, enter = Enter, exit = Exit) {
        StatusChip(stringResource(R.string.editor_plain_http), cautionOn(MaterialTheme.colorScheme.surface), Modifier.testTag("config_plain_http"))
    }
    Section(stringResource(R.string.param_headers))
    rows.forEachIndexed { index, row ->
        val masked = PrivilegedEditor.masksAuthorization(row.name)
        val open = index in revealed
        val stateTag = when {
            !masked -> null
            open -> "config_header_revealed_$index"
            else -> "config_header_masked_$index"
        }
        Column(if (stateTag == null) Modifier else Modifier.testTag(stateTag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { name -> touch { rows[index] = row.copy(name = name) } },
                    modifier = Modifier.weight(1f).testTag("config_header_name_$index"),
                    placeholder = { Text(stringResource(R.string.editor_header_name)) },
                    singleLine = true,
                    textStyle = MonoBody,
                )
                HeaderValue(index, row.value, masked && !open, Modifier.weight(1.3f)) { value ->
                    touch { rows[index] = rows[index].copy(value = value) }
                }
                if (masked) {
                    IconButton(
                        onClick = { onRevealed(if (open) revealed - index else revealed + index) },
                        modifier = Modifier.testTag("config_header_reveal_$index"),
                    ) {
                        Icon(
                            if (open) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            stringResource(if (open) R.string.editor_hide else R.string.editor_reveal),
                        )
                    }
                }
                IconButton(onClick = { touch { rows.removeAt(index) } }, modifier = Modifier.testTag("config_header_remove_$index")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.editor_remove))
                }
            }
            ErrorLine(if (PrivilegedEditor.headerError(row)) stringResource(R.string.editor_header_invalid) else null)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.editor_add_header),
            Modifier.clickable(role = Role.Button) { touch { rows.add(HeaderRow("", "")) } }.testTag("config_add_header").padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        AnimatedVisibility(visible = PrivilegedEditor.needsBearer(rows), enter = Enter, exit = Exit) {
            Text(
                stringResource(R.string.editor_add_bearer),
                Modifier
                    .clip(InnerShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape)
                    .clickable(role = Role.Button) { touch { val next = PrivilegedEditor.withBearer(rows.toList()); rows.clear(); rows.addAll(next) } }
                    .testTag("config_bearer_chip")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    AnimatedVisibility(visible = PrivilegedEditor.sendsBody(method), enter = Enter, exit = Exit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section(stringResource(R.string.param_body))
            val content = values["content_type"].orEmpty().ifEmpty { "none" }
            val invalid = PrivilegedEditor.contentTypeInvalid(values["content_type"])
            ChoiceChips(
                "content_type",
                listOf(
                    "none" to R.string.editor_body_none,
                    "json" to R.string.editor_body_json,
                    "form" to R.string.editor_body_form,
                    "text" to R.string.editor_body_text,
                ),
                if (invalid) null else content,
            ) { touch { values["content_type"] = it } }
            ErrorLine(if (invalid) stringResource(R.string.editor_content_invalid) else null)
            val conflict = PrivilegedEditor.contentTypeConflicts(if (invalid) "" else content, rows)
            ErrorLine(if (conflict) stringResource(R.string.editor_content_conflict) else null)
            Entry(
                stringResource(R.string.param_body),
                values["body"].orEmpty(),
                { touch { values["body"] = it } },
                "config_field_body",
                mono = true,
                singleLine = false,
            )
        }
    }
}

@Composable
private fun HeaderValue(index: Int, value: String, hide: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    val tag = "config_header_value_$index"
    val semantics = if (hide) {
        Modifier.clearAndSetSemantics {
            testTag = tag
            editableText = AnnotatedString("•".repeat(value.length))
            password()
            setText { updated ->
                onChange(updated.text)
                true
            }
        }
    } else {
        Modifier.testTag(tag)
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.then(semantics),
        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        textStyle = MonoBody,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShellFields(
    values: MutableMap<String, String>,
    advancedActive: Boolean,
    caution: Color,
    success: Color,
    onSetUp: () -> Unit,
    touch: (() -> Unit) -> Unit,
) {
    val command = values["command"].orEmpty()
    Entry(
        stringResource(R.string.param_command),
        command,
        { touch { values["command"] = it } },
        "config_field_command",
        if (command.isBlank()) stringResource(R.string.editor_command_empty) else null,
        mono = true,
        singleLine = false,
        minHeight = 72.dp,
    )
    val advanced = shellRunsIn(advancedActive) == ShellContext.ADVANCED
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(
            stringResource(if (advanced) R.string.editor_shell_advanced else R.string.editor_shell_limited),
            if (advanced) success else caution,
            Modifier.testTag("config_shell_context"),
        )
        if (!advanced) {
            Text(
                stringResource(R.string.editor_set_up),
                Modifier.clickable(role = Role.Button, onClick = onSetUp).testTag("config_setup_advanced").padding(8.dp),
                color = caution,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    Section(stringResource(R.string.editor_stop_after))
    val selected = PrivilegedEditor.timeoutSelection(values["timeout_sec"])
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrivilegedEditor.TIMEOUTS.forEach { seconds ->
            val on = seconds == selected
            Text(
                stringResource(R.string.editor_seconds, seconds),
                Modifier
                    .clip(InnerShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape)
                    .clickable(role = Role.Button) { touch { values["timeout_sec"] = seconds } }
                    .testTag("config_choice_timeout_sec_$seconds")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    ErrorLine(if (PrivilegedEditor.timeoutInvalid(values["timeout_sec"])) stringResource(R.string.editor_timeout_invalid) else null)
    Section(stringResource(R.string.editor_ready_commands))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrivilegedEditor.shellReadyCommands().forEach { ready ->
            val label = stringResource(ready.titleRes)
            Text(
                label,
                Modifier
                    .clip(InnerShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape)
                    .clickable(role = Role.Button) { touch { values["command"] = ready.command } }
                    .testTag("config_ready_command")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun IntentFields(
    values: MutableMap<String, String>,
    rows: MutableList<ExtraRow>,
    onPickApp: () -> Unit,
    touch: (() -> Unit) -> Unit,
) {
    val target = values["target"].orEmpty()
    Segment(
        "target",
        listOf("broadcast" to R.string.editor_target_broadcast, "activity" to R.string.editor_target_activity),
        target,
    ) { touch { values["target"] = it } }
    ErrorLine(if (PrivilegedEditor.targetInvalid(target)) stringResource(R.string.editor_target_invalid) else null)
    AppSelectRow(stringResource(R.string.editor_to_app), values["package"].orEmpty(), false, "config_app_pick_package", onPickApp)
    val action = values["action"].orEmpty()
    Entry(
        stringResource(R.string.param_action),
        action,
        { touch { values["action"] = it } },
        "config_field_action",
        if (action.isBlank()) stringResource(R.string.editor_action_empty) else null,
        mono = true,
    )
    Entry(
        stringResource(R.string.editor_data_uri),
        values["uri"].orEmpty(),
        { touch { values["uri"] = it } },
        "config_field_uri",
        mono = true,
    )
    Section(stringResource(R.string.field_extras))
    rows.forEachIndexed { index, row ->
        val error = PrivilegedEditor.extraError(row)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = row.key,
                    onValueChange = { key -> touch { rows[index] = row.copy(key = key) } },
                    modifier = Modifier.weight(1f).testTag("config_extra_key_$index"),
                    placeholder = { Text(stringResource(R.string.param_key)) },
                    singleLine = true,
                    textStyle = MonoBody,
                )
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { value -> touch { rows[index] = row.copy(value = value) } },
                    modifier = Modifier.weight(1.3f).testTag("config_extra_value_$index"),
                    isError = error != null,
                    singleLine = true,
                    textStyle = MonoBody,
                )
                IconButton(onClick = { touch { rows.removeAt(index) } }, modifier = Modifier.testTag("config_extra_remove_$index")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.editor_remove))
                }
            }
            ErrorLine(
                when (error) {
                    "empty_key" -> stringResource(R.string.editor_key_empty)
                    "separator_in_extra" -> stringResource(R.string.editor_extra_separator)
                    else -> null
                },
                if (error != null) "config_extra_error" else null,
            )
        }
    }
    Text(
        stringResource(R.string.editor_add_extra),
        Modifier.clickable(role = Role.Button) { touch { rows.add(ExtraRow("", "")) } }.testTag("config_add_extra").padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyFields(
    values: MutableMap<String, String>,
    chats: List<String>,
    typeName: Boolean,
    everyChatWarning: Boolean,
    showCooldown: Boolean,
    startsFromNotification: Boolean,
    accessGranted: Boolean,
    onPickApp: () -> Unit,
    onTypeName: () -> Unit,
    onChat: (String) -> Unit,
    onChatText: (String) -> Unit,
    onText: (String) -> Unit,
    onDismissCooldown: () -> Unit,
    onOpenAccess: () -> Unit,
    /** The app picker was never opened: "Add an app" waits until the user has tried it. */
    pristine: Boolean = false,
) {
    val app = values["packageName"].orEmpty()
    AppSelectRow(
        stringResource(R.string.param_app),
        app,
        !pristine,
        "config_app_pick_packageName",
        onPickApp,
    )
    if (!accessGranted) {
        StatusChip(
            stringResource(R.string.badge_notification_access),
            cautionOn(MaterialTheme.colorScheme.surface),
            Modifier.clickable(role = Role.Button, onClick = onOpenAccess).testTag("config_notification_access"),
        )
    }
    Section(stringResource(R.string.editor_chats))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chats.forEach { chat ->
            val on = !typeName && values["chat"] == chat
            Row(
                Modifier
                    .clip(InnerShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape)
                    .clickable(role = Role.Button) { onChat(chat) }
                    .testTag("config_chat_chip")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).background(successOn(MaterialTheme.colorScheme.surface), CircleShape))
                Text(chat, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            Modifier
                .clip(InnerShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape)
                .clickable(role = Role.Button, onClick = onTypeName)
                .testTag("config_chat_type_name")
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
            Text(stringResource(R.string.editor_type_name), style = MaterialTheme.typography.labelMedium)
        }
    }
    AnimatedVisibility(visible = typeName, enter = Enter, exit = Exit) {
        Entry(
            stringResource(R.string.param_chat),
            values["chat"].orEmpty(),
            onChatText,
            "config_field_chat",
            if (!startsFromNotification && values["chat"].isNullOrBlank()) stringResource(R.string.editor_chat_empty) else null,
        )
    }
    if (app.isNotEmpty() && values["text"].orEmpty().isNotBlank() && !typeName && !startsFromNotification && values["chat"].isNullOrBlank()) {
        ErrorLine(stringResource(R.string.editor_chat_empty))
    }
    val message = values["text"].orEmpty()
    Entry(
        stringResource(R.string.param_message),
        message,
        onText,
        "config_field_text",
        if (message.isBlank()) stringResource(R.string.editor_message_empty) else null,
        singleLine = false,
    )
    val chat = values["chat"].orEmpty()
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, InnerShape).padding(10.dp).testTag("config_reply_preview"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (chat.isBlank()) stringResource(R.string.editor_preview) else stringResource(R.string.editor_preview_chat, chat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message.isNotEmpty()) {
            Text(
                message,
                Modifier
                    .align(Alignment.End)
                    .background(MaterialTheme.colorScheme.primary, InnerShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    Fact(Icons.Outlined.Notifications, stringResource(R.string.editor_fact_shown))
    Fact(Icons.Outlined.Block, stringResource(R.string.editor_fact_no_chat))
    Fact(Icons.Outlined.Lock, stringResource(R.string.editor_fact_on_phone))
    AnimatedVisibility(visible = everyChatWarning && values["chat"].isNullOrBlank(), enter = Enter, exit = Exit) {
        Text(
            stringResource(R.string.warning_reply_every_chat),
            Modifier.fillMaxWidth().background(cautionOn(MaterialTheme.colorScheme.surface).copy(alpha = .15f), InnerShape).padding(10.dp).testTag("every_chat_warning"),
            color = cautionOn(MaterialTheme.colorScheme.surface),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    AnimatedVisibility(visible = showCooldown, enter = Enter, exit = Exit) {
        Row(
            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.editor_once_10), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onDismissCooldown, modifier = Modifier.testTag("config_reply_cooldown")) {
                Icon(Icons.Filled.Close, stringResource(R.string.editor_remove))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingFields(
    values: MutableMap<String, String>,
    query: String,
    onQuery: (String) -> Unit,
    probed: String?,
    advancedActive: Boolean,
    canWrite: Boolean,
    caution: Color,
    onAllow: () -> Unit,
    onEdit: (() -> Unit) -> Unit,
) {
    val namespace = values["namespace"].orEmpty().ifEmpty { "system" }
    Segment(
        "namespace",
        listOf(
            "system" to R.string.choice_namespace_system,
            "secure" to R.string.choice_namespace_secure,
            "global" to R.string.choice_namespace_global,
        ),
        values["namespace"].orEmpty(),
    ) { onEdit { values["namespace"] = it } }
    ErrorLine(if (values["namespace"].orEmpty().let { it.isNotEmpty() && it !in PrivilegedEditor.NAMESPACES }) stringResource(R.string.editor_namespace_invalid) else null)
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("config_setting_search"),
        placeholder = { Text(stringResource(R.string.editor_search_settings)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
    )
    val suggestionLabels = PrivilegedEditor.suggestions.associateWith { stringResource(it.labelRes) }
    val matches = PrivilegedEditor.matchingSuggestions(namespace, query) { suggestionLabels.getValue(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        matches.forEach { suggestion ->
            val on = values["key"] == suggestion.key && values["namespace"] == suggestion.namespace
            Text(
                stringResource(suggestion.labelRes),
                Modifier
                    .clip(InnerShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f), InnerShape)
                    .clickable(role = Role.Button) {
                        onEdit {
                            values["namespace"] = suggestion.namespace
                            values["key"] = suggestion.key
                        }
                    }
                    .testTag("config_setting_suggestion_${suggestion.key}")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    val key = values["key"].orEmpty()
    Entry(
        stringResource(R.string.param_key),
        key,
        { onEdit { values["key"] = it } },
        "config_field_key",
        if (key.isBlank()) stringResource(R.string.editor_key_empty) else null,
        mono = true,
    )
    val probe = PrivilegedEditor.settingProbe(namespace, advancedActive)
    AnimatedVisibility(visible = probe == SettingProbe.BLOCKED, enter = Enter, exit = Exit) {
        StatusChip(stringResource(R.string.editor_needs_advanced), caution, Modifier.testTag("config_setting_needs_advanced"))
    }
    AnimatedVisibility(visible = probe != SettingProbe.BLOCKED && key.isNotEmpty() && probed != null, enter = Enter, exit = Exit) {
        Text(
            stringResource(R.string.editor_now, probed.orEmpty().ifEmpty { stringResource(R.string.editor_now_empty) }),
            Modifier.testTag("config_setting_now"),
            style = MonoBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    AnimatedVisibility(visible = namespace == "system" && !advancedActive && !canWrite, enter = Enter, exit = Exit) {
        StatusChip(
            stringResource(R.string.editor_allow_settings),
            caution,
            Modifier.clickable(role = Role.Button, onClick = onAllow).testTag("config_allow_settings"),
        )
    }
    val value = values["value"].orEmpty()
    Entry(
        stringResource(R.string.editor_new_value),
        value,
        { onEdit { values["value"] = it } },
        "config_field_value",
        if (value.isBlank()) stringResource(R.string.editor_value_empty) else null,
        mono = true,
    )
    Fact(Icons.Outlined.Info, stringResource(R.string.editor_restart))
}

@Composable
private fun AppSelectRow(
    title: String,
    packageName: String,
    required: Boolean,
    tag: String,
    onChange: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val app = AppLabelResolver.get(context).resolve(packageName, context.resources)
    val shown = app.label
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
            .clickable(role = Role.Button, onClick = onChange)
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(colors.primary.copy(alpha = .12f), InnerShape), contentAlignment = Alignment.Center) {
            Text(shown.firstOrNull()?.uppercaseChar()?.toString() ?: "•", color = colors.primary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                if (packageName.isEmpty()) stringResource(R.string.action_config_choose_app) else shown,
                color = if (packageName.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
            )
            if (!app.installed) Text(
                stringResource(R.string.app_not_installed),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.editor_change), color = colors.primary, style = MaterialTheme.typography.labelLarge)
    }
    ErrorLine(if (required && packageName.isBlank()) stringResource(R.string.editor_app_empty) else null)
}

@Composable
private fun Segment(optionKey: String, options: List<Pair<String, Int>>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceVariant, InnerShape).padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) colors.surface else Color.Transparent, tween(180), label = "segment")
            val fg by animateColorAsState(if (on) colors.primary else colors.onSurfaceVariant, tween(180), label = "segmentText")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(InnerShape)
                    .background(bg)
                    .clickable(role = Role.Button) { onSelect(value) }
                    .testTag("config_choice_${optionKey}_$value")
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), color = fg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(optionKey: String, options: List<Pair<String, Int>>, selected: String?, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                stringResource(label),
                Modifier
                    .clip(InnerShape)
                    .background(if (on) colors.primary else colors.surface)
                    .border(1.dp, if (on) colors.primary else colors.outline.copy(alpha = .5f), InnerShape)
                    .clickable(role = Role.Button) { onSelect(value) }
                    .testTag("config_choice_${optionKey}_$value")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (on) colors.onPrimary else colors.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Entry(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    error: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
) {
    // An empty field the user never typed in shows what to fill, not a red error; a field they
    // emptied themselves, or one holding an invalid value, does.
    var edited by remember { mutableStateOf(false) }
    val shown = if (value.isEmpty() && !edited) null else error
    OutlinedTextField(
        value = value,
        onValueChange = {
            edited = true
            onValueChange(it)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).testTag(tag),
        label = { Text(label) },
        isError = shown != null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = if (mono) MonoBody else MaterialTheme.typography.bodyMedium,
    )
    ErrorLine(shown)
}

@Composable
private fun ErrorLine(message: String?, tag: String? = null) {
    AnimatedVisibility(visible = message != null, enter = Enter, exit = Exit) {
        Text(
            message.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            modifier = if (tag == null) Modifier else Modifier.testTag(tag),
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.background(color.copy(alpha = .14f), InnerShape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ResultLine(shown: Shown, success: Color, onUndo: (() -> Unit)?) {
    val tone = if (shown.ok) success else MaterialTheme.colorScheme.error
    val time = PrivilegedEditor.formatSeconds(shown.durationMs)
    val label = when (shown.kind) {
        "timeout" -> stringResource(R.string.editor_timed_out)
        "http" -> {
            val code = if (shown.status == 200) stringResource(R.string.editor_http_ok, shown.status) else shown.status?.toString().orEmpty()
            "$code · $time"
        }
        "shell" -> stringResource(R.string.editor_exit_code, shown.exit ?: 0) + " · $time"
        "written" -> stringResource(R.string.editor_written)
        "done" -> stringResource(R.string.editor_done) + " · $time"
        else -> stringResource(R.string.editor_failed)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = .14f), InnerShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("config_test_result"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (shown.ok) "✓" else "!", color = tone, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        Text(label, Modifier.weight(1f), color = tone, style = MaterialTheme.typography.labelLarge)
        if (onUndo != null) {
            Text(
                "↶ ${stringResource(R.string.editor_undo)}",
                Modifier.clickable(role = Role.Button, onClick = onUndo).testTag("config_undo").padding(8.dp),
                color = tone,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
