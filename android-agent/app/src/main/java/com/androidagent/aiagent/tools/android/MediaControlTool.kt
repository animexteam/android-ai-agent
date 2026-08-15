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
import android.view.KeyEvent

class MediaControlTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val action = args["action"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'action' is required: play, pause, next, previous, stop"))
        return try {
            withContext(Dispatchers.IO) {
                val keycode: Int = when (action.lowercase()) {
                    "play", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                    else -> return@withContext ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "INVALID_ACTION", message = "Unknown action: $action"))
                }
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keycode)
                service.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply { putExtra(Intent.EXTRA_KEY_EVENT, downEvent) })
                kotlinx.coroutines.delay(50)
                service.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply { putExtra(Intent.EXTRA_KEY_EVENT, upEvent) })
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("action", action.lowercase()); put("status", "sent") },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media control failed", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.media_control"
        private const val TAG = "MediaControlTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Control media playback: play, pause, next, previous, stop.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add(JsonPrimitive("play")); add(JsonPrimitive("pause")); add(JsonPrimitive("next")); add(JsonPrimitive("previous")); add(JsonPrimitive("stop")) })
                        put("description", "Media action to perform")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("action")) })
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
        error = ToolError(code = "MEDIA_CONTROL_FAILED", message = e.message ?: "Unknown error")
    )
}
