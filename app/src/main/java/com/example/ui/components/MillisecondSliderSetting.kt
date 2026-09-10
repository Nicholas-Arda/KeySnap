package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MillisecondSettingSpec
import com.example.data.normalizeTimingValue
import kotlin.math.roundToLong

/** A titled slider bound to a millisecond value, with a reset-to-default button. Shared by the
 * Settings screen (global defaults) and the per-script settings sheet (overrides), which resets to
 * "follow the global value" rather than to [MillisecondSettingSpec.default] and so passes its own
 * [onReset] and [resetEnabled]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MillisecondSliderSetting(
    title: String,
    description: String,
    valueMs: Long,
    spec: MillisecondSettingSpec,
    testTag: String,
    enabled: Boolean = true,
    resetEnabled: Boolean = valueMs != spec.default,
    onReset: (() -> Unit)? = null,
    onValueChange: (Long) -> Unit,
) {
    val msDescription = stringResource(R.string.slider_milliseconds, valueMs)
    val restoreDescription = stringResource(R.string.slider_restore_default, title)
    val sliderColors = SliderDefaults.colors(
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    title,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    description,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(
                modifier = Modifier.widthIn(min = 76.dp),
                shape = InnerShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.75f else 0.4f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = valueMs.toString(),
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.25).sp,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.slider_ms_unit),
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = valueMs.toFloat(),
                onValueChange = { rawValue ->
                    onValueChange(normalizeTimingValue(rawValue.roundToLong(), spec))
                },
                valueRange = spec.min.toFloat()..spec.max.toFloat(),
                steps = spec.sliderSteps,
                enabled = enabled,
                colors = sliderColors,
                // Default track paints a stop indicator dot at the far end, which reads as a
                // second, unreachable thumb.
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = enabled,
                        colors = sliderColors,
                        drawStopIndicator = null,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(testTag)
                    .semantics {
                        contentDescription = title
                        stateDescription = msDescription
                    },
            )
            IconButton(
                onClick = { onReset?.invoke() ?: onValueChange(spec.default) },
                enabled = enabled && resetEnabled,
                modifier = Modifier
                    .testTag("${testTag}_reset_button")
                    .semantics {
                        contentDescription = restoreDescription
                    },
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    tint = if (enabled && resetEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}
