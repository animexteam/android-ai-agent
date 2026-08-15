package com.androidagent.aiagent.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default assistant activity — triggered by long-press home / ASSIST intent.
 * Shows as a Gemini-style bottom overlay panel on the CURRENT display,
 * NOT a full-screen activity.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Semi-transparent background so user sees current screen behind
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent {
            AndroidAgentTheme {
                AssistOverlayScreen(
                    onSend = { query ->
                        val intent = Intent(this@AssistActivity, MainActivity::class.java).apply {
                            putExtra("assist_query", query)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    // Tap outside the panel to dismiss
    override fun onBackPressed() {
        finish()
    }
}

@Composable
private fun AssistOverlayScreen(
    onSend: (String) -> Unit,
    onClose: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
            .clickable(indication = null, interactionSource = null) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
 // Gemini-style bottom panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppColors.TextMuted.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small logo
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(AppColors.Primary, AppColors.Secondary)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "How can I help?",
                        color = AppColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input bar
                Surface(
                    color = AppColors.SurfaceVariant,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic button
                        IconButton(onClick = { /* voice - not available in assist overlay */ }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Default.MicNone,
                                contentDescription = "Voice",
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Text field
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask Android-Use anything...", color = AppColors.TextMuted, fontSize = 15.sp) },
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary,
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                cursorColor = AppColors.Primary
                            )
                        )

                        // Send button
                        IconButton(
                            onClick = { if (input.isNotBlank()) onSend(input.trim()) },
                            enabled = input.isNotBlank(),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (input.isNotBlank()) AppColors.Primary else AppColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
