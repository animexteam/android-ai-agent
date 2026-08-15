package com.androidagent.aiagent.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        } catch (_: Exception) { }
    }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, fontSize = 18.sp)
                }
                HorizontalDivider(color = AppColors.Line, thickness = 1.dp)
            }
        },
        containerColor = AppColors.DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Cloud Model Provider ──
            SectionHeader("Cloud Model Provider", Icons.Default.Cloud)

            SettingsTextField(
                value = apiKey, onValueChange = { apiKey = it; scope.launch { settingsRepository.setApiKey(it) } },
                label = "API Key", singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle", tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            SettingsTextField(
                value = endpoint, onValueChange = { endpoint = it; scope.launch { settingsRepository.setEndpoint(it) } },
                label = "API Endpoint", singleLine = true
            )
            Spacer(Modifier.height(4.dp))
            SettingsTextField(
                value = model, onValueChange = { model = it; scope.launch { settingsRepository.setModel(it) } },
                label = "Model Name", singleLine = true
            )
            Spacer(Modifier.height(4.dp))
            SettingsSliderRow(
                label = "Temperature", value = "%.1f".format(temperature), sliderValue = temperature,
                onValueChange = { temperature = it; scope.launch { settingsRepository.setTemperature(it) } }, valueRange = 0f..1f
            )
            Spacer(Modifier.height(2.dp))
            SettingsTextField(
                value = timeoutMs.toString(),
                onValueChange = { text -> text.toLongOrNull()?.let { v -> timeoutMs = v; scope.launch { settingsRepository.setTimeout(v) } } },
                label = "Request Timeout (ms)", singleLine = true
            )

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Agent ──
            SectionHeader("Agent", Icons.Default.SmartToy)

            SettingsSliderRow(
                label = "Max Steps", value = "$maxSteps", sliderValue = maxSteps.toFloat(),
                onValueChange = { maxSteps = it.toInt(); scope.launch { settingsRepository.setMaxSteps(it.toInt()) } }, valueRange = 10f..500f
            )

            Text("Vision Mode", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "ALWAYS", "OFF").forEach { mode ->
                    Surface(
                        onClick = { visionMode = mode; scope.launch { settingsRepository.setVisionMode(mode) } },
                        shape = RoundedCornerShape(20.dp),
                        color = if (visionMode == mode) AppColors.SurfaceVariant else Color.Transparent,
                        border = BorderStroke(1.dp, if (visionMode == mode) AppColors.LineVariant else AppColors.Line)
                    ) {
                        Text(mode, fontSize = 12.sp, fontWeight = if (visionMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (visionMode == mode) AppColors.TextPrimary else AppColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Integration ──
            SectionHeader("Integration", Icons.Default.Cable)

            SettingsClickableRow(
                title = "Floating Ball", subtitle = if (hasOverlayPermission.value) "Enabled" else "Tap to enable overlay permission",
                onClick = {
                    if (!OverlayService.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }, icon = if (hasOverlayPermission.value) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                iconColor = if (hasOverlayPermission.value) AppColors.Success else AppColors.Warning
            )
            Spacer(Modifier.height(2.dp))
            SettingsClickableRow(
                title = "Default Assistant", subtitle = "Long-press home > Default Assistant > Android-Use",
                onClick = { try { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) {} },
                icon = Icons.Default.Assistant, iconColor = AppColors.TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            SettingsClickableRow(
                title = "Accessibility Service", subtitle = "Required for screen control",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                icon = Icons.Default.AccessibilityNew, iconColor = AppColors.TextSecondary
            )

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Safety ──
            SectionHeader("Safety", Icons.Default.Security)

            Text("Confirmation Policy", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            listOf(
                "SENSITIVE_ONLY" to "Sensitive only (recommended)",
                "ASK_EVERY_TIME" to "Every action",
                "MANUAL_MODE" to "Manual mode"
            ).forEach { (policy, desc) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    RadioButton(selected = confirmationPolicy == policy, onClick = { confirmationPolicy = policy; scope.launch { settingsRepository.setConfirmationPolicy(policy) } }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.TextPrimary))
                    Spacer(Modifier.width(4.dp))
                    Column { Text(desc.split(" (").first(), color = AppColors.TextPrimary, fontSize = 14.sp) }
                }
            }

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Data ──
            SectionHeader("Data", Icons.Default.Storage)

            SettingsClickableRow(
                title = "Clear AI Memory", subtitle = "Remove all remembered facts",
                onClick = onClearMemory, icon = Icons.Default.DeleteSweep, iconColor = AppColors.Error
            )
            Spacer(Modifier.height(2.dp))
            Surface(color = AppColors.SurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column { Text("Debug Logging", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary) }
                    }
                    Switch(checked = debugLogging, onCheckedChange = { debugLogging = it }, colors = SwitchDefaults.colors(checkedTrackColor = AppColors.SurfaceHover, checkedThumbColor = AppColors.TextPrimary))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = AppColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    singleLine: Boolean = true, visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, color = AppColors.TextMuted, fontSize = 13.sp) },
        singleLine = singleLine, visualTransformation = visualTransformation, trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary, unfocusedTextColor = AppColors.TextPrimary,
            focusedBorderColor = AppColors.LineVariant, unfocusedBorderColor = AppColors.Line,
            cursorColor = AppColors.TextPrimary
        )
    )
}

@Composable
private fun SettingsSliderRow(label: String, value: String, sliderValue: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Slider(value = sliderValue, onValueChange = onValueChange, valueRange = valueRange,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = SliderDefaults.colors(thumbColor = AppColors.TextPrimary, activeTrackColor = AppColors.TextSecondary))
}

@Composable
private fun SettingsClickableRow(title: String, subtitle: String, onClick: () -> Unit, icon: ImageVector, iconColor: Color) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = AppColors.SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                Text(subtitle, color = AppColors.TextMuted, fontSize = 12.sp, maxLines = 2, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}