#!/bin/bash

echo "1. Refactoring package and app names..."
# Move directories
mkdir -p app/src/main/java/com/py/localhost
cp -r app/src/main/java/com/pymobileide/* app/src/main/java/com/py/localhost/
rm -rf app/src/main/java/com/pymobileide

# String replacements across Android source and React frontend
find app/ -type f -exec sed -i 's/com\.pymobileide/com.py.localhost/g' {} +
find app/ -type f -exec sed -i 's/PyMobile IDE/PY LOCALHOST/g' {} +
find src/ -type f -exec sed -i 's/PyMobile IDE/PY LOCALHOST/g' {} +
find . -maxdepth 1 -name "README.md" -exec sed -i 's/PyMobile IDE/PY LOCALHOST/g' {} +

echo "2. Writing InputProvider..."
cat << 'INNER_EOF' > app/src/main/java/com/py/localhost/pythonruntime/InputProvider.kt
package com.py.localhost.pythonruntime

import java.util.concurrent.LinkedBlockingQueue

class InputProvider {
    private val queue = LinkedBlockingQueue<String>()

    fun submitInput(text: String) {
        queue.put(text)
    }

    fun requestInput(): String {
        return queue.take()
    }
}
INNER_EOF

echo "3. Updating runner.py for stdin injection..."
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

class StdinRedirector:
    def __init__(self, input_provider, callback):
        self.input_provider = input_provider
        self.callback = callback
    def readline(self):
        res = self.input_provider.requestInput()
        self.callback.onOutput(res + "\n")
        return res + "\n"

def run_project_stream(project_dir, entry_point, callback, input_provider):
    pkg_dir = os.path.join(project_dir, '.packages')
    if os.path.exists(pkg_dir) and pkg_dir not in sys.path:
        sys.path.insert(0, pkg_dir)
        
    sys.path.insert(0, project_dir)
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    old_stdin = sys.stdin
    
    redirector = StreamRedirector(callback)
    sys.stdout = redirector
    sys.stderr = redirector
    sys.stdin = StdinRedirector(input_provider, callback)
    
    try:
        runpy.run_path(f"{project_dir}/{entry_point}", run_name="__main__")
    except Exception as e:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        sys.stdin = old_stdin
        if project_dir in sys.path:
            sys.path.remove(project_dir)
        if pkg_dir in sys.path:
            sys.path.remove(pkg_dir)
INNER_EOF

echo "4. Updating Android Kotlin classes..."
cat << 'INNER_EOF' > app/src/main/java/com/py/localhost/pythonruntime/PythonRuntimeManager.kt
package com.py.localhost.pythonruntime

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonRuntimeManager {
    suspend fun runProjectStream(projectDir: String, entryPoint: String, callback: OutputCallback, inputProvider: InputProvider) = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val runner = py.getModule("runner")
            runner.callAttr("run_project_stream", projectDir, entryPoint, callback, inputProvider)
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

cat << 'INNER_EOF' > app/src/main/java/com/py/localhost/processmanager/ProcessMonitor.kt
package com.py.localhost.processmanager

import kotlinx.coroutines.flow.MutableStateFlow
import com.py.localhost.pythonruntime.InputProvider

object ProcessMonitor {
    val processOutput = MutableStateFlow<String>("")
    val isRunning = MutableStateFlow<Boolean>(false)
    val currentProject = MutableStateFlow<String?>(null)
    var activeInputProvider: InputProvider? = null

    fun appendOutput(text: String) {
        processOutput.value += text
    }

    fun clearOutput() {
        processOutput.value = ""
    }
    
    fun sendInput(text: String) {
        activeInputProvider?.submitInput(text)
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/py/localhost/processmanager/PythonProcessService.kt
package com.py.localhost.processmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.py.localhost.pythonruntime.OutputCallback
import com.py.localhost.pythonruntime.PythonRuntimeManager
import com.py.localhost.pythonruntime.InputProvider
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
        private const val CHANNEL_ID = "PY_LOCALHOST_Process_Channel"
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
        
        val inputProvider = InputProvider()
        ProcessMonitor.activeInputProvider = inputProvider
        
        ProcessMonitor.appendOutput("Starting process for $projectName...\n")

        currentExecutionJob = scope.launch {
            runtime.runProjectStream(projectDir, "main.py", object : OutputCallback {
                override fun onOutput(text: String) {
                    ProcessMonitor.appendOutput(text)
                }
            }, inputProvider)
            
            withContext(Dispatchers.Main) {
                ProcessMonitor.appendOutput("\n[Process completed]")
                ProcessMonitor.isRunning.value = false
                ProcessMonitor.activeInputProvider = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopProcess() {
        currentExecutionJob?.cancel()
        ProcessMonitor.appendOutput("\n[Process forcibly stopped by user]")
        ProcessMonitor.isRunning.value = false
        ProcessMonitor.activeInputProvider = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(projectName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PY LOCALHOST: $projectName")
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

cat << 'INNER_EOF' > app/src/main/java/com/py/localhost/ui/viewmodels/DashboardViewModel.kt
package com.py.localhost.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.py.localhost.data.storage.StorageManager
import com.py.localhost.processmanager.ProcessMonitor
import com.py.localhost.processmanager.PythonProcessService
import com.py.localhost.pythonruntime.PythonRuntimeManager
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
    
    fun sendInput(text: String) {
        ProcessMonitor.sendInput(text)
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/py/localhost/ui/screens/ProjectDashboardScreen.kt
package com.py.localhost.ui.screens

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
import com.py.localhost.ui.viewmodels.DashboardViewModel
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
                        
                        // Scroll to bottom when output changes
                        LaunchedEffect(output) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                        
                        Text(
                            text = output,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f).verticalScroll(scrollState).fillMaxWidth()
                        )
                        
                        if (isRunning) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            var inputText by remember { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Terminal input...", fontSize = 12.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { 
                                        if (inputText.isNotEmpty()) {
                                            viewModel.sendInput(inputText)
                                            inputText = ""
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send Input")
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
INNER_EOF

echo "Done!"
