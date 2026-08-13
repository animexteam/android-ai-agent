package com.androidagent.aiagent.agent

import android.util.Log
import com.androidagent.aiagent.ai.GemmaClient
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.data.TaskRepository
import com.androidagent.aiagent.tools.*
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.tools.ToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The heart of the agent – runs the observe → reason → act → verify loop.
 *
 * Architecture:
 * - The runtime owns a single [MutableStateFlow] of [AgentState] that the
 *   UI observes.  All state mutations go through `copy()` / `withEvent()`.
 * - The main loop ([runAgentLoop]) is launched in a dedicated
 *   [SupervisorJob] + [Dispatchers.Main] scope so that the UI stays
 *   responsive.
 * - Prompt construction is **entirely delegated** to [AgentPromptBuilder].
 * - Tool name resolution (aliases, fuzzy match) is handled by
 *   [ToolExecutor.resolveToolName].
 * - Loop detection is delegated to [AgentLoopGuard].
 */
class AgentRuntime(
    private val gemmaClient: GemmaClient,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val accessibilityObserver: AccessibilityObserver,
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository? = null,
    private val promptBuilder: AgentPromptBuilder = AgentPromptBuilder(),
    private val loopGuard: AgentLoopGuard = AgentLoopGuard()
) {

    companion object {
        private const val TAG = "AgentRuntime"
        /** Maximum model call retries on transient errors. */
        private const val MAX_MODEL_RETRIES = 2
    }

    // -------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private var agentJob: Job? = null
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * System prompt is built once per task (not every turn) because it
     * contains the tool catalogue which doesn't change during a task.
     */
    private var cachedSystemPrompt: String? = null

    /**
     * Known tool names, refreshed when the system prompt is built.
     * Passed to [DecisionParser] so it can validate tool names.
     */
    private var knownToolNames: Set<String> = emptySet()

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    /** Start a new task, cancelling any running task first. */
    fun startTask(goal: String) {
        agentJob?.cancel()
        loopGuard.reset()
        cachedSystemPrompt = null

        _state.value = AgentState(
            goal = goal,
            status = AgentStatus.THINKING,
            stepNumber = 0,
            startTime = System.currentTimeMillis()
        )

        agentJob = agentScope.launch {
            runAgentLoop()
        }
    }

    /** Resume the agent after the user answered a question. */
    fun respondToUser(answer: String) {
        val currentState = _state.value
        if (currentState.status != AgentStatus.WAITING_FOR_USER) return

        val userEvent = AgentEvent.UserMessage(
            stepNumber = currentState.stepNumber,
            text = answer
        )

        _state.value = currentState
            .withEvent(userEvent)
            .copy(
                pendingQuestion = null,
                status = AgentStatus.THINKING
            )

        agentJob = agentScope.launch { runAgentLoop() }
    }

    /** Resume the agent after the user responded to a safety confirmation. */
    fun respondToConfirmation(confirmed: Boolean) {
        val currentState = _state.value
        if (currentState.status != AgentStatus.WAITING_FOR_CONFIRMATION) return
        val pending = currentState.pendingConfirmation ?: return

        if (confirmed) {
            agentJob = agentScope.launch {
                try {
                    val observationId = currentState.currentObservationId
                    val argsJson = try {
                        kotlinx.serialization.json.Json.parseToJsonElement(pending.arguments)
                    } catch (_: Exception) { null }
                    val args = argsJson as? kotlinx.serialization.json.JsonObject
                        ?: kotlinx.serialization.json.JsonObject(emptyMap())

                    val toolResult = withContext(Dispatchers.IO) {
                        toolExecutor.execute(pending.toolName, args, observationId)
                    }

                    val toolEvent = AgentEvent.ToolExecution(
                        stepNumber = currentState.stepNumber,
                        toolName = pending.toolName,
                        arguments = pending.arguments,
                        result = toolResult
                    )

                    loopGuard.recordAction(pending.toolName, pending.arguments)

                    _state.value = _state.value
                        .withEvent(toolEvent)
                        .copy(
                            pendingConfirmation = null,
                            status = AgentStatus.THINKING,
                            stepNumber = _state.value.stepNumber + 1
                        )

                    runAgentLoop()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute confirmed action", e)
                    _state.value = _state.value.copy(
                        pendingConfirmation = null,
                        status = AgentStatus.THINKING,
                        lastError = e.message
                    )
                    runAgentLoop()
                }
            }
        } else {
            val errorEvent = AgentEvent.Error(
                stepNumber = currentState.stepNumber,
                message = "User rejected the action: ${pending.toolName}"
            )
            _state.value = currentState
                .withEvent(errorEvent)
                .copy(
                    pendingConfirmation = null,
                    status = AgentStatus.THINKING
                )

            agentJob = agentScope.launch { runAgentLoop() }
        }
    }

    /** Cancel the current task. */
    fun stopAgent() {
        Log.i(TAG, "Stop requested, cancelling agent job")
        agentJob?.cancel()
        agentJob = null
        _state.value = _state.value.copy(
            status = AgentStatus.CANCELLED,
            endTime = System.currentTimeMillis(),
            pendingConfirmation = null,
            pendingQuestion = null
        )
    }

    // ===================================================================
    // Main agent loop
    // ===================================================================

    private suspend fun runAgentLoop() {
        try {
            val maxSteps = withContext(Dispatchers.IO) {
                settingsRepository.maxSteps()
            }
            _state.value = _state.value.copy(maxSteps = maxSteps)

            // Build system prompt once (includes current tool catalogue)
            ensureSystemPrompt()

            while (_state.value.status.isActive) {
                val current = _state.value

                // --- Step budget check ---
                if (current.stepNumber >= maxSteps) {
                    finishTask(
                        status = AgentStatus.FAILED,
                        error = "Max steps ($maxSteps) reached without completing the task."
                    )
                    break
                }

                // --- Observe ---
                val observation = observeScreen()
                if (observation == null) continue  // error already recorded

                // --- Loop detection ---
                val loopWarning = if (loopGuard.isLooping()) {
                    loopGuard.getLoopMessage()
                } else null

                // --- Call model ---
                val decision = callModel(observation, loopWarning)
                    ?: continue  // model error already recorded, step consumed

                // --- Record model event ---
                val modelEvent = AgentEvent.ModelResponse(
                    stepNumber = _state.value.stepNumber,
                    decisionType = decision.type,
                    content = describeDecision(decision)
                )
                _state.value = _state.value.withEvent(modelEvent)

                // --- Execute decision ---
                val shouldContinue = executeDecision(decision, observation.id)
                if (!shouldContinue) break

                // --- Advance step ---
                _state.value = _state.value.copy(
                    stepNumber = _state.value.stepNumber + 1
                )
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Agent loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Agent loop error", e)
            finishTask(
                status = AgentStatus.FAILED,
                error = e.message ?: "Unknown error"
            )
        }
    }

    // ===================================================================
    // Step 1: Observe
    // ===================================================================

    /**
     * Observe the screen and update state.  Returns the observation,
     * or null if observation failed (in which case an error event is
     * recorded and the step is consumed).
     */
    private suspend fun observeScreen(): AndroidObservation? {
        _state.value = _state.value.copy(status = AgentStatus.THINKING)

        val useVision = shouldUseVision()
        val observation: AndroidObservation
        try {
            observation = withContext(Dispatchers.IO) {
                accessibilityObserver.observeWithScreenshot(takeScreenshot = useVision)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Observation failed: ${e.message}")
            _state.value = _state.value
                .withEvent(
                    AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = "Failed to observe screen: ${e.message}"
                    )
                )
                .copy(stepNumber = _state.value.stepNumber + 1)
            return null
        }

        val obsEvent = AgentEvent.Observation(
            stepNumber = _state.value.stepNumber,
            summary = "Package: ${observation.packageName}, " +
                "Activity: ${observation.activityName}, " +
                "Nodes: ${observation.uiTree.size}, " +
                "Has screenshot: ${observation.screenshotBase64 != null}"
        )

        loopGuard.recordObservation(observation)

        _state.value = _state.value
            .withEvent(obsEvent)
            .copy(
                lastObservation = observation,
                currentPackage = observation.packageName,
                currentObservationId = observation.id
            )

        return observation
    }

    // ===================================================================
    // Step 2: Call model
    // ===================================================================

    /**
     * Call the AI model with the current screen state and history.
     * Returns the parsed decision, or null if the call failed (error
     * is recorded and step consumed).
     */
    private suspend fun callModel(
        observation: AndroidObservation,
        loopWarning: String?
       ): AgentDecision? {
        val current = _state.value
        val systemPrompt = cachedSystemPrompt!!

        val userMessage = promptBuilder.buildUserMessage(
            goal = current.goal,
            observation = observation,
            history = current.history,
            loopWarning = loopWarning
        )

        val screenshotBase64 = observation.screenshotBase64
        var lastError: String? = null

        // Retry transient model failures up to MAX_MODEL_RETRIES times.
        for (attempt in 0..MAX_MODEL_RETRIES) {
            val callStartTime = System.currentTimeMillis()
            try {
                val response = withContext(Dispatchers.IO) {
                    gemmaClient.generate(
                        systemPrompt = systemPrompt,
                        userMessage = userMessage,
                        tools = toolRegistry.getAll(),
                        screenshotBase64 = screenshotBase64
                    )
                }

                _state.value = _state.value.copy(
                    modelLatencyMs = System.currentTimeMillis() - callStartTime
                )

                return DecisionParser.parse(response, knownToolNames)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "Model call failed (attempt ${attempt + 1}/$MAX_MODEL_RETRIES): ${e.message}")
                if (attempt == MAX_MODEL_RETRIES) break
                // Brief pause before retry
                kotlinx.coroutines.delay(500L * (attempt + 1))
            }
        }

        // All retries exhausted
        Log.w(TAG, "Model call failed after all retries: $lastError")
        _state.value = _state.value
            .withEvent(
                AgentEvent.Error(
                    stepNumber = _state.value.stepNumber,
                    message = "Model call failed: $lastError"
                )
            )
            .copy(
                lastError = "Model error: $lastError",
                stepNumber = _state.value.stepNumber + 1
            )
        return null
    }

    // ===================================================================
    // Step 3: Execute decision
    // ===================================================================

    /**
     * Execute the parsed model decision.  Returns `true` if the loop
     * should continue, `false` if the task is done or paused.
     */
    private suspend fun executeDecision(
        decision: AgentDecision,
        observationId: String
    ): Boolean {
        return when (decision) {
            is ToolCallDecision -> executeToolCall(decision, observationId)
            is AskUserDecision -> {
                _state.value = _state.value.copy(
                    status = AgentStatus.WAITING_FOR_USER,
                    pendingQuestion = decision.question
                )
                false  // pause loop
            }
            is FinishDecisionData -> {
                val newStatus = if (decision.success) AgentStatus.COMPLETED else AgentStatus.FAILED
                finishTask(
                    status = newStatus,
                    error = if (!decision.success) decision.message else null
                )
                false  // stop loop
            }
            is MessageDecision -> {
                // Status message – nothing to do, loop continues.
                true
            }
            is ErrorDecisionData -> {
                _state.value = _state.value
                    .withEvent(
                        AgentEvent.Error(
                            stepNumber = _state.value.stepNumber,
                            message = decision.message
                        )
                    )
                    .copy(lastError = decision.message)
                true  // loop continues so the model can recover
            }
        }
    }

    // -------------------------------------------------------------------
    // Tool call execution
    // -------------------------------------------------------------------

    private suspend fun executeToolCall(
        decision: ToolCallDecision,
        observationId: String
    ): Boolean {
        _state.value = _state.value.copy(status = AgentStatus.EXECUTING)

        // Resolve aliases before execution
        val resolution = toolExecutor.resolveToolName(decision.toolName)
        val toolName = resolution.resolvedName

        // If the model used a wrong name, log it but execute the corrected name.
        if (resolution.wasCorrected) {
            Log.i(TAG, "Auto-corrected tool name: ${decision.toolName} → $toolName (${resolution.method})")
        }

        // Execute (safety checks are inside ToolExecutor)
        var toolResult: ToolResult
        try {
            toolResult = withContext(Dispatchers.IO) {
                toolExecutor.execute(toolName, decision.arguments, observationId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution exception for $toolName", e)
            _state.value = _state.value
                .withEvent(
                    AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = "Tool execution exception for $toolName: ${e.message}"
                    )
                    )
                .copy(status = AgentStatus.THINKING, lastError = e.message)
            return true
        }

        // Handle UNKNOWN_TOOL (should be rare since we resolve aliases first)
        if (!toolResult.success && toolResult.error?.code == ToolExecutor.ERROR_UNKNOWN_TOOL) {
            _state.value = _state.value
                .withEvent(
                    AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = buildString {
                            append("Tool '$toolName' does not exist. ")
                            append("Available: ${toolExecutor.getRegisteredHandlerNames().sorted().joinToString(", ")}.")
                            append(" Please use a valid tool name.")
                        }
                    )
                )
                .copy(status = AgentStatus.THINKING, lastError = "Unknown tool: $toolName")
            return true
        }

        // Handle NODE_NOT_FOUND with automatic re-observe + retry
        if (!toolResult.success && toolResult.error?.code == "NODE_NOT_FOUND") {
            toolResult = retryAfterReobserve(decision, toolResult)
        }

        // Record the tool execution event
        val toolEvent = AgentEvent.ToolExecution(
            stepNumber = _state.value.stepNumber,
            toolName = toolName,
            arguments = decision.arguments.toString(),
            result = toolResult
        )
        loopGuard.recordAction(toolName, decision.arguments.toString())
        _state.value = _state.value.withEvent(toolEvent)

        // Handle post-execution status transitions
        return handlePostExecutionStatus(toolResult, decision)
    }

    /**
     * When a NODE_NOT_FOUND error occurs, re-observe the screen (without
     * screenshot) and retry the same tool call once.
     */
    private suspend fun retryAfterReobserve(
        decision: ToolCallDecision,
        originalResult: ToolResult
    ): ToolResult {
        Log.i(TAG, "NODE_NOT_FOUND for ${decision.toolName}, re-observing and retrying once")

        return try {
            val newObservation = withContext(Dispatchers.IO) {
                accessibilityObserver.observeWithScreenshot(takeScreenshot = false)
            }

            val retryObsEvent = AgentEvent.Observation(
                stepNumber = _state.value.stepNumber,
                summary = "Auto re-observe (NODE_NOT_FOUND retry): " +
                    "Package: ${newObservation.packageName}, Nodes: ${newObservation.uiTree.size}"
            )
            loopGuard.recordObservation(newObservation)

            _state.value = _state.value
                .withEvent(retryObsEvent)
                .copy(
                    lastObservation = newObservation,
                    currentObservationId = newObservation.id
                )

            withContext(Dispatchers.IO) {
                toolExecutor.execute(decision.toolName, decision.arguments, newObservation.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Re-observation/retry failed, using original NODE_NOT_FOUND result", e)
            originalResult
        }
    }

    /**
     * After a tool call completes, transition the agent's status based
     * on the result's error code.
     */
    private fun handlePostExecutionStatus(
        toolResult: ToolResult,
        decision: ToolCallDecision
    ): Boolean {
        return when {
            toolResult.error?.code == "NODE_STALE" -> {
                _state.value = _state.value.copy(
                    status = AgentStatus.THINKING,
                    lastError = "Node was stale, will re-observe"
                )
                true
            }
            toolResult.error?.code == ToolExecutor.ERROR_CONFIRMATION_REQUIRED -> {
                _state.value = _state.value.copy(
                    status = AgentStatus.WAITING_FOR_CONFIRMATION,
                    pendingConfirmation = PendingConfirmation(
                        toolName = decision.toolName,
                        arguments = decision.arguments.toString(),
                        reason = toolResult.error.message,
                        stepNumber = _state.value.stepNumber
                    )
                )
                false  // pause loop
            }
            toolResult.error?.code == ToolExecutor.ERROR_BLOCKED -> {
                _state.value = _state.value
                    .withEvent(
                        AgentEvent.Error(
                            stepNumber = _state.value.stepNumber,
                            message = "Action blocked: ${toolResult.error.message}"
                        )
                    )
                    .copy(
                        status = AgentStatus.THINKING,
                        lastError = toolResult.error.message
                    )
                true
            }
            !toolResult.success -> {
                _state.value = _state.value.copy(
                    status = AgentStatus.THINKING,
                    lastError = toolResult.error?.message ?: "Tool failed"
                )
                true
            }
            else -> {
                _state.value = _state.value.copy(status = AgentStatus.THINKING)
                true
            }
        }
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    /**
     * Ensure the cached system prompt is built with the current tool set.
     * Called once at the start of each task.
     */
    private fun ensureSystemPrompt() {
        if (cachedSystemPrompt == null) {
            val tools = toolRegistry.getAll()
            knownToolNames = toolRegistry.getToolNames()
            cachedSystemPrompt = promptBuilder.buildSystemPrompt(tools)
            Log.d(TAG, "System prompt built with ${tools.size} tools")
        }
    }

    /** Determine whether to include a screenshot based on vision mode settings. */
    private suspend fun shouldUseVision(): Boolean {
        return withContext(Dispatchers.IO) {
            when (settingsRepository.visionMode()) {
                "ALWAYS" -> true
                "OFF" -> false
                "AUTO" -> {
                    val lastObs = _state.value.lastObservation
                    val actionableCount = lastObs?.uiTree
                        ?.count { it.isClickable || it.isEditable } ?: 0
                    val lastToolFailed = _state.value.history
                        .filterIsInstance<AgentEvent.ToolExecution>()
                        .lastOrNull()
                        ?.let { !it.result.success } ?: false
                    actionableCount < 3 || lastToolFailed
                }
                else -> false
            }
        }
    }

    /** Produce a one-line summary of a decision for the history log. */
    private fun describeDecision(decision: AgentDecision): String {
        return when (decision) {
            is ToolCallDecision  -> "tool_call: ${decision.toolName}"
            is MessageDecision   -> decision.content
            is AskUserDecision   -> "ask_user: ${decision.question}"
            is FinishDecisionData -> "finish: ${decision.message}"
            is ErrorDecisionData  -> "error: ${decision.message}"
        }
    }

    /** Transition the agent to a terminal state and persist the task. */
    private fun finishTask(status: AgentStatus, error: String?) {
        _state.value = _state.value.copy(
            status = status,
            endTime = System.currentTimeMillis(),
            lastError = error
        )
        saveTask()
    }

    /** Persist the completed/failed task to the database (fire-and-forget). */
    private fun saveTask() {
        val currentState = _state.value
        taskRepository?.let { repo ->
            agentScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val record = com.androidagent.aiagent.data.TaskRecord(
                            goal = currentState.goal,
                            status = currentState.status.name,
                            startTime = currentState.startTime ?: System.currentTimeMillis(),
                            endTime = currentState.endTime,
                            stepCount = currentState.stepNumber,
                            result = if (currentState.status == AgentStatus.COMPLETED)
                                "Completed" else currentState.lastError
                        )
                        repo.insertTask(record)
                    }
                } catch (_: Exception) { /* best-effort persistence */ }
            }
        }
    }
}
