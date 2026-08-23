package com.localhost.py.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localhost.py.data.git.GitManager
import com.localhost.py.data.storage.StorageManager
import com.localhost.py.domain.models.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = StorageManager(application)
    private val gitManager = GitManager()
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects

    private val _isCloning = MutableStateFlow(false)
    val isCloning: StateFlow<Boolean> = _isCloning

    private val _cloneStatus = MutableStateFlow<String?>(null)
    val cloneStatus: StateFlow<String?> = _cloneStatus

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            val dirs = storageManager.listProjects()
            _projects.value = dirs.map { dir ->
                Project(
                    id = UUID.randomUUID().toString(),
                    name = dir.name,
                    path = dir.absolutePath
                )
            }
        }
    }

    fun createNewProject(name: String) {
        storageManager.createProject(name)
        loadProjects()
    }

    fun cloneProject(repoUrl: String, projectName: String) {
        viewModelScope.launch {
            _isCloning.value = true
            _cloneStatus.value = "Cloning repository from GitHub..."
            val targetDir = File(storageManager.getProjectDir(projectName)?.parentFile ?: File(""), projectName)
            val result = gitManager.cloneRepo(repoUrl, targetDir)
            if (result.first) {
                _cloneStatus.value = "Cloned successfully!"
                loadProjects()
            } else {
                _cloneStatus.value = "Clone failed: ${result.second}"
            }
            _isCloning.value = false
        }
    }

    fun clearCloneStatus() {
        _cloneStatus.value = null
    }
}
