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
