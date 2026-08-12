package com.androidagent.aiagent.agent

import com.androidagent.aiagent.tools.ToolResult

enum class AgentStatus {
    IDLE, THINKING, EXECUTING, WAITING_FOR_USER, WAITING_FOR_CONFIRMATION,
    VERIFYING, COMPLETED, FAILED, CANCELLED
}

data class AndroidObservation(
    val id: String,
    val packageName: String?,
    val activityName: String?,
    val windowTitle: String?,
    val uiTree: List<UiNode>,
    val screenshotBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class UiNode(
    val nodeId: String,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isFocusable: Boolean = false,
    val isEnabled: Boolean = true,
    val bounds: Rect = Rect(0, 0, 0, 0),
    val parentId: String? = null,
    val childIds: List<String> = emptyList(),
    val depth: Int = 0
)

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width == 0 && height == 0
}

sealed class AgentEvent {
    abstract val timestamp: Long
    abstract val stepNumber: Int

    data class ToolExecution(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val toolName: String,
        val arguments: String,
        val result: ToolResult
    ) : AgentEvent()

    data class Observation(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val summary: String
    ) : AgentEvent()

    data class ModelResponse(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val decisionType: String,
        val content: String
    ) : AgentEvent()

    data class UserMessage(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val text: String
    ) : AgentEvent()

    data class StatusChange(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val from: AgentStatus,
        val to: AgentStatus
    ) : AgentEvent()

    data class Error(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val message: String
    ) : AgentEvent()
}

data class AgentState(
    val goal: String = "",
    val status: AgentStatus = AgentStatus.IDLE,
    val stepNumber: Int = 0,
    val maxSteps: Int = 50,
    val currentPackage: String? = null,
    val lastObservation: AndroidObservation? = null,
    val history: List<AgentEvent> = emptyList(),
    val currentObservationId: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val pendingQuestion: String? = null,
    val modelLatencyMs: Long = 0,
    val lastError: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null
) {
    val isRunning: Boolean get() = status in listOf(
        AgentStatus.THINKING, AgentStatus.EXECUTING,
        AgentStatus.VERIFYING, AgentStatus.WAITING_FOR_USER,
        AgentStatus.WAITING_FOR_CONFIRMATION
    )

    val durationMs: Long? get() = if (startTime != null) {
        (endTime ?: System.currentTimeMillis()) - startTime
    } else null
}

data class PendingConfirmation(
    val toolName: String,
    val arguments: String,
    val reason: String,
    val stepNumber: Int
)
