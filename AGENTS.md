# Testing Plan (Draft)

1. Add a minimal `androidTest` setup for UI flows (ActivityScenario + Espresso).
2. Write a test that broadcasts `ACTION_UPDATED` and verifies list refresh.
3. Write a test that appends a JSONL note and verifies it renders.
4. Write a test that long-press deletes and rewrites JSONL file.
5. Add a service test that triggers `ACTION_REPLY` and validates notification update.

Notes: Instrumentation tests manipulate `notes.txt` in app internal storage; run on an emulator or test device to avoid wiping personal notes on a real device.
