package com.example.ui.scripts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assistant.AssistantIntegration
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R

@Composable
internal fun rememberAssistantSelected(): State<Boolean> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val selected = remember(context) { mutableStateOf(AssistantIntegration.isSelected(context)) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) selected.value = AssistantIntegration.isSelected(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return selected
}

@Composable
internal fun AssistantSettingsRow() {
    val context = LocalContext.current
    val selected = rememberAssistantSelected().value
    Column(
        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
            .clickable { AssistantIntegration.openSettings(context) }.padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.assistant_setup_title), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(if (selected) R.string.assistant_selected else R.string.assistant_not_selected),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
