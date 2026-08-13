package com.androidagent.aiagent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
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

    // Auto-scroll to bottom on new user-visible events
    val visibleEvents = remember(state.history) {
        state.history.filter { event ->
            when (event) {
                is AgentEvent.ToolExecution -> true
                is AgentEvent.Error -> true
                is AgentEvent.UserMessage -> true
                else -> false
            }
        }
    }

    LaunchedEffect(visibleEvents.size) {
        if (visibleEvents.isNotEmpty()) {
            listState.animateScrollToItem(visibleEvents.size - 1)
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
                if (!isAccessibilityEnabled) {
                    AccessibilityBanner(onEnable = { viewModel.openAccessibilitySettings() })
                }

                if (isRunning) {
                    RunningStatusCard(status = state.status, stepNumber = state.stepNumber)
                }

                if (state.status == AgentStatus.COMPLETED) {
                    ResultBanner(text = "Task completed successfully", isSuccess = true)
                } else if (state.status == AgentStatus.FAILED) {
                    ResultBanner(
                        text = state.lastError ?: "Task failed",
                        isSuccess = false
                    )
                } else if (state.status == AgentStatus.CANCELLED) {
                    ResultBanner(text = "Task stopped", isSuccess = false)
                }

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
            if (state.goal.isNotBlank()) {
                GoalBanner(goal = state.goal)
            }

            if (visibleEvents.isEmpty() && !isRunning) {
                EmptyStateCard()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = visibleEvents,
                    key = { "${it.stepNumber}_${it.timestamp}" }
                ) { event ->
                    when (event) {
                        is AgentEvent.ToolExecution -> ActionCard(event)
                        is AgentEvent.Error -> ErrorCard(event)
                        is AgentEvent.UserMessage -> UserCard(event)
                        else -> {}
                    }
                }

                if (state.status == AgentStatus.COMPLETED || state.status == AgentStatus.FAILED || state.status == AgentStatus.CANCELLED) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                item { Spacer(modifier = Modifier.height(60.dp)) }
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
                onDismiss = {}
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Ready to help",
                color = AppColors.TextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Type a task below and I will do it on your phone",
                color = AppColors.TextMuted.copy(alpha = 0.6f),
                fontSize = 13.sp
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
private fun RunningStatusCard(status: AgentStatus, stepNumber: Int) {
    val (label, color) = when (status) {
        AgentStatus.THINKING -> "Analyzing screen..." to AppColors.Primary
        AgentStatus.EXECUTING -> "Performing action..." to AppColors.Success
        AgentStatus.WAITING_FOR_USER -> "Waiting for you" to AppColors.Primary
        AgentStatus.WAITING_FOR_CONFIRMATION -> "Confirm action" to AppColors.Warning
        AgentStatus.VERIFYING -> "Verifying..." to AppColors.Primary
        else -> "Working..." to AppColors.Secondary
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alpha)
                .background(color, CircleShape)
        )
        Text(
            label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "Step $stepNumber",
            color = AppColors.TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ResultBanner(text: String, isSuccess: Boolean) {
    val color = if (isSuccess) AppColors.Success else AppColors.Error
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
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

    val friendlyDescription = describeAction(event.toolName, event.arguments)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSuccess) AppColors.Surface else AppColors.Error.copy(alpha = 0.06f),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSuccess) friendlyDescription else "$friendlyDescription (failed)",
                    color = if (isSuccess) AppColors.TextPrimary else AppColors.Error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!isSuccess) {
                    event.result.error?.message?.let { errMsg ->
                        Text(
                            errMsg.take(80),
                            color = AppColors.Error.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convert tool calls into user-friendly descriptions.
 * No technical jargon — just what's happening.
 */
private fun describeAction(toolName: String, arguments: String): String {
    // Parse simple JSON to extract key info
    val text = extractJsonStringField(arguments, "text")
    val appName = extractJsonStringField(arguments, "app_name")
    val direction = extractJsonStringField(arguments, "direction")
    val url = extractJsonStringField(arguments, "url")

    return when {
        toolName.contains("launch_app") -> {
            if (!appName.isNullOrBlank()) "Opened $appName"
            else "Opened app"
        }
        toolName.contains("click") -> "Tapped"
        toolName.contains("long_click") -> "Long pressed"
        toolName.contains("type_text") -> {
            if (!text.isNullOrBlank()) "Typed: ${text.take(40)}${if (text.length > 40) "..." else ""}"
            else "Typed text"
        }
        toolName.contains("clear_text") -> "Cleared text"
        toolName.contains("scroll") -> {
            if (direction == "up") "Scrolled up" else "Scrolled down"
        }
        toolName.contains("swipe") -> {
            val d = extractJsonStringField(arguments, "direction") ?: ""
            if (d.contains("up", ignoreCase = true)) "Swiped up"
            else if (d.contains("down", ignoreCase = true)) "Swiped down"
            else if (d.contains("left", ignoreCase = true)) "Swiped left"
            else if (d.contains("right", ignoreCase = true)) "Swiped right"
            else "Swiped"
        }
        toolName.contains("back") -> "Went back"
        toolName.contains("home") -> "Went to home"
        toolName.contains("recents") -> "Opened recents"
        toolName.contains("press_key") -> {
            val key = extractJsonStringField(arguments, "key") ?: ""
            when {
                key.contains("enter", ignoreCase = true) -> "Pressed Enter"
                key.contains("space", ignoreCase = true) -> "Pressed Space"
                else -> "Pressed key"
            }
        }
        toolName.contains("wait") -> "Waiting..."
        toolName.contains("screenshot") -> "Took screenshot"
        toolName.contains("inspect") -> "Read screen"
        toolName.contains("find") -> "Searching screen..."
        toolName.contains("open_link") -> {
            if (!url.isNullOrBlank()) "Opened: ${url.take(50)}" else "Opened link"
        }
        toolName.contains("finish") -> "Done"
        toolName.contains("stop") -> "Stopped"
        toolName.contains("analyze") -> "Analyzing screen..."
        toolName.contains("visual") -> "Looking for element..."
        else -> toolName.substringAfterLast(".").replace("_", " ")
    }
}

private fun extractJsonStringField(json: String, field: String): String? {
    val pattern = """"$field"\s*:\s*"([^"]*)""""
    return Regex(pattern, RegexOption.IGNORE_CASE).find(json)?.groupValues?.getOrNull(1)
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
            event.message.take(100),
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
