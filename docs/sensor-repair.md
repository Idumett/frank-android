# Sensor repair — 6 September 2026

Camera startup is now independent of automatic microphone recording. CameraX capture runs on the main executor, stops with the foreground session, and restarts when the app returns from Settings. A requested image may wait briefly for the first fresh frame rather than fail immediately during startup.

Location now warms the fused provider while the assistant is in the foreground and requests a bounded current fix when sending a turn. It no longer depends solely on another app having populated lastLocation. Snapshot timestamps are created after acquisition. Missing, inaccurate, stale and denied signals remain explicit; movement expires sooner than location. Backgrounding cancels an outstanding send and stops foreground sensor collection.

Validation: 15 JVM unit tests, Android lint and debug build pass. Two Android 16 emulator instrumentation tests verify CameraX capture/restart and a mock fused-location fix reaching a valid snapshot. Mock coordinates establish the software path, not physical GPS performance. Physical-device acceptance still includes cold GPS start, approximate permission, permission changes in Settings, camera quality and network loss.

The companion server change performs semantic routing before requesting images. Install an updated Android build for the location/lifecycle fixes; a server update alone cannot change the installed app.
