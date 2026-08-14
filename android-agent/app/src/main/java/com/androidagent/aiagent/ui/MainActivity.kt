package com.androidagent.aiagent.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidAgentTheme {
                val viewModel: AgentViewModel = viewModel()
                val navController = rememberNavController()

                // Handle assist query from default assistant
                val assistQuery = intent.getStringExtra("assist_query")
                LaunchedEffect(assistQuery) {
                    if (!assistQuery.isNullOrBlank()) {
                        viewModel.startTask(assistQuery)
                    }
                }

                AgentNavHost(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun AgentNavHost(
    navController: NavHostController,
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "main"

    BackHandler(enabled = currentRoute != "main") {
        if (currentRoute == "settings" || currentRoute == "debug" || currentRoute == "history") {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDebug = { navController.navigate("debug") },
                onNavigateToHistory = { navController.navigate("history") }
            )
        }

        composable("settings") {
            SettingsScreen(
                settingsRepository = viewModel.settingsRepository,
                onBack = { navController.popBackStack() },
                onClearMemory = {
                    Toast.makeText(this@MainActivity, "Memory cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        composable("debug") {
            DebugScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            HistoryScreen(
                taskRepository = viewModel.taskRepository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
