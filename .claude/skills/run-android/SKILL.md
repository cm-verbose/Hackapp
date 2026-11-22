---
name: run-android
description: Run Hackapp (Plan Montréal) on the Android emulator and check it on a device. Use when asked to launch, install or screenshot the app, verify a screen or flow on a device, test at large font scale, in French or English, or with TalkBack, or check the underway alarms and notifications (UnderwayNotifier / UnderwayReceiver) with adb.
---

# run-android: emulator, install, screenshots, accessibility, notifications

Background: CLAUDE.md sections 1 (package, navigation, auth), 5.2 (Windows notes), 8 (flow screens, known gaps).
Facts checked on 2026-11-21:
- AVD `Medium_Phone`, image `google_apis_playstore`, API 37.1. It is a Play Store image, so **`adb root` is not available**.
- Package and activity: `com.example.hackapp/.MainActivity`.
- `adb` is **not on PATH**.

## Rules
- Gradle: during audits only workflow-auditor runs Gradle (see `docs/implementation-plan.md`). Otherwise run it from one agent at a time.
- **Never add a login bypass, test flag or fake account in code** to reach screens. See "Login" below.
- **Never type the user's real credentials** yourself. The user enters them on the emulator.
- Restore device settings you change (font scale, locale, TalkBack) before you finish, and say so in your report.
- Shell variables do not persist between tool calls. Start **every** command line with the `$adb` definition below. All commands are one-line PowerShell, run from the repo root `D:\29401\Documents\Kotlin\Hackapp`.
- Put screenshots in the session scratchpad directory, not in the repo, unless asked.

## 1. Find adb (prefix for every command)
`$sdk = (Get-Content local.properties | Where-Object { $_ -like 'sdk.dir=*' }) -replace '^sdk.dir=','' -replace '\\\\','\' -replace '\\:',':'; if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }; $adb = "$sdk\platform-tools\adb.exe"`

The short form below writes `$adb ...` and means "that prefix; then `& $adb ...`".

## 2. Emulator
- List devices: `& $adb devices`. If `emulator-5554  device` is listed, skip to step 3.
- List AVDs: `& "$sdk\emulator\emulator.exe" -list-avds`
- Start it without blocking: `Start-Process "$sdk\emulator\emulator.exe" -ArgumentList '-avd','Medium_Phone'`
- Wait for boot: `& $adb wait-for-device; & $adb shell getprop sys.boot_completed`. Repeat the getprop check until it prints `1`. Use a polling check, not sleep.

## 3. Install and launch
- Build and install: `.\gradlew.bat installDebug`. `connectedDebugAndroidTest` uninstalls the app when it finishes, so run `installDebug` again after connected tests. Check with `& $adb shell pm list packages com.example.hackapp`.
- Launch: `& $adb shell am start -n com.example.hackapp/.MainActivity`
- Crash log: `& $adb logcat -d -s AndroidRuntime:E`
- Fresh start: `& $adb shell pm clear com.example.hackapp`. It wipes the login, the onboarding profile, the DataStore trip and stamps, and the in-app language. Alarms that were already scheduled survive it and may still fire. Ask first if the user signed in by hand, because this signs them out.

## 4. Login (start screen is `ScreenLoginDestination`)
Firebase Auth needs network. The emulator normally has it; there is no offline sign-in. The options, honestly:
1. **Google sign-in**: the Play Store image supports a Google account. The **user** adds the account on the emulator and taps the Google button; you wait.
2. **Email and password** (Register / Welcome pages, `auth/AuthService.kt`): the **user** registers a throwaway address, or gives you a test account they created for this purpose. This works only if the Email/Password provider is enabled in the Firebase project. If registration fails with a provider error, report it; do not work around it.
3. If neither is possible, stop at the login screen and report which findings could not be checked.

Firebase keeps the session across launches, so log in once and avoid `pm clear` afterwards. A new account goes to onboarding (`isNewUser`); an existing one goes to Home.

## 5. Screenshots and driving the UI
- Screenshot. Do not use `exec-out screencap -p > file`: PowerShell 5.1 re-encodes the bytes and corrupts the PNG.
  `& $adb shell screencap -p /sdcard/shot.png; & $adb pull /sdcard/shot.png <scratchpad>\shot-01.png`
  Then view it with the Read tool.
- Find tap targets: `& $adb shell uiautomator dump /sdcard/ui.xml; & $adb pull /sdcard/ui.xml <scratchpad>\ui.xml`, then search for the `text=` or `content-desc=` and its `bounds`.
- Tap, type, back: `& $adb shell input tap X Y`; `& $adb shell input text "Plateau"` (spaces as `%s`); `& $adb shell input keyevent KEYCODE_BACK`
- Name screenshots after the route step, e.g. `onboarding-2-fr-font200.png`.

## 6. Font scale
- Set 200%: `& $adb shell settings put system font_scale 2.0`. Also check 1.3, the common large setting.
- Reset: `& $adb shell settings put system font_scale 1.0`

## 7. Language (French default, English in `values-en`)
- Preferred: switch in the app (Settings screen, FR/EN). `services/AppLocale.kt` saves it in SharedPreferences `settings` / `language` and recreates the screen. A saved in-app value overrides the system and per-app locale.
- Per-app locale from the shell, which only applies while no in-app value is saved:
  - set: `& $adb shell cmd locale set-app-locales com.example.hackapp --locales en-CA`
  - reset: `& $adb shell cmd locale set-app-locales com.example.hackapp`. Leaving out `--locales` sets an empty list, per the command's help. If that does not reset it, use `--locales '""'`; the single quotes pass a literal `""` to the device shell. Never pass a bare `--locales ""`: PowerShell 5.1 drops empty-string arguments.
  - check: `& $adb shell cmd locale get-app-locales com.example.hackapp`

  If it has no visible effect, use the in-app switch.

## 8. TalkBack
- TalkBack is installed on this image (`com.google.android.marvin.talkback`).
- On: `& $adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`
- Off: `& $adb shell settings delete secure enabled_accessibility_services`
- With TalkBack on, taps change meaning: one tap focuses, a double tap activates. For checking labels it is usually enough to read `content-desc` and `text` in a `uiautomator dump` while TalkBack is off. Turn it on only to confirm focus order or announcements, and turn it off again.

## 9. Permissions
- Notifications: `& $adb shell pm grant com.example.hackapp android.permission.POST_NOTIFICATIONS` (revoke with `pm revoke` to test the denied path)
- Location: the app requests COARSE, and FINE may be dropped in Phase 2.
  - Grant: `& $adb shell pm grant com.example.hackapp android.permission.ACCESS_COARSE_LOCATION`
  - Revoke: `& $adb shell pm revoke com.example.hackapp android.permission.ACCESS_COARSE_LOCATION`
  - FINE works the same way while it is still declared: `& $adb shell pm grant com.example.hackapp android.permission.ACCESS_FINE_LOCATION`
- Check: `& $adb shell dumpsys package com.example.hackapp | Select-String 'POST_NOTIFICATIONS|LOCATION'`

## 9a. Offline check (demo mode: local data only)
- Network off: `& $adb shell svc wifi disable; & $adb shell svc data disable`
- Network on (always restore): `& $adb shell svc wifi enable; & $adb shell svc data enable`
- Check: `& $adb shell dumpsys connectivity | Select-String 'NetworkAgentInfo' | Select-Object -First 3`. No lines means no active network.
- Sign in **before** going offline: Firebase Auth needs network, and an existing session survives offline. Screens that load remote images (Coil) should degrade gracefully; note any crash with `& $adb logcat -d -s AndroidRuntime:E`.

## 10. Underway alarms and notifications (`services/UnderwayNotifier.kt`)
- Behaviour: inexact `setAndAllowWhileIdle` alarms.
  - `com.example.hackapp.ARRIVAL_CHECK` fires 30 minutes after a stop starts.
  - `com.example.hackapp.LEAVE_REMINDER` fires 30 minutes before departure.
  - The notification buttons send `ARRIVED` / `SKIP`.
  - Notification channel: `underway`.
- Scheduled alarms: `& $adb shell dumpsys alarm | Select-String -Context 0,3 'com.example.hackapp'`
- Posted notifications: `& $adb shell dumpsys notification --noredact | Select-String -Context 0,6 'com.example.hackapp'`
- Open the shade to screenshot it: `& $adb shell cmd statusbar expand-notifications`; close it with `collapse`.
- **Limit:** `UnderwayReceiver` is `exported="false"` and this image has no root, so `am broadcast` from the shell is refused. To trigger the alarms:
  - Plan a trip whose stop times are close to now and wait for the alarm. Alarms are inexact, so allow several minutes.
  - Then tap the Arrived / Skip buttons in the shade.
  - Do not change the receiver's `exported` flag to test it. A non-Play AVD (`google_apis`, which allows `adb root`) needs the user's approval to download.

## Report
List the route covered, screenshots (absolute paths), settings changed and restored, anything blocked (login, alarms) and which findings changed from "Likely" to confirmed or refuted.
