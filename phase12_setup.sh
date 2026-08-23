#!/bin/bash

echo "1. Writing Advanced EditorScreen (File Manager included)..."
cat << 'INNER_EOF' > app/src/main/java/com/localhost/py/ui/screens/EditorScreen.kt
package com.localhost.py.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.localhost.py.data.storage.StorageManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(navController: NavController, projectName: String) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }
    val projectDir = storageManager.getProjectDir(projectName)
    
    var currentFile by remember { mutableStateOf<File?>(null) }
    var codeText by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var fileTreeTrigger by remember { mutableIntStateOf(0) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var targetParentDir by remember { mutableStateOf<File?>(projectDir) }

    // Init first file
    LaunchedEffect(Unit) {
        val files = projectDir?.listFiles()?.toList() ?: emptyList()
        currentFile = files.firstOrNull { it.name == "main.py" } ?: files.firstOrNull { it.isFile }
    }

    LaunchedEffect(currentFile) {
        currentFile?.let {
            codeText = if (it.exists() && it.isFile) it.readText() else ""
            hasUnsavedChanges = false
        }
    }

    // Function to recursively get all files for simple flat display with indentation
    fun getFilesRecursively(dir: File, depth: Int = 0): List<Pair<File, Int>> {
        val result = mutableListOf<Pair<File, Int>>()
        val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyArray()
        for (child in children) {
            result.add(Pair(child, depth))
            if (child.isDirectory) {
                result.addAll(getFilesRecursively(child, depth + 1))
            }
        }
        return result
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Project Files", style = MaterialTheme.typography.titleLarge)
                    Row {
                        IconButton(onClick = { 
                            isCreatingFolder = true
                            targetParentDir = projectDir
                            showCreateDialog = true 
                        }) { Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder") }
                        IconButton(onClick = { 
                            isCreatingFolder = false
                            targetParentDir = projectDir
                            showCreateDialog = true 
                        }) { Icon(Icons.Default.NoteAdd, contentDescription = "New File") }
                    }
                }
                HorizontalDivider()
                
                // Reactive file tree list
                val fileList = remember(fileTreeTrigger, projectDir) {
                    projectDir?.let { getFilesRecursively(it) } ?: emptyList()
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(fileList) { (file, depth) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        targetParentDir = file
                                        showCreateDialog = true
                                    } else {
                                        currentFile = file
                                        scope.launch { drawerState.close() }
                                    }
                                }
                                .padding(start = 16.dp + (depth * 16).dp, top = 12.dp, bottom = 12.dp, end = 16.dp)
                                .background(if (currentFile == file) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = file.name,
                                color = if (currentFile == file) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            // Delete button
                            if (file.name != "main.py" && file.name != "requirements.txt") {
                                IconButton(
                                    onClick = { 
                                        if (file.deleteRecursively()) {
                                            if (currentFile == file) currentFile = null
                                            fileTreeTrigger++ 
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentFile?.name ?: "No file selected") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Files")
                        }
                        IconButton(
                            onClick = {
                                currentFile?.writeText(codeText)
                                hasUnsavedChanges = false
                            },
                            enabled = hasUnsavedChanges
                        ) {
                            Icon(
                                Icons.Default.Save, 
                                contentDescription = "Save",
                                tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            if (currentFile == null) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Select a file from the menu to edit.")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF2B2B2B)) // IDE Dark Background
                ) {
                    val verticalScroll = rememberScrollState()
                    val horizontalScroll = rememberScrollState()

                    // Line Numbers
                    val lineCount = codeText.count { it == '\n' } + 1
                    Column(
                        modifier = Modifier
                            .verticalScroll(verticalScroll)
                            .background(Color(0xFF313335))
                            .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 4.dp)
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = i.toString(),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color(0xFF606366)),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    // Code Editor
                    BasicTextField(
                        value = codeText,
                        onValueChange = { 
                            codeText = it
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                            .padding(8.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace, 
                            fontSize = 14.sp,
                            color = Color(0xFFA9B7C6)
                        ),
                        cursorBrush = SolidColor(Color.White),
                        visualTransformation = if (currentFile?.name?.endsWith(".py") == true) PythonSyntaxHighlighter() else androidx.compose.ui.text.input.VisualTransformation.None
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (isCreatingFolder) "Create Folder" else "Create File") },
            text = {
                Column {
                    Text("Inside: \${targetParentDir?.name ?: projectDir?.name}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newFileName.isNotBlank() && targetParentDir != null) {
                        val newEntity = File(targetParentDir, newFileName)
                        if (isCreatingFolder) {
                            newEntity.mkdirs()
                        } else {
                            newEntity.parentFile?.mkdirs()
                            newEntity.createNewFile()
                        }
                        fileTreeTrigger++
                        showCreateDialog = false
                        newFileName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
INNER_EOF

echo "2. Updating README..."
cat << 'INNER_EOF' >> README.md

### Phase 12: File Explorer Upgrade
- **Nested Project Tree:** Implemented a full recursive file manager within the Editor Drawer.
- **Create & Delete:** Easily add folders (e.g. `templates/`, `static/`), Python scripts, HTML files directly in the app. Long-press/Click file icons to delete.
INNER_EOF

echo "Phase 12 Done!"
