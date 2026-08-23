#!/bin/bash

echo "1. Creating Syntax Highlighter..."
cat << 'INNER_EOF' > app/src/main/java/com/localhost/py/ui/screens/PythonSyntaxHighlighter.kt
package com.localhost.py.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PythonSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val inputText = text.text
        val annotatedString = buildAnnotatedString {
            append(inputText)
            
            // JetBrains Darcula-like colors
            val keywordColor = Color(0xFFCC7832) // Orange
            val stringColor = Color(0xFF6A8759) // Green
            val commentColor = Color(0xFF808080) // Gray
            val numberColor = Color(0xFF6897BB) // Blue
            val functionColor = Color(0xFFFFC66D) // Yellow
            val builtinColor = Color(0xFF8888C6) // Purple-ish

            val keywords = "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield)\\b".toRegex()
            val builtins = "\\b(print|len|range|str|int|float|list|dict|set|tuple|open|type|dir|help|input)\\b".toRegex()
            val strings = "\".*?\"|'.*?'".toRegex()
            val comments = "#.*".toRegex()
            val numbers = "\\b\\d+(\\.\\d+)?\\b".toRegex()
            val functions = "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()".toRegex()

            // Apply functions first
            functions.findAll(inputText).forEach {
                if (!keywords.matches(it.groupValues[1])) {
                    addStyle(SpanStyle(color = functionColor), it.range.first, it.range.last + 1)
                }
            }
            
            // Apply built-ins
            builtins.findAll(inputText).forEach {
                addStyle(SpanStyle(color = builtinColor), it.range.first, it.range.last + 1)
            }
            
            // Apply numbers
            numbers.findAll(inputText).forEach {
                addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
            }

            // Apply keywords
            keywords.findAll(inputText).forEach {
                addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
            }

            // Apply strings
            strings.findAll(inputText).forEach {
                addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
            }

            // Apply comments (last so they override content inside)
            comments.findAll(inputText).forEach {
                addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
INNER_EOF

echo "2. Re-writing EditorScreen for advanced Syntax Highlighting..."
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    var files by remember { mutableStateOf(projectDir?.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()) }
    var currentFile by remember { mutableStateOf<File?>(files.firstOrNull { it.name == "main.py" } ?: files.firstOrNull()) }
    
    var codeText by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentFile) {
        currentFile?.let {
            codeText = it.readText()
            hasUnsavedChanges = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("Project Files", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                LazyColumn {
                    items(files) { file ->
                        Text(
                            text = file.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFile = file
                                    scope.launch { drawerState.close() }
                                }
                                .padding(16.dp)
                                .background(if (currentFile == file) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                            color = if (currentFile == file) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
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
                        color = Color(0xFFA9B7C6) // Default text color
                    ),
                    cursorBrush = SolidColor(Color.White),
                    visualTransformation = PythonSyntaxHighlighter() // Applied here
                )
            }
        }
    }
}
INNER_EOF

echo "3. Updating README..."
cat << 'INNER_EOF' >> README.md

### Phase 10: Syntax Highlighting & Code Editor Upgrade
- **Custom Jetpack Compose VisualTransformation:** Developed a real-time regex-based Syntax Highlighter specifically for Python.
- **Darcula Theme:** The code editor now mimics the IntelliJ/PyCharm dark theme (Darcula).
- Colors elements dynamically: Keywords (Orange), Strings (Green), Comments (Gray), Numbers (Blue), Functions (Yellow).
INNER_EOF

echo "Done!"
