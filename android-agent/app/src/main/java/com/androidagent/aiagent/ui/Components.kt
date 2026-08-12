package com.androidagent.aiagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidagent.aiagent.agent.AgentEvent

// ─────────────────────────────────────────────────────────────────────────────
// Status Pill (horizontal bar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AgentStatusBar(
    status: com.androidagent.aiagent.agent.AgentStatus,
    stepNumber: Int,
    maxSteps: Int,
    modelLatencyMs: Long
) {
    val statusColor = when (status) {
        com.androidagent.aiagent.agent.AgentStatus.IDLE -> AppColors.TextSecondary
        com.androidagent.aiagent.agent.AgentStatus.THINKING -> AppColors.AccentBlue
        com.androidagent.aiagent.agent.AgentStatus.EXECUTING -> AppColors.WarningAmber
        com.androidagent.aiagent.agent.AgentStatus.WAITING_FOR_USER -> AppColors.AccentBlue
        com.androidagent.aiagent.agent.AgentStatus.WAITING_FOR_CONFIRMATION -> AppColors.WarningAmber
        com.androidagent.aiagent.agent.AgentStatus.VERIFYING -> AppColors.AccentBlue
        com.androidagent.aiagent.agent.AgentStatus.COMPLETED -> AppColors.SuccessGreen
        com.androidagent.aiagent.agent.AgentStatus.FAILED -> AppColors.ErrorRed
        com.androidagent.aiagent.agent.AgentStatus.CANCELLED -> AppColors.TextSecondary
    }

    val statusLabel = when (status) {
        com.androidagent.aiagent.agent.AgentStatus.IDLE -> "Idle"
        com.androidagent.aiagent.agent.AgentStatus.THINKING -> "Thinking"
        com.androidagent.aiagent.agent.AgentStatus.EXECUTING -> "Executing"
        com.androidagent.aiagent.agent.AgentStatus.WAITING_FOR_USER -> "Waiting"
        com.androidagent.aiagent.agent.AgentStatus.WAITING_FOR_CONFIRMATION -> "Confirm?"
        com.androidagent.aiagent.agent.AgentStatus.VERIFYING -> "Verifying"
        com.androidagent.aiagent.agent.AgentStatus.COMPLETED -> "Done"
        com.androidagent.aiagent.agent.AgentStatus.FAILED -> "Failed"
        com.androidagent.aiagent.agent.AgentStatus.CANCELLED -> "Cancelled"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Colored dot + status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Step progress
        Text(
            text = "Step $stepNumber/$maxSteps",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary,
            fontFamily = FontFamily.Monospace
        )

        // Separator dot
        Box(
            modifier = Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceBorder)
        )

        // Latency
        Text(
            text = "${modelLatencyMs}ms",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step Cards (colored left border, smooth expand/collapse)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EventCard(
    event: AgentEvent,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    data class EventInfo(val icon: ImageVector, val accentColor: Color, val title: String, val stepLabel: String)

    val info = when (event) {
        is AgentEvent.ToolExecution -> {
            val isSuccess = event.result.success
            EventInfo(
                icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                accentColor = if (isSuccess) AppColors.SuccessGreen else AppColors.ErrorRed,
                title = event.toolName,
                stepLabel = "Tool"
            )
        }
        is AgentEvent.Observation -> EventInfo(
            Icons.Default.KeyboardArrowDown,
            AppColors.ObservationPurple,
            "Screen captured",
            "Observation"
        )
        is AgentEvent.ModelResponse -> EventInfo(
            Icons.AutoMirrored.Filled.ArrowRight,
            AppColors.AccentBlue,
            event.decisionType,
            "Model"
        )
        is AgentEvent.UserMessage -> EventInfo(
            Icons.Default.KeyboardArrowDown,
            AppColors.AccentBlue,
            event.text.take(60),
            "User"
        )
        is AgentEvent.StatusChange -> EventInfo(
            Icons.Default.KeyboardArrowDown,
            AppColors.TextSecondary,
            "${event.from} \u2192 ${event.to}",
            "Status"
        )
        is AgentEvent.Error -> EventInfo(
            Icons.Default.Error,
            AppColors.ErrorRed,
            event.message.take(80),
            "Error"
        )
    }

    val (icon, accentColor, title, stepLabel) = info

    // Chevron rotation animation
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
    ) {
        // Header row with colored left accent bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Step type label (small, muted)
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            // Step number
            Text(
                text = "#${event.stepNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Expand chevron with rotation
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = AppColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
        }

        // Animated detail section
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 25.dp, end = 14.dp, bottom = 12.dp)
            ) {
                // Subtle separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.SurfaceBorder)
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (event) {
                    is AgentEvent.ToolExecution -> ToolExecutionDetail(event)
                    is AgentEvent.Observation -> {
                        Text(
                            text = event.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    is AgentEvent.ModelResponse -> {
                        Text(
                            text = event.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                    is AgentEvent.UserMessage -> {
                        Text(
                            text = event.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                    is AgentEvent.StatusChange -> {
                        Text(
                            text = "${event.from} → ${event.to}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    is AgentEvent.Error -> {
                        Text(
                            text = event.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.ErrorRed,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolExecutionDetail(event: AgentEvent.ToolExecution) {
    val isSuccess = event.result.success

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tool name row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tool",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = event.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.AccentBlue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        // Arguments
        Text(
            text = event.arguments,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        // Status badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSuccess) AppColors.SuccessGreen else AppColors.ErrorRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isSuccess) "Success" else "Failed",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSuccess) AppColors.SuccessGreen else AppColors.ErrorRed,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Result card
        Column {
            Text(
                text = "RESULT",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Background)
                    .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = event.result.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirmation Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConfirmationDialog(
    confirmation: com.androidagent.aiagent.agent.PendingConfirmation,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(
                text = "Confirm Action",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "The agent wants to perform:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                // Tool info card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.Background)
                        .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row {
                        Text(
                            text = "Tool: ",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = confirmation.toolName,
                            color = AppColors.AccentBlue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (confirmation.arguments.isNotBlank()) {
                        Row {
                            Text(
                                text = "Args: ",
                                fontWeight = FontWeight.SemiBold,
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
                                fontWeight = FontWeight.SemiBold,
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
                Text(
                    text = "Step ${confirmation.stepNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.SuccessGreen)
            ) {
                Text("Confirm", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.ErrorRed)
            ) {
                Text("Deny")
            }
        },
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(16.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// User Question Dialog
// ─────────────────────────────────────────────────────────────────────────────

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
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    lineHeight = 22.sp
                )
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("Your answer") },
                    placeholder = { Text("Type your answer…") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedBorderColor = AppColors.AccentBlue,
                        unfocusedBorderColor = AppColors.SurfaceBorder,
                        focusedLabelColor = AppColors.AccentBlue,
                        cursorColor = AppColors.AccentBlue
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
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentBlue)
            ) {
                Text("Submit", fontWeight = FontWeight.SemiBold)
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

// ─────────────────────────────────────────────────────────────────────────────
// Accessibility Status Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AccessibilityStatusCard(
    isEnabled: Boolean,
    onEnable: () -> Unit
) {
    if (isEnabled) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.ErrorRed.copy(alpha = 0.08f))
            .border(1.dp, AppColors.ErrorRed.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SettingsAccessibility,
            contentDescription = "Accessibility",
            tint = AppColors.WarningAmber,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Accessibility Required",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "The agent needs accessibility permissions to interact with apps.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                lineHeight = 16.sp
            )
        }
        Button(
            onClick = onEnable,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.WarningAmber),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Enable", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.ErrorRed.copy(alpha = 0.08f))
            .border(1.dp, AppColors.ErrorRed.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = AppColors.ErrorRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.ErrorRed,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = AppColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
