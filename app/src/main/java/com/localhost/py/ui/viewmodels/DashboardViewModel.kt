package com.localhost.py.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localhost.py.data.git.GitManager
import com.localhost.py.data.storage.StorageManager
import com.localhost.py.processmanager.ProcessMonitor
import com.localhost.py.processmanager.PythonProcessService
import com.localhost.py.pythonruntime.PythonRuntimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = StorageManager(application)
    private val pythonRuntime = PythonRuntimeManager()
    private val gitManager = GitManager()
    private val context = application.applicationContext

    val output: StateFlow<String> = ProcessMonitor.processOutput
    val isRunning: StateFlow<Boolean> = ProcessMonitor.isRunning
    
    val cpuUsage: StateFlow<String> = ProcessMonitor.cpuUsage
    val ramUsage: StateFlow<String> = ProcessMonitor.ramUsage
    val runtimeDuration: StateFlow<String> = ProcessMonitor.runtimeDuration

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

    fun restartProject(projectName: String) {
        val projectDir = storageManager.getProjectDir(projectName)
        if (projectDir != null) {
            val intent = Intent(context, PythonProcessService::class.java).apply {
                action = PythonProcessService.ACTION_RESTART
                putExtra(PythonProcessService.EXTRA_PROJECT_DIR, projectDir.absolutePath)
                putExtra(PythonProcessService.EXTRA_PROJECT_NAME, projectName)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopProject() {
        val intent = Intent(context, PythonProcessService::class.java).apply {
            action = PythonProcessService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun clearOutput() {
        ProcessMonitor.clearOutput()
    }

    fun sendInput(text: String) {
        ProcessMonitor.sendInput(text)
    }

    fun installDependencies(projectName: String) {
        viewModelScope.launch {
            _isInstalling.value = true
            ProcessMonitor.appendOutput("Installing dependencies. This may take a moment...\n")
            
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir != null) {
                pythonRuntime.installDependencies(projectDir.absolutePath, object : com.localhost.py.pythonruntime.OutputCallback {
                    override fun onOutput(text: String) {
                        ProcessMonitor.appendOutput(text)
                    }
                })
            } else {
                ProcessMonitor.appendOutput("Error: Project directory not found.\n")
            }
            
            _isInstalling.value = false
        }
    }

    fun commitAndPush(projectName: String, message: String, token: String) {
        viewModelScope.launch {
            _isInstalling.value = true
            ProcessMonitor.appendOutput("\n[Git]: Committing changes...\n")
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir != null) {
                val resultMsg = gitManager.commitAndPush(projectDir, message, token)
                ProcessMonitor.appendOutput("[Git]: $resultMsg\n")
            }
            _isInstalling.value = false
        }
    }

    fun exportZip(projectName: String, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val projectDir = storageManager.getProjectDir(projectName)
            if (projectDir == null) {
                withContext(Dispatchers.Main) {
                    ProcessMonitor.appendOutput("\n[Storage]: Error - Project directory not found.\n")
                }
                return@launch
            }
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { os ->
                    java.util.zip.ZipOutputStream(os).use { zos ->
                        projectDir.walkTopDown().forEach { file ->
                            val name = file.toRelativeString(projectDir)
                            if (name.isNotEmpty() && !name.contains(".packages") && !name.contains("__pycache__")) {
                                if (file.isDirectory) {
                                    zos.putNextEntry(java.util.zip.ZipEntry("$name/"))
                                    zos.closeEntry()
                                } else {
                                    zos.putNextEntry(java.util.zip.ZipEntry(name))
                                    file.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    ProcessMonitor.appendOutput("\n[Storage]: Project exported successfully to chosen file.\n")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ProcessMonitor.appendOutput("\n[Storage]: Export failed: ${e.message}\n")
                }
            }
        }
    }
}
