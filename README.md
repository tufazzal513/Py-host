# PyMobile IDE

PyMobile IDE is a production-oriented native Android application built with Kotlin and Jetpack Compose. It serves as a Python project runner and mobile IDE.

## Features (Phase 1)
- Clean, responsive Android UI built with Jetpack Compose.
- Project structure set up for scaling (MVVM/Clean Architecture readiness).
- Automated GitHub Actions workflow for building the APK.

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

## Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle (Kotlin DSL)
- **CI/CD**: GitHub Actions

## Next Phases
Future development phases will implement the embedded Python runtime, native code editor, Git integration, and terminal interfaces as outlined in the project specification.
