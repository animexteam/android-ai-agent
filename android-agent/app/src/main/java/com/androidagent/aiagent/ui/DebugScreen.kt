package com.androidagent.aiagent.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidagent.aiagent.agent.AgentEvent
import com.androidagent.aiagent.agent.AgentStatus
import com.androidagent.aiagent.agent.AgentState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: AgentViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.stateFlow.collectAsState()
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copyToastShown by remember { mutableStateOf(false) }

    if (copyToastShown) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Debug report copied to clipboard", Toast.LENGTH_SHORT).show()
            copyToastShown = false
        }
    }

    val lastToolEvent = state.history.filterIsInstance<AgentEvent.ToolExecution>().lastOrNull()
    val lastObservation = state.history.filterIsInstance<AgentEvent.Observation>().lastOrNull()

    val statusColor = when (state.status) {
        AgentStatus.IDLE -> AppColors.Secondary
        AgentStatus.THINKING -> AppColors.Primary
        AgentStatus.EXECUTING -> AppColors.Warning
        AgentStatus.WAITING_FOR_USER -> AppColors.Primary
        AgentStatus.WAITING_FOR_CONFIRMATION -> AppColors.Warning
        AgentStatus.VERIFYING -> AppColors.Primary
        AgentStatus.COMPLETED -> AppColors.Success
        AgentStatus.FAILED -> AppColors.Error
        AgentStatus.CANCELLED -> AppColors.Secondary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Debug",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface
                )
            )
        },
        containerColor = AppColors.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Current Package / Activity
            DebugInfoCard(
                label = "Current Package",
                value = state.currentPackage?.ifBlank { "N/A" } ?: "N/A"
            )

            // Agent Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agent Status",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.status.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Step Count
            DebugInfoCard(
                label = "Step Count",
                value = "${state.stepNumber} / ${state.maxSteps}"
            )

            // Last Tool Name
            DebugInfoCard(
                label = "Last Tool",
                value = lastToolEvent?.toolName ?: "N/A"
            )

            // Last Tool Arguments (truncated)
            DebugInfoCard(
                label = "Last Tool Arguments",
                value = truncate(lastToolEvent?.arguments ?: "N/A", 200),
                isMono = true
            )

            // Last Tool Result (success/failure)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Tool Result",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    val isSuccess = lastToolEvent?.result?.success == true
                    Text(
                        text = if (lastToolEvent == null) "N/A" else if (isSuccess) "Success" else "Failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (lastToolEvent == null) AppColors.TextMuted
                        else if (isSuccess) AppColors.Success
                        else AppColors.Error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Observation Summary
            DebugInfoCard(
                label = "Observation Summary",
                value = lastObservation?.summary ?: "N/A",
                isMono = true
            )

            // Model Latency
            DebugInfoCard(
                label = "Model Latency",
                value = "${state.modelLatencyMs}ms"
            )

            // History Size
            DebugInfoCard(
                label = "History Events",
                value = "${state.history.size}"
            )

            // Last Error
            DebugInfoCard(
                label = "Last Error",
                value = state.lastError ?: "None",
                valueColor = state.lastError?.let { AppColors.Error } ?: AppColors.Success
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Copy Debug Report
            Button(
                onClick = {
                    val report = buildDebugReport(state, lastToolEvent, lastObservation)
                    clipboardManager.setText(AnnotatedString(report))
                    copyToastShown = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Copy Debug Report")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DebugInfoCard(
    label: String,
    value: String,
    isMono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = AppColors.TextPrimary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun truncate(text: String, maxLen: Int): String {
    return if (text.length > maxLen) text.take(maxLen) + "..." else text
}

private fun buildDebugReport(
    state: AgentState,
    lastToolEvent: AgentEvent.ToolExecution?,
    lastObservation: AgentEvent.Observation?
): String {
    return buildString {
        appendLine("=== Android AI Agent Debug Report ===")
        appendLine()
        appendLine("--- Agent State ---")
        appendLine("Status: ${state.status}")
        appendLine("Goal: ${state.goal}")
        appendLine("Step: ${state.stepNumber}/${state.maxSteps}")
        appendLine("Current Package: ${state.currentPackage ?: "N/A"}")
        appendLine("Model Latency: ${state.modelLatencyMs}ms")
        appendLine("History Events: ${state.history.size}")
        appendLine("Last Error: ${state.lastError ?: "None"}")
        appendLine()
        appendLine("--- Last Tool ---")
        appendLine("Name: ${lastToolEvent?.toolName ?: "N/A"}")
        appendLine("Arguments: ${truncate(lastToolEvent?.arguments ?: "N/A", 500)}")
        appendLine("Result: ${truncate(lastToolEvent?.result?.toString() ?: "N/A", 500)}")
        appendLine()
        appendLine("--- Last Observation ---")
        appendLine("Summary: ${truncate(lastObservation?.summary ?: "N/A", 500)}")
        appendLine()
        appendLine("--- Recent Events (last 10) ---")
        state.history.takeLast(10).forEach { event ->
            when (event) {
                is AgentEvent.ToolExecution -> appendLine("[Step ${event.stepNumber}] Tool: ${event.toolName}")
                is AgentEvent.Observation -> appendLine("[Step ${event.stepNumber}] Observation: ${truncate(event.summary, 100)}")
                is AgentEvent.ModelResponse -> appendLine("[Step ${event.stepNumber}] Model: ${event.decisionType}")
                is AgentEvent.UserMessage -> appendLine("[Step ${event.stepNumber}] User: ${truncate(event.text, 100)}")
                is AgentEvent.StatusChange -> appendLine("[Step ${event.stepNumber}] ${event.from} -> ${event.to}")
                is AgentEvent.Error -> appendLine("[Step ${event.stepNumber}] Error: ${truncate(event.message, 100)}")
                is AgentEvent.AgentMessage -> appendLine("[Step ${event.stepNumber}] Agent: ${truncate(event.text, 100)}")
                else -> {}
        }
        appendLine()
        appendLine("[REDACTED: API keys, tokens, and credentials are not included in this report]")
    }
}
