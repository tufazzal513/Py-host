#!/bin/bash
mkdir -p app/src/main/python
mkdir -p app/src/main/java/com/pymobileide/pythonruntime

cat << 'INNER_EOF' > build.gradle.kts
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.chaquo.python") version "15.0.2" apply false
}
INNER_EOF

cat << 'INNER_EOF' > app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.pymobileide"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pymobileide"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended")
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application
        android:name=".PyMobileIDEApplication"
        android:allowBackup="true"
        android:label="PyMobile IDE"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.NoTitleBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/PyMobileIDEApplication.kt
package com.pymobileide

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class PyMobileIDEApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/python/runner.py
import sys
import runpy
import io
import traceback

def run_project(project_dir, entry_point):
    sys.path.insert(0, project_dir)
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    redirected_output = io.StringIO()
    sys.stdout = redirected_output
    sys.stderr = redirected_output
    
    try:
        runpy.run_path(f"{project_dir}/{entry_point}", run_name="__main__")
    except Exception as e:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        if project_dir in sys.path:
            sys.path.remove(project_dir)
    
    return redirected_output.getvalue()
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/pythonruntime/PythonRuntimeManager.kt
package com.pymobileide.pythonruntime

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonRuntimeManager {
    suspend fun runProject(projectDir: String, entryPoint: String): String = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val runner = py.getModule("runner")
            runner.callAttr("run_project", projectDir, entryPoint).toString()
        } catch (e: Exception) {
            e.message ?: "Unknown error"
        }
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/viewmodels/DashboardViewModel.kt
package com.pymobileide.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pymobileide.data.storage.StorageManager
import com.pymobileide.pythonruntime.PythonRuntimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = StorageManager(application)
    private val pythonRuntime = PythonRuntimeManager()

    private val _output = MutableStateFlow<String?>(null)
    val output: StateFlow<String?> = _output

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun runProject(projectName: String) {
        viewModelScope.launch {
            _isRunning.value = true
            _output.value = "Running...\n"
            
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir != null) {
                val result = pythonRuntime.runProject(projectDir.absolutePath, "main.py")
                _output.value = result.ifBlank { "[Process completed with no output]" }
            } else {
                _output.value = "Project directory not found."
            }
            
            _isRunning.value = false
        }
    }
    
    fun clearOutput() {
        _output.value = null
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/screens/ProjectDashboardScreen.kt
package com.pymobileide.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pymobileide.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardScreen(
    navController: NavController, 
    projectName: String,
    viewModel: DashboardViewModel = viewModel()
) {
    val output by viewModel.output.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Project Configuration", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Text("Python Version: 3.11", style = MaterialTheme.typography.bodyMedium)
                    Text("Entry Point: main.py", style = MaterialTheme.typography.bodyMedium)
                    Text("Dependencies: requirements.txt", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (isRunning) "Status: Running..." else "Status: Ready", 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.runProject(projectName) }, 
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRunning) "Running..." else "Run")
                }
                OutlinedButton(
                    onClick = { navController.navigate("editor/\$projectName") }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Editor")
                }
            }
            
            if (output != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Output Console", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { viewModel.clearOutput() }) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        val scrollState = rememberScrollState()
                        Text(
                            text = output ?: "",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.verticalScroll(scrollState).fillMaxSize()
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
INNER_EOF

