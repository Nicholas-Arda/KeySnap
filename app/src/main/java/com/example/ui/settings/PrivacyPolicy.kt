package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppAlertDialog
import com.example.ui.theme.MonoLabel

/**
 * The privacy policy, in one place, so the same wording can be published on the web without
 * drifting from what the app says.
 *
 * Google Play requires a **hosted** policy URL in the Console listing; showing it in the app does
 * not satisfy that. Nothing is published yet, deliberately: the app's name is still going to
 * change, and a public policy page carrying the old name would have to be taken down and
 * republished anyway. Host `docs/privacy-policy.md` under the final name shortly before the Play
 * upload, then paste that URL into the Console.
 *
 * Every claim here is a claim about the code. If an action, permission or stored file changes,
 * this text changes with it.
 */
object PrivacyPolicy {

    const val LAST_UPDATED = "8 September 2026"

    /**
     * Left blank deliberately. A published policy needs a contact address, but which address to
     * publish is the developer's call, so the section is simply omitted until one is set rather
     * than shipping a placeholder.
     */
    const val CONTACT = ""

    val sections: List<Pair<String, String>> = listOf(
        "What is collected" to
            "KeySnap has no servers, no accounts, no analytics and no crash reporting. " +
            "Nothing about your shortcuts, the keys you press or the way you use the app is ever " +
            "sent to the developer or to anyone else. The only data that leaves your device goes " +
            "to Google's advertising and billing services, described under \"Advertising and " +
            "purchases\" below, and it is limited to what those services need to show an ad or " +
            "confirm a purchase.",

        "What is stored on your device" to
            "Your shortcuts, their triggers and any names you give to unlabelled hardware keys. " +
            "Your timing, haptic and appearance preferences. An ADB key pair the app generates " +
            "to authorise this one device for wireless debugging. When Advanced Mode is on, the " +
            "bridge program and the local token the app uses to talk to it. All of this stays in " +
            "the app's private storage on this device.",

        "The accessibility service" to
            "Android gives no other way for an app to see hardware key presses, so the " +
            "accessibility service exists to detect them. It is restricted to reading screen " +
            "content from the system Settings app only, and it uses that solely to read the " +
            "six-digit pairing code from the Wireless Debugging dialog while you are setting up " +
            "Advanced Mode. It cannot read any other app's screen, it does not receive the text " +
            "you type, and the pairing code is never stored or transmitted.",

        "Advanced Mode and the system bridge" to
            "Advanced Mode pairs with your device's own Wireless Debugging over the local network " +
            "and starts a small helper program that runs with shell privileges. That program " +
            "reads hardware keys from the kernel and runs the actions in your shortcuts. It " +
            "reads its configuration from a file the app writes and keeps no record of what you " +
            "press. Turning Advanced Mode off, or rebooting, stops it; turning it off also deletes " +
            "it from the device.",

        "Network use" to
            "The app makes three kinds of network connection. The first is to your own device " +
            "over the local network, to pair with Wireless Debugging and start Advanced Mode; it " +
            "never leaves your network. The second is any HTTP request or URL that you put into a " +
            "shortcut yourself, sent to the address you entered, exactly as you wrote it; the app " +
            "never adds, rewrites or redirects those. The third is to Google, to fetch ads and to " +
            "ask Google Play whether you have bought Pro. The app makes no other requests of its " +
            "own.",

        "Permissions and why" to
            "Internet, network state, Wi-Fi state and multicast: to find and connect to your " +
            "device's own wireless debugging service on the local network. Notifications: to " +
            "show the pairing code prompt and the setup notification. Vibrate: for the " +
            "confirmation buzz when a shortcut fires. Display over other apps: only when you tap " +
            "\"Pick on screen\" to choose tap coordinates; the manual X/Y fields work without " +
            "it. Bluetooth: to list your paired devices and notice when one connects, for the " +
            "Bluetooth device conditions. Location: Android only reveals the name of the Wi-Fi " +
            "network you are on to apps holding the location permission, so the \"connected to " +
            "Wi-Fi network\" condition asks for it; the app never reads your position, and the " +
            "permission is requested only when you add that condition. Usage access: to know " +
            "which app is in the foreground, for the \"app in foreground\" condition. Do Not " +
            "Disturb access: to switch the ringer to Silent. Modify system settings: to change " +
            "brightness and screen rotation without Advanced Mode. Each of these is a switch " +
            "you turn on yourself in Android's settings, and the app never asks for one until " +
            "you use the feature that needs it. The camera permission is deliberately not " +
            "requested — the torch is controlled through an API that needs no permission.",

        "Backup" to
            "Your shortcuts and preferences are included in Android's backup so they return if " +
            "you reinstall or move to a new phone. The ADB key pair and the bridge token are " +
            "excluded from both cloud backup and device-to-device transfer, because they " +
            "authorise one specific device and must never leave it.",

        "Deleting your data" to
            "Settings > About > Erase all data removes every shortcut, preference, key and " +
            "bridge file, and stops Advanced Mode first so nothing is left running. Uninstalling " +
            "the app removes its storage as well; if Advanced Mode is running when you uninstall, " +
            "turn it off first so the bridge can clean itself up.",

        "Advertising and purchases" to
            "The free version shows ads supplied by Google AdMob. AdMob receives only what its " +
            "own SDK needs to select and measure an ad, such as your device model, coarse " +
            "language and region, and the advertising ID that Android gives it, which you can " +
            "reset or delete in Android's own settings. It never receives your shortcuts, the " +
            "keys you press, or anything else this app records. Buying Pro removes the ads and " +
            "is handled entirely by Google Play Billing: the app never sees your payment details " +
            "and only asks Google Play whether the purchase exists. What Google does with what " +
            "those two services receive is covered by Google's privacy policy at " +
            "policies.google.com/privacy. The app is not directed at children.",

        "Changes" to
            "If this policy changes, the updated version ships with the app update and the date " +
            "above changes with it.",
    )
}

/** Scrollable, dismissible policy. Uses the same AlertDialog idiom as the key-naming dialog. */
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy_title)) },
        text = {
            val contactSection = PrivacyPolicy.CONTACT
                .takeIf { it.isNotBlank() }
                ?.let { listOf(stringResource(R.string.privacy_contact_heading) to it) }
                .orEmpty()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.privacy_last_updated, PrivacyPolicy.LAST_UPDATED),
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (PrivacyPolicy.sections + contactSection).forEach { (heading, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            heading,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
