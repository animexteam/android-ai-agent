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


class ShellCommandTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val command = args["command"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'command' is required"))
        val timeoutMs = args["timeout"]?.jsonPrimitive?.content?.toLongOrNull() ?: 10000L
        val dangerousPrefixes = listOf("rm -rf /", "format ", "dd if=")
        for (prefix in dangerousPrefixes) {
            if (command.contains(prefix))
                return ToolResult(success = false, toolName = TOOL_NAME,
                    error = ToolError(code = "DANGEROUS_COMMAND", message = "Command blocked for safety: $prefix"))
        }
        return try {
            withContext(Dispatchers.IO) {
                val proc = ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText().take(10000)
                val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "TIMEOUT", message = "Command timed out after ${timeoutMs}ms"))
                } else {
                    ToolResult(
                        success = true, toolName = TOOL_NAME,
                        result = buildJsonObject {
                            put("exit_code", proc.exitValue())
                            put("output", output)
                        },
                        observationRequired = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.shell"
        private const val TAG = "ShellCommandTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Execute a shell command. Use for custom operations not covered by other tools. Dangerous commands are blocked.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("command", buildJsonObject { put("type", "string"); put("description", "Shell command to execute") })
                    put("timeout", buildJsonObject { put("type", "integer"); put("description", "Timeout in ms (default 10000)") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("command")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "SHELL_FAILED", message = e.message ?: "Unknown error")
    )
}
