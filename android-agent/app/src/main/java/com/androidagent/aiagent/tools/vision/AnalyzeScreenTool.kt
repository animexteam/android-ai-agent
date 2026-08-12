package com.androidagent.aiagent.tools.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.ai.VisionAnalyzer
import com.androidagent.aiagent.ai.VisionAnalyzer.VisionObservation
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


class AnalyzeScreenTool(private val visionAnalyzer: VisionAnalyzer) : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance
        if (service == null || !AndroidAgentAccessibilityService.isConnected) {
            return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "SERVICE_NOT_CONNECTED",
                    message = "Accessibility service is not connected."
                )
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val screenshot = service.takeScreenshot()
                if (screenshot == null) {
                    return@withContext ToolResult(
                        success = false,
                        toolName = TOOL_NAME,
                        error = ToolError(
                            code = "SCREENSHOT_FAILED",
                            message = "Could not take screenshot. Device may not support this API (requires Android 14+)."
                        ),
                        observationRequired = false
                    )
                }

                val outputStream = java.io.ByteArrayOutputStream()
                var scaled = screenshot
                if (screenshot.width > 1024) {
                    val scale = 1024f / screenshot.width
                    scaled = Bitmap.createScaledBitmap(
                        screenshot, 1024,
                        (screenshot.height * scale).toInt(), true
                    )
                    if (scaled != screenshot) screenshot.recycle()
                }
                scaled?.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                scaled?.recycle()
                outputStream.close()

                val query = args["query"]?.toString()?.removeSurrounding("\"")
                    ?: "Describe the current screen content, visible UI elements, text, buttons, and their approximate positions."

                val observation: VisionObservation = visionAnalyzer.analyzeScreen(base64, query)

                val elementsJson = kotlinx.serialization.json.buildJsonArray {
                    observation.elements.forEach { element ->
                        add(buildJsonObject {
                            put("description", element.description)
                            element.x?.let { put("x", it) }
                            element.y?.let { put("y", it) }
                            put("confidence", element.confidence)
                        })
                    }
                }

                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("description", observation.description)
                        put("elements", elementsJson)
                    },
                    observationRequired = false
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "VISION_ERROR",
                        message = e.message ?: "Unknown vision analysis error."
                    ),
                    observationRequired = false
                )
            }
        }
    }

    companion object {
        const val TOOL_NAME = "vision.analyze_screen"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Take a screenshot and analyze the visual content of the current screen. Returns a description of visible UI elements, text, buttons, icons, and their approximate positions. Useful when accessibility information is insufficient or for visually identifying elements.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("query", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "Optional specific question about the screen content")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE
        )
    }
}
