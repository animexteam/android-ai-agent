package com.androidagent.aiagent.safety

import kotlinx.serialization.json.JsonObject

enum class SafetyCheckResult {
    ALLOWED,
    REQUIRES_CONFIRMATION,
    BLOCKED
}

class SafetyController(private val confirmationManager: ConfirmationManager) {

    var lastReason: String? = null
        private set

    private val blockedTools: MutableSet<String> = mutableSetOf(
        "android.install_app",
        "android.uninstall_app",
        "android.grant_permission",
        "android.revoke_permission",
        "android.factory_reset",
        "android.enable_developer_options",
        "android.enable_usb_debugging",
        "system.install_apk",
        "system.root_device",
        "system.flash_firmware"
    )

    companion object {
        private val SAFE_TOOLS = setOf(
            "android.inspect_screen",
            "android.screenshot",
            "android.find",
            "android.click",
            "android.scroll",
            "android.swipe",
            "android.type_text",
            "android.clear_text",
            "android.back",
            "android.home",
            "android.recents",
            "android.wait",
            "android.press_key",
            "vision.analyze_screen",
            "vision.find_visual_target",
            "agent.finish",
            "agent.stop",
            "agent.ask_user"
        )

        private val SENSITIVE_KEYWORDS = listOf(
            "send", "post", "delete", "share", "purchase",
            "payment", "account", "password", "login", "logout"
        )

        private val BLOCKED_KEYWORDS = listOf(
            "install", "uninstall", "grant_permission", "revoke_permission",
            "factory_reset", "developer_options", "usb_debugging",
            "root", "flash_firmware", "su ", "superuser"
        )
    }

    fun checkToolCall(toolName: String, args: JsonObject): SafetyCheckResult {
        lastReason = null

        val normalizedToolName = toolName.lowercase()

        // Check explicitly blocked tools first
        if (normalizedToolName in blockedTools.map { it.lowercase() }) {
            lastReason = "Tool '$toolName' is blocked for safety reasons."
            return SafetyCheckResult.BLOCKED
        }

        // Check for blocked keywords in tool name and args
        val argsString = args.toString().lowercase()
        for (keyword in BLOCKED_KEYWORDS) {
            if (normalizedToolName.contains(keyword) || argsString.contains(keyword)) {
                lastReason = "Tool '$toolName' contains blocked keyword '$keyword'. Action is not permitted."
                return SafetyCheckResult.BLOCKED
            }
        }

        // Determine risk level
        val riskLevel = determineRiskLevel(normalizedToolName, argsString)

        return when (riskLevel) {
            RiskLevel.SAFE -> {
                if (confirmationManager.needsConfirmation(toolName, riskLevel)) {
                    lastReason = "Confirmation required for tool '$toolName' under current policy."
                    SafetyCheckResult.REQUIRES_CONFIRMATION
                } else {
                    SafetyCheckResult.ALLOWED
                }
            }
            RiskLevel.CONFIRM -> {
                lastReason = "Tool '$toolName' involves a sensitive operation and requires confirmation."
                if (confirmationManager.needsConfirmation(toolName, riskLevel)) {
                    SafetyCheckResult.REQUIRES_CONFIRMATION
                } else {
                    SafetyCheckResult.ALLOWED
                }
            }
            RiskLevel.BLOCKED -> {
                lastReason = "Tool '$toolName' is blocked for safety reasons."
                SafetyCheckResult.BLOCKED
            }
        }
    }

    fun addBlockedTool(toolName: String) {
        blockedTools.add(toolName.lowercase())
    }

    fun removeBlockedTool(toolName: String) {
        blockedTools.remove(toolName.lowercase())
    }

    private fun determineRiskLevel(toolName: String, argsString: String): RiskLevel {
        // Check explicitly safe tools first
        if (toolName in SAFE_TOOLS.map { it.lowercase() }) {
            return RiskLevel.SAFE
        }

        // Check for sensitive keywords in tool name
        for (keyword in SENSITIVE_KEYWORDS) {
            if (toolName.contains(keyword)) {
                return RiskLevel.CONFIRM
            }
        }

        // Check for sensitive keywords in args
        for (keyword in SENSITIVE_KEYWORDS) {
            if (argsString.contains(keyword)) {
                return RiskLevel.CONFIRM
            }
        }

        // Default to safe for unknown tools (conservative but not overly restrictive)
        return RiskLevel.SAFE
    }
}
