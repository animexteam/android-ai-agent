package com.androidagent.aiagent.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            copyToastShown = false
        }
    }

    val lastToolEvent = state.history.filterIsInstance<AgentEvent.ToolExecution>().lastOrNull()
    val lastObservation = state.history.filterIsInstance<AgentEvent.Observation>().lastOrNull()

    val statusColor = when (state.status) {
        AgentStatus.IDLE -> AppColors.TextSecondary
        AgentStatus.THINKING -> AppColors.AccentBlue
        AgentStatus.EXECUTING -> AppColors.WarningAmber
        AgentStatus.WAITING_FOR_USER -> AppColors.AccentBlue
        AgentStatus.WAITING_FOR_CONFIRMATION -> AppColors.WarningAmber
        AgentStatus.VERIFYING -> AppColors.AccentBlue
        AgentStatus.COMPLETED -> AppColors.SuccessGreen
        AgentStatus.FAILED -> AppColors.ErrorRed
        AgentStatus.CANCELLED -> AppColors.TextSecondary
    }

    Scaffold(
        topBar = {
            // Minimal top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background)
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Debug",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = -0.5.sp
                    )
                }
            }
        },
        containerColor = AppColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section: Agent
            SectionHeader(label = "AGENT")

            DebugInfoCard(
                label = "Status",
                value = state.status.name,
                valueColor = statusColor
            )

            DebugInfoCard(
                label = "Current Package",
                value = state.currentPackage?.ifBlank { "—" } ?: "—",
                isMono = true
            )

            DebugInfoCard(
                label = "Step Count",
                value = "${state.stepNumber} / ${state.maxSteps}",
                isMono = true
            )

            DebugInfoCard(
                label = "Model Latency",
                value = "${state.modelLatencyMs}ms",
                isMono = true
            )

            DebugInfoCard(
                label = "History Events",
                value = "${state.history.size}",
                isMono = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section: Last Tool
            SectionHeader(label = "LAST TOOL")

            DebugInfoCard(
                label = "Tool Name",
                value = lastToolEvent?.toolName ?: "—",
                valueColor = if (lastToolEvent != null) AppColors.AccentBlue else AppColors.TextPrimary,
                isMono = true
            )

            DebugInfoCard(
                label = "Arguments",
                value = truncate(lastToolEvent?.arguments ?: "—", 300),
                isMono = true
            )

            val isSuccess = lastToolEvent?.result?.success == true
            DebugInfoCard(
                label = "Result",
                value = if (lastToolEvent == null) "—" else if (isSuccess) "Success" else "Failed",
                valueColor = if (lastToolEvent == null) AppColors.TextMuted
                else if (isSuccess) AppColors.SuccessGreen
                else AppColors.ErrorRed
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section: Observation
            SectionHeader(label = "OBSERVATION")

            DebugInfoCard(
                label = "Summary",
                value = lastObservation?.summary ?: "—",
                isMono = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section: Errors
            SectionHeader(label = "ERRORS")

            DebugInfoCard(
                label = "Last Error",
                value = state.lastError ?: "None",
                valueColor = state.lastError?.let { AppColors.ErrorRed } ?: AppColors.SuccessGreen
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Copy Debug Report button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.AccentBlue)
                    .clickable {
                        val report = buildDebugReport(state, lastToolEvent, lastObservation)
                        clipboardManager.setText(AnnotatedString(report))
                        copyToastShown = true
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Copy Debug Report",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.TextMuted,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun DebugInfoCard(
    label: String,
    value: String,
    isMono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = AppColors.TextPrimary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp
        )
    }
}

private fun truncate(text: String, maxLen: Int): String {
    return if (text.length > maxLen) text.take(maxLen) + "…" else text
}

private fun buildDebugReport(
    state: AgentState,
    lastToolEvent: AgentEvent.ToolExecution?,
    lastObservation: AgentEvent.Observation?
): String {
    return buildString {
        appendLine("=== TaskFlow Debug Report ===")
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
            }
        }
        appendLine()
        appendLine("[REDACTED: API keys, tokens, and credentials are not included in this report]")
    }
}
