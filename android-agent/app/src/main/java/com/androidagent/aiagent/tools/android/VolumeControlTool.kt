package com.androidagent.aiagent.tools.android

import android.content.Context
import android.media.AudioManager
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VolumeControlTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val stream = args["stream"]?.toString()?.removeSurrounding("\"")?.lowercase() ?: "music"
        val level = args["level"]?.toString()?.removeSurrounding("\"")?.toIntOrNull()
        val direction = args["direction"]?.toString()?.removeSurrounding("\"")?.lowercase()
        val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "NO_AM", message = "AudioManager not available"))
        val streamType = when {
            stream.contains("alarm") -> AudioManager.STREAM_ALARM
            stream.contains("ring") || stream.contains("call") -> AudioManager.STREAM_RING
            stream.contains("notif") -> AudioManager.STREAM_NOTIFICATION
            stream.contains("system") -> AudioManager.STREAM_SYSTEM
            stream.contains("voice") -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        return when {
            level != null -> {
                am.setStreamVolume(streamType, level.coerceIn(0, am.getStreamMaxVolume(streamType)), 0)
                ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("level", level); put("stream", stream) })
            }
            direction == "up" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, 0)
                ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("direction", "up") })
            }
            direction == "down" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, 0)
                ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("direction", "down") })
            }
            direction == "mute" || direction == "silence" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, 0)
                ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("action", "muted") })
            }
            else -> ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "Provide 'level' (int) or 'direction' (up/down/mute)"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.volume"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Control volume. Provide 'direction' (up/down/mute) OR 'level' (0-max). Optional 'stream' (music/ring/alarm/notification/system, default music).",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("direction", buildJsonObject { put("type", "string"); put("description", "up, down, or mute") })
                put("level", buildJsonObject { put("type", "integer"); put("description", "Absolute volume level") })
                put("stream", buildJsonObject { put("type", "string"); put("description", "music, ring, alarm, notification, system") })
            })}, riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}
