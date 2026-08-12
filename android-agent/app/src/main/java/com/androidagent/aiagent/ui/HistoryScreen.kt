package com.androidagent.aiagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.androidagent.aiagent.data.TaskRecord
import com.androidagent.aiagent.data.TaskRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    taskRepository: TaskRepository,
    onBack: () -> Unit
) {
    val tasks by taskRepository.getAllTasks().collectAsState(initial = emptyList())
    var taskToDelete by remember { mutableStateOf<TaskRecord?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
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
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "History",
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
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AppColors.Surface)
                            .border(1.dp, AppColors.SurfaceBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Start a task from the main screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskHistoryCard(
                        task = task,
                        onDelete = { taskToDelete = task }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Delete confirmation dialog
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = {
                Text(
                    text = "Delete Task?",
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This action cannot be undone.",
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            taskRepository.deleteTask(task.id)
                            taskToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = AppColors.ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun TaskHistoryCard(
    task: TaskRecord,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (task.status) {
        "COMPLETED" -> AppColors.SuccessGreen
        "FAILED" -> AppColors.ErrorRed
        "CANCELLED" -> AppColors.WarningAmber
        else -> AppColors.TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = AppColors.SurfaceBorder),
                onClick = { expanded = !expanded }
            )
    ) {
        // Main row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Colored left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.goal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status dot + label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = task.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = formatTimestamp(task.startTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${task.stepCount} steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expanded details
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.SurfaceBorder)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = calculateDuration(task.startTime, task.endTime ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.result?.ifBlank { "No result recorded." } ?: "No result recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun calculateDuration(startTime: Long, endTime: Long): String {
    if (startTime <= 0) return "N/A"
    val end = if (endTime > 0) endTime else System.currentTimeMillis()
    val diffMs = end - startTime
    val seconds = (diffMs / 1000) % 60
    val minutes = (diffMs / (1000 * 60)) % 60
    val hours = diffMs / (1000 * 60 * 60)
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
