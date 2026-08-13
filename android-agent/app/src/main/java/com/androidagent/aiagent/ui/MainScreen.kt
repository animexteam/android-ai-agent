package com.androidagent.aiagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidagent.aiagent.agent.AgentEvent
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
    val isAccessibilityEnabled by remember {
        mutableStateOf(viewModel.isAccessibilityServiceEnabled())
    }
    var taskInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isRunning = state.status.isActive

    // Auto-scroll to bottom when new events arrive
    LaunchedEffect(state.history.size) {
        if (state.history.isNotEmpty()) {
            listState.animateScrollToItem(state.history.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Android-Use",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = AppColors.TextSecondary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AppColors.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Accessibility banner (only when disabled)
                if (!isAccessibilityEnabled) {
                    AccessibilityBanner(onEnable = { viewModel.openAccessibilitySettings() })
                }

                // Status pill (only when running)
                if (isRunning) {
                    StatusPill(status = state.status, stepNumber = state.stepNumber)
                }

                // Completed / Failed banner
                if (state.status == AgentStatus.COMPLETED) {
                    ResultBanner(text = "Task completed", isSuccess = true)
                } else if (state.status == AgentStatus.FAILED) {
                    ResultBanner(
                        text = state.lastError ?: "Task failed",
                        isSuccess = false
                    )
                }

                // Input bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        placeholder = { Text("What should I do?", color = AppColors.TextMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = AppColors.SurfaceVariant,
                            cursorColor = AppColors.Primary
                        ),
                        maxLines = 3,
                        enabled = !isRunning
                    )
                    if (isRunning) {
                        IconButton(
                            onClick = { viewModel.stopAgent() },
                            modifier = Modifier.size(48.dp).padding(4.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = AppColors.Error, modifier = Modifier.size(24.dp))
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
                            modifier = Modifier.size(48.dp).padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Send, contentDescription = "Send",
                                tint = if (taskInput.isNotBlank()) AppColors.Primary else AppColors.TextMuted,
                                modifier = Modifier.size(24.dp)
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
            // Goal banner
            if (state.goal.isNotBlank()) {
                GoalBanner(goal = state.goal)
            }

            // Event timeline
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = state.history.filter { it !is AgentEvent.Observation && it !is AgentEvent.ModelResponse },
                    key = { "${it.stepNumber}_${it.timestamp}" }
                ) { event ->
                    when (event) {
                        is AgentEvent.ToolExecution -> ActionCard(event)
                        is AgentEvent.Error -> ErrorCard(event)
                        is AgentEvent.UserMessage -> UserCard(event)
                        else -> {}
                    }
                }

                // Show finish message
                if (state.status == AgentStatus.COMPLETED || state.status == AgentStatus.FAILED) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }

        // Confirmation dialog
        state.pendingConfirmation?.let { confirmation ->
            ConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { viewModel.respondToConfirmation(true) },
                onDeny = { viewModel.respondToConfirmation(false) }
            )
        }

        // User question dialog
        state.pendingQuestion?.let { question ->
            UserQuestionDialog(
                question = question,
                onAnswer = { answer -> viewModel.respondToUser(answer) },
                onDismiss = {}
            )
        }
    }
}

@Composable
private fun GoalBanner(goal: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(AppColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = goal,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AccessibilityBanner(onEnable: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Warning.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Enable accessibility to get started",
                color = AppColors.Warning,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(onClick = onEnable) {
                Text("Enable", color = AppColors.Warning, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusPill(status: AgentStatus, stepNumber: Int) {
    val (label, color) = when (status) {
        AgentStatus.THINKING -> "Thinking..." to AppColors.Primary
        AgentStatus.EXECUTING -> "Working..." to AppColors.Warning
        AgentStatus.WAITING_FOR_USER -> "Waiting for you" to AppColors.Primary
        AgentStatus.WAITING_FOR_CONFIRMATION -> "Confirm action" to AppColors.Warning
        AgentStatus.VERIFYING -> "Checking..." to AppColors.Primary
        else -> "" to AppColors.Secondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animated dot
        // Pulsing indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            "$label  ·  Step $stepNumber",
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ResultBanner(text: String, isSuccess: Boolean) {
    val color = if (isSuccess) AppColors.Success else AppColors.Error
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionCard(event: AgentEvent.ToolExecution) {
    val isSuccess = event.result.success
    val actionLabel = event.toolName.substringAfterLast(".")
        .replace("_", " ")
        .replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSuccess) AppColors.Surface else AppColors.Error.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 32.dp)
                    .background(
                        if (isSuccess) AppColors.Success else AppColors.Error,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    actionLabel,
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!isSuccess) {
                    event.result.error?.message?.let { errMsg ->
                        Text(
                            errMsg.take(100),
                            color = AppColors.Error,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(event: AgentEvent.Error) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Error.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            event.message.take(120),
            color = AppColors.Error.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun UserCard(event: AgentEvent.UserMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            "You: ${event.text}",
            color = AppColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
