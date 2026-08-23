# PY LOCALHOST

PY LOCALHOST is a production-oriented native Android application built with Kotlin and Jetpack Compose. It serves as a Python project runner and mobile IDE.

## Features (Completed Phases)

### Phase 1-5: Core IDE & Runtime
- Clean Jetpack Compose UI.
- Local Storage Management (projects organized in folders).
- Multi-file Code Editor with Syntax Highlighting foundation.
- Embedded Chaquopy Runtime for On-Device Execution.
- PIP Integration for installing `requirements.txt`.

### Phase 6 & 7: Process Manager & Foreground Service
- **Real-Time Streaming Output:** Python `stdout` is redirected live via a Kotlin interface.
- **Android Foreground Service:** Long-running Python scripts (like Telegram/Discord bots or Flask servers) continue to run when the app is minimized.
- **Process Control:** Real "Stop" and "Restart" functionality cleanly terminating the background Python job.
- **Persistent Notification:** Keeps the OS from killing the Python process due to battery optimizations.

## How to Build

1. Push this code to a GitHub repository.
2. Go to the **Actions** tab of your repository.
3. Download `app-debug.apk` from the latest successful build.

## Next Phases
- **Phase 8:** Advanced Terminal Interface.
- **Phase 9:** Local Web Server URL detection.

### Phase 8 & 9: Terminal Input & Local Server Web Preview
- **Interactive Stdin:** `input()` prompts in Python can now receive data natively via the UI.
- **Local Web Server Detection:** If you launch a Flask, Django, or FastAPI server on `localhost:PORT`, you can use the built-in WebView preview browser to see your server running natively inside the IDE!

### Phase 10: Syntax Highlighting & Code Editor Upgrade
- **Custom Jetpack Compose VisualTransformation:** Developed a real-time regex-based Syntax Highlighter specifically for Python.
- **Darcula Theme:** The code editor now mimics the IntelliJ/PyCharm dark theme (Darcula).
- Colors elements dynamically: Keywords (Orange), Strings (Green), Comments (Gray), Numbers (Blue), Functions (Yellow).
