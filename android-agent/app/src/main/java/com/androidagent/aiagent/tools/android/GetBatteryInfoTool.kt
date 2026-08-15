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
import android.content.IntentFilter
import android.os.BatteryManager

class GetBatteryInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val bm = service.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager
                val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val isCharging = bm?.isCharging ?: false
                val chargeType = when {
                    (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0) > 0 -> "plugged"
                    else -> "unknown"
                }
                val batteryIntent = service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temperature = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0 ?: 0.0
                val voltage = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0 ?: 0.0
                val health = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    else -> "unknown"
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("level", level)
                        put("is_charging", isCharging)
                        put("health", health)
                        put("temperature_c", temperature)
                        put("voltage_v", voltage)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery info", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_battery_info"
        private const val TAG = "GetBatteryInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get battery level, charging status, health, temperature, and voltage.",
            inputSchema = buildJsonObject { put("type", "object") },
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
        error = ToolError(code = "BATTERY_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
