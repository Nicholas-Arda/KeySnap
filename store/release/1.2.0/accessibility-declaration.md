# Play Console — Accessibility API declaration (1.2.0 update)

Play Console → App content → Accessibility API. What the service does is unchanged since 1.1.5: the same config (`packageNames="com.android.settings"`), same capabilities, same three uses. Only the in-app prominent disclosure looks different: the old Accessibility dialog is now a bottom sheet with "Can / Can't" rows, and every entry point (Home "Fix" banner, Settings row, setup) opens that same sheet.

## What to change
- **Description:** nothing. The 1.1.5 text (`store/release/1.1.5/accessibility-declaration.md`) still matches the app word for word.
- **"Is your app an accessibility tool?":** still No.
- **Video:** re-record. The current video shows the old dialog; a reviewer comparing it with 1.2.0 would not find that screen. Record on the **1.2.0 release build** (not `.dev`).

## The disclosure the video must show (1.2.0)
Sheet "Turn on Accessibility":
- Why: "Android shows button presses only through Accessibility."
- Can: Read your button presses · Open Settings' pairing-code dialog and read the code
- Can't: See other apps' screens · Save or send anything it reads
- Button: "Agree and open settings" (Android's Accessibility settings open only after this tap)

## Video (30–60 seconds)
1. Fresh KeySnap → Home shows "Setup" pill + red banner → tap **Fix** → the sheet above → tap **Agree and open settings** → Android Accessibility settings → turn KeySnap on → back in KeySnap.
2. Map a button to Flashlight → press the physical button → torch turns on.
3. Settings → Advanced Mode → "Open settings and start pairing" → KeySnap opens Wireless Debugging → taps "Pair device with pairing code" → code is read → back in KeySnap, Advanced Mode running.

Recording: `adb shell screenrecord --time-limit 60 /sdcard/keysnap-a11y.mp4` (step 2 needs a real key press; `input keyevent` never reaches Accessibility). Upload as an unlisted YouTube video and paste the link in the declaration.
