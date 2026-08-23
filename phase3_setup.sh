#!/bin/bash
mkdir -p app/src/main/java/com/pymobileide/ui/components

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/data/storage/StorageManager.kt
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
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/viewmodels/EditorViewModel.kt
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
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/components/CodeEditor.kt
package com.pymobileide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val lines = code.count { it == '\n' } + 1
        Column(
            modifier = Modifier
                .verticalScroll(verticalScrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            for (i in 1..lines) {
                Text(
                    text = i.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }
        }

        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/screens/EditorScreen.kt
package com.pymobileide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pymobileide.ui.components.CodeEditor
import com.pymobileide.ui.viewmodels.EditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    projectName: String,
    viewModel: EditorViewModel = viewModel()
) {
    val fileTree by viewModel.fileTree.collectAsState()
    val currentFile by viewModel.currentFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectName) {
        viewModel.loadProject(projectName)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Project Files",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(fileTree) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.openFile(file)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = file.name,
                                color = if (currentFile?.absolutePath == file.absolutePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(currentFile?.name ?: "No file open", style = MaterialTheme.typography.titleMedium)
                            if (hasUnsavedChanges) {
                                Text("Unsaved changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Files")
                        }
                        IconButton(onClick = { viewModel.saveCurrentFile() }, enabled = hasUnsavedChanges) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save",
                                tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (currentFile != null) {
                    CodeEditor(
                        code = fileContent,
                        onCodeChange = { viewModel.updateContent(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select a file from the menu to start editing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/screens/ProjectDashboardScreen.kt
package com.pymobileide.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardScreen(navController: NavController, projectName: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Project Configuration", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Text("Python Version: 3.11", style = MaterialTheme.typography.bodyMedium)
                    Text("Entry Point: main.py", style = MaterialTheme.typography.bodyMedium)
                    Text("Dependencies: requirements.txt", style = MaterialTheme.typography.bodyMedium)
                    Text("Status: Ready", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Button(onClick = { /* TODO: Run in Phase 4 */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Project")
            }
            
            OutlinedButton(
                onClick = { navController.navigate("editor/\$projectName") }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Editor (Files)")
            }

            OutlinedButton(onClick = { /* TODO: Terminal in Phase 8 */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Terminal")
            }
        }
    }
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/pymobileide/ui/navigation/AppNavigation.kt
package com.pymobileide.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pymobileide.ui.screens.HomeScreen
import com.pymobileide.ui.screens.ProjectDashboardScreen
import com.pymobileide.ui.screens.EditorScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable(
            route = "dashboard/{projectName}",
            arguments = listOf(navArgument("projectName") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectName = backStackEntry.arguments?.getString("projectName") ?: "Unknown"
            ProjectDashboardScreen(navController, projectName)
        }
        composable(
            route = "editor/{projectName}",
            arguments = listOf(navArgument("projectName") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectName = backStackEntry.arguments?.getString("projectName") ?: "Unknown"
            EditorScreen(navController, projectName)
        }
    }
}
INNER_EOF

