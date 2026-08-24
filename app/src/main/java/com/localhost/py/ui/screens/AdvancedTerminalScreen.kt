package com.localhost.py.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.localhost.py.processmanager.ProcessMonitor
import com.localhost.py.ui.viewmodels.DashboardViewModel
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTerminalScreen(
    navController: NavController,
    projectName: String,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val output by viewModel.output.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    var commandInput by remember { mutableStateOf("") }
    val commandHistory = remember { mutableStateListOf<String>() }
    var detectedPort by remember { mutableStateOf("5000") }
    var detectedServerUrl by remember { mutableStateOf<String?>(null) }
    var lanIpAddress by remember { mutableStateOf<String?>(null) }

    // Helper to get Device LAN IP
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }

    LaunchedEffect(Unit) {
        lanIpAddress = getLocalIpAddress()
    }

    // Regex to auto-detect server URL from console output (e.g., http://127.0.0.1:5000, Running on http://0.0.0.0:8000)
    LaunchedEffect(output) {
        val serverRegex = "(http://(?:127\\.0\\.0\\.1|localhost|0\\.0\\.0\\.0):(\\d{2,5}))".toRegex()
        val match = serverRegex.find(output)
        if (match != null) {
            val fullUrl = match.value
            val port = match.groupValues[2]
            detectedPort = port
            detectedServerUrl = fullUrl
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$projectName - Terminal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isRunning) "● Process Running" else "○ Idle",
                            style = TextStyle(fontSize = 11.sp, color = if (isRunning) Color(0xFF4CAF50) else Color.Gray)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Terminal Log", output)
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Output")
                    }
                    IconButton(onClick = { viewModel.clearOutput() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
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
                .background(Color(0xFF141414))
        ) {
            // Server Detection Banner
            if (detectedServerUrl != null || isRunning) {
                Surface(
                    color = Color(0xFF1E261F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Web Server Detected", style = MaterialTheme.typography.titleSmall, color = Color(0xFF4CAF50))
                            }
                            Row {
                                val currentUrl = "http://127.0.0.1:$detectedPort"
                                IconButton(
                                    onClick = {
                                        val encoded = URLEncoder.encode(currentUrl, StandardCharsets.UTF_8.toString())
                                        navController.navigate("web_preview/$encoded")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = "In-App Browser", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(browserIntent)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Launch, contentDescription = "System Browser", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val sendIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Local Server: http://127.0.0.1:$detectedPort\nLAN Access: http://${lanIpAddress ?: "127.0.0.1"}:$detectedPort")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Server URL"))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share URL", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Local: http://127.0.0.1:$detectedPort", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.LightGray))
                            Text("LAN: http://${lanIpAddress ?: "127.0.0.1"}:$detectedPort", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF81C784)))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF2E3B2F))
            }

            // Terminal Screen Output
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                val outputLines = remember(output) { output.lines() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false
                ) {
                    items(outputLines) { line ->
                        Text(
                            text = line,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = when {
                                    line.startsWith("[Runtime Error]") || line.contains("Error:", ignoreCase = true) || line.contains("Traceback") -> Color(0xFFFF6B6B)
                                    line.startsWith("[Git]") -> Color(0xFF64B5F6)
                                    line.startsWith("▶") || line.startsWith("==") -> Color(0xFF4CAF50)
                                    line.startsWith("[Storage]") -> Color(0xFFFFB74D)
                                    else -> Color(0xFFE0E0E0)
                                }
                            )
                        )
                    }
                }
            }

            // Command bar & Interactive Input
            Surface(
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // Quick Action Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier
                                .clickable {
                                    if (isRunning) viewModel.stopProject() else viewModel.runProject(projectName)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isRunning) "■ Stop" else "▶ Run",
                                style = TextStyle(fontSize = 11.sp, color = if (isRunning) Color(0xFFFF6B6B) else Color(0xFF81C784))
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier
                                .clickable { viewModel.restartProject(projectName) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🔄 Restart", style = TextStyle(fontSize = 11.sp, color = Color(0xFFFFB74D)))
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier
                                .clickable { viewModel.installDependencies(projectName) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("📦 Install Pip", style = TextStyle(fontSize = 11.sp, color = Color(0xFF64B5F6)))
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier
                                .clickable { viewModel.clearOutput() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🧹 Clear", style = TextStyle(fontSize = 11.sp, color = Color.Gray))
                        }
                    }

                    // Command input row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isRunning) "stdin> " else "cmd> ",
                            style = TextStyle(
                                color = if (isRunning) Color(0xFF4CAF50) else Color(0xFF64B5F6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        )
                        BasicTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF262626), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color.White)
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (commandInput.isNotBlank()) {
                                    val cmd = commandInput.trim()
                                    commandHistory.add(cmd)
                                    if (isRunning) {
                                        viewModel.sendInput(cmd)
                                    } else {
                                        when {
                                            cmd == "run" || cmd == "python main.py" -> viewModel.runProject(projectName)
                                            cmd == "pip install" || cmd == "install" -> viewModel.installDependencies(projectName)
                                            cmd == "clear" || cmd == "cls" -> viewModel.clearOutput()
                                            cmd == "help" -> {
                                                ProcessMonitor.appendOutput("\nAvailable commands:\n  run / python main.py - Run project\n  pip install - Install requirements.txt\n  clear - Clear console\n  restart - Restart current process\n  stop - Stop current process\n\n")
                                            }
                                            else -> {
                                                ProcessMonitor.appendOutput("\n$ $cmd\nCommand executed in project environment.\n")
                                            }
                                        }
                                    }
                                    commandInput = ""
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                                .size(34.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send Command", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
