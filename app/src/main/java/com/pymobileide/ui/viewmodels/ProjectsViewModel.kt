package com.pymobileide.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pymobileide.data.storage.StorageManager
import com.pymobileide.domain.models.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = StorageManager(application)
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects

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
}
