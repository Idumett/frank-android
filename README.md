# Frank Android

Frank is a native Android AI accessibility assistant for blind and low-vision
users. It listens to a question, gathers only the context or camera evidence that
the request needs, sends a typed turn to the Frank server, then presents and speaks
the response.

<img width="1774" height="887" alt="Frank Android assistant" src="https://github.com/user-attachments/assets/a88698a7-8474-4bcf-ad76-790b2aa79cf4" />

## Current position

Frank V1 is connected to the live staging server and distributed through Google
Play Internal Testing. The current signed release is version `1.0.0` (version code
`1`) and supports Android 9 through Android 16, API 28-36. Access is currently
limited to the configured tester email list while the new Play developer account
completes Google's closed-testing and production-access requirements.

The app currently provides:

- voice interaction with adaptive silence detection and spoken replies;
- deliberate CameraX evidence capture for visual requests;
- follow-up conversation, memory and repeat-response flows;
- a foreground Context Engine covering device time, permissions, app state,
  location, movement and evidence status;
- “Where am I?”, “What’s nearby?” and conservative “I’m lost” assistance using
  user-confirmed places and remembered hazards;
- reconnect, cancellation, stale-response and lifecycle recovery;
- accessible state announcements, labelled controls, large touch targets, reduced
  motion and configurable haptics; and
- guided onboarding and release configuration outside the main assistant flow.

Frank is not a navigation app. It does not provide turn-by-turn directions, decide
when a road is safe to cross or replace a mobility aid. Location is foreground-only,
raw route history is not stored, and uncertain or stale context must be described
as uncertain rather than turned into false precision.

The remaining work is real-device testing across the supported Android range,
TalkBack and wording review with blind and low-vision users, broader closed testing,
production account identity and recovery, Play review, and offline support where
it improves reliability.

## Turn flow

```text
Open Frank
    |
    v
Ready -> Listening -> Thinking -> Speaking -> Ready
              |           |
              |           `-- relevant Context Snapshot
              `-- camera evidence only when the intent requires it
                              |
                              v
                    authenticated WebSocket
                              |
                              v
                         Frank server
```

Each interaction receives a unique request ID across recording, evidence,
transport, reasoning and playback. A reply from an older or cancelled request is
not allowed to complete into a newer turn. The client reconnects with controlled
backoff and stops delayed recording, playback and callbacks when the session closes.

Camera frames carry a frame ID, capture time, age and quality. The server can ask
the user to reposition the camera or capture again. Time questions, memory requests
and general conversation do not upload an image simply because the camera exists.

## Accessibility

Ready, Listening, Thinking, Speaking and Error reflect the actual assistant state
and are announced accessibly. The main controls expose clear labels and roles,
maintain a minimum 48 dp target and follow visual reading order. Decorative motion
can be disabled, and haptic cues can be turned off independently.

Camera or location denial must not break voice-only use. Recorder, audio-player,
camera and network failures surface recoverable messages instead of leaving the UI
in a false state. The physical release checklist covers TalkBack, large text,
screen sizes, permission changes, phone-in-pocket use, audio interruptions, network
loss, battery behavior and lifecycle restoration.

See [the accessibility release checklist](docs/accessibility-release-checklist.md).

## Technology

- Kotlin and Jetpack Compose
- CameraX
- Android location services
- OkHttp WebSockets and Retrofit
- Kotlin coroutines and lifecycle-aware state
- Android media recording and playback

The server provides Google Cloud speech and vision, Gemini reasoning, conversation,
memory, Context Snapshot policy and text-to-speech.

## Permissions

Frank requests microphone access for voice interaction. Camera and foreground
location are optional and requested through onboarding when their related features
are enabled. Denying either preserves voice-only operation. The V1 app does not
request background location.

## Building

Debug builds can use Gradle properties or ignored `local.properties` values for the
server URL and product token. Never commit a token.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

A signed Play bundle requires the upload-key environment variables plus increasing
version values:

```sh
./gradlew bundleRelease \
  -PfrankVersionCode=2 \
  -PfrankVersionName=1.0.1 \
  -PfrankServerUrl=https://35.242.143.73.sslip.io/ \
  -PfrankAccessToken="$FRANK_ACCESS_TOKEN"
```

Release secrets belong in the protected CI environment or local secret storage.
See [release operations](docs/release-operations.md) and
[Play Console values](docs/play-console-values.md).

## Distribution

The current Play release is active on the Internal Testing track. Testers must be
signed into an allow-listed Google account and opt in using the private testing
link. Google currently requires this developer account to run a closed test with at
least 12 opted-in testers for 14 continuous days before production access can be
requested. Until that gate is complete, there is no unrestricted Play Store link.

The release workflow builds a signed Android App Bundle and publishes versioned
release notes after its protected GitHub environment is configured. Play App
Signing holds the production signing key; Frank retains a replaceable upload key.

## Copyright

This repository is published to showcase the project and its implementation.
All source code remains the intellectual property of **Hilary Asogwa**. No
permission is granted to copy, modify, redistribute or use this software without
prior written consent.

**© 2026 Hilary Asogwa. All Rights Reserved.**
