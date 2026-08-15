package com.androidagent.aiagent.tools.android

import android.net.wifi.WifiManager
import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ToggleWifiTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance
            ?: return noService()

        val enable = args["enable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: args["enable"]?.jsonPrimitive?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull()

        return if (enable != null) {
            try {
                val wm = service.applicationContext.getSystemService(WifiManager::class.java)
                val isCurrentlyEnabled = wm.isWifiEnabled
                // WifiManager.setWifiEnabled is deprecated but still works for automation agents
                @Suppress("DEPRECATION")
                wm.isWifiEnabled = enable
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("was_enabled", isCurrentlyEnabled)
                        put("now_enabled", enable)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle WiFi", e)
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "WIFI_TOGGLE_FAILED", message = "Failed to toggle WiFi: ${e.message}")
                )
            }
        } else {
            // Just return current state
            try {
                val wm = service.applicationContext.getSystemService(WifiManager::class.java)
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("wifi_enabled", wm.isWifiEnabled)
                    },
                    observationRequired = false
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "WIFI_CHECK_FAILED", message = "Failed to check WiFi: ${e.message}")
                )
            }
        }
    }

    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.toggle_wifi"
        private const val TAG = "ToggleWifiTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Toggle WiFi on/off or check current WiFi state. Pass enable=true to turn on, enable=false to turn off, or omit to check state.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("enable", buildJsonObject {
                        put("type", "boolean")
                        put("description", "true to enable WiFi, false to disable. Omit to just check current state.")
                    })
                })
            },
            riskLevel = RiskLevel.CONFIRM,
            requiresConfirmation = true
        )
    }
}