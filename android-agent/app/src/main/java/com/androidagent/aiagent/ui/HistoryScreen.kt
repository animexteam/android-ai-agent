package com.androidagent.aiagent.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidagent.aiagent.data.TaskRecord
import com.androidagent.aiagent.data.TaskRepository
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
            TopAppBar(
                title = {
                    Text(
                        text = "Task History",
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
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextMuted
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskHistoryCard(
                        task = task,
                        onDelete = { taskToDelete = task }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = {
                Text(
                    text = "Delete Task?",
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this task? This action cannot be undone.",
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
                    Text("Delete", color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface
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
        "COMPLETED" -> AppColors.Success
        "FAILED" -> AppColors.Error
        "CANCELLED" -> AppColors.Warning
        else -> AppColors.Secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.goal,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(
                            containerColor = statusColor,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = task.status,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            text = formatTimestamp(task.startTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted
                        )
                        Text(
                            text = "${task.stepCount} steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    androidx.compose.material3.HorizontalDivider(
                        color = AppColors.SurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Duration:",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = calculateDuration(task.startTime, task.endTime ?: 0L),
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Result
                    Text(
                        text = "Result:",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.result?.ifBlank { "No result recorded." } ?: "No result recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
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
