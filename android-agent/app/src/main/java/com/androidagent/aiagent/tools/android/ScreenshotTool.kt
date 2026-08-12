package com.androidagent.aiagent.tools.android

import android.graphics.Bitmap
import android.util.Base64
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

@Suppress("DEPRECATION")
class ScreenshotTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val service = AndroidAgentAccessibilityService.instance
                ?: return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "SERVICE_NOT_CONNECTED",
                        message = "Accessibility service is not connected"
                    )
                )

            val screenshot = withContext(Dispatchers.IO) {
                service.takeScreenshot()
            } ?: return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "SCREENSHOT_FAILED",
                    message = "takeScreenshot() returned null. The device may not support this API."
                )
            )

            val base64String = withContext(Dispatchers.IO) {
                bitmapToBase64Jpeg(screenshot, QUALITY, MAX_WIDTH)
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("base64", base64String)
                    put("format", "image/jpeg")
                    put("quality", QUALITY)
                },
                observationRequired = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take screenshot", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "SCREENSHOT_FAILED",
                    message = "Failed to take screenshot: ${e.message}"
                )
            )
        }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int, maxWidth: Int): String {
        val scaledBitmap = if (bitmap.width > maxWidth) {
            val scale = maxWidth.toFloat() / bitmap.width.toFloat()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        internal const val TOOL_NAME = "android.screenshot"
        private const val TAG = "ScreenshotTool"
        private const val QUALITY = 50
        private const val MAX_WIDTH = 1024

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Takes a screenshot of the current screen and returns it as a base64-encoded " +
                "JPEG image. The image is resized to a maximum width of 1024px and compressed " +
                "at 50% quality to reduce size.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    // No required inputs
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
