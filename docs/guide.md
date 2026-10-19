# KeySnap guide

[? Back to the overview](../README.md) ? [Get KeySnap on Google Play](https://play.google.com/store/apps/details?id=io.github.nicholasarda.keysnap)

Setup details, permissions, troubleshooting and development.

- [Shortcuts and features](#what-can-i-make)
- [Advanced Mode](#advanced-mode)
- [Permissions](#permissions)
- [Privacy](#privacy)
- [Building from source](#building-from-source)
- [FAQ](#faq)
- [Contributing](#contributing)

## What can I make?

A **shortcut** binds one or more triggers to one or more actions. Some examples:

| Press | Does |
|---|---|
| Volume Down, twice | Toggle the flashlight |
| Volume Up, hold | Take a screenshot |
| Power, twice | Open the camera *(Advanced Mode)* |
| Volume Down, hold | Turn on Do Not Disturb |
| No key at all, after 17:00 on your home Wi‑Fi | Toggle mobile data |

Shortcuts run in the background. Local actions work offline; web requests, links and RSS feeds need a connection to the destinations you choose. Some examples require Advanced Mode or an additional permission.

## How much control do I have?

**Triggers.** Any hardware key Android reports: volume, power, camera, the assistant button, headset buttons, keys on a Bluetooth keyboard or game controller, and unlabelled OEM buttons you can name yourself. Each key can be a single press, a double press or a long press, and a trigger can also be several keys pressed together, such as Volume Up + Volume Down. There is also an **Automation** trigger with no key at all: it fires when its conditions become true. Any shortcut can also run from a **home-screen widget** or one of three **Quick Settings tiles**, and a shortcut can skip the key entirely and live only there, so a phone without a spare button still gets a one-tap way in.

**Actions.** 90, in twelve groups: input and gestures, apps, flashlight, navigation, volume, display, media, connectivity, keyboard, device, sound and automation. Every action's settings are visible in the editor before it runs, and privileged ones such as shell commands, intents and HTTP requests show the exact command or URL they will execute.

**Conditions.** Attach an *Only if* list to any shortcut: time of day, day of week, charging state, battery level, Wi‑Fi network, Bluetooth device, headphones, dark mode, Do Not Disturb, ringer mode, screen and lock state, orientation, which app is in the foreground, whether media is playing, a notification from an app you choose, a limit of once every so many minutes, and more. More than fifty in total. Automations can also start at a time of day or repeat every interval, wait for a calendar event to start, or for a new item in an RSS feed you follow; the feed is checked at most every 15 minutes, at exactly the address you typed.

**Recipes.** Home has a gallery of ready-made shortcuts in categories: pick one, press the key you want, and it is ready. A *Ready-made commands* shelf adds shell commands for Advanced Mode (record the screen, switch dark mode, grayscale or power saving, change animation speed, start a timer, with timed versions that run on their own), map searches, and web requests for smart-home, chat, notification and spreadsheet services. These always open in the editor first, so you see the exact command before it can run, and every web request comes with its address left blank, so you paste your own. A *Send a message* recipe opens a chat with a contact you pick, in the messaging app you pick, with your text typed in; you tap Send, or turn on *Send automatically* and Advanced Mode taps it for you.

**Sharing.** Any shortcut can be shared as text through Android's share sheet and imported on another phone, by sharing that text or file to KeySnap or pasting it under *Import*. The key stays behind, because keys belong to a phone. Before anything is saved, KeySnap lists what the shortcut runs, with the exact command, URL or intent of every privileged action, and a shared shortcut that contains one is saved switched off.

**Run history.** Each shortcut remembers its last 20 runs: when it ran, which key or automation started it, and whether each action worked. When an action can't run, a notification says why, such as a missing permission or Advanced Mode being off, and opens the shortcut. The history holds no action contents and never leaves the phone.

**Key capsule.** Optional visual feedback for physical key shortcuts, with Pixel, ColorOS and Nothing styles. Customize the appearance and motion, align each recorded key separately, and preview simulated results without running an action. Enable it under Settings → Key capsule and allow display over other apps. It stays hidden on the lock screen. Feedback requires the app process to be running; the independent Advanced Mode bridge can keep executing shortcuts without it.

*Coming soon: Key capsule is in development and is not included in the current Google Play release.*

**Appearance.** Follow the system theme or choose light, dark or sepia, with an OLED option for dark mode. The interface ships in English, Arabic, German, Spanish, French, Hindi, Italian, Portuguese (Brazil), Russian and Turkish.

## Advanced Mode

Android only lets a normal app see the keys it decides to share, which leaves the power button, the assistant button and many OEM buttons out of reach. Advanced Mode fixes that without root.

The app pairs with your phone's own **Wireless Debugging** over the local network and starts a small helper, the *bridge*, with shell privileges. The bridge reads key presses straight from the kernel and runs your shortcuts on its own, so it keeps working after Android closes the app in the background. It reads its configuration from a file the app writes, keeps no record of what you press, never leaves your network, and deletes itself when you turn Advanced Mode off. When a *Send a message* action has *Send automatically* on, the bridge also reads the screen layout of the messaging app KeySnap just opened, only while that app is in front and for a few seconds, to find its Send button and to check that the chat is with the person you chose and that the message field holds exactly your text, before it taps Send and once after. That layout is not stored, except for a moment in a private temporary file on some Android versions, deleted right away, and it is never logged or sent. A reboot stops the bridge. With the optional Start after restart setting, KeySnap brings it back after unlock and Wi-Fi; otherwise restart it through setup.

Input capture is strictly read-only. The bridge listens to the kernel's event stream and never grabs or forges input.

## Permissions

KeySnap asks for a permission only when you use the feature that needs it, and each one is a switch you turn on yourself in Android's settings.

- **Accessibility service.** The only way an app can see hardware key presses. It is restricted to reading screen content from the system Settings app alone, and uses that solely to read the pairing code from the Wireless Debugging dialog during Advanced Mode setup.
- **Run in the background and notifications.** So Android does not put the app to sleep, so setup can show the pairing prompt, so a shortcut that can't run can tell you why, and so a shortcut can show a notification you wrote.
- **Location.** Android only reveals the Wi‑Fi network name to apps holding it, so the *connected to Wi‑Fi network* condition needs it. The app never reads your position.
- **Calendar.** Read-only, and requested only when you add a *Calendar event in progress* condition. KeySnap reads the title, calendar name, start and end of your events, and whether each is all-day or cancelled, on the phone to check them against that condition, and never saves, logs or sends them.
- **Alarms & reminders.** Optional, and asked only when you add a *Calendar event in progress* condition. Used only to wake KeySnap at the start or end of a matching event, so the automation runs on time; nothing is read or sent through it.
- **Notification access.** Only if you add a *Notification received* condition or a *Reply to notification* action. Android then tells KeySnap about every new notification; KeySnap looks only at which app posted it, and reads the title and text only from an app you named, to check it against your condition on the phone. It keeps in memory only when a condition matched and which notification it was, so a reply can find that chat, and never saves, logs or sends notification content.
- **Bluetooth, Do Not Disturb access, usage access, modify system settings, display over other apps.** Each unlocks one group of actions or conditions and is requested from inside that feature.
- **Set wallpaper** is granted at install and used only by the *Set wallpaper* action. The picture comes from Android's photo picker, so KeySnap sees the one image you pick and never the rest of your photos.
- **Camera** is deliberately not requested. The flashlight uses an API that needs no permission.
- **Start after restart (optional).** Uses the restart broadcast and `WRITE_SECURE_SETTINGS`, granted through your Advanced Mode bridge, to bring Advanced Mode back after unlock and Wi-Fi. The extra permission is removed on disable. Only Wireless Debugging is changed; USB debugging and Android's paired-key expiry setting are left alone.

The full list with reasons is in the [privacy policy](https://nicholas-arda.github.io/keysnap-privacy/).

## Privacy

No servers, no accounts, no analytics, no automatic crash reporting. KeySnap does not automatically upload your shortcuts or key presses. Sharing a shortcut or sending an optional problem recording hands it to the app you choose. Network actions and RSS feeds use the destinations you configure. The Pro purchase goes through Google Play Billing, which never receives your shortcuts, key presses or usage history from KeySnap. Details: [privacy policy](https://nicholas-arda.github.io/keysnap-privacy/).

## Building from source

Open the project in Android Studio and use its bundled JDK as the Gradle JDK. For command-line builds, set `JAVA_HOME` to that JDK and connect an Android 11+ device or start an emulator.

```bash
git clone https://github.com/Nicholas-Arda/KeySnap.git
cd KeySnap
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :bridge:test
./gradlew :app:installDebug       # optional: install on a connected device or emulator
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`. The Gradle wrapper is included; no API key is needed. `:app` is the Kotlin and Jetpack Compose application; `:bridge` is the small Java helper that runs with shell privileges in Advanced Mode, shipped inside the app as `bridge.dex`.

## FAQ

**Is it free?** The free version allows three active shortcuts and two home-screen widgets. Pro is a one-time purchase through Google Play that removes those limits; there is no subscription. The three Quick Settings tiles are free.

**Does it need root?** No. Everyday shortcuts use the accessibility service; Advanced Mode uses Wireless Debugging, a feature already in Android's Developer options. Nothing on the phone is modified.

**Which phones does it run on?** KeySnap requires Android 11 or newer. The keys you can map depend on what your device reports: start with volume or headset buttons. Power, assistant and manufacturer buttons may require Advanced Mode, and availability varies by device.

**Why does Google Play warn me about the accessibility permission?** Play shows that warning for every app that uses an accessibility service. In KeySnap it is the only way to see hardware key presses. The service is restricted to the system Settings app and cannot read anything you type or any other app's screen.

**Will it break my volume buttons?** Only the presses you map. A key with a shortcut is taken over by default so it does not also do its normal job; each shortcut has a *Consume original input* switch if you want the key to do both. Keys that only Advanced Mode can see, such as power, are never blocked; the shortcut runs alongside what the phone already does.

**Does it drain the battery?** It listens for key events instead of polling, so it costs almost nothing while idle. Advanced Mode's helper is a single small process that sleeps until a key is pressed.

**Does it work with the screen off?** With Advanced Mode, yes: the helper reads the kernel directly. Without it, Android decides which keys reach an accessibility service while the screen is off, so it varies by device and key.

**Does it work with Bluetooth headphones and game controllers?** Yes, any device Android reports as a keyboard, headset or gamepad. Unlabelled buttons can be recorded and given a name.

**Can it send messages?** Two ways. *Reply to notification* answers a chat whose notification is showing, through that notification's own Reply button, without opening the app. It cannot start a new chat, and once you open or swipe away that notification there is nothing left to reply to. *Send a message* writes to anyone: it opens a chat with a contact you pick, in the messaging app you pick, with your text typed in, and you tap Send. With *Send automatically* on, Advanced Mode taps Send for you; the screen has to be on and unlocked.

**Why did my shortcuts stop after a day?** Some phone makers kill background apps aggressively. Home shows a setup card with the two switches that stop this. Advanced Mode is immune because its helper is not part of the app process.

**Can Advanced Mode return after a reboot?** Enable *Settings → Advanced Mode → Start after restart*. It starts again after your first unlock and a Wi-Fi connection, using the existing pairing. Android 11–12 can ask for network approval after each restart; some phone makers require auto-launch permission. The option is off by default. Wireless Debugging stays on while Advanced Mode runs. If KeySnap enabled it, turning the option off also stops Advanced Mode and restores Wireless Debugging. A daily local connection checks the existing pairing but does not guarantee that Android extends its expiry; an expired or removed pairing needs setup again.

**Does it need internet?** Shortcuts work offline unless they use a network action or RSS feed. The app never contacts a server of its own. Network use is limited to pairing with your own phone over your local network, processing and checking the Pro purchase through Google Play, and any HTTP request, URL or RSS feed you put into a shortcut yourself.

**Can I back up my shortcuts?** They travel with Android's own backup, so they come back when you restore a phone. The Advanced Mode pairing is deliberately not backed up, because it authorises one specific device.

**Can I export/import my shortcuts to a file?** Yes, one at a time. Open a shortcut and tap *Share*, then save the text wherever you like. To bring it back, share that text or file to KeySnap, or use *Import* on the Shortcuts page.

**Can I share my shortcuts with friends?** Yes, the same way. Anyone you send one to can read it, URLs and commands included, so check it for passwords or tokens first. A shortcut that runs commands, intents, settings changes, web requests or links arrives switched off.

## Support

Bugs and feature requests go to [GitHub Issues](https://github.com/Nicholas-Arda/KeySnap/issues) or to [support@ardasenn.com](mailto:support@ardasenn.com). Include your Android version, phone model and whether Advanced Mode was on.

## Disclaimer

Shell commands, intents and HTTP requests you put into a shortcut run with the privileges you gave the app, exactly as you wrote them. Advanced Mode relies on Android's Wireless Debugging, a developer feature; on some phones it also switches off automatically on reboot. KeySnap is not affiliated with Google or any phone manufacturer.

## Contributing

Issues and pull requests are welcome. Before a change to key handling or the bridge, please read the safety rules the project follows:

- Input capture stays read-only. Never grab or inject evdev input.
- Privileged actions show the user exactly what will run and pass it through unchanged.
- No secrets in logs: pairing codes, tokens, addresses and the arguments of user-authored actions never reach a log.
- The accessibility service reads the Settings app only.
- Turning Advanced Mode off must leave nothing running.

Translations live in `app/src/main/res/values-*/strings.xml`.

## Licence

KeySnap is free software under the [GNU General Public License v3.0](../LICENSE). Google Play Billing is the only third-party SDK service, used only to process and check the Pro purchase. Network actions and RSS feeds connect only to destinations you configure.
