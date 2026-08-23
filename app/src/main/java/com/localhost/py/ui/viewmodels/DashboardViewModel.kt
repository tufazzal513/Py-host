package com.localhost.py.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localhost.py.data.storage.StorageManager
import com.localhost.py.processmanager.ProcessMonitor
import com.localhost.py.processmanager.PythonProcessService
import com.localhost.py.pythonruntime.PythonRuntimeManager
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
