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

    fun createProject(name: String, template: String = "Basic Python"): File? {
        val safeName = name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val dir = File(projectsDir, safeName)
        if (!dir.exists() && dir.mkdirs()) {
            when (template) {
                "Flask" -> {
                    File(dir, "app.py").writeText("""
                        from flask import Flask
                        app = Flask(__name__)
                        
                        @app.route('/')
                        def hello():
                            return "Hello from Flask on Android!"
                            
                        if __name__ == '__main__':
                            app.run(host='0.0.0.0', port=5000)
                    """.trimIndent())
                    File(dir, "requirements.txt").writeText("flask\n")
                    File(dir, "README.md").writeText("# Flask Project\nRun `app.py` to start the server.")
                }
                "FastAPI" -> {
                    File(dir, "main.py").writeText("""
                        from fastapi import FastAPI
                        import uvicorn
                        
                        app = FastAPI()
                        
                        @app.get('/')
                        def read_root():
                            return {"message": "Hello from FastAPI on Android!"}
                            
                        if __name__ == '__main__':
                            uvicorn.run(app, host='0.0.0.0', port=8000)
                    """.trimIndent())
                    File(dir, "requirements.txt").writeText("fastapi\nuvicorn\n")
                    File(dir, "README.md").writeText("# FastAPI Project\nRun `main.py` to start the server.")
                }
                "Telegram Bot" -> {
                    File(dir, "bot.py").writeText("""
                        import os
                        import telebot
                        
                        TOKEN = os.getenv("TELEGRAM_TOKEN", "YOUR_TOKEN_HERE")
                        bot = telebot.TeleBot(TOKEN)
                        
                        @bot.message_handler(commands=['start', 'help'])
                        def send_welcome(message):
                            bot.reply_to(message, "Howdy, how are you doing?")
                            
                        if __name__ == '__main__':
                            print("Bot is polling...")
                            bot.infinity_polling()
                    """.trimIndent())
                    File(dir, "requirements.txt").writeText("pyTelegramBotAPI\n")
                    File(dir, ".env").writeText("TELEGRAM_TOKEN=your_token_here\n")
                    File(dir, "README.md").writeText("# Telegram Bot\nAdd your token in `.env` and run `bot.py`.")
                }
                else -> {
                    File(dir, "main.py").writeText("""
                        import sys
                        
                        def main():
                            print("Hello from $name")
                            print(f"Python version: {sys.version}")
                            
                        if __name__ == "__main__":
                            main()
                    """.trimIndent())
                    File(dir, "requirements.txt").writeText("# Add your Python dependencies here\n")
                    File(dir, "README.md").writeText("# $name\nBasic Python project.")
                }
            }
            
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

    fun deleteProject(name: String): Boolean {
        val dir = getProjectDir(name)
        return dir?.deleteRecursively() ?: false
    }
    
    fun renameProject(oldName: String, newName: String): Boolean {
        val oldDir = getProjectDir(oldName) ?: return false
        val safeNewName = newName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val newDir = File(projectsDir, safeNewName)
        if (newDir.exists()) return false
        return oldDir.renameTo(newDir)
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
