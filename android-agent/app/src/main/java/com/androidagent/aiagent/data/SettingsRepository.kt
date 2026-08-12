package com.androidagent.aiagent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_settings")

class SettingsRepository(
    private val context: Context,
    private val secureStorage: SecureStorage
) {
    companion object {
        private val ENDPOINT_KEY = stringPreferencesKey("endpoint")
        private val MODEL_KEY = stringPreferencesKey("model")
        private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
        private val MAX_STEPS_KEY = intPreferencesKey("max_steps")
        private val TIMEOUT_KEY = longPreferencesKey("timeout_ms")
        private val VISION_MODE_KEY = stringPreferencesKey("vision_mode")
        private val CONFIRMATION_POLICY_KEY = stringPreferencesKey("confirmation_policy")
        private val SAVE_SCREENSHOTS_KEY = booleanPreferencesKey("save_screenshots")
        private val DEBUG_LOGGING_KEY = booleanPreferencesKey("debug_logging")
        private val SCREENSHOT_RESOLUTION_KEY = intPreferencesKey("screenshot_resolution")

        const val DEFAULT_ENDPOINT = "https://ollama.com/api/chat"
        const val DEFAULT_MODEL = "gemma4:31b"
        const val DEFAULT_TEMPERATURE = 0.3f
        const val DEFAULT_MAX_STEPS = 50
        const val DEFAULT_TIMEOUT_MS = 120000L
        const val DEFAULT_VISION_MODE = "AUTO"
        const val DEFAULT_CONFIRMATION_POLICY = "SENSITIVE_ONLY"
        const val DEFAULT_SAVE_SCREENSHOTS = false
        const val DEFAULT_DEBUG_LOGGING = false
        const val DEFAULT_SCREENSHOT_RESOLUTION = 1024
    }

    // ---- API Key (stored in SecureStorage) ----

    suspend fun apiKey(): String {
        return secureStorage.getString(SecureStorage.KEY_API_KEY) ?: ""
    }

    suspend fun setApiKey(key: String) {
        secureStorage.putString(SecureStorage.KEY_API_KEY, key)
    }

    // ---- Endpoint ----

    suspend fun endpoint(): String {
        return context.dataStore.data.first()[ENDPOINT_KEY] ?: DEFAULT_ENDPOINT
    }

    suspend fun setEndpoint(value: String) {
        context.dataStore.edit { preferences ->
            preferences[ENDPOINT_KEY] = value
        }
    }

    // ---- Model ----

    suspend fun model(): String {
        return context.dataStore.data.first()[MODEL_KEY] ?: DEFAULT_MODEL
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_KEY] = value
        }
    }

    // ---- Temperature ----

    suspend fun temperature(): Float {
        return context.dataStore.data.first()[TEMPERATURE_KEY] ?: DEFAULT_TEMPERATURE
    }

    suspend fun setTemperature(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE_KEY] = value
        }
    }

    // ---- Max Steps ----

    suspend fun maxSteps(): Int {
        return context.dataStore.data.first()[MAX_STEPS_KEY] ?: DEFAULT_MAX_STEPS
    }

    suspend fun setMaxSteps(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_STEPS_KEY] = value
        }
    }

    // ---- Timeout ----

    suspend fun timeout(): Long {
        return context.dataStore.data.first()[TIMEOUT_KEY] ?: DEFAULT_TIMEOUT_MS
    }

    suspend fun setTimeout(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[TIMEOUT_KEY] = value
        }
    }

    // ---- Vision Mode ----

    suspend fun visionMode(): String {
        return context.dataStore.data.first()[VISION_MODE_KEY] ?: DEFAULT_VISION_MODE
    }

    suspend fun setVisionMode(value: String) {
        context.dataStore.edit { preferences ->
            preferences[VISION_MODE_KEY] = value
        }
    }

    // ---- Confirmation Policy ----

    suspend fun confirmationPolicy(): String {
        return context.dataStore.data.first()[CONFIRMATION_POLICY_KEY] ?: DEFAULT_CONFIRMATION_POLICY
    }

    suspend fun setConfirmationPolicy(value: String) {
        context.dataStore.edit { preferences ->
            preferences[CONFIRMATION_POLICY_KEY] = value
        }
    }

    // ---- Save Screenshots ----

    suspend fun saveScreenshots(): Boolean {
        return context.dataStore.data.first()[SAVE_SCREENSHOTS_KEY] ?: DEFAULT_SAVE_SCREENSHOTS
    }

    suspend fun setSaveScreenshots(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SAVE_SCREENSHOTS_KEY] = value
        }
    }

    // ---- Debug Logging ----

    suspend fun debugLogging(): Boolean {
        return context.dataStore.data.first()[DEBUG_LOGGING_KEY] ?: DEFAULT_DEBUG_LOGGING
    }

    suspend fun setDebugLogging(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEBUG_LOGGING_KEY] = value
        }
    }

    // ---- Screenshot Resolution ----

    suspend fun screenshotResolution(): Int {
        return context.dataStore.data.first()[SCREENSHOT_RESOLUTION_KEY] ?: DEFAULT_SCREENSHOT_RESOLUTION
    }

    suspend fun setScreenshotResolution(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[SCREENSHOT_RESOLUTION_KEY] = value
        }
    }

    // ---- Clear All ----

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        secureStorage.remove(SecureStorage.KEY_API_KEY)
    }
}