# PY LOCALHOST

A production-oriented native Android application built with Kotlin, Jetpack Compose, and Chaquopy. It acts as an embedded Python Runtime environment, Localhost Server, and Mobile IDE.

## Features

### 1. Embedded Python Runtime
- Built on top of Chaquopy (Python 3.11).
- Runs Python scripts in an isolated process foreground service.
- Dynamic dependency installation (`pip install`) via `requirements.txt`.

### 2. Localhost Server & Built-in WebView
- Run web servers (Flask, FastAPI, Django, Tornado, etc.) on `localhost:<port>`.
- Built-in WebView preview browser allows instantaneous testing of your running localhost web apps.

### 3. Native IDE & Code Editor
- Real-time Python Syntax Highlighting (Darcula Theme).
- Line numbers, monospace typography, and multi-file project explorer.
- Interactive Terminal Console with Stdin (`input()`) support.

### 4. Git & GitHub Integration (Phase 11)
- Clone public/private repositories directly from GitHub.
- Local commits and remote pushes via GitHub Personal Access Tokens (PAT).

### Phase 12: File Explorer Upgrade
- **Nested Project Tree:** Implemented a full recursive file manager within the Editor Drawer.
- **Create & Delete:** Easily add folders (e.g. `templates/`, `static/`), Python scripts, HTML files directly in the app. Long-press/Click file icons to delete.

### Phase 13: Log/Output Export
- **Share Execution Logs:** You can now instantly share or export your Python execution logs / server output via the Share icon in the terminal console.
