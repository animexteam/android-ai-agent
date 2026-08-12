package com.androidagent.aiagent.tools.android

import android.content.Context
import android.content.Intent
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

class LaunchAppTool : ToolHandler {

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

            val context: Context = service
            val packageName = args["package"]?.toString()?.removeSurrounding("\"")
            val appName = args["app_name"]?.toString()?.removeSurrounding("\"")

            val resolvedPackage = when {
                !packageName.isNullOrBlank() -> {
                    launchByPackageName(context, packageName)
                }
                !appName.isNullOrBlank() -> {
                    launchByAppName(context, appName)
                }
                else -> {
                    return ToolResult(
                        success = false,
                        toolName = TOOL_NAME,
                        error = ToolError(
                            code = "INVALID_INPUT",
                            message = "Either 'package' or 'app_name' must be provided"
                        )
                    )
                }
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("package", resolvedPackage)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "LAUNCH_FAILED",
                    message = "Failed to launch app: ${e.message}"
                )
            )
        }
    }

    private fun launchByPackageName(context: Context, packageName: String): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            throw IllegalArgumentException(
                "Cannot resolve launch intent for package: $packageName. " +
                "Ensure the app is installed and has a launcher activity."
            )
        }

        context.startActivity(intent)
        return packageName
    }

    private fun launchByAppName(context: Context, appName: String): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)

        val normalizedAppName = appName.lowercase().trim()

        // First try exact match
        val exactMatch = resolveInfos.firstOrNull {
            it.loadLabel(context.packageManager).toString().lowercase() == normalizedAppName
        }
        if (exactMatch != null) {
            val pkg = exactMatch.activityInfo.packageName
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                setPackage(pkg)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(launchIntent)
            return pkg
        }

        // Then try contains match
        val containsMatch = resolveInfos.firstOrNull {
            it.loadLabel(context.packageManager).toString().lowercase().contains(normalizedAppName)
        }
        if (containsMatch != null) {
            val pkg = containsMatch.activityInfo.packageName
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                setPackage(pkg)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(launchIntent)
            return pkg
        }

        throw IllegalArgumentException(
            "No app found with name '$appName'. " +
            "Available apps: ${resolveInfos.map { it.loadLabel(context.packageManager) }}"
        )
    }

    companion object {
        private const val TOOL_NAME = "android.launch_app"
        private const val TAG = "LaunchAppTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Launches an Android application by its package name or app name. " +
                "If 'package' is provided, launches directly. If 'app_name' is provided, " +
                "searches installed apps for a matching label.",
            inputSchema = buildJsonObject {
                put("type", "object")
                addJsonObject("properties") {
                    addJsonObject("package") {
                        put("type", "string")
                        put("description", "Android package name to launch (e.g., com.android.chrome)")
                    }
                    addJsonObject("app_name") {
                        put("type", "string")
                        put("description", "Human-readable app name to search for (e.g., Chrome, Settings)")
                    }
                }
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
