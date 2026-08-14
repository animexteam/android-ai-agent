package com.androidagent.aiagent.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    // Voice input state
    var isListening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }

    // Build display items: chat messages + tool actions
    val displayItems = remember(state.history) {
        state.history.mapNotNull { event ->
            when (event) {
                is AgentEvent.UserMessage -> DisplayItem.UserMessage(event.text, event.timestamp)
                is AgentEvent.AgentMessage -> DisplayItem.AgentMessage(event.text, event.timestamp)
                is AgentEvent.ToolExecution -> DisplayItem.ToolAction(
                    name = event.toolName,
                    args = event.arguments,
                    success = event.result.success,
                    error = event.result.error?.message
                )
                is AgentEvent.Error -> DisplayItem.ErrorItem(event.message)
                else -> null
            }
        }
    }

    LaunchedEffect(displayItems.size) {
        if (displayItems.isNotEmpty()) {
            listState.animateScrollToItem(displayItems.size - 1)
        }
    }

    // Voice recognizer
    val speechRecognizer = remember {
        try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) { null }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startVoiceInput() {
        if (speechRecognizer == null) return
        isListening = true
        voiceText = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Android-Use...")
        }
        try {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        taskInput = text
                    }
                    isListening = false
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    voiceText = matches?.firstOrNull() ?: ""
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        } catch (e: ActivityNotFoundException) {
            isListening = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            TopBar(
                onNewChat = { viewModel.newChat() },
                onHistory = onNavigateToHistory,
                onSettings = onNavigateToSettings
            )

            // ── Status bar (when running) ──
            if (isRunning) {
                RunningStatusBar(state = state)
            }

            // ── Accessibility banner ──
            if (!isAccessibilityEnabled) {
                AccessibilityBanner(onEnable = { viewModel.openAccessibilitySettings() })
            }

            // ── Result banners ──
            if (state.status == AgentStatus.COMPLETED && !isRunning) {
                ResultBanner(text = "Done", isSuccess = true)
            } else if (state.status == AgentStatus.FAILED && !isRunning) {
                ResultBanner(text = state.lastError ?: "Something went wrong", isSuccess = false)
            } else if (state.status == AgentStatus.CANCELLED && !isRunning) {
                ResultBanner(text = "Stopped", isSuccess = false)
            }

            // ── Chat / Event List ──
            if (displayItems.isEmpty() && !isRunning) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = displayItems,
                        key = { it.timestamp.toString() + it::class.simpleName }
                    ) { item ->
                        when (item) {
                            is DisplayItem.UserMessage -> UserChatBubble(text = item.text)
                            is DisplayItem.AgentMessage -> AgentChatBubble(text = item.text)
                            is DisplayItem.ToolAction -> ToolActionCard(item)
                            is DisplayItem.ErrorItem -> ErrorText(item.message)
                        }
                    }
                    // Bottom padding for input bar
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // ── Bottom Input Bar ──
        BottomInputBar(
            input = taskInput,
            onInputChange = { taskInput = it },
            onSend = {
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
            onStop = { viewModel.stopAgent() },
            isRunning = isRunning,
            isListening = isListening,
            onVoiceClick = { startVoiceInput() },
            canSend = taskInput.isNotBlank() && !isRunning
        )

        // ── Dialogs ──
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
// Display items
// ===================================================================

private sealed class DisplayItem {
    abstract val timestamp: Long
    data class UserMessage(val text: String, override val timestamp: Long) : DisplayItem()
    data class AgentMessage(val text: String, override val timestamp: Long) : DisplayItem()
    data class ToolAction(
        val name: String,
        val args: String,
        val success: Boolean,
        val error: String?
    ) : DisplayItem() { override val timestamp: Long = System.currentTimeMillis() }
    data class ErrorItem(val message: String) : DisplayItem() { override val timestamp: Long = System.currentTimeMillis() }
}

// ===================================================================
// Top Bar
// ===================================================================

@Composable
private fun TopBar(
    onNewChat: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App name with gradient accent
        Row(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
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
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Android-Use",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }

        IconButton(onClick = onNewChat) {
            Icon(Icons.Default.EditNote, contentDescription = "New Chat", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onHistory) {
            Icon(Icons.Default.History, contentDescription = "History", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
    HorizontalDivider(color = AppColors.SurfaceVariant, thickness = 1.dp)
}

// ===================================================================
// Running Status Bar (Manus-style)
// ===================================================================

@Composable
private fun RunningStatusBar(state: com.androidagent.aiagent.agent.AgentState) {
    val (label, icon, color) = when (state.status) {
        AgentStatus.THINKING -> Triple("Analyzing screen", Icons.Default.Psychology, AppColors.Info)
        AgentStatus.EXECUTING -> Triple("Using mobile...", Icons.Default.TouchApp, AppColors.Success)
        AgentStatus.WAITING_FOR_USER -> Triple("Waiting for you", Icons.Default.QuestionAnswer, AppColors.Primary)
        AgentStatus.WAITING_FOR_CONFIRMATION -> Triple("Confirm action", Icons.Default.Security, AppColors.Warning)
        AgentStatus.VERIFYING -> Triple("Verifying...", Icons.Default.VerifiedUser, AppColors.Info)
        else -> Triple("Working...", Icons.Default.Autorenew, AppColors.Secondary)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp).alpha(alpha))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${state.durationFormatted}", color = AppColors.TextMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text("Step ${state.stepNumber}", color = AppColors.TextMuted, fontSize = 11.sp)
    }
}

// ===================================================================
// Chat Bubbles
// ===================================================================

@Composable
private fun UserChatBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = AppColors.UserBubble,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AgentChatBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = AppColors.AgentBubble,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.AgentBubbleBorder),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Text(
                text,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// ===================================================================
// Tool Action Card (compact)
// ===================================================================

@Composable
private fun ToolActionCard(action: DisplayItem.ToolAction) {
    val desc = describeAction(action.name, action.args)
    val icon = getActionIcon(action.name)
    val color = if (action.success) AppColors.TextMuted else AppColors.Error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.SurfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            desc,
            color = color,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (action.success) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(14.dp))
        } else {
            Icon(Icons.Default.Close, contentDescription = null, tint = AppColors.Error, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message.take(120),
        color = AppColors.Error.copy(alpha = 0.7f),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

// ===================================================================
// Bottom Input Bar (Gemini-style)
// ===================================================================

@Composable
private fun BottomInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
    isListening: Boolean,
    onVoiceClick: () -> Unit,
    canSend: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Listening indicator
        if (isListening) {
            val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f, targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
                label = "scale"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Listening",
                    tint = AppColors.Error,
                    modifier = Modifier.size(20.dp * scale)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Listening...", color = AppColors.Error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Input row
        Surface(
            color = AppColors.SurfaceVariant,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice button
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Voice input",
                        tint = if (isListening) AppColors.Error else AppColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Text field
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (input.isEmpty()) {
                                Text(
                                    "Ask Android-Use anything...",
                                    color = AppColors.TextMuted,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Send / Stop button
                if (isRunning) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.StopCircle,
                            contentDescription = "Stop",
                            tint = AppColors.Error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) AppColors.Primary else AppColors.TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ===================================================================
// Empty State
// ===================================================================

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo
            Box(
                modifier = Modifier.size(64.dp).background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(AppColors.Primary, AppColors.Secondary)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Android-Use",
                color = AppColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Your AI phone assistant",
                color = AppColors.TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Suggestion chips
            val suggestions = listOf(
                "Say Hello" to Icons.Default.WavingHand,
                "Open WhatsApp" to Icons.Default.Chat,
                "Search YouTube" to Icons.Default.VideoLibrary,
                "What is 15% of 847?" to Icons.Default.Calculate
            )
            suggestions.forEach { (text, icon) ->
                Surface(
                    color = AppColors.SurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(vertical = 3.dp)
                        .clickable(enabled = true) { }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text, color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ===================================================================
// Accessibility Banner
// ===================================================================

@Composable
private fun AccessibilityBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Warning.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Enable accessibility service", color = AppColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = onEnable) {
            Text("Enable", color = AppColors.Warning, fontWeight = FontWeight.Bold)
        }
    }
}

// ===================================================================
// Result Banner
// ===================================================================

@Composable
private fun ResultBanner(text: String, isSuccess: Boolean) {
    val color = if (isSuccess) AppColors.Success else AppColors.Error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null, tint = color, modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

// ===================================================================
// Helpers
// ===================================================================

private fun describeAction(toolName: String, arguments: String): String {
    val text = extractJsonField(arguments, "text")
    val appName = extractJsonField(arguments, "app_name")
    val direction = extractJsonField(arguments, "direction")
    return when {
        toolName.contains("launch_app") -> if (!appName.isNullOrBlank()) "Opened $appName" else "Opened app"
        toolName.contains("click") -> "Tapped element"
        toolName.contains("long_click") -> "Long pressed"
        toolName.contains("type_text") -> if (!text.isNullOrBlank()) "Typed: ${text.take(40)}" else "Typed text"
        toolName.contains("clear_text") -> "Cleared text"
        toolName.contains("scroll") -> "Scrolled ${direction ?: ""}"
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
        toolName.contains("home") -> "Went home"
        toolName.contains("recents") -> "Opened recents"
        toolName.contains("press_key") -> "Pressed key"
        toolName.contains("wait") -> "Waiting..."
        toolName.contains("screenshot") -> "Took screenshot"
        toolName.contains("inspect") -> "Read screen"
        toolName.contains("find") -> "Searching..."
        toolName.contains("analyze") -> "Analyzing screen"
        toolName.contains("visual") -> "Looking for element"
        toolName.contains("finish") -> "Done"
        toolName.contains("stop") -> "Stopped"
        else -> toolName.substringAfterLast(".").replace("_", " ")
    }
}

private fun getActionIcon(toolName: String): ImageVector = when {
    toolName.contains("launch") -> Icons.Default.Launch
    toolName.contains("click") -> Icons.Default.TouchApp
    toolName.contains("type") -> Icons.Default.Keyboard
    toolName.contains("scroll") -> Icons.Default.SwipeVertical
    toolName.contains("swipe") -> Icons.Default.Swipe
    toolName.contains("back") -> Icons.Default.ArrowBack
    toolName.contains("home") -> Icons.Default.Home
    toolName.contains("press_key") -> Icons.Default.KeyboardAlt
    toolName.contains("wait") -> Icons.Default.Schedule
    toolName.contains("screenshot") -> Icons.Default.Screenshot
    toolName.contains("find") -> Icons.Default.Search
    toolName.contains("analyze") -> Icons.Default.Visibility
    toolName.contains("finish") -> Icons.Default.CheckCircle
    else -> Icons.Default.Build
}

private fun extractJsonField(json: String, field: String): String? {
    return Regex(""""$field"\s*:\s*"([^"']*)"""", RegexOption.IGNORE_CASE).find(json)?.groupValues?.getOrNull(1)
}
