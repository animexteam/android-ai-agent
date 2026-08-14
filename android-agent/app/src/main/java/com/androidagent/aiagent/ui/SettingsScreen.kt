package com.androidagent.aiagent.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
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
                title = { Text("Settings", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextSecondary)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── API Configuration ──
            SectionHeader("API Configuration", Icons.Default.Cloud)

            SettingsTextField(
                value = apiKey,
                onValueChange = { apiKey = it; scope.launch { settingsRepository.setApiKey(it) } },
                label = "API Key",
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle", tint = AppColors.TextMuted)
                    }
                }
            )

            SettingsTextField(
                value = endpoint,
                onValueChange = { endpoint = it; scope.launch { settingsRepository.setEndpoint(it) } },
                label = "API Endpoint",
                singleLine = true
            )

            SettingsTextField(
                value = model,
                onValueChange = { model = it; scope.launch { settingsRepository.setModel(it) } },
                label = "Model Name",
                singleLine = true
            )

            // Temperature slider
            SettingsSliderRow(
                label = "Temperature",
                value = "%.1f".format(temperature),
                sliderValue = temperature,
                onValueChange = { temperature = it; scope.launch { settingsRepository.setTemperature(it) } },
                valueRange = 0f..1f
            )

            SettingsTextField(
                value = timeoutMs.toString(),
                onValueChange = { text -> text.toLongOrNull()?.let { v -> timeoutMs = v; scope.launch { settingsRepository.setTimeout(v) } } },
                label = "Request Timeout (ms)",
                singleLine = true
            )

            HorizontalDivider(color = AppColors.SurfaceVariant, thickness = 1.dp)

            // ── Agent Configuration ──
            SectionHeader("Agent", Icons.Default.SmartToy)

            SettingsSliderRow(
                label = "Max Agent Steps",
                value = "$maxSteps",
                sliderValue = maxSteps.toFloat(),
                onValueChange = { maxSteps = it.toInt(); scope.launch { settingsRepository.setMaxSteps(it.toInt()) } },
                valueRange = 10f..500f
            )

            // Vision mode chips
            Text("Vision Mode", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "ALWAYS", "OFF").forEach { mode ->
                    FilterChip(
                        selected = visionMode == mode,
                        onClick = { visionMode = mode; scope.launch { settingsRepository.setVisionMode(mode) } },
                        label = { Text(mode, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Primary.copy(alpha = 0.2f),
                            selectedLabelColor = AppColors.Primary
                        )
                    )
                }
            }

            HorizontalDivider(color = AppColors.SurfaceVariant, thickness = 1.dp)

            // ── Assistant Integration ──
            SectionHeader("Assistant", Icons.Default.Assistant)

            SettingsClickableRow(
                title = "Floating Ball",
                subtitle = if (hasOverlayPermission.value) "Enabled - drag to move, tap to open" else "Tap to enable overlay permission",
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

            SettingsClickableRow(
                title = "Default Assistant",
                subtitle = "Long-press home > Default Digital Assistant > Android-Use",
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                icon = Icons.Default.Assistant,
                iconColor = AppColors.Primary
            )

            SettingsClickableRow(
                title = "Accessibility Service",
                subtitle = "Required for screen control. Tap to check settings.",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                icon = Icons.Default.AccessibilityNew,
                iconColor = AppColors.Info
            )

            HorizontalDivider(color = AppColors.SurfaceVariant, thickness = 1.dp)

            // ── Safety ──
            SectionHeader("Safety", Icons.Default.Security)

            Text("Confirmation Policy", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            listOf(
                "SENSITIVE_ONLY" to "Sensitive actions only (recommended)",
                "ASK_EVERY_TIME" to "Ask every time",
                "MANUAL_MODE" to "Manual mode"
            ).forEach { (policy, desc) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    RadioButton(
                        selected = confirmationPolicy == policy,
                        onClick = { confirmationPolicy = policy; scope.launch { settingsRepository.setConfirmationPolicy(policy) } },
                        colors = RadioButtonDefaults.colors(selectedColor = AppColors.Primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(desc.split(" (").first(), color = AppColors.TextPrimary, fontSize = 14.sp)
                        if (desc.contains("(")) {
                            Text("(${desc.substringAfter("(")}", color = AppColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = AppColors.SurfaceVariant, thickness = 1.dp)

            // ── Data & Debug ──
            SectionHeader("Data", Icons.Default.Storage)

            SettingsClickableRow(
                title = "Clear AI Memory",
                subtitle = "Remove all remembered facts about you",
                onClick = onClearMemory,
                icon = Icons.Default.DeleteSweep,
                iconColor = AppColors.Error
            )

            // Debug logging toggle
            Surface(
                color = AppColors.SurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Debug Logging", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                            Text("Extra logs for troubleshooting", fontSize = 12.sp, color = AppColors.TextMuted)
                        }
                    }
                    Switch(
                        checked = debugLogging,
                        onCheckedChange = { debugLogging = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Primary.copy(alpha = 0.4f), checkedThumbColor = AppColors.Primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ===================================================================
// Reusable Settings Components
// ===================================================================

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, color = AppColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppColors.TextMuted, fontSize = 13.sp) },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            focusedBorderColor = AppColors.Primary,
            unfocusedBorderColor = AppColors.SurfaceVariant,
            cursorColor = AppColors.Primary
        )
    )
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: String,
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(value, color = AppColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Slider(
        value = sliderValue,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = SliderDefaults.colors(
            thumbColor = AppColors.Primary,
            activeTrackColor = AppColors.Primary
        )
    )
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector,
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
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                Text(subtitle, color = AppColors.TextMuted, fontSize = 12.sp, maxLines = 2, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}
