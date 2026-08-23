package com.localhost.py.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.localhost.py.ui.viewmodels.ProjectsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ProjectsViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val isCloning by viewModel.isCloning.collectAsState()
    val cloneStatus by viewModel.cloneStatus.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showImportZipDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var importProjectName by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var cloneProjectName by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && importProjectName.isNotBlank()) {
            viewModel.importZip(uri, importProjectName)
            importProjectName = ""
        }
    }

    LaunchedEffect(cloneStatus) {
        cloneStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCloneStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("PY LOCALHOST") },
                actions = {
                    IconButton(onClick = { showImportZipDialog = true }, enabled = !isCloning) {
                        Icon(Icons.Default.Archive, contentDescription = "Import ZIP")
                    }
                    IconButton(onClick = { showCloneDialog = true }, enabled = !isCloning) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Clone from GitHub")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Projects Found", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap + to create a project or the Cloud icon to clone from GitHub.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            navController.navigate("dashboard/\${project.name}")
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = project.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = project.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New Project") },
                text = {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newProjectName.isNotBlank()) {
                            viewModel.createNewProject(newProjectName)
                            showCreateDialog = false
                            newProjectName = ""
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showCloneDialog) {
            AlertDialog(
                onDismissRequest = { showCloneDialog = false },
                title = { Text("Clone from GitHub") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = repoUrl,
                            onValueChange = { 
                                repoUrl = it
                                if (cloneProjectName.isBlank() && it.contains("/")) {
                                    cloneProjectName = it.substringAfterLast("/").removeSuffix(".git")
                                }
                            },
                            label = { Text("Git Repository URL") },
                            placeholder = { Text("https://github.com/user/repo.git") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cloneProjectName,
                            onValueChange = { cloneProjectName = it },
                            label = { Text("Project Folder Name") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (repoUrl.isNotBlank() && cloneProjectName.isNotBlank()) {
                                viewModel.cloneProject(repoUrl.trim(), cloneProjectName.trim())
                                showCloneDialog = false
                                repoUrl = ""
                                cloneProjectName = ""
                            }
                        }
                    ) { Text("Clone") }
                },
                dismissButton = {
                    TextButton(onClick = { showCloneDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showImportZipDialog) {
            AlertDialog(
                onDismissRequest = { showImportZipDialog = false },
                title = { Text("Import ZIP Project") },
                text = {
                    OutlinedTextField(
                        value = importProjectName,
                        onValueChange = { importProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (importProjectName.isNotBlank()) {
                            showImportZipDialog = false
                            zipPickerLauncher.launch("application/zip")
                        }
                    }) { Text("Select ZIP") }
                },
                dismissButton = {
                    TextButton(onClick = { showImportZipDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
