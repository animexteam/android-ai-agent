package com.androidagent.aiagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.androidagent.aiagent.agent.AgentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AgentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val state by viewModel.stateFlow.collectAsState()
    val isAccessibilityEnabled = remember { viewModel.isAccessibilityServiceEnabled() }
    var taskInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isAgentRunning = state.status == AgentStatus.THINKING ||
            state.status == AgentStatus.EXECUTING ||
            state.status == AgentStatus.VERIFYING

    AutoScrollToBottom(
        listState = listState,
        itemCount = state.history.size
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = "Android AI Agent",
                        color = AppColors.TextPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                },
                navigationIcon = {},
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = AppColors.TextSecondary
                        )
                    }
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug",
                            tint = AppColors.TextSecondary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AppColors.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        placeholder = {
                            androidx.compose.material3.Text(
                                "Describe a task for the agent...",
                                color = AppColors.TextMuted
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = AppColors.TextMuted,
                            cursorColor = AppColors.Primary
                        ),
                        maxLines = 3,
                        enabled = !isAgentRunning
                    )
                    if (isAgentRunning) {
                        IconButton(
                            onClick = {
                                viewModel.stopAgent()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = AppColors.Error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val trimmed = taskInput.trim()
                                if (trimmed.isNotBlank()) {
                                    viewModel.startTask(trimmed)
                                    taskInput = ""
                                }
                            },
                            enabled = taskInput.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (taskInput.isNotBlank()) AppColors.Primary else AppColors.TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = AppColors.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            AccessibilityStatusCard(
                isEnabled = isAccessibilityEnabled,
                onEnable = { viewModel.openAccessibilitySettings() }
            )

            AgentStatusBar(
                status = state.status,
                stepNumber = state.stepNumber,
                maxSteps = state.maxSteps,
                modelLatencyMs = state.modelLatencyMs
            )

            state.lastError?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { /* Dismiss via state update is handled by runtime */ }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(state.history) { index, event ->
                    val expandedStates = remember { mutableStateOf(false) }
                    EventCard(
                        event = event,
                        expanded = expandedStates.value,
                        onToggle = { expandedStates.value = !expandedStates.value }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        state.pendingConfirmation?.let { confirmation ->
            ConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { viewModel.respondToConfirmation(true) },
                onDeny = { viewModel.respondToConfirmation(false) }
            )
        }

        state.pendingQuestion?.let { question ->
            UserQuestionDialog(
                question = question,
                onAnswer = { answer -> viewModel.respondToUser(answer) },
                onDismiss = { /* User dismissed */ }
            )
        }
    }
}

@Composable
private fun AutoScrollToBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int
) {
    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
}
