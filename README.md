
# Frank Android Assistant

Frank is a native Android AI assistant that captures voice and vision, streams them to the Frank server, and speaks the response back to the user.

This project serves as the Android frontend for the Frank ecosystem.

---

# Features

- Native Kotlin + Jetpack Compose
- Assistant overlay
- CameraX image capture
- Voice recording
- Adaptive silence detection
- WebSocket streaming
- Multimodal (image + audio)
- Server-side AI reasoning
- Text-to-Speech playback
- Animated AI interface
- State-based haptic feedback
- Accessibility-focused design

---

# User Flow

```
Open Frank

↓

Camera begins capturing frames

↓

Frank waits for speech

↓

User speaks

↓

Adaptive silence detector stops recording

↓

Latest image + audio sent to server

↓

Server analyses request

↓

Response returned

↓

Frank speaks response

↓

Returns to Ready state
```

---

# Assistant States

| State | Behaviour |
|--------|-----------|
| Ready | Idle breathing animation |
| Listening | Double vibration, brighter glow |
| Thinking | Purple processing animation |
| Speaking | Pulsing AI core while speaking |
| Error | Red animation with warning vibration |

---

# Accessibility

Frank is designed with accessibility in mind.

## Visual

- High contrast interface
- Large animated AI core
- Minimal UI
- Large touch targets

## Haptics

Each assistant state provides a unique vibration cue.

| State | Feedback |
|--------|----------|
| Ready | Light confirmation |
| Listening | Double pulse |
| Thinking | Short pulse |
| Speaking | Strong pulse followed by soft pulse |
| Error | Triple warning pulse |

This allows blind or low-vision users to understand what Frank is doing without looking at the screen.

---

# Architecture

```
Android App
        │
        ▼
FrankConversationHost
        │
        ├── CameraX
        ├── Audio Recorder
        ├── Silence Detector
        ├── WebSocket Client
        ├── Audio Player
        └── Haptics
                │
                ▼
          Frank Server
                │
                ├── Speech-to-Text
                ├── Vision
                ├── Memory
                ├── Gemini
                └── Text-to-Speech
```

---

# Tech Stack

- Kotlin
- Jetpack Compose
- CameraX
- OkHttp WebSockets
- Retrofit
- Kotlin Coroutines

Server

- Node.js
- TypeScript
- Gemini
- Google Cloud Vision
- Google Cloud Speech
- Google Cloud Text-to-Speech

---

# Permissions

```
CAMERA
RECORD_AUDIO
INTERNET
VIBRATE
```

---

# Future Improvements

- Wake word ("Frank")
- Continuous conversation
- On-device speech recognition
- On-device vision models
- Streaming TTS
- Streaming camera frames
- Face recognition
- Object tracking
- Navigation assistance
- Offline mode
- Cross-platform iOS app

---

# Building

```
./gradlew assembleDebug
```

Windows

```
.\gradlew.bat assembleDebug
```

APK output

```
app/build/outputs/apk/debug/app-debug.apk
```

---

# Project Status

Current version includes:

- Voice interaction
- Vision understanding
- Animated assistant UI
- Accessibility haptics
- Camera integration
- Adaptive silence detection
- WebSocket communication
- Spoken AI responses

The Android proof of concept is feature-complete and ready for demonstration.

## Copyright

This repository is published to showcase the project and its implementation.

All source code remains the intellectual property of Hilary Asogwa. No permission is granted to copy, modify, redistribute, or use this software without prior written consent.

© 2026 Hilary Asogwa. All Rights Reserved.
