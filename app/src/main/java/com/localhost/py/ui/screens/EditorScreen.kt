package com.localhost.py.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.localhost.py.data.storage.StorageManager
import com.localhost.py.ui.components.PythonSyntaxHighlighter
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(navController: NavController, projectName: String) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }
    val projectDir = storageManager.getProjectDir(projectName)
    
    var currentFile by remember { mutableStateOf<File?>(null) }
    var openFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    
    // TextFieldValue with selection and undo/redo history
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var fileTreeTrigger by remember { mutableIntStateOf(0) }
    
    // Undo / Redo stacks
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    
    // Search & Replace state
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplaceField by remember { mutableStateOf(false) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var isRenamingFile by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }
    var targetParentDir by remember { mutableStateOf<File?>(projectDir) }

    // Init first file
    LaunchedEffect(Unit) {
        val files = projectDir?.listFiles()?.toList() ?: emptyList()
        val initialFile = files.firstOrNull { it.name == "main.py" } ?: files.firstOrNull { it.isFile }
        if (initialFile != null) {
            currentFile = initialFile
            openFiles = listOf(initialFile)
        }
    }

    LaunchedEffect(currentFile) {
        currentFile?.let { file ->
            if (file !in openFiles && file.isFile) {
                openFiles = openFiles + file
            }
            val text = if (file.exists() && file.isFile) file.readText() else ""
            textFieldValue = TextFieldValue(text, TextRange(0, 0))
            hasUnsavedChanges = false
            undoStack.clear()
            redoStack.clear()
        }
    }

    fun updateCode(newValue: TextFieldValue, addToUndo: Boolean = true) {
        if (addToUndo && newValue.text != textFieldValue.text) {
            if (undoStack.size > 50) undoStack.removeAt(0)
            undoStack.add(textFieldValue.text)
            redoStack.clear()
        }
        textFieldValue = newValue
        hasUnsavedChanges = true
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeLast()
            redoStack.add(textFieldValue.text)
            textFieldValue = TextFieldValue(last, TextRange(last.length))
            hasUnsavedChanges = true
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            undoStack.add(textFieldValue.text)
            textFieldValue = TextFieldValue(next, TextRange(next.length))
            hasUnsavedChanges = true
        }
    }

    fun insertSymbol(symbol: String) {
        val currentText = textFieldValue.text
        val selection = textFieldValue.selection
        val newText = currentText.replaceRange(selection.start, selection.end, symbol)
        val newCursorPos = selection.start + symbol.length
        updateCode(TextFieldValue(newText, TextRange(newCursorPos)))
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

    val quickSymbols = listOf("Tab", ":", "(", ")", "[", "]", "{", "}", "\"", "'", "=", "+", "-", "*", "/", "#", "_", ".", ",")

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
                Divider()
                
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
                            if (file.name != "main.py" && file.name != "requirements.txt") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { 
                                            renameTargetFile = file
                                            newFileName = file.name
                                            isRenamingFile = true
                                            showCreateDialog = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { 
                                            if (file.deleteRecursively()) {
                                                if (currentFile == file) currentFile = null
                                                openFiles = openFiles.filter { it != file }
                                                fileTreeTrigger++ 
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
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
                    title = { 
                        Column {
                            Text(currentFile?.name ?: "Editor", style = MaterialTheme.typography.titleMedium)
                            if (hasUnsavedChanges) {
                                Text("• Unsaved Changes", style = TextStyle(fontSize = 11.sp, color = Color(0xFFFF9800)))
                            } else {
                                Text("Saved", style = TextStyle(fontSize = 11.sp, color = Color(0xFF4CAF50)))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { performUndo() }, enabled = undoStack.isNotEmpty()) {
                            Icon(
                                Icons.Default.Undo, 
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { performRedo() }, enabled = redoStack.isNotEmpty()) {
                            Icon(
                                Icons.Default.Redo, 
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = {
                                currentFile?.writeText(textFieldValue.text)
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Files")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search and Replace Bar
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Find...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    textStyle = TextStyle(fontSize = 14.sp)
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { showReplaceField = !showReplaceField }) {
                                    Icon(Icons.Default.FindReplace, contentDescription = "Toggle Replace")
                                }
                                IconButton(onClick = { 
                                    if (searchQuery.isNotEmpty()) {
                                        val idx = textFieldValue.text.indexOf(searchQuery, textFieldValue.selection.end, ignoreCase = true)
                                        val foundIdx = if (idx != -1) idx else textFieldValue.text.indexOf(searchQuery, 0, ignoreCase = true)
                                        if (foundIdx != -1) {
                                            textFieldValue = textFieldValue.copy(selection = TextRange(foundIdx, foundIdx + searchQuery.length))
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Find Next")
                                }
                            }
                            if (showReplaceField) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = replaceQuery,
                                        onValueChange = { replaceQuery = it },
                                        placeholder = { Text("Replace with...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        textStyle = TextStyle(fontSize = 14.sp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Button(
                                        onClick = {
                                            if (searchQuery.isNotEmpty()) {
                                                val sel = textFieldValue.selection
                                                val selectedText = textFieldValue.text.substring(sel.start, sel.end)
                                                if (selectedText.equals(searchQuery, ignoreCase = true)) {
                                                    val newText = textFieldValue.text.replaceRange(sel.start, sel.end, replaceQuery)
                                                    updateCode(TextFieldValue(newText, TextRange(sel.start + replaceQuery.length)))
                                                }
                                            }
                                        },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("Replace", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Button(
                                        onClick = {
                                            if (searchQuery.isNotEmpty()) {
                                                val newText = textFieldValue.text.replace(searchQuery, replaceQuery, ignoreCase = false)
                                                updateCode(TextFieldValue(newText, TextRange(0)))
                                            }
                                        },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("All", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Open Files Tab Row
                if (openFiles.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF202020))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        items(openFiles) { file ->
                            val isSelected = currentFile == file
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFF3C3F41) else Color(0xFF2B2B2B))
                                    .clickable { currentFile = file }
                                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = file.name,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Spacer(Modifier.width(4.dp))
                                if (openFiles.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                val remaining = openFiles.filter { it != file }
                                                openFiles = remaining
                                                if (currentFile == file) {
                                                    currentFile = remaining.firstOrNull()
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                if (currentFile == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Select a file from the menu to edit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Code Editor Main Area
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                    ) {
                        val verticalScroll = rememberScrollState()
                        val horizontalScroll = rememberScrollState()

                        // Line Numbers
                        val lineCount = textFieldValue.text.count { it == '\n' } + 1
                        Column(
                            modifier = Modifier
                                .verticalScroll(verticalScroll)
                                .background(Color(0xFF252526))
                                .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 6.dp)
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = i.toString(),
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF858585)),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        // Code Editor
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                var updatedValue = newValue
                                if (newValue.text.length > textFieldValue.text.length) {
                                    val lastCharIndex = newValue.selection.start - 1
                                    if (lastCharIndex >= 0 && newValue.text[lastCharIndex] == '\n') {
                                        val textBeforeCursor = newValue.text.substring(0, lastCharIndex)
                                        val lines = textBeforeCursor.split('\n')
                                        val prevLine = lines.lastOrNull() ?: ""
                                        val indent = prevLine.takeWhile { it.isWhitespace() }
                                        val addExtraIndent = prevLine.trimEnd().endsWith(":")
                                        val finalIndent = if (addExtraIndent) "$indent    " else indent
                                        
                                        val newText = newValue.text.substring(0, lastCharIndex + 1) + finalIndent + newValue.text.substring(lastCharIndex + 1)
                                        updatedValue = TextFieldValue(newText, TextRange(lastCharIndex + 1 + finalIndent.length))
                                    }
                                }
                                updateCode(updatedValue)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                                .padding(8.dp),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace, 
                                fontSize = 13.sp,
                                color = Color(0xFFD4D4D4)
                            ),
                            cursorBrush = SolidColor(Color.White),
                            visualTransformation = if (currentFile?.name?.endsWith(".py") == true) PythonSyntaxHighlighter() else androidx.compose.ui.text.input.VisualTransformation.None
                        )
                    }

                    // Mobile Quick Coding Toolbar
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2D2D2D))
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        items(quickSymbols) { symbol ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF3E3E42),
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .clickable {
                                        if (symbol == "Tab") {
                                            insertSymbol("    ")
                                        } else {
                                            insertSymbol(symbol)
                                        }
                                    }
                            ) {
                                Text(
                                    text = symbol,
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreateDialog = false
                isRenamingFile = false
            },
            title = { Text(if (isRenamingFile) "Rename" else if (isCreatingFolder) "Create Folder" else "Create File") },
            text = {
                Column {
                    if (!isRenamingFile) {
                        Text("Inside: ${targetParentDir?.name ?: projectDir?.name}")
                        Spacer(Modifier.height(8.dp))
                    }
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
                    if (newFileName.isNotBlank()) {
                        if (isRenamingFile && renameTargetFile != null) {
                            val newEntity = File(renameTargetFile!!.parentFile, newFileName)
                            if (!newEntity.exists()) {
                                renameTargetFile!!.renameTo(newEntity)
                                if (currentFile == renameTargetFile) {
                                    currentFile = newEntity
                                }
                            }
                        } else if (targetParentDir != null) {
                            val newEntity = File(targetParentDir, newFileName)
                            if (isCreatingFolder) {
                                newEntity.mkdirs()
                            } else {
                                newEntity.parentFile?.mkdirs()
                                newEntity.createNewFile()
                            }
                        }
                        fileTreeTrigger++
                        showCreateDialog = false
                        isRenamingFile = false
                        newFileName = ""
                    }
                }) { Text(if (isRenamingFile) "Rename" else "Create") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreateDialog = false
                    isRenamingFile = false
                }) { Text("Cancel") }
            }
        )
    }
}
