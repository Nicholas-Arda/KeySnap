package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.ConsoleShape
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.home.rememberReducedMotion
import com.example.ui.theme.MonoLabel
import io.github.nicholasarda.keysnap.BuildConfig
import io.github.nicholasarda.keysnap.R

/**
 * The page that answers "is this app safe?" before a user decides it isn't.
 *
 * Key Mapper's one-star reviews are not complaints about wording, they are disbelief: an app that
 * reads every hardware key and runs shell commands looks like spyware to someone who has only the
 * permission list to go on. Disbelief is not answered by a longer summary line, it is answered by
 * things that can be checked — published source, a boundary Android itself enforces, a switch that
 * deletes the bridge. So each claim here comes with the check that backs it, the ads are named
 * rather than omitted, and the animated miniatures carry the claim the sentence underneath spells
 * out. Full screen rather than a dialog, because it is read, not dismissed.
 *
 * Every claim restates something [PrivacyPolicy] already says. If an action, permission or stored
 * file changes, both change together.
 */
@Composable
fun AboutDialog(onEraseAllData: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val uriHandler = LocalUriHandler.current
    var showPolicy by remember { mutableStateOf(false) }
    var confirmingErase by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .testTag("about_screen"),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("about_back_button")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_close),
                        tint = colors.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.settings_about_app),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // Window insets read as zero inside a decorFitsSystemWindows=false dialog, the
                    // same way they do in the setup wizard, so the last panel needs the gesture
                    // handle cleared by hand.
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.about_hero_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onBackground,
                    )
                    Text(
                        stringResource(R.string.about_hero_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                AboutCard(Icons.Outlined.CloudOff, stringResource(R.string.about_no_server_title)) {
                    NoServerIllustration(reduced)
                    AboutBody(stringResource(R.string.about_no_server_body))
                }

                AboutCard(Icons.Filled.Code, stringResource(R.string.about_open_source_title)) {
                    OpenSourceIllustration(reduced)
                    AboutBody(stringResource(R.string.about_open_source_body))
                    val sourceUrl = stringResource(R.string.about_source_url)
                    Button(
                        onClick = { uriHandler.openUri(sourceUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("about_source_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = .14f)),
                        shape = InnerShape,
                    ) {
                        Icon(Icons.Filled.OpenInNew, null, Modifier.size(16.dp), tint = colors.primary)
                        Text(
                            stringResource(R.string.about_view_source),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }
                }

                AboutCard(Icons.Outlined.RemoveRedEye, stringResource(R.string.about_scope_title)) {
                    AccessibilityScopeIllustration(reduced)
                    AboutBody(stringResource(R.string.about_scope_body))
                    Text(
                        stringResource(R.string.about_scope_config),
                        style = MonoLabel,
                        color = colors.onSurfaceVariant,
                    )
                }

                AboutCard(Icons.Filled.Terminal, stringResource(R.string.about_bridge_title)) {
                    BridgeOffIllustration(reduced)
                    AboutBody(stringResource(R.string.about_bridge_body))
                }

                AboutCard(Icons.Outlined.Wifi, stringResource(R.string.about_traffic_title)) {
                    TrafficRow(
                        stringResource(R.string.about_traffic_device),
                        stringResource(R.string.about_traffic_device_body),
                        accent = true,
                    )
                    TrafficRow(
                        stringResource(R.string.about_traffic_yours),
                        stringResource(R.string.about_traffic_yours_body),
                        accent = true,
                    )
                    TrafficRow(
                        stringResource(R.string.about_traffic_google),
                        stringResource(R.string.about_traffic_google_body),
                        accent = false,
                    )
                    AboutBody(stringResource(R.string.about_traffic_all))
                }

                AboutCard(Icons.Filled.Delete, stringResource(R.string.about_erase_title), tint = colors.error) {
                    AboutBody(stringResource(R.string.about_erase_body))
                    Button(
                        onClick = { confirmingErase = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("about_erase_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error.copy(alpha = .13f)),
                        shape = InnerShape,
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp), tint = colors.error)
                        Text(
                            stringResource(R.string.settings_erase),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.error,
                        )
                    }
                }

                Card(
                    Modifier.fillMaxWidth(),
                    shape = ConsoleShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
                ) {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouchTarget)
                                .clickable(onClick = { showPolicy = true })
                                .testTag("about_privacy_policy_button"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_privacy_policy),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                        val supportEmail = stringResource(R.string.about_contact_email)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouchTarget)
                                // A device with no mail app resolves nothing; the row goes quiet
                                // rather than crashing the About page.
                                .clickable { runCatching { uriHandler.openUri("mailto:$supportEmail") } }
                                .testTag("about_contact_button"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.about_contact),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                )
                                Text(
                                    supportEmail,
                                    style = MonoLabel,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                        Text(
                            stringResource(
                                R.string.about_footer_line,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            Modifier.padding(top = 10.dp),
                            style = MonoLabel,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPolicy = false })
    }
    if (confirmingErase) {
        EraseAllDataDialog(
            onConfirm = {
                confirmingErase = false
                onEraseAllData()
                onDismiss()
            },
            onDismiss = { confirmingErase = false },
        )
    }
}

/** The About page's card: the wizard's bordered panel, with the claim's icon on its title. */
@Composable
private fun AboutCard(
    icon: ImageVector,
    title: String,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = ConsoleShape,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint ?: colors.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onBackground,
                )
            }
            content()
        }
    }
}

@Composable
private fun AboutBody(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One destination in the "every connection" list; [accent] marks the two that stay yours. */
@Composable
private fun TrafficRow(title: String, body: String, accent: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .background(if (accent) colors.primary else colors.onSurfaceVariant, CircleShape),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * Shared by the Settings row and the About page's own button, so the warning a user reads before
 * erasing cannot differ depending on which one they tapped.
 */
@Composable
internal fun EraseAllDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_erase_confirm_title)) },
        text = { Text(stringResource(R.string.settings_erase_warning)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("erase_all_data_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error,
                    contentColor = colors.onError,
                ),
            ) { Text(stringResource(R.string.settings_erase_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
