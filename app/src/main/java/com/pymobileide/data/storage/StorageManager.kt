package com.pymobileide.data.storage

import android.content.Context
import java.io.File

class StorageManager(private val context: Context) {
    val rootDir: File get() = File(context.getExternalFilesDir(null), "PyMobileIDE").apply { mkdirs() }
    val projectsDir: File get() = File(rootDir, "projects").apply { mkdirs() }

    fun createProject(name: String): File? {
        val safeName = name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val dir = File(projectsDir, safeName)
        if (!dir.exists() && dir.mkdirs()) {
            val mainFile = File(dir, "main.py")
            mainFile.writeText("print('Hello from \$name')")
            
            val reqFile = File(dir, "requirements.txt")
            reqFile.writeText("# Add your Python dependencies here\n")
            
            return dir
        }
        return null
    }

    fun listProjects(): List<File> {
        return projectsDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
