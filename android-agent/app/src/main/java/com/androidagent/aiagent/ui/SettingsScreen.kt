package com.androidagent.aiagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidagent.aiagent.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = -0.5.sp
                    )
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Model Configuration ──
            SectionHeader(label = "MODEL")

            SettingsField(
                label = "API Key",
                value = apiKey,
                onValueChange = { apiKey = it; scope.launch { settingsRepository.setApiKey(it) } },
                isPassword = true,
                showPassword = showApiKey,
                onTogglePassword = { showApiKey = !showApiKey }
            )

            SettingsField(
                label = "API Endpoint",
                value = endpoint,
                onValueChange = { endpoint = it; scope.launch { settingsRepository.setEndpoint(it) } },
                isMono = true
            )

            SettingsField(
                label = "Model Name",
                value = model,
                onValueChange = { model = it; scope.launch { settingsRepository.setModel(it) } },
                isMono = true
            )

            // Temperature slider
            SettingsSliderCard(
                label = "Temperature",
                value = "%.1f".format(temperature),
                valueFloat = temperature,
                onValueChange = { temperature = it; scope.launch { settingsRepository.setTemperature(it) } },
                valueRange = 0f..1f
            )

            SettingsField(
                label = "Request Timeout (ms)",
                value = timeoutMs.toString(),
                onValueChange = { text -> text.toLongOrNull()?.let { v -> timeoutMs = v; scope.launch { settingsRepository.setTimeout(v) } } },
                isMono = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Agent ──
            SectionHeader(label = "AGENT")

            SettingsSliderCard(
                label = "Max Agent Steps",
                value = "$maxSteps",
                valueFloat = maxSteps.toFloat(),
                onValueChange = { maxSteps = it.toInt(); scope.launch { settingsRepository.setMaxSteps(it.toInt()) } },
                valueRange = 10f..100f
            )

            // Vision Mode chips
            SettingsChipGroupCard(
                label = "Vision Mode",
                options = listOf("AUTO", "ALWAYS", "WHEN_NEEDED", "OFF"),
                selected = visionMode,
                onSelect = { visionMode = it; scope.launch { settingsRepository.setVisionMode(it) } }
            )

            // Screenshot Resolution chips
            SettingsChipGroupCard(
                label = "Screenshot Resolution",
                options = listOf("512", "768", "1024", "1536").map { "${it}px" },
                optionsRaw = listOf(512, 768, 1024, 1536),
                selected = "${screenshotResolution}px",
                onSelect = { label ->
                    val res = label.removeSuffix("px").toIntOrNull() ?: 1024
                    screenshotResolution = res
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Safety ──
            SectionHeader(label = "SAFETY")

            SettingsRadioCard(
                label = "Confirmation Policy",
                options = mapOf(
                    "SENSITIVE_ONLY" to "Sensitive actions only",
                    "ASK_EVERY_TIME" to "Ask every time",
                    "MANUAL_MODE" to "Manual mode"
                ),
                selected = confirmationPolicy,
                onSelect = { confirmationPolicy = it; scope.launch { settingsRepository.setConfirmationPolicy(it) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Data ──
            SectionHeader(label = "DATA")

            SettingsToggleCard(
                label = "Save Screenshots",
                description = if (saveScreenshots) "Screenshots may contain sensitive information." else null,
                descriptionColor = AppColors.WarningAmber,
                checked = saveScreenshots,
                onCheckedChange = { saveScreenshots = it; scope.launch { settingsRepository.setSaveScreenshots(it) } }
            )

            SettingsToggleCard(
                label = "Debug Logging",
                checked = debugLogging,
                onCheckedChange = { debugLogging = it; scope.launch { settingsRepository.setDebugLogging(it) } }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.TextMuted,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings text field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    isMono: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = onTogglePassword, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (isMono) AppColors.TextPrimary else AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedBorderColor = AppColors.AccentBlue,
                unfocusedBorderColor = AppColors.SurfaceBorder,
                cursorColor = AppColors.AccentBlue
            ),
            textStyle = if (isMono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                       else MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings slider card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSliderCard(
    label: String,
    value: String,
    valueFloat: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.AccentBlue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = valueFloat,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.AccentBlue,
                activeTrackColor = AppColors.AccentBlue,
                inactiveTrackColor = AppColors.SurfaceBorder
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings chip group card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsChipGroupCard(
    label: String,
    options: List<String>,
    optionsRaw: List<Int>? = null,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                val isSelected = selected == opt
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(opt) },
                    label = {
                        Text(
                            opt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.AccentBlue.copy(alpha = 0.15f),
                        selectedLabelColor = AppColors.AccentBlue,
                        containerColor = AppColors.Background,
                        labelColor = AppColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = AppColors.SurfaceBorder,
                        selectedBorderColor = AppColors.AccentBlue.copy(alpha = 0.4f),
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings radio card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRadioCard(
    label: String,
    options: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        options.forEach { (key, displayLabel) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = AppColors.SurfaceBorder)
                    ) {
                        onSelect(key)
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == key,
                    onClick = { onSelect(key) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = AppColors.AccentBlue,
                        unselectedColor = AppColors.SurfaceBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected == key) AppColors.TextPrimary else AppColors.TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings toggle card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleCard(
    label: String,
    description: String? = null,
    descriptionColor: Color = AppColors.TextSecondary,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = AppColors.SurfaceBorder)
            ) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = descriptionColor,
                        lineHeight = 16.sp
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AppColors.AccentBlue,
                    uncheckedTrackColor = AppColors.SurfaceBorder,
                    checkedThumbColor = Color.White,
                    uncheckedThumbColor = AppColors.TextSecondary
                )
            )
        }
    }
}
