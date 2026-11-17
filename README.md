# ATV Spam Blocker

An Android call blocker that rejects spam calls by **number series**, flags suspicious callers from your call history, and syncs a community spam database — tuned for Indian spam (TRAI number series, telemarketing pools). Inspired by [NoPhoneSpam](https://gitlab.com/bitfireAT/NoPhoneSpam) — no tracking, no ads. Everything stays on the device except the optional spam-list download.

## Features

- **Series blocking** — block an entire prefix (e.g. `74168`, `140`, or a full number). Any incoming call whose number starts with a blocked series is rejected automatically.
- **Spam suggestions** — the app reads your call log (last 30 days) and groups calls by the first 5 digits. Series with many calls, several distinct numbers and a high missed ratio are flagged as *suspected spam* with a one-tap block.
- **Block from received calls** — tap any call in the Calls tab and choose to block the exact number or a 4–8 digit series from it. Contact names are shown as the primary line.
- **Edit series** — tap a series in the Shield tab to change its prefix or note; long-press to remove.
- **Live verdict while typing** — the "Add series" dialog scores the series against your call log (calls · numbers · % missed · avg duration) and marks it likely spam / suspicious / normal.
- **Spam database sync** — *Update spam database* in the ⋮ menu downloads a community blocklist (default: 44k+ AI/robocaller numbers, one per line) and merges it into your series. The list URL is configurable (`spam_db_url` pref) so any Indian or regional list can be plugged in.
- **Smart screening** — while a call rings, the number's 5-digit prefix is scored against recent call history. Score ≥ 2 → "Suspicious call" notification; score ≥ 3 + *auto-block suspect* pref → rejected (contacts are never heuristically blocked).
- **India-focused rules**
  - `140` series (TRAI promotional/telemarketing route) is preloaded as a blocked series.
  - `1600` / `1601` series (TRAI transactional/OTP route) are trusted — never auto-blocked, so bank OTPs always ring.
  - Optional *Block foreign (non-+91) numbers* toggle for international scam routes (+92, +44, …).
- **Light / dark theme** — *Light theme* toggle in the ⋮ menu.
- **Blocked-call log & notifications** — every rejected call is recorded with the matched series; optional notification per block.
- **Private-number blocking** — optional toggle to reject calls with hidden numbers.

## How blocking works

| Android version | Mechanism |
|---|---|
| Android 10+ (API 29+) | `CallScreeningService` + the *Caller ID & spam* app role (`ROLE_CALL_SCREENING`) |
| Android 8–9 (API 26–28) | Phone-state broadcast + `ITelephony.endCall()` reflection fallback |

Numbers are normalized to the local 10-digit form (`+91…` / `0…` prefixes stripped) before matching, so series work with or without country codes. Series matching runs as a SQL prefix query, so screening stays fast even with a large synced cloud list.

## Permissions

- `READ_CALL_LOG` — spam suggestions, live screening scores and the received-calls list
- `READ_CONTACTS` — to exclude numbers saved in contacts from spam suggestions and heuristic blocking
- `READ_PHONE_STATE` — call-end fallback on Android 8–9
- `POST_NOTIFICATIONS` — blocked/suspicious-call notifications (Android 13+)
- `INTERNET` — only for the manual *Update spam database* action

## Setup

1. Install the APK and open the app.
2. Grant call log (+ contacts / notifications) permissions.
3. Tap **Enable screening** and accept the system prompt to set ATV Spam Blocker as your *Caller ID & spam* app.
4. Optional: ⋮ → **Update spam database** to download the community blocklist, and enable **Block foreign (non-+91) numbers** if you never expect international calls.

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
  - `ScreeningService.java` — call screening / rejection (series, cloud list, India series rules, heuristics)
  - `PhoneStateReceiver.java` — Android 8/9 fallback
  - `SpamAdvisor.java` — series scoring heuristics + live verdict for ringing calls
  - `SpamDbSync.java` — community spam-list download/merge
  - `Db.java` — SQLite storage
  - `MainActivity.java` — UI (Shield / Calls / Blocked tabs)
