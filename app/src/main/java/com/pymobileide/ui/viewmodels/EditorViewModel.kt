package com.pymobileide.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pymobileide.data.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = StorageManager(application)
    
    private val _fileTree = MutableStateFlow<List<File>>(emptyList())
    val fileTree: StateFlow<List<File>> = _fileTree

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent
    
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges

    private var projectName: String = ""

    fun loadProject(name: String) {
        projectName = name
        viewModelScope.launch {
            val projectDir = storageManager.getProjectDir(name)
            if (projectDir != null && projectDir.exists()) {
                _fileTree.value = storageManager.getFileTree(projectDir)
                val mainPy = File(projectDir, "main.py")
                if (mainPy.exists()) {
                    openFile(mainPy)
                }
            }
        }
    }

    fun openFile(file: File) {
        if (file.isDirectory) return
        viewModelScope.launch {
            _currentFile.value = file
            _fileContent.value = storageManager.readFile(file)
            _hasUnsavedChanges.value = false
        }
    }

    fun updateContent(newContent: String) {
        _fileContent.value = newContent
        _hasUnsavedChanges.value = true
    }

    fun saveCurrentFile() {
        _currentFile.value?.let { file ->
            viewModelScope.launch {
                storageManager.saveFile(file, _fileContent.value)
                _hasUnsavedChanges.value = false
            }
        }
    }
}
