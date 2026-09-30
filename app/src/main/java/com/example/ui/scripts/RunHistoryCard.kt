package com.example.ui.scripts

import android.content.res.Resources
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.data.RunFailure
import com.example.data.RunRecord
import com.example.data.ScriptActionCatalog
import com.example.data.TriggerPressType
import com.example.data.getReadableKeyName
import com.example.ui.components.InnerShape
import io.github.nicholasarda.keysnap.R
import java.text.DateFormat
import java.util.Date

/**
 * The editor's "Recent runs", newest first: when this shortcut ran, what started it, and which
 * actions failed and why. Framed like the Only-if card so the chain reads as one column.
 */
@Composable
internal fun RunHistoryCard(runs: List<RunRecord>) {
    val colors = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
            .testTag("run_history_card"),
    ) {
        if (runs.isEmpty()) {
            Text(
                stringResource(R.string.run_history_empty),
                Modifier.padding(16.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        runs.forEachIndexed { index, run ->
            val failures = run.actions.filterNot { it.ok }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("run_row_$index"),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    if (failures.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    null,
                    Modifier.size(18.dp),
                    tint = if (failures.isEmpty()) colors.primary else colors.error,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(runTriggerLabel(run, res), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
                    Text(timeFormat.format(Date(run.at)), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (failures.isEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.run_all, run.actions.size, run.actions.size),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    failures.forEach { failure ->
                        val title = ScriptActionCatalog.descriptor(failure.type)?.let { stringResource(it.titleRes) } ?: failure.type
                        Text(
                            "$title · ${stringResource(RunFailure.from(failure.reason).labelRes)}",
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun runTriggerLabel(run: RunRecord, res: Resources): String {
    val keyCode = run.keyCode
    val press = TriggerPressType.entries.firstOrNull { it.name == run.pressType }
    return when {
        run.source == RunRecord.SOURCE_AUTOMATION -> res.getString(R.string.trigger_kind_automation)
        run.source == RunRecord.SOURCE_WIDGET -> res.getString(R.string.run_source_widget)
        run.source == RunRecord.SOURCE_TILE -> res.getString(R.string.run_source_tile)
        keyCode == null -> res.getString(R.string.trigger_kind_key)
        press == null -> getReadableKeyName(res, keyCode)
        else -> "${getReadableKeyName(res, keyCode)} · ${res.getString(press.titleRes)}"
    }
}
