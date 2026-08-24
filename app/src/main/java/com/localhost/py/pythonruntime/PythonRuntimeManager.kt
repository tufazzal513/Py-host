package com.localhost.py.pythonruntime

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

    suspend fun installDependencies(projectDir: String, callback: OutputCallback) = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val installer = py.getModule("installer")
            installer.callAttr("install_requirements", projectDir, callback)
        } catch (e: Exception) {
            callback.onOutput("\n[Runtime Error]: ${e.message}\n")
        }
    }
}
