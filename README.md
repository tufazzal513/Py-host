# PyMobile IDE

PyMobile IDE is a production-oriented native Android application built with Kotlin and Jetpack Compose. It serves as a Python project runner and mobile IDE.

## Features (Completed Phases)

### Phase 1: Native Foundation
- Clean, responsive Android UI built with Jetpack Compose.
- Project structure set up for scaling (MVVM/Clean Architecture readiness).
- Automated GitHub Actions workflow for building the APK.

### Phase 2: Project & File Manager
- **Local Storage Management:** Utilizes Android's `getExternalFilesDir` to securely create the `PyMobileIDE/projects/` directory.
- **Projects Dashboard:** The Home Screen automatically scans and lists existing Python projects.
- **Project Creation:** Users can create new projects via an interactive FAB. This automatically provisions a new directory with a default `main.py` and `requirements.txt`.
- **Navigation:** Deep links the selected project into a detailed Project Dashboard that prepares configurations for Python Version, Entry Points, and dependencies.

## How to Build

### Using GitHub Actions (Automated)
This project is configured to automatically build an Android APK whenever you push to the `main` branch.
1. Push this code to a GitHub repository.
2. Go to the **Actions** tab of your repository.
3. Click on the latest workflow run.
4. Scroll down to the **Artifacts** section and download `app-debug.apk`.
5. Install the APK on your Android device.

### Local Development (Android Studio)
1. Clone this repository to your local machine.
2. Open **Android Studio**.
3. Select **Open an existing project** and navigate to this repository.
4. Let Gradle sync and resolve dependencies.
5. Click **Run** to launch the app on an emulator or physical device.

## Next Phases
- **Phase 3:** Native Code Editor (Syntax Highlighting, Multi-tab support).
- **Phase 4:** Embedded Python Runtime (Local execution of `main.py`).
