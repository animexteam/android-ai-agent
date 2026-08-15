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

import android.location.Location
import android.location.LocationManager

class GetLocationTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val lm = service.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
                var bestLocation: Location? = null
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                    try {
                        if (lm?.isProviderEnabled(provider) == true) {
                            val loc = lm.getLastKnownLocation(provider)
                            if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                                bestLocation = loc
                            }
                        }
                    } catch (_: SecurityException) { /* no permission */ }
                }
                if (bestLocation != null) {
                    ToolResult(
                        success = true, toolName = TOOL_NAME,
                        result = buildJsonObject {
                            put("latitude", bestLocation.latitude)
                            put("longitude", bestLocation.longitude)
                            put("accuracy_m", bestLocation.accuracy)
                            put("provider", bestLocation.provider ?: "unknown")
                        },
                        observationRequired = false
                    )
                } else {
                    ToolResult(
                        success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "NO_LOCATION", message = "No location available. Enable GPS or check permissions.")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_location"
        private const val TAG = "GetLocationTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get the device's last known GPS/location coordinates.",
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
        error = ToolError(code = "LOCATION_FAILED", message = e.message ?: "Unknown error")
    )
}
