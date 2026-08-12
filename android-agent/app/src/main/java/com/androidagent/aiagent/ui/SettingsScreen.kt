package com.androidagent.aiagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    var loaded by remember { mutableStateOf(false) }

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
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ollama Configuration
            Text("Ollama Configuration", style = MaterialTheme.typography.titleMedium,
                color = AppColors.Primary)

            // API Key
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
                modifier = Modifier.fillMaxWidth()
            )

            // Endpoint
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; scope.launch { settingsRepository.setEndpoint(it) } },
                label = { Text("API Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Model
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; scope.launch { settingsRepository.setModel(it) } },
                label = { Text("Model Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Temperature
            Text("Temperature: ${"%.1f".format(temperature)}", style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary)
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
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Agent Configuration
            Text("Agent Configuration", style = MaterialTheme.typography.titleMedium,
                color = AppColors.Primary)

            // Max Steps
            Text("Max Agent Steps: $maxSteps", style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary)
            Slider(
                value = maxSteps.toFloat(),
                onValueChange = { maxSteps = it.toInt(); scope.launch { settingsRepository.setMaxSteps(it.toInt()) } },
                valueRange = 10f..100f,
                modifier = Modifier.fillMaxWidth()
            )

            // Vision Mode
            Text("Vision Mode", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "ALWAYS", "WHEN_NEEDED", "OFF").forEach { mode ->
                    FilterChip(
                        selected = visionMode == mode,
                        onClick = { visionMode = mode; scope.launch { settingsRepository.setVisionMode(mode) } },
                        label = { Text(mode) }
                    )
                }
            }

            // Screenshot Resolution
            Text("Screenshot Resolution", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(512, 768, 1024, 1536).forEach { res ->
                    FilterChip(
                        selected = screenshotResolution == res,
                        onClick = { screenshotResolution = res },
                        label = { Text("${res}px") }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Safety
            Text("Safety", style = MaterialTheme.typography.titleMedium,
                color = AppColors.Primary)

            Text("Confirmation Policy", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            listOf("SENSITIVE_ONLY", "ASK_EVERY_TIME", "MANUAL_MODE").forEach { policy ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = confirmationPolicy == policy,
                        onClick = { confirmationPolicy = policy; scope.launch { settingsRepository.setConfirmationPolicy(policy) } }
                    )
                    Text(when (policy) {
                        "SENSITIVE_ONLY" -> "Sensitive actions only"
                        "ASK_EVERY_TIME" -> "Ask every time"
                        else -> "Manual mode"
                    }, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Data
            Text("Data", style = MaterialTheme.typography.titleMedium,
                color = AppColors.Primary)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Save Screenshots", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = saveScreenshots, onCheckedChange = { saveScreenshots = it })
            }
            if (saveScreenshots) {
                Text("Screenshots may contain sensitive information.",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.Warning)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Debug Logging", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = debugLogging, onCheckedChange = { debugLogging = it })
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
