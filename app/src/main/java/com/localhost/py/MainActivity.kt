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
