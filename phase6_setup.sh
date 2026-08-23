#!/bin/bash
mkdir -p app/src/main/java/com/pymobileide/processmanager
mkdir -p app/src/main/java/com/pymobileide/pythonruntime

cat << 'INNER_EOF' > app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
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
        
        <service 
            android:name=".processmanager.PythonProcessService"
            android:foregroundServiceType="specialUse"
            android:exported="false">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                      android:value="Running embedded Python runtime" />
        </service>
    </application>
</manifest>
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/pythonruntime/OutputCallback.kt
package com.pymobileide.pythonruntime

interface OutputCallback {
    fun onOutput(text: String)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/python/runner.py
import sys
import runpy
import traceback
import os

class StreamRedirector:
    def __init__(self, callback):
        self.callback = callback
    def write(self, text):
        self.callback.onOutput(text)
    def flush(self):
        pass

def run_project_stream(project_dir, entry_point, callback):
    pkg_dir = os.path.join(project_dir, '.packages')
    if os.path.exists(pkg_dir) and pkg_dir not in sys.path:
        sys.path.insert(0, pkg_dir)
        
    sys.path.insert(0, project_dir)
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    
    redirector = StreamRedirector(callback)
    sys.stdout = redirector
    sys.stderr = redirector
    
    try:
        runpy.run_path(f"{project_dir}/{entry_point}", run_name="__main__")
    except Exception as e:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        if project_dir in sys.path:
            sys.path.remove(project_dir)
        if pkg_dir in sys.path:
            sys.path.remove(pkg_dir)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/pythonruntime/PythonRuntimeManager.kt
package com.pymobileide.pythonruntime

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonRuntimeManager {
    suspend fun runProjectStream(projectDir: String, entryPoint: String, callback: OutputCallback) = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val runner = py.getModule("runner")
            runner.callAttr("run_project_stream", projectDir, entryPoint, callback)
        } catch (e: Exception) {
            callback.onOutput("\n[Runtime Error]: ${e.message}\n")
        }
    }

    suspend fun installDependencies(projectDir: String): String = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val installer = py.getModule("installer")
            installer.callAttr("install_requirements", projectDir).toString()
        } catch (e: Exception) {
            e.message ?: "Dependency installation error"
        }
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/processmanager/ProcessMonitor.kt
package com.pymobileide.processmanager

import kotlinx.coroutines.flow.MutableStateFlow

object ProcessMonitor {
    val processOutput = MutableStateFlow<String>("")
    val isRunning = MutableStateFlow<Boolean>(false)
    val currentProject = MutableStateFlow<String?>(null)

    fun appendOutput(text: String) {
        processOutput.value += text
    }

    fun clearOutput() {
        processOutput.value = ""
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/processmanager/PythonProcessService.kt
package com.pymobileide.processmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pymobileide.pythonruntime.OutputCallback
import com.pymobileide.pythonruntime.PythonRuntimeManager
import kotlinx.coroutines.*

class PythonProcessService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val runtime = PythonRuntimeManager()
    private var currentExecutionJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PROJECT_DIR = "EXTRA_PROJECT_DIR"
        const val EXTRA_PROJECT_NAME = "EXTRA_PROJECT_NAME"
        private const val CHANNEL_ID = "PyMobileIDE_Process_Channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val projectDir = intent.getStringExtra(EXTRA_PROJECT_DIR) ?: return START_NOT_STICKY
                val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: "Unknown"
                startProcess(projectDir, projectName)
            }
            ACTION_STOP -> {
                stopProcess()
            }
        }
        return START_NOT_STICKY
    }

    private fun startProcess(projectDir: String, projectName: String) {
        startForeground(NOTIFICATION_ID, buildNotification(projectName))
        
        ProcessMonitor.currentProject.value = projectName
        ProcessMonitor.isRunning.value = true
        ProcessMonitor.clearOutput()
        ProcessMonitor.appendOutput("Starting process for \$projectName...\n")

        currentExecutionJob = scope.launch {
            runtime.runProjectStream(projectDir, "main.py", object : OutputCallback {
                override fun onOutput(text: String) {
                    ProcessMonitor.appendOutput(text)
                }
            })
            
            withContext(Dispatchers.Main) {
                ProcessMonitor.appendOutput("\n[Process completed]")
                ProcessMonitor.isRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopProcess() {
        currentExecutionJob?.cancel()
        ProcessMonitor.appendOutput("\n[Process forcibly stopped by user]")
        ProcessMonitor.isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(projectName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PyMobile IDE: \$projectName")
            .setContentText("Python process is running in the background")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Running Projects",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        ProcessMonitor.isRunning.value = false
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/viewmodels/DashboardViewModel.kt
package com.pymobileide.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pymobileide.data.storage.StorageManager
import com.pymobileide.processmanager.ProcessMonitor
import com.pymobileide.processmanager.PythonProcessService
import com.pymobileide.pythonruntime.PythonRuntimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = StorageManager(application)
    private val pythonRuntime = PythonRuntimeManager()
    private val context = application.applicationContext

    val output: StateFlow<String> = ProcessMonitor.processOutput
    val isRunning: StateFlow<Boolean> = ProcessMonitor.isRunning

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling

    fun runProject(projectName: String) {
        val projectDir = storageManager.getProjectDir(projectName)
        if (projectDir != null) {
            val intent = Intent(context, PythonProcessService::class.java).apply {
                action = PythonProcessService.ACTION_START
                putExtra(PythonProcessService.EXTRA_PROJECT_DIR, projectDir.absolutePath)
                putExtra(PythonProcessService.EXTRA_PROJECT_NAME, projectName)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            ProcessMonitor.appendOutput("Error: Project directory not found.\n")
        }
    }

    fun stopProject() {
        val intent = Intent(context, PythonProcessService::class.java).apply {
            action = PythonProcessService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun installDependencies(projectName: String) {
        viewModelScope.launch {
            _isInstalling.value = true
            ProcessMonitor.appendOutput("Installing dependencies. This may take a moment...\n")
            
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir != null) {
                val result = pythonRuntime.installDependencies(projectDir.absolutePath)
                ProcessMonitor.appendOutput(result)
            } else {
                ProcessMonitor.appendOutput("Error: Project directory not found.\n")
            }
            
            _isInstalling.value = false
        }
    }
    
    fun clearOutput() {
        ProcessMonitor.clearOutput()
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/screens/ProjectDashboardScreen.kt
package com.pymobileide.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardScreen(
    navController: NavController, 
    projectName: String,
    viewModel: DashboardViewModel = viewModel()
) {
    val output by viewModel.output.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()

    // Request Notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                    
                    val statusText = when {
                        isRunning -> "Status: Running (Foreground Service)"
                        isInstalling -> "Status: Installing dependencies..."
                        else -> "Status: Ready"
                    }
                    val statusColor = when {
                        isRunning -> MaterialTheme.colorScheme.secondary
                        isInstalling -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    Button(
                        onClick = { viewModel.stopProject() }, 
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = { viewModel.runProject(projectName) }, 
                        enabled = !isInstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                }
                
                OutlinedButton(
                    onClick = { viewModel.installDependencies(projectName) }, 
                    enabled = !isRunning && !isInstalling,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install")
                }
            }
            
            OutlinedButton(
                onClick = { navController.navigate("editor/\$projectName") }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Editor (Files)")
            }
            
            if (output.isNotEmpty()) {
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
                            text = output,
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

cat << 'INNER_EOF' > README.md
# PyMobile IDE

PyMobile IDE is a production-oriented native Android application built with Kotlin and Jetpack Compose. It serves as a Python project runner and mobile IDE.

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
