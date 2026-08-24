package com.localhost.py.processmanager

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.localhost.py.pythonruntime.InputProvider
import java.io.RandomAccessFile

object ProcessMonitor {
    val processOutput = MutableStateFlow<String>("")
    val isRunning = MutableStateFlow<Boolean>(false)
    val currentProject = MutableStateFlow<String?>(null)
    
    val cpuUsage = MutableStateFlow<String>("0%")
    val ramUsage = MutableStateFlow<String>("0 MB")
    val runtimeDuration = MutableStateFlow<String>("00:00:00")
    
    var activeInputProvider: InputProvider? = null
    
    private var monitorJob: Job? = null
    private var startTime: Long = 0

    fun appendOutput(text: String) {
        processOutput.value += text
    }

    fun clearOutput() {
        processOutput.value = "localhost@pymobile:~$ \n"
    }
    
    fun sendInput(text: String) {
        activeInputProvider?.submitInput(text)
    }

    fun startMonitoring(context: Context) {
        startTime = System.currentTimeMillis()
        isRunning.value = true
        monitorJob?.cancel()
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var lastCpuTime = 0L
            var lastSysTime = 0L
            
            while (isRunning.value) {
                // Time Duration
                val elapsed = System.currentTimeMillis() - startTime
                val s = (elapsed / 1000) % 60
                val m = (elapsed / (1000 * 60)) % 60
                val h = (elapsed / (1000 * 60 * 60))
                runtimeDuration.value = String.format("%02d:%02d:%02d", h, m, s)
                
                // RAM usage
                val memInfo = arrayOf(Process.myPid()).let { am.getProcessMemoryInfo(it) }
                if (memInfo.isNotEmpty()) {
                    val mb = memInfo[0].totalPss / 1024
                    ramUsage.value = "$mb MB"
                }

                // Simplified CPU usage
                try {
                    val reader = RandomAccessFile("/proc/stat", "r")
                    val stat = reader.readLine()
                    reader.close()
                    val toks = stat.split("\\s+".toRegex())
                    val idle = toks[4].toLong()
                    val total = toks.drop(1).take(7).sumOf { it.toLong() }
                    
                    if (lastSysTime != 0L) {
                        val sysDelta = total - lastSysTime
                        val idleDelta = idle - lastCpuTime
                        val usage = 100f * (1f - (idleDelta.toFloat() / sysDelta.toFloat()))
                        cpuUsage.value = String.format("%.1f%%", usage.coerceIn(0f, 100f))
                    }
                    lastSysTime = total
                    lastCpuTime = idle
                } catch (e: Exception) {
                    // Ignore on restricted devices
                }

                delay(1000)
            }
        }
    }

    fun stopMonitoring() {
        isRunning.value = false
        monitorJob?.cancel()
        cpuUsage.value = "0%"
        ramUsage.value = "0 MB"
    }
}
