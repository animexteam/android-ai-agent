package com.androidagent.aiagent.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.service.OverlayService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onClearMemory: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(SettingsRepository.DEFAULT_ENDPOINT) }
    var model by remember { mutableStateOf(SettingsRepository.DEFAULT_MODEL) }
    var temperature by remember { mutableFloatStateOf(SettingsRepository.DEFAULT_TEMPERATURE) }
    var timeoutMs by remember { mutableLongStateOf(SettingsRepository.DEFAULT_TIMEOUT_MS) }
    var maxSteps by remember { mutableIntStateOf(SettingsRepository.DEFAULT_MAX_STEPS) }
    var visionMode by remember { mutableStateOf(SettingsRepository.DEFAULT_VISION_MODE) }
    var confirmationPolicy by remember { mutableStateOf(SettingsRepository.DEFAULT_CONFIRMATION_POLICY) }
    var saveScreenshots by remember { mutableStateOf(SettingsRepository.DEFAULT_SAVE_SCREENSHOTS) }
    var debugLogging by remember { mutableStateOf(SettingsRepository.DEFAULT_DEBUG_LOGGING) }
    var screenshotResolution by remember { mutableIntStateOf(SettingsRepository.DEFAULT_SCREENSHOT_RESOLUTION) }
    var showApiKey by remember { mutableStateOf(false) }

    val hasOverlayPermission = remember { mutableStateOf(OverlayService.canDrawOverlays(context)) }

    LaunchedEffect(Unit) {
        try {
            apiKey = settingsRepository.apiKey()
            endpoint = settingsRepository.endpoint()
            model = settingsRepository.model()
            temperature = settingsRepository.temperature()
            timeoutMs = settingsRepository.timeout()
            maxSteps = settingsRepository.maxSteps()
            visionMode = settingsRepository.visionMode()
            confirmationPolicy = settingsRepository.confirmationPolicy()
            saveScreenshots = settingsRepository.saveScreenshots()
            debugLogging = settingsRepository.debugLogging()
            screenshotResolution = settingsRepository.screenshotResolution()
        } catch (_: Exception) { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        },
        containerColor = AppColors.DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── API Configuration ──
            SectionHeader("API Configuration")

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; scope.launch { settingsRepository.setApiKey(it) } },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedFieldColors()
            )

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; scope.launch { settingsRepository.setEndpoint(it) } },
                label = { Text("API Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedFieldColors()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it; scope.launch { settingsRepository.setModel(it) } },
                label = { Text("Model Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedFieldColors()
            )

            // Temperature
            Text("Temperature: ${"%.1f".format(temperature)}", color = AppColors.TextSecondary, fontSize = 13.sp)
            Slider(
                value = temperature,
                onValueChange = { temperature = it; scope.launch { settingsRepository.setTemperature(it) } },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            // Timeout
            OutlinedTextField(
                value = timeoutMs.toString(),
                onValueChange = { text -> text.toLongOrNull()?.let { v -> timeoutMs = v; scope.launch { settingsRepository.setTimeout(v) } } },
                label = { Text("Request Timeout (ms)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedFieldColors()
            )

            HorizontalDivider(color = AppColors.SurfaceVariant)

            // ── Agent Configuration ──
            SectionHeader("Agent")

            Text("Max Agent Steps: $maxSteps", color = AppColors.TextSecondary, fontSize = 13.sp)
            Slider(
                value = maxSteps.toFloat(),
                onValueChange = { maxSteps = it.toInt(); scope.launch { settingsRepository.setMaxSteps(it.toInt()) } },
                valueRange = 10f..500f,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Vision Mode", color = AppColors.TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "ALWAYS", "OFF").forEach { mode ->
                    FilterChip(
                        selected = visionMode == mode,
                        onClick = { visionMode = mode; scope.launch { settingsRepository.setVisionMode(mode) } },
                        label = { Text(mode) }
                    )
                }
            }

            HorizontalDivider(color = AppColors.SurfaceVariant)

            // ── Overlay & Assistant ──
            SectionHeader("Overlay & Assistant")

            // Overlay permission
            SettingRow(
                title = "Floating Ball",
                subtitle = if (hasOverlayPermission.value) "Enabled" else "Disabled — tap to enable",
                onClick = {
                    if (!OverlayService.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                },
                icon = if (hasOverlayPermission.value) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                iconColor = if (hasOverlayPermission.value) AppColors.Success else AppColors.Warning
            )

            // Default assistant info
            SettingRow(
                title = "Default Assistant",
                subtitle = "Long-press home → Default Digital Assistant → Android-Use",
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_ASSIST)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } catch (_: Exception) {}
                },
                icon = Icons.Default.Assistant,
                iconColor = AppColors.Primary
            )

            HorizontalDivider(color = AppColors.SurfaceVariant)

            // ── Safety ──
            SectionHeader("Safety")

            Text("Confirmation Policy", color = AppColors.TextSecondary, fontSize = 13.sp)
            listOf("SENSITIVE_ONLY", "ASK_EVERY_TIME", "MANUAL_MODE").forEach { policy ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = confirmationPolicy == policy,
                        onClick = { confirmationPolicy = policy; scope.launch { settingsRepository.setConfirmationPolicy(policy) } }
                    )
                    Text(
                        when (policy) {
                            "SENSITIVE_ONLY" -> "Sensitive actions only"
                            "ASK_EVERY_TIME" -> "Ask every time"
                            else -> "Manual mode"
                        }
                    )
                }
            }

            HorizontalDivider(color = AppColors.SurfaceVariant)

            // ── Data & Debug ──
            SectionHeader("Data")

            SettingRow(
                title = "Clear AI Memory",
                subtitle = "Remove all remembered facts about you",
                onClick = onClearMemory,
                icon = Icons.Default.DeleteSweep,
                iconColor = AppColors.Error
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Debug Logging")
                Switch(checked = debugLogging, onCheckedChange = { debugLogging = it })
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
 Text(title, color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.SurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp, maxLines = 2)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.SurfaceVariant,
    cursorColor = AppColors.Primary
)