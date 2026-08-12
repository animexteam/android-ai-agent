package com.androidagent.aiagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidagent.aiagent.agent.AgentEvent
import com.androidagent.aiagent.agent.AgentStatus
import com.androidagent.aiagent.agent.PendingConfirmation

@Composable
fun AgentStatusBar(
    status: AgentStatus,
    stepNumber: Int,
    maxSteps: Int,
    modelLatencyMs: Long
) {
    val statusColor = when (status) {
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

    val statusLabel = when (status) {
        AgentStatus.IDLE -> "Idle"
        AgentStatus.THINKING -> "Thinking..."
        AgentStatus.EXECUTING -> "Executing..."
        AgentStatus.WAITING_FOR_USER -> "Waiting for Input"
        AgentStatus.WAITING_FOR_CONFIRMATION -> "Needs Confirmation"
        AgentStatus.VERIFYING -> "Verifying..."
        AgentStatus.COMPLETED -> "Completed"
        AgentStatus.FAILED -> "Failed"
        AgentStatus.CANCELLED -> "Cancelled"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.SurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Steps",
                    modifier = Modifier.size(14.dp),
                    tint = AppColors.TextSecondary
                )
                Text(
                    text = "$stepNumber/$maxSteps",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Latency",
                    modifier = Modifier.size(14.dp),
                    tint = AppColors.TextSecondary
                )
                Text(
                    text = "${modelLatencyMs}ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    event: AgentEvent,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val (icon, iconColor, title) = when (event) {
        is AgentEvent.ToolExecution -> {
            val isSuccess = event.result.success
            Triple(
                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                if (isSuccess) AppColors.Success else AppColors.Error,
                "Tool: ${event.toolName}"
            )
        }
        is AgentEvent.Observation -> Triple(
            Icons.Default.KeyboardArrowDown,
            AppColors.Primary,
            "Observation"
        )
        is AgentEvent.ModelResponse -> Triple(
            Icons.AutoMirrored.Filled.ArrowRight,
            AppColors.Primary,
            "Model: ${event.decisionType}"
        )
        is AgentEvent.UserMessage -> Triple(
            Icons.Default.KeyboardArrowDown,
            AppColors.Primary,
            "User: ${event.text.take(50)}"
        )
        is AgentEvent.StatusChange -> Triple(
            Icons.Default.KeyboardArrowDown,
            AppColors.Secondary,
            "${event.from} → ${event.to}"
        )
        is AgentEvent.Error -> Triple(
            Icons.Default.Error,
            AppColors.Error,
            event.message.take(80)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Step ${event.stepNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    when (event) {
                        is AgentEvent.ToolExecution -> ToolExecutionDetail(event)
                        is AgentEvent.Observation -> {
                            Text(
                                text = event.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        is AgentEvent.ModelResponse -> {
                            Text(
                                text = event.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                        is AgentEvent.UserMessage -> {
                            Text(
                                text = event.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                        is AgentEvent.StatusChange -> {
                            Text(
                                text = "${event.from} → ${event.to}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                        is AgentEvent.Error -> {
                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolExecutionDetail(event: AgentEvent.ToolExecution) {
    val isSuccess = event.result.success

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Tool:",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = event.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Primary,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Arguments:",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = event.arguments,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status:",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Bold
            )
            Badge(
                containerColor = if (isSuccess) AppColors.Success else AppColors.Error,
                contentColor = Color.White
            ) {
                Text(
                    text = if (isSuccess) "Success" else "Failed",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Result:",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Bold
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AppColors.DarkBackground
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = event.result.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    confirmation: PendingConfirmation,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(
                text = "Confirm Action",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "The agent wants to perform the following action:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.SurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row {
                            Text(
                                text = "Tool: ",
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = confirmation.toolName,
                                color = AppColors.Primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (confirmation.arguments.isNotBlank()) {
                            Row {
                                Text(
                                    text = "Args: ",
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = confirmation.arguments,
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (confirmation.reason.isNotBlank()) {
                            Row {
                                Text(
                                    text = "Reason: ",
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = confirmation.reason,
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Step ${confirmation.stepNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Success
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.Error
                )
            ) {
                Text("Deny")
            }
        },
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun UserQuestionDialog(
    question: String,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var answerText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Agent Question",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("Your answer") },
                    placeholder = { Text("Type your answer...") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.TextMuted,
                        focusedLabelColor = AppColors.Primary,
                        cursorColor = AppColors.Primary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (answerText.isNotBlank()) {
                        onAnswer(answerText)
                    }
                },
                enabled = answerText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary
                )
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppColors.TextSecondary)
            }
        },
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AccessibilityStatusCard(
    isEnabled: Boolean,
    onEnable: () -> Unit
) {
    if (isEnabled) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Error.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SettingsAccessibility,
                contentDescription = "Accessibility",
                tint = AppColors.Warning,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Accessibility Service Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "The agent needs accessibility permissions to interact with apps on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Warning
                )
            ) {
                Text("Enable", color = Color.Black)
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Error.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = AppColors.Error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Error,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = AppColors.Error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
