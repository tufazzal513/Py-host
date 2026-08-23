#!/bin/bash

echo "1. Refactoring package name from com.py.localhost to com.localhost.py..."
mkdir -p app/src/main/java/com/localhost/py
cp -r app/src/main/java/com/py/localhost/* app/src/main/java/com/localhost/py/
rm -rf app/src/main/java/com/py

# String replacements across Android source
find app/ -type f -exec sed -i 's/com\.py\.localhost/com.localhost.py/g' {} +

# Allow cleartext traffic in AndroidManifest.xml (needed for http://localhost)
sed -i 's/<application/<application\n        android:usesCleartextTraffic="true"/g' app/src/main/AndroidManifest.xml

echo "2. Adding Phase 9: Web Server Preview..."

cat << 'INNER_EOF' > app/src/main/java/com/localhost/py/ui/screens/WebPreviewScreen.kt
package com.localhost.py.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewScreen(navController: NavController, url: String) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(decodedUrl, style = MaterialTheme.typography.bodyLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(decodedUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
INNER_EOF

# Update MainActivity.kt to include the new route
cat << 'INNER_EOF' > app/src/main/java/com/localhost/py/MainActivity.kt
package com.localhost.py

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localhost.py.ui.screens.HomeScreen
import com.localhost.py.ui.screens.ProjectDashboardScreen
import com.localhost.py.ui.screens.EditorScreen
import com.localhost.py.ui.screens.WebPreviewScreen
import com.localhost.py.ui.theme.PyMobileIDETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PyMobileIDETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController)
                        }
                        composable("dashboard/{projectName}") { backStackEntry ->
                            val projectName = backStackEntry.arguments?.getString("projectName") ?: ""
                            ProjectDashboardScreen(navController, projectName)
                        }
                        composable("editor/{projectName}") { backStackEntry ->
                            val projectName = backStackEntry.arguments?.getString("projectName") ?: ""
                            EditorScreen(navController, projectName)
                        }
                        composable("web_preview/{url}") { backStackEntry ->
                            val url = backStackEntry.arguments?.getString("url") ?: "http://localhost:5000"
                            WebPreviewScreen(navController, url)
                        }
                    }
                }
            }
        }
    }
}
INNER_EOF

# Update Dashboard to include the Preview button
cat << 'INNER_EOF' > app/src/main/java/com/localhost/py/ui/screens/ProjectDashboardScreen.kt
package com.localhost.py.ui.screens

import android.Manifest
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
                            TextButton(onClick = { viewModel.clearOutput() }) {
                                Text("Clear", fontSize = 12.sp)
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
}
INNER_EOF

cat << 'INNER_EOF' >> README.md

### Phase 8 & 9: Terminal Input & Local Server Web Preview
- **Interactive Stdin:** `input()` prompts in Python can now receive data natively via the UI.
- **Local Web Server Detection:** If you launch a Flask, Django, or FastAPI server on `localhost:PORT`, you can use the built-in WebView preview browser to see your server running natively inside the IDE!
INNER_EOF

echo "Done!"
