# PyMobile IDE

A production-oriented, native Android Python Mobile IDE, Project Runner, and Localhost Web Server built with Kotlin, Jetpack Compose, Chaquopy, and JGit.

---

## 🌟 Overview & Architecture

PyMobile IDE turns your Android device into a complete Python development environment. It combines concepts from **Android Studio, VS Code, and Termux** into a user-friendly mobile interface that does not require prior terminal knowledge, while providing an advanced interactive CLI when needed.

- **Architecture:** Clean MVVM Architecture with Kotlin Coroutines, StateFlow, and Android Foreground Service.
- **Embedded Runtime:** Chaquopy Embedded Python 3.11 with isolated project-level dependencies (`.packages`).
- **Security:** Anti-Path Traversal ZIP extraction, Secret masking, Android Storage Access Framework (SAF) integration.
- **CI/CD:** Automated GitHub Actions APK build workflow (`.github/workflows/android-build.yml`).

---

## 🚀 Key Modules & Capabilities

### 1. Embedded Python Runtime & Dependency Manager
- Runs scripts (`main.py`) directly on the device using phone CPU and RAM.
- Dynamic pip installation from `requirements.txt` into project-isolated directories.
- Real-time output streaming (`stdout`, `stderr`) and interactive `stdin` input support.

### 2. Localhost Web Server & Auto Detection (Phase 9)
- Run web servers (Flask, FastAPI, Django, Tornado, etc.) on `0.0.0.0` or `127.0.0.1`.
- **Automatic Server URL Detection:** Automatically identifies running ports (e.g. `5000`, `8000`) and displays:
  - Local URL: `http://127.0.0.1:<port>`
  - LAN URL: `http://192.168.x.x:<port>` (for testing from PC/browser on the same Wi-Fi).
- Built-in In-App WebView preview and one-tap external browser launching.

### 3. Foreground Service & Background Execution (Phase 7)
- Persistent foreground notification with real-time status.
- Direct **Stop** action from the notification shade.
- Continuous background execution for Telegram bots, Discord bots, automation scripts, and web servers.

### 4. Advanced Dual-Mode Terminal (Phase 8)
- **Beginner Mode:** One-tap action chips (▶ Run, ■ Stop, 🔄 Restart, 📦 Install Pip, 🧹 Clear).
- **Advanced CLI Mode:** Command line execution with prompt, history, and interactive `stdin` streaming.
- Formatted color-coded log lines for errors, Git events, and runtime notifications.

### 5. Mobile Code Editor (Phase 3)
- Multi-file tab switcher with Darcula dark theme.
- High-performance regex-based Python syntax highlighting.
- Quick mobile coding symbol bar (`Tab`, `:`, `( )`, `[ ]`, `{ }`, `" "`, `' '`, `=`, `+`, `-`, `*`, `/`, `#`).
- Search & Replace bar with `Find Next` and `Replace All`.
- Auto-indentation on new lines and undo/redo history.

### 6. Git & GitHub Integration (Phase 10)
- Clone public repositories directly from GitHub URLs.
- View repository status, modified files, and branches.
- Commit changes locally or push to remote GitHub repositories using Personal Access Tokens (PAT).

### 7. Storage, Backup & ZIP Export (Phase 11)
- Import and extract `.zip` files safely with traversal protection.
- Export entire projects to `.zip` via Android Storage Access Framework (SAF).

---

## 🛠️ Building the Project

### Local Build (Android Studio)
1. Open this repository in Android Studio (Giraffe / Hedgehog / Iguana / Ladybug).
2. Sync Gradle files (`settings.gradle.kts` and `app/build.gradle.kts`).
3. Build and run on an Android Device (API 26+ / Android 8.0+ recommended).

### Automated Build (GitHub Actions)
1. Push this repository to GitHub.
2. Navigate to the **Actions** tab in your repository.
3. The `Build Android APK` workflow will run automatically and generate a downloadable debug APK artifact.

---

## 🔒 Security & Limitations
- **Background Restrictions:** Some OEM skins (MIUI, OneUI, EMUI) may kill background processes. Set Battery Usage to *Unrestricted* in App Info.
- **Native Wheels:** Pure Python packages and pre-compiled wheels for Android ARM64/x86_64 are supported. C-extensions requiring missing system header libraries may fail to build on device.
- **Privacy:** No telemetry or mandatory cloud servers. All Python execution is entirely local to your device.
