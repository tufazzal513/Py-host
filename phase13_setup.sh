#!/bin/bash

echo "1. Adding Log/Output Sharing Feature to Dashboard..."
cat << 'INNER_EOF' > app/src/main/java/com/localhost/py/ui/screens/ProjectDashboardScreen.kt
package com.localhost.py.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.localhost.py.ui.viewmodels.DashboardViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardScreen(
    navController: NavController, 
    projectName: String,
    viewModel: DashboardViewModel = viewModel()
) {
    val output by viewModel.output.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    
    var localServerPort by remember { mutableStateOf("5000") }
    var showGitDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("Update via PY LOCALHOST IDE") }
    var gitToken by remember { mutableStateOf("") }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showGitDialog = true }) {
                        Icon(Icons.Default.Sync, contentDescription = "Git Commit & Push")
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
                    
                    val statusText = when {
                        isRunning -> "Status: Running (Foreground Service)"
                        isInstalling -> "Status: Busy (Dependencies/Git)..."
                        else -> "Status: Ready"
                    }
                    val statusColor = when {
                        isRunning -> MaterialTheme.colorScheme.secondary
                        isInstalling -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    Button(
                        onClick = { viewModel.stopProject() }, 
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = { viewModel.runProject(projectName) }, 
                        enabled = !isInstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                }
                
                OutlinedButton(
                    onClick = { viewModel.installDependencies(projectName) }, 
                    enabled = !isRunning && !isInstalling,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install")
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { navController.navigate("editor/\$projectName") }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Files")
                }
                
                OutlinedTextField(
                    value = localServerPort,
                    onValueChange = { localServerPort = it.filter { char -> char.isDigit() } },
                    label = { Text("Port", fontSize = 12.sp) },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp)
                )
                
                FilledTonalButton(
                    onClick = { 
                        val url = "http://localhost:\$localServerPort"
                        val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        navController.navigate("web_preview/\$encoded") 
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WebView")
                }
            }
            
            if (output.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Output Console", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val sendIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Log from \$projectName:\n\n\$output")
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Share Output Console")
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Logs", modifier = Modifier.size(16.dp))
                                }
                                TextButton(onClick = { viewModel.clearOutput() }) {
                                    Text("Clear", fontSize = 12.sp)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        val scrollState = rememberScrollState()
                        
                        LaunchedEffect(output) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                        
                        Text(
                            text = output,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f).verticalScroll(scrollState).fillMaxWidth()
                        )
                        
                        if (isRunning) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            var inputText by remember { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Terminal input...", fontSize = 12.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { 
                                        if (inputText.isNotEmpty()) {
                                            viewModel.sendInput(inputText)
                                            inputText = ""
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send Input")
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showGitDialog) {
        AlertDialog(
            onDismissRequest = { showGitDialog = false },
            title = { Text("Commit & Push") },
            text = {
                Column {
                    Text("This will commit all local changes and push them to your repository.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gitToken,
                        onValueChange = { gitToken = it },
                        label = { Text("GitHub Token (PAT)") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Required for pushing. Leave blank for local commit only.") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.commitAndPush(projectName, commitMessage, gitToken)
                    showGitDialog = false
                }) { Text("Commit & Push") }
            },
            dismissButton = {
                TextButton(onClick = { showGitDialog = false }) { Text("Cancel") }
            }
        )
    }
}
INNER_EOF

echo "2. Updating README..."
cat << 'INNER_EOF' >> README.md

### Phase 13: Log/Output Export
- **Share Execution Logs:** You can now instantly share or export your Python execution logs / server output via the Share icon in the terminal console.
INNER_EOF

echo "Phase 13 Done!"
