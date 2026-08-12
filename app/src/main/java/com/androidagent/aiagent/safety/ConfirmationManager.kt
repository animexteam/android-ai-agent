package com.androidagent.aiagent.safety

enum class ConfirmationPolicy {
    ASK_EVERY_TIME,
    SENSITIVE_ONLY,
    MANUAL_MODE
}

class ConfirmationManager(private var policy: ConfirmationPolicy = ConfirmationPolicy.SENSITIVE_ONLY) {

    fun needsConfirmation(toolName: String, riskLevel: RiskLevel): Boolean {
        return when (policy) {
            ConfirmationPolicy.ASK_EVERY_TIME -> true
            ConfirmationPolicy.SENSITIVE_ONLY -> riskLevel == RiskLevel.CONFIRM
            ConfirmationPolicy.MANUAL_MODE -> true
        }
    }

    fun updatePolicy(policy: ConfirmationPolicy) {
        this.policy = policy
    }
}