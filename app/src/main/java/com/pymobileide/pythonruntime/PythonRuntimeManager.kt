package com.pymobileide.pythonruntime

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonRuntimeManager {
    suspend fun runProject(projectDir: String, entryPoint: String): String = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val runner = py.getModule("runner")
            runner.callAttr("run_project", projectDir, entryPoint).toString()
        } catch (e: Exception) {
            e.message ?: "Unknown error"
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
