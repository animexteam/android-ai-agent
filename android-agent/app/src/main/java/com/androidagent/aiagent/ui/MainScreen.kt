package com.androidagent.aiagent.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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

    var isListening by remember { mutableStateOf(false) }

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

    val speechRecognizer = remember {
        try { SpeechRecognizer.createSpeechRecognizer(context) } catch (_: Exception) { null }
    }
    DisposableEffect(Unit) { onDispose { speechRecognizer?.destroy() } }

    fun startVoiceInput() {
        if (speechRecognizer == null) return
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Android-Use...")
        }
        try {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(r: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(e: Int) { isListening = false }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) taskInput = matches.first()
                    isListening = false
                }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(e: Int, p: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        } catch (_: ActivityNotFoundException) { isListening = false }
    }

    fun sendTask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            if (state.goal.isBlank()) viewModel.startTask(trimmed)
            else viewModel.continueChat(trimmed)
            taskInput = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.DarkBackground)) {
        Column(modifier = Modifier.fillMaxSize().align(Alignment.TopStart)) {
            TopBar(
                onNewChat = { viewModel.newChat() },
                onHistory = onNavigateToHistory,
                onSettings = onNavigateToSettings,
                onDebug = onNavigateToDebug
            )
            if (isRunning) RunningStatusBar(state = state)
            if (!isAccessibilityEnabled) {
                AccessibilityBanner(onEnable = { viewModel.openAccessibilitySettings() })
            }
            if (state.status == AgentStatus.COMPLETED && !isRunning) {
                ResultBanner(text = "Done", isSuccess = true)
            } else if (state.status == AgentStatus.FAILED && !isRunning) {
                ResultBanner(text = state.lastError ?: "Something went wrong", isSuccess = false)
            } else if (state.status == AgentStatus.CANCELLED && !isRunning) {
                ResultBanner(text = "Stopped", isSuccess = false)
            }

            if (displayItems.isEmpty() && !isRunning) {
                EmptyState(onSuggestionClick = { sendTask(it) })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(items = displayItems, key = { it.timestamp.toString() + it::class.simpleName }) { item ->
                        when (item) {
                            is DisplayItem.UserMessage -> UserChatBubble(text = item.text)
                            is DisplayItem.AgentMessage -> AgentChatBubble(text = item.text)
                            is DisplayItem.ToolAction -> ToolActionCard(item)
                            is DisplayItem.ErrorItem -> ErrorText(item.message)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(90.dp)) }
                }
            }
        }

        // ── BOTTOM INPUT BAR — anchored to bottom ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(AppColors.DarkBackground)
        ) {
            BottomInputBar(
                input = taskInput,
                onInputChange = { taskInput = it },
                onSend = { sendTask(taskInput) },
                onStop = { viewModel.stopAgent() },
                isRunning = isRunning,
                isListening = isListening,
                onVoiceClick = { startVoiceInput() },
                canSend = taskInput.isNotBlank() && !isRunning
            )
        }

        state.pendingConfirmation?.let { conf ->
            ConfirmationDialog(conf, onConfirm = { viewModel.respondToConfirmation(true) }, onDeny = { viewModel.respondToConfirmation(false) })
        }
        state.pendingQuestion?.let { q ->
            UserQuestionDialog(q, onAnswer = { viewModel.respondToUser(it) }, onDismiss = {})
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
    data class ToolAction(val name: String, val args: String, val success: Boolean, val error: String?) : DisplayItem() { override val timestamp = System.currentTimeMillis() }
    data class ErrorItem(val message: String) : DisplayItem() { override val timestamp = System.currentTimeMillis() }
}

// ===================================================================
// Top Bar
// ===================================================================
@Composable
private fun TopBar(onNewChat: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onDebug: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo + name
            Row(
                modifier = Modifier.weight(1f).clickable { onNewChat() }.padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(30.dp).background(AppColors.SurfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SmartToy, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Android-Use", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            // Action icons — ALL visible
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, "Settings", tint = AppColors.TextSecondary, modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onHistory, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.History, "History", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onNewChat, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.EditNote, "New Chat", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDebug, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.BugReport, "Debug", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = AppColors.Line, thickness = 1.dp)
    }
}

// ===================================================================
// Running Status
// ===================================================================
@Composable
private fun RunningStatusBar(state: com.androidagent.aiagent.agent.AgentState) {
    val (label, icon, color) = when (state.status) {
        AgentStatus.THINKING -> Triple("Analyzing screen", Icons.Default.Psychology, AppColors.TextSecondary)
        AgentStatus.EXECUTING -> Triple("Using mobile...", Icons.Default.TouchApp, AppColors.Success)
        AgentStatus.WAITING_FOR_USER -> Triple("Waiting for you", Icons.Default.QuestionAnswer, AppColors.Primary)
        AgentStatus.WAITING_FOR_CONFIRMATION -> Triple("Confirm action", Icons.Default.Security, AppColors.Warning)
        AgentStatus.VERIFYING -> Triple("Verifying...", Icons.Default.VerifiedUser, AppColors.TextSecondary)
        else -> Triple("Working...", Icons.Default.Autorenew, AppColors.Secondary)
    }
    val pulse = rememberInfiniteTransition(label = "p").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), "a"
    )
    Row(
        modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp).alpha(pulse.value))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${state.durationFormatted}", color = AppColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Text("Step ${state.stepNumber}", color = AppColors.TextMuted, fontSize = 11.sp)
    }
}

// ===================================================================
// Chat Bubbles
// ===================================================================
@Composable
private fun UserChatBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            color = AppColors.UserBubble,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
            border = BorderStroke(1.dp, AppColors.Line),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Text(text, color = AppColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun AgentChatBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier.size(26.dp).background(AppColors.TextPrimary, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, null, tint = AppColors.DarkBackground, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = AppColors.AgentBubble,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
            border = BorderStroke(1.dp, AppColors.Line),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Text(text, color = AppColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

// ===================================================================
// Tool Action Card
// ===================================================================
@Composable
private fun ToolActionCard(action: DisplayItem.ToolAction) {
    val desc = describeAction(action.name, action.args)
    val icon = getActionIcon(action.name)
    val color = if (action.success) AppColors.TextMuted else AppColors.Error
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppColors.SurfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(AppColors.Surface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(13.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(desc, color = color, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(
            if (action.success) Icons.Default.Check else Icons.Default.Close,
            null, tint = if (action.success) AppColors.Success else AppColors.Error, modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message.take(120), color = AppColors.Error.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

// ===================================================================
// Bottom Input Bar — THE FIX: aligned to BottomCenter
// ===================================================================
@Composable
private fun BottomInputBar(
    input: String, onInputChange: (String) -> Unit, onSend: () -> Unit,
    onStop: () -> Unit, isRunning: Boolean, isListening: Boolean,
    onVoiceClick: () -> Unit, canSend: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(AppColors.Surface).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (isListening) {
            val s = rememberInfiniteTransition(label = "m").animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(400), RepeatMode.Reverse), "s")
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Mic, "Listening", tint = AppColors.Error, modifier = Modifier.size(20.dp * s.value))
                Spacer(Modifier.width(8.dp))
                Text("Listening...", color = AppColors.Error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Surface(
            color = AppColors.SurfaceVariant, shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, AppColors.Line), modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVoiceClick, modifier = Modifier.size(42.dp)) {
                    Icon(
                        if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        "Voice", tint = if (isListening) AppColors.Error else AppColors.TextMuted, modifier = Modifier.size(20.dp)
                    )
                }
                BasicTextField(
                    value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(AppColors.TextPrimary),
                    decorationBox = { innerTextField ->
                        Box(Modifier.padding(vertical = 10.dp)) {
                            if (input.isEmpty()) Text("Ask Android-Use anything...", color = AppColors.TextMuted, fontSize = 15.sp)
                            innerTextField()
                        }
                    }
                )
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(
                        if (isRunning) AppColors.Error else if (canSend) AppColors.TextPrimary else AppColors.SurfaceHover
                    ).clickable(enabled = canSend || isRunning) {
                        if (isRunning) onStop() else onSend()
                    }, contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        if (isRunning) "Stop" else "Send",
                        tint = when { isRunning -> Color.White; canSend -> AppColors.DarkBackground; else -> AppColors.TextMuted },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ===================================================================
// Empty State
// ===================================================================
@Composable
private fun EmptyState(onSuggestionClick: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(64.dp).background(AppColors.TextPrimary, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.SmartToy, null, tint = AppColors.DarkBackground, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("Android-Use", color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(6.dp))
            Text("Your AI phone assistant", color = AppColors.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            val suggestions = listOf(
                "Say hello" to Icons.Default.EmojiEmotions,
                "Open WhatsApp" to Icons.Default.Chat,
                "Search YouTube" to Icons.Default.VideoLibrary,
                "What is 15% of 847?" to Icons.Default.Calculate
            )
            suggestions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    row.forEach { (text, icon) ->
                        Surface(
                            color = AppColors.Surface, shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AppColors.Line),
                            modifier = Modifier.weight(1f).clickable { onSuggestionClick(text) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(9.dp))
                                Text(text, color = AppColors.TextSecondary, fontSize = 12.5.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================================================================
// Banners
// ===================================================================
@Composable
private fun AccessibilityBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(AppColors.Warning.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Warning, null, tint = AppColors.Warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enable accessibility service", color = AppColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Surface(onClick = onEnable, color = AppColors.Warning.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
            Text("Enable", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = AppColors.Warning, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun ResultBanner(text: String, isSuccess: Boolean) {
    val c = if (isSuccess) AppColors.Success else AppColors.Error
    Row(
        modifier = Modifier.fillMaxWidth().background(c.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = c, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

// ===================================================================
// Helpers
// ===================================================================
private fun describeAction(toolName: String, args: String): String {
    val text = extractJsonField(args, "text")
    val appName = extractJsonField(args, "app_name")
    val direction = extractJsonField(args, "direction")
    return when {
        toolName.contains("launch_app") -> if (!appName.isNullOrBlank()) "Opened $appName" else "Opened app"
        toolName.contains("double_click") -> "Double tapped"
        toolName.contains("drag") -> "Dragged element"
        toolName.contains("pinch") -> "Pinch zoomed"
        toolName.contains("fling") -> "Flinged ${direction ?: ""}"
        toolName.contains("click") -> "Tapped element"
        toolName.contains("long_click") -> "Long pressed"
        toolName.contains("type_text") -> if (!text.isNullOrBlank()) "Typed: ${text.take(40)}" else "Typed text"
        toolName.contains("clear_text") -> "Cleared text"
        toolName.contains("select_all") -> "Selected all text"
        toolName.contains("copy_text") -> "Copied text"
        toolName.contains("paste_text") -> "Pasted text"
        toolName.contains("clipboard") -> "Set clipboard"
        toolName.contains("scroll") -> "Scrolled ${direction ?: ""}"
        toolName.contains("swipe") -> "Swiped ${direction ?: ""}"
        toolName.contains("open_notif") -> "Opened notifications"
        toolName.contains("quick_settings") -> "Opened quick settings"
        toolName.contains("open_url") -> "Opened URL"
        toolName.contains("make_call") -> "Made a call"
        toolName.contains("send_sms") -> "Sent SMS"
        toolName.contains("share") -> "Shared content"
        toolName.contains("toggle") -> "Toggled setting"
        toolName.contains("volume") -> "Changed volume"
        toolName.contains("power") -> "Power menu"
        toolName.contains("lock") -> "Locked screen"
        toolName.contains("split") -> "Split screen"
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
    toolName.contains("double_click") -> Icons.Default.TouchApp
    toolName.contains("drag") -> Icons.Default.OpenWith
    toolName.contains("pinch") -> Icons.Default.ZoomIn
    toolName.contains("fling") -> Icons.Default.Swipe
    toolName.contains("click") -> Icons.Default.TouchApp
    toolName.contains("type") -> Icons.Default.Keyboard
    toolName.contains("clear") -> Icons.Default.Backspace
    toolName.contains("select_all") -> Icons.Default.SelectAll
    toolName.contains("copy") -> Icons.Default.ContentCopy
    toolName.contains("paste") -> Icons.Default.ContentPaste
    toolName.contains("clipboard") -> Icons.Default.ContentCopy
    toolName.contains("scroll") -> Icons.Default.SwipeVertical
    toolName.contains("swipe") -> Icons.Default.Swipe
    toolName.contains("notif") -> Icons.Default.Notifications
    toolName.contains("quick_settings") -> Icons.Default.Settings
    toolName.contains("url") -> Icons.Default.Language
    toolName.contains("call") -> Icons.Default.Phone
    toolName.contains("sms") -> Icons.Default.Sms
    toolName.contains("share") -> Icons.Default.Share
    toolName.contains("toggle") -> Icons.Default.ToggleOn
    toolName.contains("volume") -> Icons.Default.VolumeUp
    toolName.contains("power") -> Icons.Default.PowerSettingsNew
    toolName.contains("lock") -> Icons.Default.Lock
    toolName.contains("split") -> Icons.Default.Splitscreen
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
    return Regex("""$field"\s*:\s*"([^"\x27]*)"""", RegexOption.IGNORE_CASE).find(json)?.groupValues?.getOrNull(1)
}
