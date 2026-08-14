package com.androidagent.aiagent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    val visibleEvents = remember(state.history) {
        state.history.filter { it is AgentEvent.ToolExecution || it is AgentEvent.Error || it is AgentEvent.UserMessage }
    }

    LaunchedEffect(visibleEvents.size) {
        if (visibleEvents.isNotEmpty()) {
            listState.animateScrollToItem(visibleEvents.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Android-Use",
                            color = AppColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.height(20.dp),
                            color = AppColors.Primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("v4", color = AppColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                actions = {
                    // New chat button
                    IconButton(onClick = { viewModel.newChat() }) {
                        Icon(Icons.Default.EditNote, contentDescription = "New Chat", tint = AppColors.TextSecondary)
                    }
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

                // Manus-style floating status pill
                if (isRunning) {
                    AgentStatusPill(state = state)
                }

                // Result banners
                if (state.status == AgentStatus.COMPLETED) {
                    ResultBanner(text = "Task completed", isSuccess = true)
                } else if (state.status == AgentStatus.FAILED) {
                    ResultBanner(text = state.lastError ?: "Task failed", isSuccess = false)
                } else if (state.status == AgentStatus.CANCELLED) {
                    ResultBanner(text = "Stopped", isSuccess = false)
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
                        placeholder = {
                            if (state.goal.isBlank()) {
                                Text("What should I do?", color = AppColors.TextMuted)
                            } else {
                                Text("Continue...", color = AppColors.TextMuted)
                            }
                        },
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
                                    if (state.goal.isBlank()) {
                                        viewModel.startTask(trimmed)
                                    } else {
                                        viewModel.continueChat(trimmed)
                                    }
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

// ===================================================================
// Manus-style status pill
// ===================================================================

@Composable
private fun AgentStatusPill(state: com.androidagent.aiagent.agent.AgentState) {
    val (label, color) = when (state.status) {
        AgentStatus.THINKING -> "Analyzing screen..." to AppColors.Primary
        AgentStatus.EXECUTING -> "Using mobile..." to AppColors.Success
        AgentStatus.WAITING_FOR_USER -> "Waiting for you" to AppColors.Primary
        AgentStatus.WAITING_FOR_CONFIRMATION -> "Confirm action" to AppColors.Warning
        AgentStatus.VERIFYING -> "Verifying..." to AppColors.Primary
        else -> "Working..." to AppColors.Secondary
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
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
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${state.durationFormatted}", color = AppColors.TextMuted, fontSize = 11.sp)
        Text("Step ${state.stepNumber}", color = AppColors.TextMuted, fontSize = 11.sp)
    }
}

// ===================================================================
// UI Cards
// ===================================================================

@Composable
private fun EmptyStateCard() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Android-Use", color = AppColors.TextMuted, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tell me what to do on your phone", color = AppColors.TextMuted.copy(alpha = 0.6f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Examples:", color = AppColors.TextMuted.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(""Open WhatsApp and tell Rahul I will be late", color = AppColors.TextMuted.copy(alpha = 0.4f), fontSize = 12.sp)
            Text(""Search for best biryani recipe on YouTube", color = AppColors.TextMuted.copy(alpha = 0.4f), fontSize = 12.sp)
            Text(""What is 15% of 847?", color = AppColors.TextMuted.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun GoalBanner(goal: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(AppColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Terminal, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(goal, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AccessibilityBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Warning.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Enable accessibility to get started", color = AppColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        TextButton(onClick = onEnable) { Text("Enable", color = AppColors.Warning, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ResultBanner(text: String, isSuccess: Boolean) {
    val color = if (isSuccess) AppColors.Success else AppColors.Error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Close, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionCard(event: AgentEvent.ToolExecution) {
    val isSuccess = event.result.success
    val desc = describeAction(event.toolName, event.arguments)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSuccess) AppColors.Surface else AppColors.Error.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 28.dp)
                .background(if (isSuccess) AppColors.Success else AppColors.Error, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isSuccess) desc else "$desc (failed)",
                color = if (isSuccess) AppColors.TextPrimary else AppColors.Error,
                fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            if (!isSuccess) {
                event.result.error?.message?.let { Text(it.take(80), color = AppColors.Error.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ErrorCard(event: AgentEvent.Error) {
    Text(
        event.message.take(100),
        color = AppColors.Error.copy(alpha = 0.8f),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

@Composable
private fun UserCard(event: AgentEvent.UserMessage) {
    Text(
        "You: ${event.text}",
        color = AppColors.Primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

// ===================================================================
// Action description helper
// ===================================================================

private fun describeAction(toolName: String, arguments: String): String {
    val text = extractJsonField(arguments, "text")
    val appName = extractJsonField(arguments, "app_name")
    val direction = extractJsonField(arguments, "direction")
    return when {
        toolName.contains("launch_app") -> if (!appName.isNullOrBlank()) "Opened $appName" else "Opened app"
        toolName.contains("click") -> "Tapped"
        toolName.contains("long_click") -> "Long pressed"
        toolName.contains("type_text") -> if (!text.isNullOrBlank()) "Typed: ${text.take(40)}${if (text.length > 40) "..." else ""}" else "Typed text"
        toolName.contains("clear_text") -> "Cleared text"
        toolName.contains("scroll") -> if (direction == "up") "Scrolled up" else "Scrolled down"
        toolName.contains("swipe") -> {
 val d = extractJsonField(arguments, "direction") ?: ""
            when {
                d.contains("up", ignoreCase = true) -> "Swiped up"
                d.contains("down", ignoreCase = true) -> "Swiped down"
                d.contains("left", ignoreCase = true) -> "Swiped left"
                d.contains("right", ignoreCase = true) -> "Swiped right"
                else -> "Swiped"
            }
        }
        toolName.contains("back") -> "Went back"
        toolName.contains("home") -> "Went to home"
        toolName.contains("recents") -> "Opened recents"
        toolName.contains("press_key") -> {
            val key = extractJsonField(arguments, "key") ?: ""
            when { key.contains("enter", true) -> "Pressed Enter"; key.contains("space", true) -> "Pressed Space"; else -> "Pressed key" }
        }
        toolName.contains("wait") -> "Waiting..."
        toolName.contains("screenshot") -> "Took screenshot"
        toolName.contains("inspect") -> "Read screen"
        toolName.contains("find") -> "Searching screen..."
        toolName.contains("open_link") -> "Opened link"
        toolName.contains("analyze") -> "Analyzing screen..."
        toolName.contains("visual") -> "Looking for element..."
        toolName.contains("finish") -> "Done"
        toolName.contains("stop") -> "Stopped"
        else -> toolName.substringAfterLast(".").replace("_", " ")
    }
}

private fun extractJsonField(json: String, field: String): String? {
    return Regex(""""$field""\s*:\s*""([^""']*)"""", RegexOption.IGNORE_CASE).find(json)?.groupValues?.getOrNull(1)
}