package com.pymobileide.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pymobileide.ui.viewmodels.DashboardViewModel

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
                    
                    val statusText = when {
                        isRunning -> "Status: Running..."
                        isInstalling -> "Status: Installing dependencies..."
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
                Button(
                    onClick = { viewModel.runProject(projectName) }, 
                    enabled = !isRunning && !isInstalling,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRunning) "Running..." else "Run")
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
            
            OutlinedButton(
                onClick = { navController.navigate("editor/\$projectName") }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Editor (Files)")
            }
            
            if (output != null) {
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
                            TextButton(onClick = { viewModel.clearOutput() }) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        val scrollState = rememberScrollState()
                        Text(
                            text = output ?: "",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.verticalScroll(scrollState).fillMaxSize()
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
