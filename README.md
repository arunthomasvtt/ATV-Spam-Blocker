# ATV Spam Blocker

A fully offline Android call blocker that rejects spam calls by **number series** and warns you when a series in your own call history looks like spam. Inspired by [NoPhoneSpam](https://gitlab.com/bitfireAT/NoPhoneSpam) — no network permission, no tracking, no ads. Everything stays on the device.

## Features

- **Series blocking** — block an entire prefix (e.g. `74168`, `140`, or a full number). Any incoming call whose number starts with a blocked series is rejected automatically.
- **Spam suggestions** — the app reads your call log (last 30 days) and groups calls by the first 5 digits. Series with many calls, several distinct numbers and a high missed ratio are flagged as *suspected spam* with a one-tap block.
- **Block from received calls** — tap any call in the Calls tab and choose to block the exact number or a 4–8 digit series from it.
- **Live verdict while typing** — the "Add series" dialog scores the series against your call log (calls · numbers · % missed · avg duration) and marks it likely spam / suspicious / normal.
- **Blocked-call log & notifications** — every rejected call is recorded with the matched series; optional notification per block.
- **Private-number blocking** — optional toggle to reject calls with hidden numbers.
- **Built-in seed** — the widely-reported Indian telemarketing prefix `140` is preloaded.

## How blocking works

| Android version | Mechanism |
|---|---|
| Android 10+ (API 29+) | `CallScreeningService` + the *Caller ID & spam* app role (`ROLE_CALL_SCREENING`) |
| Android 8–9 (API 26–28) | Phone-state broadcast + `ITelephony.endCall()` reflection fallback |

Numbers are normalized to the local 10-digit form (`+91…` / `0…` prefixes stripped) before matching, so series work with or without country codes.

## Permissions

- `READ_CALL_LOG` — spam suggestions and the received-calls list
- `READ_CONTACTS` — to exclude numbers saved in contacts from spam suggestions
- `READ_PHONE_STATE` — call-end fallback on Android 8–9
- `POST_NOTIFICATIONS` — blocked-call notifications (Android 13+)

## Setup

1. Install the APK and open the app.
2. Grant call log (+ contacts / notifications) permissions.
3. Tap **Enable screening** and accept the system prompt to set ATV Spam Blocker as your *Caller ID & spam* app.

## Building

Requirements: JDK 17, Android SDK (platform 35, build-tools 35).

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Project

- Package: `com.atv.spamblocker`, minSdk 26, targetSdk 35
- Java + AndroidX/Material, SQLite for series and blocked-call storage
- Source layout: `app/src/main/java/com/atv/spamblocker/`
  - `ScreeningService.java` — call screening / rejection
  - `PhoneStateReceiver.java` — Android 8/9 fallback
  - `SpamAdvisor.java` — series scoring heuristics
  - `Db.java` — SQLite storage
  - `MainActivity.java` — UI (Shield / Calls / Blocked tabs)
