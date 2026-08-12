package com.androidagent.aiagent.tools.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.ai.VisionAnalyzer.VisualTargetResult
import com.androidagent.aiagent.ai.VisionAnalyzer
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


class FindVisualTargetTool(
    private val visionAnalyzer: VisionAnalyzer
) : ToolHandler {

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

        val description = args["description"]?.jsonPrimitive?.content
        if (description.isNullOrBlank()) {
            return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "MISSING_ARGUMENT",
                    message = "'description' is required."
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
                            message = "Could not take screenshot."
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

                val displayMetrics = service.resources?.displayMetrics
                    ?: android.util.DisplayMetrics().also {
                        @Suppress("DEPRECATION")
                        android.view.WindowManager.LayoutParams().let { _ -> }
                    }
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                val target: VisualTargetResult = visionAnalyzer.findVisualTarget(
                    base64, description, screenWidth, screenHeight
                )

                ToolResult(
                    success = target.found,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("found", target.found)
                        target.x?.let { put("x", it) }
                        target.y?.let { put("y", it) }
                        put("confidence", target.confidence)
                        put("description", target.description)
                    },
                    error = if (!target.found) ToolError(
                        code = "TARGET_NOT_FOUND",
                        message = "Visual target not found: $description"
                    ) else null,
                    observationRequired = false
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "VISION_ERROR",
                        message = e.message ?: "Unknown vision error."
                    ),
                    observationRequired = false
                )
            }
        }
    }

    companion object {
        const val TOOL_NAME = "vision.find_visual_target"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Find a visual target on the current screen by description. Returns coordinates (x, y) and confidence. Use as fallback when accessibility nodes cannot locate the target. Prefer accessibility-based finding when reliable.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("description", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "Description of the visual element to find, e.g. 'the first video thumbnail' or 'the red submit button'")
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray { add("description") })
            },
            riskLevel = RiskLevel.SAFE
        )
    }
}
