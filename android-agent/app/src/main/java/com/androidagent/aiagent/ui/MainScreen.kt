package com.androidagent.aiagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Track which step card is expanded — auto-expand latest, collapse others
    val historySize = state.history.size
    var expandedStepIndex by remember { mutableIntStateOf(-1) }

    // Auto-expand the newest step, auto-scroll to it
    LaunchedEffect(historySize) {
        if (historySize > 0) {
            expandedStepIndex = historySize - 1
            listState.animateScrollToItem(historySize - 1)
        }
    }

    Scaffold(
        topBar = {
            // Minimal top bar: pure black, TaskFlow branding + 3 icon buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App name
                    Text(
                        text = "TaskFlow",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = -0.5.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Icon buttons
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToDebug,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Pill input area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            Text(
                                "Describe a task…",
                                color = AppColors.TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedBorderColor = AppColors.AccentBlue,
                            unfocusedBorderColor = AppColors.SurfaceBorder,
                            cursorColor = AppColors.AccentBlue
                        ),
                        maxLines = 3,
                        enabled = !isAgentRunning,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                    )

                    if (isAgentRunning) {
                        // Red stop button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(AppColors.ErrorRed)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    ),
                                    onClick = { viewModel.stopAgent() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Blue send button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    if (taskInput.isNotBlank()) AppColors.AccentBlue
                                    else AppColors.SurfaceBorder
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(
                                        color = if (taskInput.isNotBlank()) Color.White.copy(alpha = 0.3f)
                                        else AppColors.SurfaceBorder
                                    ),
                                    enabled = taskInput.isNotBlank(),
                                    onClick = {
                                        val trimmed = taskInput.trim()
                                        if (trimmed.isNotBlank()) {
                                            viewModel.startTask(trimmed)
                                            taskInput = ""
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (taskInput.isNotBlank()) Color.White else AppColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = AppColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    EventCard(
                        event = event,
                        expanded = expandedStepIndex == index,
                        onToggle = {
                            expandedStepIndex = if (expandedStepIndex == index) -1 else index
                        }
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