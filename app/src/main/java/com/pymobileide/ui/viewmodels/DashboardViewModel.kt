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

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling

    fun runProject(projectName: String) {
        viewModelScope.launch {
            _isRunning.value = true
            _output.value = "Starting project...\n"
            
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir != null) {
                val result = pythonRuntime.runProject(projectDir.absolutePath, "main.py")
                _output.value = _output.value + result.ifBlank { "[Process completed with no output]" }
            } else {
                _output.value = "Project directory not found."
            }
            
            _isRunning.value = false
        }
    }

    fun installDependencies(projectName: String) {
        viewModelScope.launch {
            _isInstalling.value = true
            _output.value = "Installing dependencies. This may take a moment...\n"
            
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir != null) {
                val result = pythonRuntime.installDependencies(projectDir.absolutePath)
                _output.value = _output.value + result
            } else {
                _output.value = "Project directory not found."
            }
            
            _isInstalling.value = false
        }
    }
    
    fun clearOutput() {
        _output.value = null
    }
}
