# Traypad

A tiny Android jotter for anything you want to capture fast

## Features

- Foreground `NoteForegroundService` that keeps a persistent notification with inline reply support
- Notes persist in `filesDir/notes.txt` as newline-delimited JSON and hydrate the RecyclerView on launch or refresh broadcasts
- Long-press any row in the timeline to edit text in place; blanking the list rewrites the file
- Espresso + ActivityScenario instrumentation tests cover notification broadcasts, JSONL parsing, long-press edits, and service reply flows

## Usage

```bash
# Build the debug APK
./gradlew assembleDebug

# Install on a connected device or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Tap **Start** in the UI to begin the foreground service, then send notes either from the inline notification action or the in-app composer
- Tap **View Log** to jump to the bottom of the list; long-press entries to edit them

## Tests

```bash
# Run instrumentation tests (uses internal storage on the target device/emulator)
./gradlew connectedDebugAndroidTest
```

The tests write directly to `notes.txt` inside the app sandbox, so run them only on a dedicated emulator or throwaway device
