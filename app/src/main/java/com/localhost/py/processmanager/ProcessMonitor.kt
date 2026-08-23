package com.localhost.py.processmanager

import kotlinx.coroutines.flow.MutableStateFlow
import com.localhost.py.pythonruntime.InputProvider

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
