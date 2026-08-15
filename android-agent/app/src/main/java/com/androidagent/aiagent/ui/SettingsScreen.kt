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

// ============================================================================
// Provider presets — when user picks a provider, auto-fill endpoint + model
// ============================================================================

private data class ProviderPreset(
    val displayName: String,
    val endpoint: String,
    val defaultModel: String,
    val needsApiKey: Boolean
)

private val PROVIDER_PRESETS: List<ProviderPreset> = listOf(
    ProviderPreset("Ollama Cloud", "https://ollama.com/api/chat", "gemma4:31b", false),
    ProviderPreset("Ollama Local", "http://localhost:11434/api/chat", "llama3.1:8b", false),
    ProviderPreset("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o", true),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o", true),
    ProviderPreset("Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", true),
    ProviderPreset("Together AI", "https://api.together.xyz/v1/chat/completions", "meta-llama/Llama-3.3-70B-Instruct-Turbo", true),
    ProviderPreset("DeepInfra", "https://api.deepinfra.com/v1/openai/chat/completions", "meta-llama/Llama-3.3-70B-Instruct", true),
    ProviderPreset("Fireworks AI", "https://api.fireworks.ai/inference/v1/chat/completions", "accounts/fireworks/models/llama-v3p3-70b-instruct", true),
    ProviderPreset("Cerebras", "https://api.cerebras.ai/v1/chat/completions", "llama-3.3-70b", true),
    ProviderPreset("Custom", "", "", false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onClearMemory: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Model config
    var selectedProvider by remember { mutableStateOf(SettingsRepository.DEFAULT_PROVIDER) }
    var apiKey by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(SettingsRepository.DEFAULT_ENDPOINT) }
    var model by remember { mutableStateOf(SettingsRepository.DEFAULT_MODEL) }
    var showApiKey by remember { mutableStateOf(false) }

    // Agent config
    var temperature by remember { mutableFloatStateOf(SettingsRepository.DEFAULT_TEMPERATURE) }
    var timeoutMs by remember { mutableLongStateOf(SettingsRepository.DEFAULT_TIMEOUT_MS) }
    var maxSteps by remember { mutableIntStateOf(SettingsRepository.DEFAULT_MAX_STEPS) }
    var visionMode by remember { mutableStateOf(SettingsRepository.DEFAULT_VISION_MODE) }
    var confirmationPolicy by remember { mutableStateOf(SettingsRepository.DEFAULT_CONFIRMATION_POLICY) }

    // System
    var saveScreenshots by remember { mutableStateOf(SettingsRepository.DEFAULT_SAVE_SCREENSHOTS) }
    var debugLogging by remember { mutableStateOf(SettingsRepository.DEFAULT_DEBUG_LOGGING) }
    val hasOverlayPermission = remember { mutableStateOf(OverlayService.canDrawOverlays(context)) }

    // Provider dropdown
    var showProviderDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            selectedProvider = settingsRepository.provider()
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

    // Auto-fill when provider changes
    fun applyProvider(preset: ProviderPreset) {
        selectedProvider = preset.displayName
        if (preset.endpoint.isNotEmpty()) endpoint = preset.endpoint
        if (preset.defaultModel.isNotEmpty()) model = preset.defaultModel
        scope.launch {
            settingsRepository.setProvider(preset.displayName)
            if (preset.endpoint.isNotEmpty()) settingsRepository.setEndpoint(preset.endpoint)
            if (preset.defaultModel.isNotEmpty()) settingsRepository.setModel(preset.defaultModel)
        }
    }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Cloud Model Provider ──────────────────────────────────
            SettingsSectionHeader("Cloud Model Provider", Icons.Default.Cloud)

            // Provider selector dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { showProviderDropdown = true },
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.SurfaceVariant,
                    border = BorderStroke(1.dp, AppColors.Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Dns, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Provider", fontSize = 12.sp, color = AppColors.TextMuted)
                            Spacer(Modifier.height(2.dp))
                            Text(selectedProvider, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        }
                        Icon(Icons.Default.UnfoldMore, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(
                    expanded = showProviderDropdown,
                    onDismissRequest = { showProviderDropdown = false },
                    containerColor = AppColors.Surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.Line)
                ) {
                    PROVIDER_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            onClick = {
                                applyProvider(preset)
                                showProviderDropdown = false
                            },
                            text = {
                                Column {
                                    Text(preset.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                                    Text(
                                        preset.defaultModel.ifEmpty { "Enter your own details" },
                                        fontSize = 12.sp, color = AppColors.TextMuted
                                    )
                                }
                            },
                            modifier = Modifier.background(
                                if (selectedProvider == preset.displayName) AppColors.SurfaceHover else Color.Transparent
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // API Key
            SettingsOutlinedField(
                value = apiKey,
                onValueChange = { apiKey = it; scope.launch { settingsRepository.setApiKey(it) } },
                label = "API Key",
                placeholder = if (PROVIDER_PRESETS.find { it.displayName == selectedProvider }?.needsApiKey == true) "sk-... or key-..." else "Leave empty if not required",
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle visibility",
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            Spacer(Modifier.height(4.dp))

            // API Endpoint (URL)
            SettingsOutlinedField(
                value = endpoint,
                onValueChange = { endpoint = it; scope.launch { settingsRepository.setEndpoint(it) } },
                label = "API URL",
                placeholder = "https://api.example.com/v1/chat/completions",
                singleLine = true
            )

            Spacer(Modifier.height(4.dp))

            // Model Name
            SettingsOutlinedField(
                value = model,
                onValueChange = { model = it; scope.launch { settingsRepository.setModel(it) } },
                label = "Model Name",
                placeholder = "gemma4:31b",
                singleLine = true
            )

            Spacer(Modifier.height(4.dp))

            // Temperature
            SettingsSliderRow(
                label = "Temperature",
                displayValue = "%.1f"format(temperature),
                sliderValue = temperature,
                onValueChange = { temperature = it; scope.launch { settingsRepository.setTemperature(it) } },
                valueRange = 0f..1f
            )

            Spacer(Modifier.height(4.dp))

            // Timeout
            SettingsOutlinedField(
                value = timeoutMs.toString(),
                onValueChange = { text -> text.toLongOrNull()?.let { v -> timeoutMs = v; scope.launch { settingsRepository.setTimeout(v) } } },
                label = "Request Timeout (ms)",
                placeholder = "120000",
                singleLine = true
            )

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Agent ──────────────────────────────────────────────────
            SettingsSectionHeader("Agent", Icons.Default.SmartToy)

            SettingsSliderRow(
                label = "Max Steps",
                displayValue = "$maxSteps",
                sliderValue = maxSteps.toFloat(),
                onValueChange = { maxSteps = it.toInt(); scope.launch { settingsRepository.setMaxSteps(it.toInt()) } },
                valueRange = 10f..500f
            )

            Spacer(Modifier.height(6.dp))
            Text("Vision Mode", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "ALWAYS", "OFF").forEach { mode ->
                    val isSelected = visionMode == mode
                    Surface(
                        onClick = { visionMode = mode; scope.launch { settingsRepository.setVisionMode(mode) } },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) AppColors.SurfaceHover else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) AppColors.LineVariant else AppColors.Line)
                    ) {
                        Text(
                            mode, fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) AppColors.TextPrimary else AppColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Integration ───────────────────────────────────────────
            SettingsSectionHeader("Integration", Icons.Default.Cable)

            SettingsNavRow(
                title = "Floating Ball",
                subtitle = if (hasOverlayPermission.value) "Permission granted" else "Tap to enable overlay permission",
                onClick = {
                    if (!OverlayService.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                },
                icon = if (hasOverlayPermission.value) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                iconColor = if (hasOverlayPermission.value) AppColors.Success else AppColors.Warning
            )
            Spacer(Modifier.height(4.dp))
            SettingsNavRow(
                title = "Default Assistant",
                subtitle = "Long-press home > Default Assistant > Android-Use",
                onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) {}
                },
                icon = Icons.Default.Assistant,
                iconColor = AppColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            SettingsNavRow(
                title = "Accessibility Service",
                subtitle = "Required for screen control",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                },
                icon = Icons.Default.AccessibilityNew,
                iconColor = AppColors.TextSecondary
            )

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Safety ────────────────────────────────────────────────
            SettingsSectionHeader("Safety", Icons.Default.Security)

            Text("Confirmation Policy", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            listOf(
                "SENSITIVE_ONLY" to "Sensitive only (recommended)",
                "ASK_EVERY_TIME" to "Every action",
                "MANUAL_MODE" to "Manual mode"
            ).forEach { (policy, desc) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    RadioButton(
                        selected = confirmationPolicy == policy,
                        onClick = { confirmationPolicy = policy; scope.launch { settingsRepository.setConfirmationPolicy(policy) } },
                        colors = RadioButtonDefaults.colors(selectedColor = AppColors.TextPrimary, unselectedColor = AppColors.TextMuted)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(desc, color = AppColors.TextPrimary, fontSize = 14.sp)
                }
            }

            HorizontalDivider(color = AppColors.Line, thickness = 1.dp)

            // ── Data ───────────────────────────────────────────────────
            SettingsSectionHeader("Data", Icons.Default.Storage)

            SettingsNavRow(
                title = "Clear AI Memory",
                subtitle = "Remove all remembered facts",
                onClick = onClearMemory,
                icon = Icons.Default.DeleteSweep,
                iconColor = AppColors.Error
            )
            Spacer(Modifier.height(4.dp))
            // Debug Logging toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppColors.SurfaceVariant,
                border = BorderStroke(1.dp, AppColors.Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Debug Logging", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                    }
                    Switch(
                        checked = debugLogging,
                        onCheckedChange = { debugLogging = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AppColors.SurfaceHover,
                            checkedThumbColor = AppColors.TextPrimary,
                            uncheckedThumbColor = AppColors.TextMuted,
                            uncheckedTrackColor = AppColors.Line
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Save Screenshots toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppColors.SurfaceVariant,
                border = BorderStroke(1.dp, AppColors.Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoCamera, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Save Screenshots", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                    }
                    Switch(
                        checked = saveScreenshots,
                        onCheckedChange = { saveScreenshots = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AppColors.SurfaceHover,
                            checkedThumbColor = AppColors.TextPrimary,
                            uncheckedThumbColor = AppColors.TextMuted,
                            uncheckedTrackColor = AppColors.Line
                        )
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ============================================================================
// Reusable settings components
// ============================================================================

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Icon(icon, null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = AppColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SettingsOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppColors.TextMuted, fontSize = 13.sp) },
        placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder, color = AppColors.TextMuted, fontSize = 13.sp) }} else null,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            focusedBorderColor = AppColors.LineVariant,
            unfocusedBorderColor = AppColors.Line,
            cursorColor = AppColors.TextPrimary,
            focusedPlaceholderColor = AppColors.TextMuted,
            unfocusedPlaceholderColor = AppColors.TextMuted
        )
    )
}

@Composable
private fun SettingsSliderRow(
    label: String,
    displayValue: String,
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
        Text(displayValue, color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Slider(
        value = sliderValue,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = SliderDefaults.colors(
            thumbColor = AppColors.TextPrimary,
            activeTrackColor = AppColors.TextSecondary,
            inactiveTrackColor = AppColors.Line
        )
    )
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector,
    iconColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.SurfaceVariant,
        border = BorderStroke(1.dp, AppColors.Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
