# Accessibility and device release check

Run this check on every release candidate. Record the app commit, device model,
Android version and result. A failed safety, permission-recovery or speech check
blocks release.

## Automated checks

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleRelease
```

Run the connected tests on the oldest supported API (28), a current phone, the
target API, and a tablet. Repeat with small and large font, display size at its
largest setting, portrait and landscape.

## TalkBack

1. Enable TalkBack before opening Frank and turn on its developer option to show
   node information.
2. Complete onboarding using swipe navigation and double tap only.
3. Confirm headings, body text, Continue, Back, permission controls and switches
   are read once in visual order.
4. Start Frank and confirm Ready, Listening, Thinking, Speaking and error changes
   are announced once. Error announcements must interrupt lower-priority status.
5. Confirm the animated eye is skipped and every actionable control has a clear
   name, role, state and at least a 48 dp target.
6. Repeat with Select to Speak and Android's largest font and display size.

## Motion, haptics and contrast

- Disable Android animations before first launch; Reduce motion should default on.
- Turn Reduce motion on and confirm the eye, glow and state transitions remain still.
- Turn vibration cues off and confirm no state cue is emitted.
- Check the dark and light themes in sunlight and low brightness. Text, focus,
  disabled controls and errors must remain distinguishable without relying on colour.

## Physical device matrix

Run once on Android 9 or the oldest available device and once on the release target.

- Deny microphone, then grant it in Settings and retry without reinstalling.
- Deny camera and location and complete a voice-only conversation.
- Cover the camera, use low light and revoke camera access during a session.
- Interrupt recording and playback with a call, Bluetooth disconnect and audio focus loss.
- Disable Wi-Fi/mobile data during listening, thinking and speaking; confirm recovery.
- Background and restore the app during each state. Confirm recording never restarts
  after the assistant closes.
- Run a 20-turn conversation on battery, then inspect Android battery usage, thermal
  state and background restrictions. Camera capture must stop when the session closes.
- Put the phone in a pocket and repeat a location question and a lost-assistance turn.

## Release evidence

Attach the automated test report, screenshots at phone and tablet widths, TalkBack
screen recording, physical-device matrix, battery result and any incident trace to
the release record. Do not describe a physical check as passed without that evidence.
