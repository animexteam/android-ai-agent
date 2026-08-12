package com.androidagent.aiagent.agent

import android.util.Log
import com.androidagent.aiagent.ai.GemmaClient
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.data.TaskRepository
import com.androidagent.aiagent.tools.*
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
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
import kotlinx.coroutines.withTimeoutOrNull

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
    }

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private var agentJob: Job? = null
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * When set to true, the next iteration of the agent loop should
     * *not* increment the step counter.  Used for UNKNOWN_TOOL recovery
     * so the model's mistaken call doesn't consume a step budget.
     */
    private var skipNextStepIncrement = false

    fun startTask(goal: String) {
        agentJob?.cancel()
        loopGuard.reset()
        skipNextStepIncrement = false

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

    fun respondToUser(answer: String) {
        val currentState = _state.value
        if (currentState.status != AgentStatus.WAITING_FOR_USER) return

        val userEvent = AgentEvent.UserMessage(
            stepNumber = currentState.stepNumber,
            text = answer
        )

        _state.value = currentState.copy(
            pendingQuestion = null,
            status = AgentStatus.THINKING,
            history = currentState.history + userEvent
        )

        agentJob = agentScope.launch {
            runAgentLoop()
        }
    }

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
                        toolExecutor.execute(
                            pending.toolName,
                            args,
                            observationId
                        )
                    }

                    val toolEvent = AgentEvent.ToolExecution(
                        stepNumber = currentState.stepNumber,
                        toolName = pending.toolName,
                        arguments = pending.arguments,
                        result = toolResult
                    )

                    loopGuard.recordAction(pending.toolName, pending.arguments)

                    _state.value = _state.value.copy(
                        pendingConfirmation = null,
                        status = AgentStatus.THINKING,
                        stepNumber = _state.value.stepNumber + 1,
                        history = _state.value.history + toolEvent
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
            _state.value = currentState.copy(
                pendingConfirmation = null,
                status = AgentStatus.THINKING,
                history = currentState.history + errorEvent
            )

            agentJob = agentScope.launch {
                runAgentLoop()
            }
        }
    }

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

    private suspend fun runAgentLoop() {
        try {
            val maxSteps = withContext(Dispatchers.IO) {
                settingsRepository.maxSteps()
            }
            _state.value = _state.value.copy(maxSteps = maxSteps)

            val systemPrompt = promptBuilder.buildSystemPrompt()

            while (_state.value.isRunning) {
                val current = _state.value

                if (current.stepNumber >= maxSteps) {
                    _state.value = current.copy(
                        status = AgentStatus.FAILED,
                        lastError = "Max steps ($maxSteps) reached without completing the task.",
                        endTime = System.currentTimeMillis()
                    )
                    saveTask()
                    break
                }

                // --- Observe ---
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
                    val errorEvent = AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = "Failed to observe screen: ${e.message}"
                    )
                    _state.value = _state.value.copy(
                        history = _state.value.history + errorEvent,
                        stepNumber = _state.value.stepNumber + 1
                    )
                    continue
                }

                val obsEvent = AgentEvent.Observation(
                    stepNumber = _state.value.stepNumber,
                    summary = "Package: ${observation.packageName}, Activity: ${observation.activityName}, " +
                            "Nodes: ${observation.uiTree.size}, Has screenshot: ${observation.screenshotBase64 != null}"
                )

                loopGuard.recordObservation(observation)

                _state.value = _state.value.copy(
                    lastObservation = observation,
                    currentPackage = observation.packageName,
                    currentObservationId = observation.id,
                    history = _state.value.history + obsEvent
                )

                // --- Loop detection ---
                val loopWarning = if (loopGuard.isLooping()) {
                    loopGuard.getLoopMessage()
                } else null

                // --- Build prompt ---
                val currentStateForPrompt = _state.value
                val compactTree = AccessibilityNodeMapper.serializeCompact(observation.uiTree)
                val recentHistory = currentStateForPrompt.history.takeLast(10).joinToString("\n") { event ->
                    when (event) {
                        is AgentEvent.ToolExecution ->
                            "Step ${event.stepNumber}: ${event.toolName} -> ${if (event.result.success) "success" else "failed: ${event.result.error?.message ?: "unknown"}"
                            }"
                        is AgentEvent.Observation -> "Step ${event.stepNumber}: Observed ${event.summary}"
                        is AgentEvent.ModelResponse -> "Step ${event.stepNumber}: Model -> ${event.decisionType}"
                        is AgentEvent.Error -> "Step ${event.stepNumber}: Error: ${event.message}"
                        is AgentEvent.UserMessage -> "Step ${event.stepNumber}: User said: ${event.text}"
                        is AgentEvent.StatusChange -> "Step ${event.stepNumber}: ${event.from} -> ${event.to}"
                    }
                }

                var userMessage = buildString {
                    appendLine("## Current Task")
                    appendLine(currentStateForPrompt.goal)
                    appendLine()
                    appendLine("## Current Screen State")
                    appendLine("Package: ${observation.packageName ?: "unknown"}")
                    appendLine("Activity: ${observation.activityName ?: "unknown"}")
                    appendLine("Observation ID: ${observation.id}")
                    appendLine()
                    appendLine("### UI Hierarchy")
                    appendLine(compactTree)
                    appendLine()
                    if (recentHistory.isNotEmpty()) {
                        appendLine("### Recent History")
                        appendLine(recentHistory)
                        appendLine()
                    }
                    appendLine("### Instruction")
                    append("Decide your next action. Return a single JSON object.")
                }

                if (loopWarning != null) {
                    userMessage += "\n\n### WARNING: Loop Detected\n$loopWarning\nYou MUST choose a different action."
                }

                val screenshotBase64 = observation.screenshotBase64
                val tools = toolRegistry.getAll()

                // --- Call model ---
                val modelResponse: String
                val callStartTime = System.currentTimeMillis()
                try {
                    modelResponse = withContext(Dispatchers.IO) {
                        gemmaClient.generate(
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            tools = tools,
                            screenshotBase64 = screenshotBase64
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Model call failed: ${e.message}")
                    val errorEvent = AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = "Model call failed: ${e.message}"
                    )
                    _state.value = _state.value.copy(
                        history = _state.value.history + errorEvent,
                        lastError = "Model error: ${e.message}",
                        stepNumber = _state.value.stepNumber + 1
                    )
                    continue
                }

                val latency = System.currentTimeMillis() - callStartTime

                val decision = DecisionParser.parse(modelResponse)

                val modelEvent = AgentEvent.ModelResponse(
                    stepNumber = _state.value.stepNumber,
                    decisionType = decision.type,
                    content = when (decision) {
                        is ToolCallDecision -> "tool_call: ${decision.toolName}"
                        is MessageDecision -> decision.content
                        is AskUserDecision -> "ask_user: ${decision.question}"
                        is FinishDecisionData -> "finish: ${decision.message}"
                        is ErrorDecisionData -> "error: ${decision.message}"
                    }
                )

                _state.value = _state.value.copy(
                    modelLatencyMs = latency,
                    history = _state.value.history + modelEvent
                )

                // --- Execute decision ---
                val shouldContinue = executeDecision(decision, observation.id)
                if (!shouldContinue) break

                if (!skipNextStepIncrement) {
                    _state.value = _state.value.copy(
                        stepNumber = _state.value.stepNumber + 1
                    )
                } else {
                    skipNextStepIncrement = false
                    Log.d(TAG, "Skipped step increment (UNKNOWN_TOOL recovery)")
                }
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Agent loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Agent loop error", e)
            _state.value = _state.value.copy(
                status = AgentStatus.FAILED,
                lastError = e.message ?: "Unknown error",
                endTime = System.currentTimeMillis()
            )
            saveTask()
        }
    }

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
                false
            }
            is FinishDecisionData -> {
                val newStatus = if (decision.success) AgentStatus.COMPLETED else AgentStatus.FAILED
                _state.value = _state.value.copy(
                    status = newStatus,
                    endTime = System.currentTimeMillis(),
                    lastError = if (!decision.success) decision.message else null
                )
                saveTask()
                false
            }
            is MessageDecision -> true
            is ErrorDecisionData -> {
                val errorEvent = AgentEvent.Error(
                    stepNumber = _state.value.stepNumber,
                    message = decision.message
                )
                _state.value = _state.value.copy(
                    history = _state.value.history + errorEvent,
                    lastError = decision.message
                )
                true
            }
        }
    }

    private suspend fun executeToolCall(
        decision: ToolCallDecision,
        observationId: String
    ): Boolean {
        _state.value = _state.value.copy(status = AgentStatus.EXECUTING)

        var toolResult: ToolResult
        try {
            toolResult = withContext(Dispatchers.IO) {
                toolExecutor.execute(decision.toolName, decision.arguments, observationId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution exception for ${decision.toolName}", e)
            val errorEvent = AgentEvent.Error(
                stepNumber = _state.value.stepNumber,
                message = "Tool execution exception for ${decision.toolName}: ${e.message}"
            )
            _state.value = _state.value.copy(
                status = AgentStatus.THINKING,
                lastError = e.message,
                history = _state.value.history + errorEvent
            )
            return true
        }

        // ── BUG 2: UNKNOWN_TOOL ──
        // The model called a tool that doesn't exist.  Find the closest
        // registered name, tell the model, and let it retry without
        // consuming a step.
        if (!toolResult.success && toolResult.error?.code == ToolExecutor.ERROR_UNKNOWN_TOOL) {
            val closest = toolExecutor.findClosestToolName(decision.toolName)
            val correctionMsg = if (closest != null) {
                "Tool '${decision.toolName}' does not exist. The correct tool name is '$closest'. " +
                    "Please retry your action using '$closest' with the same arguments."
            } else {
                "Tool '${decision.toolName}' does not exist. " +
                    "Available tools: ${toolExecutor.getRegisteredHandlerNames().sorted().joinToString(", ")}. " +
                    "Please choose a valid tool name and retry."
            }
            Log.i(TAG, correctionMsg)

            val errorEvent = AgentEvent.Error(
                stepNumber = _state.value.stepNumber,
                message = correctionMsg
            )
            _state.value = _state.value.copy(
                status = AgentStatus.THINKING,
                lastError = correctionMsg,
                history = _state.value.history + errorEvent
            )

            // Don't count this as a step – let the model retry for free.
            skipNextStepIncrement = true
            return true
        }

        // ── BUG 1: NODE_NOT_FOUND auto-retry ──
        // The node the model wanted to interact with vanished from the
        // UI tree (screen changed since last observation).  Re-observe
        // and retry the *same* tool call once before reporting failure.
        if (!toolResult.success && toolResult.error?.code == "NODE_NOT_FOUND") {
            Log.i(TAG, "NODE_NOT_FOUND for ${decision.toolName}(${decision.arguments}), re-observing and retrying once")

            val retryResult: ToolResult? = try {
                // Re-observe the screen (no screenshot – we just need the tree)
                val newObservation = withContext(Dispatchers.IO) {
                    accessibilityObserver.observeWithScreenshot(takeScreenshot = false)
                }

                val retryObsEvent = AgentEvent.Observation(
                    stepNumber = _state.value.stepNumber,
                    summary = "Auto re-observe (NODE_NOT_FOUND retry): " +
                        "Package: ${newObservation.packageName}, Nodes: ${newObservation.uiTree.size}"
                )

                loopGuard.recordObservation(newObservation)

                _state.value = _state.value.copy(
                    lastObservation = newObservation,
                    currentObservationId = newObservation.id,
                    history = _state.value.history + retryObsEvent
                )

                // Retry the exact same tool call with the new observation ID
                withContext(Dispatchers.IO) {
                    toolExecutor.execute(decision.toolName, decision.arguments, newObservation.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Re-observation/retry failed, using original NODE_NOT_FOUND result", e)
                null
            }

            // Use the retry result if we got one
            if (retryResult != null) {
                toolResult = retryResult
            }
        }

        val toolEvent = AgentEvent.ToolExecution(
            stepNumber = _state.value.stepNumber,
            toolName = decision.toolName,
            arguments = decision.arguments.toString(),
            result = toolResult
        )

        loopGuard.recordAction(decision.toolName, decision.arguments.toString())

        _state.value = _state.value.copy(
            history = _state.value.history + toolEvent
        )

        return when {
            toolResult.error?.code == "NODE_STALE" -> {
                _state.value = _state.value.copy(
                    status = AgentStatus.THINKING,
                    lastError = "Node was stale, will re-observe"
                )
                true
            }
            toolResult.error?.code == "SAFETY_CONFIRMATION_REQUIRED" -> {
                _state.value = _state.value.copy(
                    status = AgentStatus.WAITING_FOR_CONFIRMATION,
                    pendingConfirmation = PendingConfirmation(
                        toolName = decision.toolName,
                        arguments = decision.arguments.toString(),
                        reason = toolResult.error.message,
                        stepNumber = _state.value.stepNumber
                    )
                )
                false
            }
            toolResult.error?.code == "SAFETY_BLOCKED" -> {
                val errorEvent = AgentEvent.Error(
                    stepNumber = _state.value.stepNumber,
                    message = "Action blocked: ${toolResult.error.message}"
                )
                _state.value = _state.value.copy(
                    status = AgentStatus.THINKING,
                    lastError = toolResult.error.message,
                    history = _state.value.history + errorEvent
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
                _state.value = _state.value.copy(
                    status = AgentStatus.THINKING
                )
                true
            }
        }
    }

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
                "WHEN_NEEDED" -> false
                else -> false
            }
        }
    }

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
                            result = if (currentState.status == AgentStatus.COMPLETED) "Completed" else currentState.lastError
                        )
                        repo.insertTask(record)
                    }
                } catch (_: Exception) { }
            }
        }
    }
}
