# KeySnap — Privacy Policy

Last updated 8 September 2026

This is the same text the app shows under **Settings → About → Privacy policy**. A unit test
(`PrivacyPolicyTest`) fails if the two ever disagree, so publish this file as-is and update it from
`PrivacyPolicy.kt` whenever that changes. The published copy (minus this note) lives at
https://nicholas-arda.github.io/keysnap-privacy/ — the `index.md` of the public `keysnap-privacy`
repo; update it by hand whenever this file changes.

## What is collected

KeySnap has no servers, no accounts, no analytics and no crash reporting. Nothing about your shortcuts, the keys you press or the way you use the app is ever sent to the developer or to anyone else. The only data that leaves your device goes to Google's advertising and billing services, described under "Advertising and purchases" below, and it is limited to what those services need to show an ad or confirm a purchase.

## What is stored on your device

Your shortcuts, their triggers and any names you give to unlabelled hardware keys. Your timing, haptic and appearance preferences. An ADB key pair the app generates to authorise this one device for wireless debugging. When Advanced Mode is on, the bridge program and the local token the app uses to talk to it. All of this stays in the app's private storage on this device.

## The accessibility service

Android gives no other way for an app to see hardware key presses, so the accessibility service exists to detect them. It is restricted to reading screen content from the system Settings app only, and it uses that solely to read the six-digit pairing code from the Wireless Debugging dialog while you are setting up Advanced Mode. It cannot read any other app's screen, it does not receive the text you type, and the pairing code is never stored or transmitted.

## Advanced Mode and the system bridge

Advanced Mode pairs with your device's own Wireless Debugging over the local network and starts a small helper program that runs with shell privileges. That program reads hardware keys from the kernel and runs the actions in your shortcuts. It reads its configuration from a file the app writes and keeps no record of what you press. Turning Advanced Mode off, or rebooting, stops it; turning it off also deletes it from the device.

## Network use

The app makes three kinds of network connection. The first is to your own device over the local network, to pair with Wireless Debugging and start Advanced Mode; it never leaves your network. The second is any HTTP request or URL that you put into a shortcut yourself, sent to the address you entered, exactly as you wrote it; the app never adds, rewrites or redirects those. The third is to Google, to fetch ads and to ask Google Play whether you have bought Pro. The app makes no other requests of its own.

## Permissions and why

Internet, network state, Wi-Fi state and multicast: to find and connect to your device's own wireless debugging service on the local network. Notifications: to show the pairing code prompt and the setup notification. Vibrate: for the confirmation buzz when a shortcut fires. Display over other apps: only when you tap "Pick on screen" to choose tap coordinates; the manual X/Y fields work without it. Bluetooth: to list your paired devices and notice when one connects, for the Bluetooth device conditions. Location: Android only reveals the name of the Wi-Fi network you are on to apps holding the location permission, so the "connected to Wi-Fi network" condition asks for it; the app never reads your position, and the permission is requested only when you add that condition. Usage access: to know which app is in the foreground, for the "app in foreground" condition. Do Not Disturb access: to switch the ringer to Silent. Modify system settings: to change brightness and screen rotation without Advanced Mode. Each of these is a switch you turn on yourself in Android's settings, and the app never asks for one until you use the feature that needs it. The camera permission is deliberately not requested — the torch is controlled through an API that needs no permission.

## Backup

Your shortcuts and preferences are included in Android's backup so they return if you reinstall or move to a new phone. The ADB key pair and the bridge token are excluded from both cloud backup and device-to-device transfer, because they authorise one specific device and must never leave it.

## Deleting your data

Settings > About > Erase all data removes every shortcut, preference, key and bridge file, and stops Advanced Mode first so nothing is left running. Uninstalling the app removes its storage as well; if Advanced Mode is running when you uninstall, turn it off first so the bridge can clean itself up.

## Advertising and purchases

The free version shows ads supplied by Google AdMob. AdMob receives only what its own SDK needs to select and measure an ad, such as your device model, coarse language and region, and the advertising ID that Android gives it, which you can reset or delete in Android's own settings. It never receives your shortcuts, the keys you press, or anything else this app records. Buying Pro removes the ads and is handled entirely by Google Play Billing: the app never sees your payment details and only asks Google Play whether the purchase exists. What Google does with what those two services receive is covered by Google's privacy policy at policies.google.com/privacy. The app is not directed at children.

## Changes

If this policy changes, the updated version ships with the app update and the date above changes with it.
