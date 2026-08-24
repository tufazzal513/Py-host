package com.localhost.py.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.localhost.py.ui.viewmodels.DashboardViewModel
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
    val cpuUsage by viewModel.cpuUsage.collectAsState()
    val ramUsage by viewModel.ramUsage.collectAsState()
    val runtimeDuration by viewModel.runtimeDuration.collectAsState()
    
    var showGitDialog by remember { mutableStateOf(false) }
    var showBatteryHelpDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("Update via PyMobile IDE") }
    var gitToken by remember { mutableStateOf("") }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportZip(projectName, uri)
        }
    }
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Auto detect port from output if Flask/FastAPI is running
    LaunchedEffect(output) {
        val serverRegex = ":(\\d{2,5})".toRegex()
        val match = serverRegex.find(output)
        if (match != null) {
            val port = match.groupValues[1]
            if (port != "80" && port != "443") {
                localServerPort = port
            }
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
                    IconButton(onClick = { showBatteryHelpDialog = true }) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = "Background Settings")
                    }
                    IconButton(onClick = { zipExportLauncher.launch("${projectName}.zip") }) {
                        Icon(Icons.Default.Archive, contentDescription = "Export ZIP")
                    }
                    IconButton(onClick = { showGitDialog = true }) {
                        Icon(Icons.Default.Sync, contentDescription = "Git Commit & Push")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Project Configuration", style = MaterialTheme.typography.titleMedium)
                        AssistChip(
                            onClick = { },
                            label = { Text("Python 3.11", fontSize = 11.sp) }
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Entry: main.py", style = MaterialTheme.typography.bodyMedium)
                        Text("Deps: requirements.txt", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    val statusText = when {
                        isRunning -> "● Running in Foreground Service"
                        isInstalling -> "● Installing Dependencies / Git..."
                        else -> "○ Ready"
                    }
                    val statusColor = when {
                        isRunning -> Color(0xFF4CAF50)
                        isInstalling -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                    
                    if (isRunning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CPU: $cpuUsage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("RAM: $ramUsage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Uptime: $runtimeDuration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Primary Controls
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
                    Button(
                        onClick = { viewModel.restartProject(projectName) }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restart")
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
            }
            
            // Secondary Tools Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("editor/$projectName") }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Code", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { navController.navigate("terminal/$projectName") }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CLI", fontSize = 12.sp)
                }
                
                OutlinedTextField(
                    value = localServerPort,
                    onValueChange = { localServerPort = it.filter { char -> char.isDigit() } },
                    label = { Text("Port", fontSize = 10.sp) },
                    modifier = Modifier.width(68.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp)
                )
                
                FilledTonalButton(
                    onClick = { 
                        val url = "http://localhost:$localServerPort"
                        val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        navController.navigate("web_preview/$encoded") 
                    },
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview", fontSize = 12.sp)
                }
            }
            
            // Terminal & Console Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Output Console", style = MaterialTheme.typography.titleSmall, color = Color(0xFF4CAF50))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Log from ${projectName}:\n\n${output}")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Output Console")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share Logs", modifier = Modifier.size(16.dp), tint = Color.LightGray)
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                onClick = { viewModel.clearOutput() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp, color = Color.LightGray)
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.DarkGray)
                    
                    val scrollState = rememberScrollState()
                    
                    LaunchedEffect(output) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                    
                    Text(
                        text = if (output.isEmpty()) "localhost@pymobile:~$ Ready to execute.\n" else output,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFD4D4D4)),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .fillMaxWidth()
                    )
                    
                    if (isRunning) {
                        Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color.DarkGray)
                        var inputText by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF2D2D2D), shape = MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("> ", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        if (inputText.isEmpty()) {
                                            Text("interactive stdin input...", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { 
                                    if (inputText.isNotEmpty()) {
                                        viewModel.sendInput(inputText)
                                        inputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send Input", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBatteryHelpDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryHelpDialog = false },
            title = { Text("Background Execution Info") },
            text = {
                Column {
                    Text(
                        "PyMobile IDE uses an Android Foreground Service to keep Python scripts, Flask/FastAPI servers, and Telegram bots running in the background.\n\n" +
                        "Note: Some device manufacturers (Xiaomi, Samsung, Huawei, etc.) may aggressively kill background processes. To prevent this, set Battery Usage to 'Unrestricted' in your device settings.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showBatteryHelpDialog = false }) { Text("Got it") }
            }
        )
    }

    if (showGitDialog) {
        AlertDialog(
            onDismissRequest = { showGitDialog = false },
            title = { Text("Git Commit & Push") },
            text = {
                Column {
                    Text("Commit and push all project changes to the remote Git repository.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(14.dp))
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
                        label = { Text("GitHub Personal Access Token (PAT)") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Leave blank for local commit only.") }
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
