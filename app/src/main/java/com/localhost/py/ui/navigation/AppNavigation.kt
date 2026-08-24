package com.localhost.py.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localhost.py.ui.screens.HomeScreen
import com.localhost.py.ui.screens.ProjectDashboardScreen
import com.localhost.py.ui.screens.EditorScreen
import com.localhost.py.ui.screens.AdvancedTerminalScreen
import com.localhost.py.ui.screens.WebPreviewScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable(
            route = "dashboard/{projectName}",
            arguments = listOf(navArgument("projectName") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectName = backStackEntry.arguments?.getString("projectName") ?: "Unknown"
            ProjectDashboardScreen(navController, projectName)
        }
        composable(
            route = "editor/{projectName}",
            arguments = listOf(navArgument("projectName") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectName = backStackEntry.arguments?.getString("projectName") ?: "Unknown"
            EditorScreen(navController, projectName)
        }
        composable(
            route = "terminal/{projectName}",
            arguments = listOf(navArgument("projectName") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectName = backStackEntry.arguments?.getString("projectName") ?: "Unknown"
            AdvancedTerminalScreen(navController, projectName)
        }
        composable(
            route = "web_preview/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            WebPreviewScreen(navController, url)
        }
    }
}
