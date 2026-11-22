package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.SensitiveAppsRepository
import com.example.service.AdvancedModeRecovery
import com.example.service.RecoveryStep
import com.example.service.adb.WirelessAdbManager
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgeState
import com.example.service.recoverySteps
import com.example.ui.theme.setupWarning
import io.github.nicholasarda.keysnap.R

/**
 * After a Sensitive app ended Advanced Mode: one card per missing piece, each with one button, each
 * leaving the moment it is fixed. [hideAccessibility]: another card on screen already asks for it.
 */
@Composable
fun AdvancedModeFixCards(
    onOpenAccessibility: () -> Unit,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier,
    hideAccessibility: Boolean = false,
) {
    val context = LocalContext.current
    val store by remember(context) { SensitiveAppsRepository.getInstance(context).store }.collectAsState()
    val running by WirelessAdbManager.isAdvancedModeRunning.collectAsState()
    val bridgeState by BridgeController.state.collectAsState()
    val switches by AdvancedModeRecovery.switches.collectAsState()
    val connecting by AdvancedModeRecovery.connecting.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { AdvancedModeRecovery.refresh(context) }
    val steps = recoverySteps(store.advancedModeEnded, running, switches)
        .filterNot { hideAccessibility && it == RecoveryStep.Accessibility }
    val tint = setupWarning()

    AnimatedVisibility(steps.isNotEmpty(), modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecoveryStep.entries.forEach { step ->
                AnimatedVisibility(step in steps, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    val tag = Modifier.testTag("advanced_fix_${step.name}")
                    when (step) {
                        RecoveryStep.DeveloperOptions -> BannerRow(
                            stringResource(R.string.sensitive_fix_dev_off), tint,
                            { AdvancedModeRecovery.openDeveloperOptionsFix(context) }, tag,
                        ) {
                            Text(
                                stringResource(R.string.sensitive_fix_dev_hint),
                                Modifier.padding(start = 26.dp),
                                color = tint,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        RecoveryStep.WirelessDebugging -> BannerRow(
                            stringResource(R.string.sensitive_fix_wireless_off), tint,
                            { AdvancedModeRecovery.openWirelessDebuggingFix(context) }, tag,
                        )
                        RecoveryStep.Accessibility -> BannerRow(
                            stringResource(R.string.sensitive_fix_accessibility_off), tint, onOpenAccessibility, tag,
                            buttonLabel = R.string.home_setup_illo_allow,
                        )
                        RecoveryStep.TurnOn -> BannerRow(
                            stringResource(R.string.sensitive_fix_advanced_off), tint,
                            { AdvancedModeRecovery.turnOn(context, onFailed = onOpenWizard) }, tag,
                            buttonLabel = R.string.setup_turn_on,
                            busy = connecting || bridgeState == BridgeState.STARTING,
                        )
                    }
                }
            }
        }
    }
}
