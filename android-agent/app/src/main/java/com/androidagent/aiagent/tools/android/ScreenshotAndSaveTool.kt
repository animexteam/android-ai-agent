package com.androidagent.aiagent.tools.android

import android.os.Environment
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotAndSaveTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance
            ?: return noService()

        return try {
            // Take screenshot via accessibility
            val bitmap = com.androidagent.aiagent.ai.VisionAnalyzer.takeScreenshot(service)
                ?: return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "SCREENSHOT_FAILED", message = "Failed to capture screenshot")
                )

            // Save to Pictures/AndroidAgent
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "AndroidAgent"
            )
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = args["filename"]?.jsonPrimitive?.content ?: "screenshot_$timestamp.png"
            val safeName = if (filename.endsWith(".png", ignoreCase = true)) filename else "$filename.png"
            val file = File(dir, safeName)

            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("path", file.absolutePath)
                    put("filename", safeName)
                    put("size_bytes", file.length())
                },
                observationRequired = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "SAVE_FAILED", message = "Failed to save screenshot: ${e.message}")
            )
        }
    }

    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.screenshot_save"
        private const val TAG = "ScreenshotAndSaveTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Take a screenshot and save it to device storage (Pictures/AndroidAgent/). Optionally specify a filename.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("filename", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional filename (without extension). Auto-generates timestamped name if omitted.")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}