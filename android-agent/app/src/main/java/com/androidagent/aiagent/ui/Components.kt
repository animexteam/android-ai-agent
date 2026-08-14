package com.androidagent.aiagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidagent.aiagent.agent.PendingConfirmation

@Composable
fun ConfirmationDialog(
    confirmation: PendingConfirmation,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = AppColors.Warning, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Action", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("The agent wants to:", color = AppColors.TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = AppColors.SurfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text("${confirmation.toolName}\n${confirmation.reason}", color = AppColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Warning)) { Text("Allow") } },
        dismissButton = { OutlinedButton(onClick = onDeny) { Text("Deny", color = AppColors.TextSecondary) } },
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
    val answerState = remember { mutableStateOf("") }
    val answer = answerState.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QuestionAnswer, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agent needs info", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(question, color = AppColors.TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answerState.value = it },
                    placeholder = { Text("Your answer", color = AppColors.TextMuted) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.SurfaceVariant,
                        cursorColor = AppColors.Primary
                    )
                )
            }
        },
        confirmButton = { Button(onClick = { if (answer.isNotBlank()) onAnswer(answer) }, enabled = answer.isNotBlank()) { Text("Reply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip", color = AppColors.TextSecondary) } },
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(16.dp)
    )
}
