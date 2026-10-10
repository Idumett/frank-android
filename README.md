# Frank Android

Frank is a native Android AI accessibility assistant for blind and low-vision
users. It listens to a question, gathers only the context or camera evidence that
the request needs, sends a typed turn to the Frank server, then presents and speaks
the response.

<img width="1774" height="887" alt="Frank Android assistant" src="https://github.com/user-attachments/assets/a88698a7-8474-4bcf-ad76-790b2aa79cf4" />

## Current position

Frank V1 is connected to the live staging server and distributed through Google
Play Internal Testing. CI assigns each `main` build its own version name and an
increasing version code. The app supports Android 9 through Android 16, API 28-36.
Access is currently limited to the configured tester email list while the Play
developer account completes Google's testing and production-access requirements.

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

## Project map

```text
app/src/main/java/com/idumet/frank/
  MainActivity.kt                 launcher and onboarding entry point
  AssistActivity.kt               assistant activity entry point
  FrankConversationHost.kt        lifecycle-aware UI/controller composition
  FrankAssistantScreen.kt         main Compose experience and state presentation
  assistant/FrankAssistantController.kt
                                   turn coordination and client state
  network/FrankWebSocketClient.kt WebSocket turns, reconnect and evidence exchange
  network/DeviceStreamModels.kt   typed transport envelopes
  context/                        Context Snapshot models, location and movement
  camera/FrankCamera.kt           CameraX capture, focus and prepared evidence
  productdata/                    Kotlin mirrors of typed server actions/data
  service/                        recording, silence detection, playback and voice service
  permissions/                    microphone, camera and location permissions
  ui/theme/                       colour, typography and Compose theme
app/src/test/                     local JVM tests
app/src/androidTest/              device and accessibility-oriented tests
docs/                             release, accessibility and Play operations
fastlane/                         Play listing metadata and upload lane
.github/workflows/                signed internal-release automation
```

Keep wire and product-data models aligned with the server's versioned Zod
contracts. Network callbacks should flow through `FrankAssistantController`; avoid
letting individual screens create their own sockets, recorders or camera sessions.
Any lifecycle change should preserve cancellation, stale-request rejection and the
Ready → Listening → Thinking → Speaking state model.

## Permissions

Frank requests microphone access for voice interaction. Camera and foreground
location are optional and requested through onboarding when their related features
are enabled. Denying either preserves voice-only operation. The V1 app does not
request background location.

## Local development

### Fresh checkout

Use Android Studio with JDK 17 and the Android 36 SDK. Let the project use its
checked-in Gradle wrapper; a separate Gradle installation is unnecessary. Create
an ignored `local.properties` file containing your SDK path and private test
configuration:

```properties
sdk.dir=/absolute/path/to/Android/sdk
frank.server.url=https://your-frank-server.example/
frank.access.token=your-private-product-token
```

The URL must use `https://`; the client derives the secure `wss://` device-stream
URL itself. Keep the trailing slash for Retrofit. Never commit `local.properties`,
tokens, keystores or service-account JSON.

Build and run the `app` configuration in Android Studio, or use:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For a quick server check, open the configured `/health` URL first. A device stuck
on “Waiting for Frank” usually means the bearer token, WebSocket URL, network or
server health is wrong; inspect Logcat for `FrankWebSocketClient` before changing
audio thresholds.

### Test guidance

Run local JVM tests for every code change:

```sh
./gradlew testDebugUnitTest
```

Run lint and assemble the actual APK before merging:

```sh
./gradlew lintDebug assembleDebug
```

Camera, microphone, location, audio focus, reconnection and TalkBack behavior need
a physical phone. Exercise permission denial, screen lock, app background/restore,
network loss, low speech volume, stale camera evidence and stop during playback.
The full manual sequence is in
[the accessibility release checklist](docs/accessibility-release-checklist.md).

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

Every commit pushed to `main` starts `Publish internal Android release`. The job
builds a signed Android App Bundle, assigns an increasing version code, and uploads
the bundle plus listing text to the Play Internal Testing track. Runs are serialized
so two commits cannot upload concurrently. A documentation-only commit still makes
a Play build, so use branches while drafting.

The workflow requires these GitHub secrets:

```text
FRANK_UPLOAD_KEYSTORE_BASE64
FRANK_UPLOAD_STORE_PASSWORD
FRANK_UPLOAD_KEY_ALIAS
FRANK_UPLOAD_KEY_PASSWORD
PLAY_SERVICE_ACCOUNT_JSON
FRANK_SERVER_URL
FRANK_ACCESS_TOKEN
```

Play App Signing holds the production signing key; Frank retains only the
replaceable upload key. The workflow can also be started manually with an explicit
version code/name or from an `android-v*` tag. Do not reuse a Play version code.

### Safe maintenance checklist

1. Work on a branch and run unit tests, lint and `assembleDebug`.
2. Test voice, cancellation, reconnection and any affected sensor on a real phone.
3. Check server contract changes against `network`, `context` and `productdata`.
4. Merge to `main` and watch the GitHub Actions release through the Play upload.
5. Confirm the new version appears in Play Internal Testing before sharing it.
6. If a release fails, read the failed step; do not rotate or paste secrets into logs.

The app is currently deployed to Google Play Internal Testing, while its backend
runs on the Frank staging VM in Google Cloud. Publishing the Android app does not
deploy the server, and deploying the server does not rebuild Android; each
repository follows its own `main` branch automation.

## Copyright

This repository is published to showcase the project and its implementation.
All source code remains the intellectual property of **Hilary Asogwa**. No
permission is granted to copy, modify, redistribute or use this software without
prior written consent.

**© 2026 Hilary Asogwa. All Rights Reserved.**
