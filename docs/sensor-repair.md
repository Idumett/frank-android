# Sensor repair — 6 September 2026

Camera startup is now independent of automatic microphone recording. CameraX capture runs on the main executor, stops with the foreground session, and restarts when the app returns from Settings. A requested image may wait briefly for the first fresh frame rather than fail immediately during startup.

Location now warms the fused provider while the assistant is in the foreground and requests a bounded current fix when sending a turn. It no longer depends solely on another app having populated lastLocation. Snapshot timestamps are created after acquisition. Missing, inaccurate, stale and denied signals remain explicit; movement expires sooner than location. Backgrounding cancels an outstanding send and stops foreground sensor collection.

Validation: 15 JVM unit tests, Android lint and debug build pass. Two Android 16 emulator instrumentation tests verify CameraX capture/restart and a mock fused-location fix reaching a valid snapshot. Mock coordinates establish the software path, not physical GPS performance. Physical-device acceptance still includes cold GPS start, approximate permission, permission changes in Settings, camera quality and network loss.

The companion server change performs semantic routing before requesting images. Install an updated Android build for the location/lifecycle fixes; a server update alone cannot change the installed app.

## Silent recording and retry repair (6 September 2026)

Six of eight device-stream requests in the reported emulator session failed with
`No speech recognized`; the other two completed STT, reasoning, vision and TTS.
The failed WAV sizes matched the eight-second no-speech timeout. The Android
client was uploading that timeout recording, including automatically opened
follow-up recordings after Frank answered.

Silent automatic recordings are now cancelled locally and return to Ready.
The normal assistant screen shows the specific status and a Speak to Frank
button, so listening can be restarted without closing the assistant. Empty STT
results also explain the microphone-input issue instead of a generic server error.
Speech during initial calibration no longer raises the background-noise threshold;
quiet-noise adaptation persists after calibration, and short answers can trigger
speech detection. Timing uses a monotonic clock. Recorder startup/read errors
release resources instead of silently spinning or leaking the microphone.

Regression coverage includes immediate speech, short answers, noise, pauses,
continuous speech, silence, reset/restart and ongoing ambient adaptation.
`FrankAudioRecorderTest` verifies two real AudioRecord capture cycles and the
PCM WAV header. `FrankSilentRecoveryTest` is an opt-in device/server test: set
`android.testInstrumentationRunnerArguments.runLiveRecovery=true` and supply a
working test server URL/token through the usual Gradle properties. It masks the
microphone using app-ops, verifies two silent listening cycles return to Ready
without Thinking/Error, and checks the retry button works. Run it only on a test
device; it restores RECORD_AUDIO to allow afterwards.

For emulator testing, Android permission alone does not enable the host microphone.
Enable **Extended controls → Microphone → Virtual microphone uses host audio input**
(or `adb emu avd hostmicon`), and allow microphone access for the emulator/Android
Studio in macOS settings if prompted. See
https://developer.android.com/studio/run/emulator-extended-controls.
The emulator console confirmed host input was enabled during this investigation;
its previous state was not observable. A live synthetic WAV request successfully
completed transcription, a factual answer and returned audio. Real host-microphone
input still needs a spoken retest. Recording-end diagnostics now include peak RMS
and the detection threshold (no audio content), to distinguish missing/quiet input
from recognition or transport errors on subsequent tests.
