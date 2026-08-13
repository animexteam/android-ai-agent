package com.androidagent.aiagent.agent

import com.androidagent.aiagent.tools.ToolResult

// ============================================================================
// Agent lifecycle statuses
// ============================================================================

enum class AgentStatus {
    /** No task is active. */
    IDLE,
    /** The agent is waiting for the model to respond. */
    THINKING,
    /** A tool is being executed on the device. */
    EXECUTING,
    /** The agent paused to ask the user a question. */
    WAITING_FOR_USER,
    /** The agent paused because a tool requires safety confirmation. */
    WAITING_FOR_CONFIRMATION,
    /** The agent is verifying the result of an action. */
    VERIFYING,
    /** The task completed successfully. */
    COMPLETED,
    /** The task failed (max steps, unrecoverable error, etc.). */
    FAILED,
    /** The user cancelled the task. */
    CANCELLED;

    /** True when the agent loop should still be running. */
    val isActive: Boolean
        get() = this in setOf(THINKING, EXECUTING, VERIFYING, WAITING_FOR_USER, WAITING_FOR_CONFIRMATION)
}

// ============================================================================
// UI tree data classes
// ============================================================================

/** A snapshot of one node in the Android accessibility tree. */
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

/** Axis-aligned rectangle with convenience accessors. */
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

// ============================================================================
// Observation – a single screen capture (tree + optional screenshot)
// ============================================================================

/**
 * A full snapshot of the current screen state observed via the
 * accessibility service.
 *
 * @property id Unique identifier for this observation.  Node IDs are
 *   only valid within this specific observation.
 */
data class AndroidObservation(
    val id: String,
    val packageName: String?,
    val activityName: String?,
    val windowTitle: String?,
    val uiTree: List<UiNode>,
    val screenshotBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ============================================================================
// Event history – immutable records of everything that happened
// ============================================================================

/**
 * A single immutable event in the agent's execution timeline.
 *
 * Each subclass records one type of occurrence (tool call, observation,
 * model response, user message, status change, or error).  The
 * [stepNumber] field ties events to the agent's step counter so that
 * the UI can display a chronological timeline.
 */
sealed class AgentEvent {
    abstract val timestamp: Long
    abstract val stepNumber: Int

    /** A tool was executed and returned a result. */
    data class ToolExecution(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val toolName: String,
        val arguments: String,
        val result: ToolResult
    ) : AgentEvent()

    /** The agent observed the screen (accessibility tree + optional screenshot). */
    data class Observation(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val summary: String
    ) : AgentEvent()

    /** The model returned a decision (tool_call, message, finish, …). */
    data class ModelResponse(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val decisionType: String,
        val content: String
    ) : AgentEvent()

    /** The user replied to an [ask_user] pause. */
    data class UserMessage(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val text: String
    ) : AgentEvent()

    /** The agent's status changed (e.g. THINKING → EXECUTING). */
    data class StatusChange(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val from: AgentStatus,
        val to: AgentStatus
    ) : AgentEvent()

    /** An error occurred during a step. */
    data class Error(
        override val timestamp: Long = System.currentTimeMillis(),
        override val stepNumber: Int,
        val message: String
    ) : AgentEvent()
}

// ============================================================================
// Top-level agent state – the single source of truth for the UI
// ============================================================================

/**
 * Holds the full mutable state of the running agent.
 *
 * This is exposed as a `StateFlow<AgentState>` from [AgentRuntime] and
 * observed by the Compose UI.  Every field is a `val` so that the state
 * can only be updated by replacing the entire object via `copy()`,
 * ensuring thread-safe reads from the UI thread.
 */
data class AgentState(
    val goal: String = "",
    val status: AgentStatus = AgentStatus.IDLE,
    val stepNumber: Int = 0,
    val maxSteps: Int = 50,
    val currentPackage: String? = null,
    val lastObservation: AndroidObservation? = null,
    /** Bounded event history – oldest events are trimmed first. */
    val history: List<AgentEvent> = emptyList(),
    val currentObservationId: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val pendingQuestion: String? = null,
    val modelLatencyMs: Long = 0,
    val lastError: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null
) {
    /** True when the agent loop is still actively running. */
    val isRunning: Boolean get() = status.isActive

    /** Milliseconds elapsed since the task started, or null if not started. */
    val durationMs: Long? get() = if (startTime != null) {
        (endTime ?: System.currentTimeMillis()) - startTime
    } else null

    companion object {
        /** Maximum number of history events retained. */
        const val MAX_HISTORY_SIZE = 200
    }

    /**
     * Append an event, trimming the oldest events if the history exceeds
     * [MAX_HISTORY_SIZE].
     */
    fun withEvent(event: AgentEvent): AgentState {
        val updated = history + event
        val trimmed = if (updated.size > MAX_HISTORY_SIZE) {
            updated.drop(updated.size - MAX_HISTORY_SIZE)
        } else {
            updated
        }
        return copy(history = trimmed)
    }
}

/** A tool call that is waiting for user safety-confirmation. */
data class PendingConfirmation(
    val toolName: String,
    val arguments: String,
    val reason: String,
    val stepNumber: Int
)