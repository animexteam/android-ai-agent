package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import android.content.Intent
import android.provider.Settings

class OpenSettingsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val page = args["page"]?.jsonPrimitive?.content
        return try {
            val intent = when (page?.lowercase()) {
                "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "display", "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                "sound", "volume", "audio" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                "apps", "applications" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                "location", "gps" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                "date", "time" -> Intent(Settings.ACTION_DATE_SETTINGS)
                "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "network", "data_usage" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                "about" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                "nfc" -> Intent(Settings.ACTION_NFC_SETTINGS)
                "default_apps" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                "special_access" -> Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
                "locale", "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("page", page ?: "main"); put("action", "opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.open_settings"
        private const val TAG = "OpenSettingsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open a specific Android Settings page. Pages: wifi, bluetooth, display, sound, battery, storage, apps, notifications, security, location, date, network, about, airplane, nfc, accessibility, language.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("page", buildJsonObject {
                        put("type", "string")
                        put("description", "Settings page name (wifi, bluetooth, display, sound, battery, storage, apps, notifications, security, location, etc.)")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "OPEN_SETTINGS_FAILED", message = e.message ?: "Unknown error")
    )
}
