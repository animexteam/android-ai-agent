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

import android.os.VibrationEffect

class VibrateTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val durationMs = args["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 200L
        val pattern = args["pattern"]?.jsonPrimitive?.content
        return try {
            val vibrator = service.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                ?: return ToolResult(success = false, toolName = TOOL_NAME,
                    error = ToolError(code = "NO_VIBRATOR", message = "Device has no vibrator"))
            withContext(Dispatchers.IO) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (pattern != null) {
                        val timings = pattern.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                        if (timings.size >= 2) {
                            val effect = VibrationEffect.createWaveform(timings, -1)
                            vibrator.vibrate(effect)
                        } else {
                            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(durationMs)
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("duration_ms", durationMs); put("action", "vibrated") },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate failed", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.vibrate"
        private const val TAG = "VibrateTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Vibrate the device. Specify duration in ms or a pattern like '100,50,100'.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("duration", buildJsonObject { put("type", "integer"); put("description", "Duration in ms (default 200)") })
                    put("pattern", buildJsonObject { put("type", "string"); put("description", "Vibration pattern: comma-separated ms values e.g. '100,50,100'") })
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
        error = ToolError(code = "VIBRATE_FAILED", message = e.message ?: "Unknown error")
    )
}
