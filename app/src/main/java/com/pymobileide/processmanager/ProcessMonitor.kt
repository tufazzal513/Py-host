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
