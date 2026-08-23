package com.localhost.py.data.storage

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    fun getProjectDir(name: String): File? {
        val safeName = name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val dir = File(projectsDir, safeName)
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    fun getFileTree(dir: File): List<File> {
        return dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }

    fun readFile(file: File): String {
        return if (file.exists() && file.isFile) file.readText() else ""
    }

    fun saveFile(file: File, content: String) {
        if (file.exists() && file.isFile) {
            file.writeText(content)
        }
    }

    fun exportProjectZip(projectName: String, destinationZip: File): Boolean {
        val projectDir = getProjectDir(projectName) ?: return false
        try {
            ZipOutputStream(FileOutputStream(destinationZip)).use { zos ->
                projectDir.walkTopDown().forEach { file ->
                    if (file.name == ".packages" || file.name == "__pycache__") return@forEach
                    if (file.isDirectory && file.name == ".packages") return@forEach
                    
                    val relativePath = file.toRelativeString(projectDir)
                    if (relativePath.isEmpty()) return@forEach
                    
                    val entry = ZipEntry(if (file.isDirectory) "$relativePath/" else relativePath)
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun importProjectZip(zipFile: File, projectName: String): Boolean {
        val safeName = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val destDir = File(projectsDir, safeName)
        if (!destDir.exists()) destDir.mkdirs()

        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName.contains("..")) {
                        throw SecurityException("Zip extraction failed: path traversal attempt detected.")
                    }
                    
                    val newFile = File(destDir, entryName)
                    val canonicalDestPath = destDir.canonicalPath
                    val canonicalNewFilePath = newFile.canonicalPath
                    
                    if (!canonicalNewFilePath.startsWith(canonicalDestPath + File.separator)) {
                        throw SecurityException("Zip extraction failed: path traversal attempt detected.")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
