package com.localhost.py.processmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localhost.py.MainActivity
import com.localhost.py.pythonruntime.OutputCallback
import com.localhost.py.pythonruntime.PythonRuntimeManager
import com.localhost.py.pythonruntime.InputProvider
import kotlinx.coroutines.*

class PythonProcessService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val runtime = PythonRuntimeManager()
    private var currentExecutionJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"
        const val EXTRA_PROJECT_DIR = "EXTRA_PROJECT_DIR"
        const val EXTRA_PROJECT_NAME = "EXTRA_PROJECT_NAME"
        private const val CHANNEL_ID = "PY_LOCALHOST_Process_Channel"
        private const val NOTIFICATION_ID = 1001
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
            ACTION_RESTART -> {
                val projectDir = intent.getStringExtra(EXTRA_PROJECT_DIR)
                val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: ProcessMonitor.currentProject.value ?: "Unknown"
                if (projectDir != null) {
                    stopProcess()
                    startProcess(projectDir, projectName)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startProcess(projectDir: String, projectName: String) {
        startForeground(NOTIFICATION_ID, buildNotification(projectName))
        
        ProcessMonitor.currentProject.value = projectName
        ProcessMonitor.startMonitoring(this)
        ProcessMonitor.clearOutput()
        
        val inputProvider = InputProvider()
        ProcessMonitor.activeInputProvider = inputProvider
        
        ProcessMonitor.appendOutput("========================================\n")
        ProcessMonitor.appendOutput("▶ Starting project: $projectName\n")
        ProcessMonitor.appendOutput("  Working Dir: $projectDir\n")
        ProcessMonitor.appendOutput("  Runtime: Embedded Python (Foreground Service)\n")
        ProcessMonitor.appendOutput("========================================\n\n")

        currentExecutionJob?.cancel()
        currentExecutionJob = scope.launch {
            runtime.runProjectStream(projectDir, "main.py", object : OutputCallback {
                override fun onOutput(text: String) {
                    ProcessMonitor.appendOutput(text)
                }
            }, inputProvider)
            
            withContext(Dispatchers.Main) {
                ProcessMonitor.appendOutput("\n\n[Process completed with status 0]")
                ProcessMonitor.stopMonitoring()
                ProcessMonitor.activeInputProvider = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopProcess() {
        currentExecutionJob?.cancel()
        ProcessMonitor.appendOutput("\n\n[■ Process stopped by user]")
        ProcessMonitor.stopMonitoring()
        ProcessMonitor.activeInputProvider = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(projectName: String): Notification {
        // PendingIntent to open app dashboard
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent to Stop process directly from notification
        val stopIntent = Intent(this, PythonProcessService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PyMobile IDE: $projectName")
            .setContentText("Python process is running in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Python Background Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Python server and script execution status"
            }
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
