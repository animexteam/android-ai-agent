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
 * The agent runtime — runs the observe-reason-act loop.
 *
 * v4.1 Key improvements:
 * - Chat mode: For simple messages, responds in chat UI and finishes (no screen control)
 * - AgentMessage events: Agent chat responses visible to user
 * - Speed: Adaptive screenshot usage, reduced settle times
 * - No action limits: Runs until task done or user stops
 * - Memory-safe: Doesn't let old memories corrupt new tasks
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
        private const val MAX_MODEL_RETRIES = 1
        private const val POST_ACTION_SETTLE_MS = 250L
        private const val POST_CLICK_SETTLE_MS = 300L
    }

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private var agentJob: Job? = null
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var cachedSystemPrompt: String? = null
    private var knownToolNames: Set<String> = emptySet()

    // Track if we've done any tool calls (to distinguish chat-only from agent tasks)
    private var hasTakenAnyAction = false

    // ===================================================================
    // Public API
    // ===================================================================

    fun startTask(goal: String, memoryBlock: String = "") {
        agentJob?.cancel()
        loopGuard.reset()
        cachedSystemPrompt = null
        hasTakenAnyAction = false

        _state.value = AgentState(
            goal = goal,
            status = AgentStatus.THINKING,
            stepNumber = 0,
            startTime = System.currentTimeMillis(),
            chatHistory = listOf(
                GemmaClient.ChatMessage(role = "user", content = goal)
            )
        )

        // Add user message event for UI
        _state.value = _state.value.withEvent(
            AgentEvent.UserMessage(stepNumber = 0, text = goal)
        )

        agentJob = agentScope.launch { runAgentLoop(memoryBlock) }
    }

    /** Start a fresh task but keep previous chat context. */
    fun continueChat(message: String, memoryBlock: String = "") {
        val current = _state.value
        if (current.status.isActive) {
            Log.w(TAG, "Cannot continue chat while agent is running")
            return
        }

        agentJob?.cancel()
        cachedSystemPrompt = null

        val newHistory = current.chatHistory +
            GemmaClient.ChatMessage(role = "user", content = message)

        _state.value = current.copy(
            status = AgentStatus.THINKING,
            stepNumber = 0,
            startTime = System.currentTimeMillis(),
            chatHistory = newHistory,
            lastError = null,
            endTime = null
        )

        // Add user message event for UI
        _state.value = _state.value.withEvent(
            AgentEvent.UserMessage(stepNumber = 0, text = message)
        )

        agentJob = agentScope.launch { runAgentLoop(memoryBlock) }
    }

    /** Reset everything for a new chat session. */
    fun newChat() {
        agentJob?.cancel()
        agentJob = null
        cachedSystemPrompt = null
        hasTakenAnyAction = false
        _state.value = AgentState()
    }

    fun respondToUser(answer: String) {
        val currentState = _state.value
        if (currentState.status != AgentStatus.WAITING_FOR_USER) return

        val userMsg = GemmaClient.ChatMessage(role = "user", content = answer)
        _state.value = currentState
            .copy(
                pendingQuestion = null,
                status = AgentStatus.THINKING,
                chatHistory = currentState.chatHistory + userMsg
            )

        _state.value = _state.value.withEvent(
            AgentEvent.UserMessage(stepNumber = currentState.stepNumber, text = answer)
        )

        agentJob = agentScope.launch { runAgentLoop() }
    }

    fun respondToConfirmation(confirmed: Boolean) {
        val currentState = _state.value
        if (currentState.status != AgentStatus.WAITING_FOR_CONFIRMATION) return
        val pending = currentState.pendingConfirmation ?: return

        if (confirmed) {
            agentJob = agentScope.launch {
                try {
                    val argsJson = try {
                        kotlinx.serialization.json.Json.parseToJsonElement(pending.arguments)
                    } catch (_: Exception) { null }
                    val args = argsJson as? kotlinx.serialization.json.JsonObject
                        ?: kotlinx.serialization.json.JsonObject(emptyMap())

                    val toolResult = withContext(Dispatchers.IO) {
                        toolExecutor.execute(pending.toolName, args, currentState.currentObservationId)
                    }

                    val toolEvent = AgentEvent.ToolExecution(
                        stepNumber = currentState.stepNumber,
                        toolName = pending.toolName,
                        arguments = pending.arguments,
                        result = toolResult
                    )
                    loopGuard.recordAction(pending.toolName, pending.arguments)
                    hasTakenAnyAction = true

                    _state.value = _state.value
                        .withEvent(toolEvent)
                        .copy(
                            pendingConfirmation = null,
                            status = AgentStatus.THINKING,
                            stepNumber = _state.value.stepNumber + 1
                        )

                    runAgentLoop()
                } catch (e: CancellationException) { throw e
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
            _state.value = currentState
                .copy(
                    pendingConfirmation = null,
                    status = AgentStatus.THINKING
                )
            agentJob = agentScope.launch { runAgentLoop() }
        }
    }

    fun stopAgent() {
        Log.i(TAG, "Stop requested")
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

    private suspend fun runAgentLoop(memoryBlock: String = "") {
        try {
            ensureSystemPrompt(memoryBlock)

            while (_state.value.status.isActive) {
                val current = _state.value

                // --- Observe (skip for chat-only mode) ---
                val observation = observeScreen()
                if (observation == null) continue

                // --- Loop detection ---
                val loopWarning = if (loopGuard.isLooping()) {
                    loopGuard.getLoopMessage()
                } else null

                // --- Call model with full chat history ---
                val decision = callModel(observation, loopWarning)
                    ?: continue

                // --- Execute decision ---
                val shouldContinue = executeDecision(decision, observation.id)
                if (!shouldContinue) break

                // --- Post-action settle (adaptive) ---
                if (decision is ToolCallDecision) {
                    val settleTime = when {
                        decision.toolName.contains("click") || decision.toolName.contains("long_click") -> POST_CLICK_SETTLE_MS
                        decision.toolName.contains("wait") -> 50L
                        decision.toolName.contains("press_key") -> 100L
                        else -> POST_ACTION_SETTLE_MS
                    }
                    kotlinx.coroutines.delay(settleTime)
                }

                _state.value = _state.value.copy(
                    stepNumber = _state.value.stepNumber + 1
                )
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Agent loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Agent loop error", e)
            finishTask(AgentStatus.FAILED, e.message ?: "Unknown error")
        }
    }

    // ===================================================================
    // Step 1: Observe
    // ===================================================================

    private suspend fun observeScreen(): AndroidObservation? {
        _state.value = _state.value.copy(status = AgentStatus.THINKING)
        val useVision = shouldUseVision()
        val observation: AndroidObservation
        try {
            observation = withContext(Dispatchers.IO) {
                accessibilityObserver.observeWithScreenshot(takeScreenshot = useVision)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.w(TAG, "Observation failed: ${e.message}")
            _state.value = _state.value
                .withEvent(AgentEvent.Error(
                    stepNumber = _state.value.stepNumber,
                    message = "Screen observation failed: ${e.message}"
                ))
                .copy(stepNumber = _state.value.stepNumber + 1)
            return null
        }

        loopGuard.recordObservation(observation)
        _state.value = _state.value
            .copy(
                lastObservation = observation,
                currentPackage = observation.packageName,
                currentObservationId = observation.id
            )
        return observation
    }

    // ===================================================================
    // Step 2: Call model (MULTI-TURN)
    // ===================================================================

    private suspend fun callModel(
        observation: AndroidObservation,
        loopWarning: String?
    ): AgentDecision? {
        val current = _state.value
        val systemPrompt = cachedSystemPrompt!!

        // Build the screen state as the latest user message content
        val screenState = promptBuilder.buildScreenState(
            observation = observation,
            loopWarning = loopWarning
        )

        // Build chat history: existing messages + new screen state
        val historyForModel = mutableListOf<GemmaClient.ChatMessage>()
        val existingHistory = current.chatHistory
        val trimThreshold = maxOf(0, existingHistory.size - 16) // Keep last 16 messages (reduced for speed)
        for ((i, msg) in existingHistory.withIndex()) {
            if (i >= trimThreshold) {
                historyForModel.add(msg)
            }
        }
        // Add the current screen state as the latest user message
        historyForModel.add(GemmaClient.ChatMessage(role = "user", content = screenState))

        val screenshotBase64 = observation.screenshotBase64
        var lastError: String? = null

        for (attempt in 0..MAX_MODEL_RETRIES) {
            val callStartTime = System.currentTimeMillis()
            try {
                val response = withContext(Dispatchers.IO) {
                    gemmaClient.generateWithHistory(
                        systemPrompt = systemPrompt,
                        messages = historyForModel,
                        screenshotBase64 = screenshotBase64
                    )
                }

                _state.value = _state.value.copy(
                    modelLatencyMs = System.currentTimeMillis() - callStartTime
                )

                val decision = DecisionParser.parse(response, knownToolNames)

                // Record the assistant's response in chat history
                _state.value = _state.value.copy(
                    chatHistory = current.chatHistory +
                        GemmaClient.ChatMessage(role = "assistant", content = response.trim())
                )

                return decision

            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "Model call failed (attempt ${attempt + 1}): ${e.message}")
                if (attempt == MAX_MODEL_RETRIES) break
                kotlinx.coroutines.delay(300L * (attempt + 1)) // Faster retry
            }
        }

        Log.w(TAG, "Model call failed: $lastError")
        _state.value = _state.value
            .withEvent(AgentEvent.Error(
                stepNumber = _state.value.stepNumber,
                message = "AI response failed: $lastError"
            ))
            .copy(
                lastError = "AI error: $lastError",
                stepNumber = _state.value.stepNumber + 1
            )
        return null
    }

    // ===================================================================
    // Step 3: Execute decision
    // ===================================================================

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
                finishTask(newStatus, if (!decision.success) decision.message else null)
                false
            }
            is MessageDecision -> {
                // CRITICAL FIX: Show the agent's chat response in the UI
                _state.value = _state.value.withEvent(
                    AgentEvent.AgentMessage(
                        stepNumber = _state.value.stepNumber,
                        text = decision.content
                    )
                )

                // If no tool calls have been made, this is a chat-only interaction
                // — show the response and finish
                if (!hasTakenAnyAction) {
                    finishTask(AgentStatus.COMPLETED, null)
                    return false
                }

                // If we've been doing agentic work and the model sends a message,
                // it might be a status update — continue the loop
                true
            }
            is ErrorDecisionData -> {
                _state.value = _state.value
                    .withEvent(AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = decision.message
                    ))
                    .copy(lastError = decision.message)
                true
            }
        }
    }

    private suspend fun executeToolCall(
        decision: ToolCallDecision,
        observationId: String
    ): Boolean {
        _state.value = _state.value.copy(status = AgentStatus.EXECUTING)

        val resolution = toolExecutor.resolveToolName(decision.toolName)
        val toolName = resolution.resolvedName

        var toolResult: ToolResult
        try {
            toolResult = withContext(Dispatchers.IO) {
                toolExecutor.execute(toolName, decision.arguments, observationId)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution exception for $toolName", e)
            _state.value = _state.value
                .withEvent(AgentEvent.Error(
                    stepNumber = _state.value.stepNumber,
                    message = "Tool error: ${e.message}"
                ))
                .copy(status = AgentStatus.THINKING, lastError = e.message)
            return true
        }

        hasTakenAnyAction = true

        if (!toolResult.success && toolResult.error?.code == ToolExecutor.ERROR_UNKNOWN_TOOL) {
            _state.value = _state.value
                .withEvent(AgentEvent.Error(
                    stepNumber = _state.value.stepNumber,
                    message = "Tool '$toolName' not found. Available: ${toolExecutor.getRegisteredHandlerNames().sorted().joinToString(", ")}"
                ))
                .copy(status = AgentStatus.THINKING, lastError = "Unknown tool: $toolName")
            return true
        }

        if (!toolResult.success && toolResult.error?.code == "NODE_NOT_FOUND") {
            toolResult = retryAfterReobserve(decision, toolResult)
        }

        val toolEvent = AgentEvent.ToolExecution(
            stepNumber = _state.value.stepNumber,
            toolName = toolName,
            arguments = decision.arguments.toString(),
            result = toolResult
        )
        loopGuard.recordAction(toolName, decision.arguments.toString())
        _state.value = _state.value.withEvent(toolEvent)

        return handlePostExecutionStatus(toolResult, decision)
    }

    private suspend fun retryAfterReobserve(
        decision: ToolCallDecision,
        originalResult: ToolResult
    ): ToolResult {
        Log.i(TAG, "NODE_NOT_FOUND for ${decision.toolName}, re-observing")
        return try {
            val newObservation = withContext(Dispatchers.IO) {
                accessibilityObserver.observeWithScreenshot(takeScreenshot = false)
            }
            loopGuard.recordObservation(newObservation)
            _state.value = _state.value.copy(
                lastObservation = newObservation,
                currentObservationId = newObservation.id
            )
            withContext(Dispatchers.IO) {
                toolExecutor.execute(decision.toolName, decision.arguments, newObservation.id)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.w(TAG, "Retry failed", e)
            originalResult
        }
    }

    private fun handlePostExecutionStatus(
        toolResult: ToolResult,
        decision: ToolCallDecision
    ): Boolean {
        return when {
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
                false
            }
            toolResult.error?.code == ToolExecutor.ERROR_BLOCKED -> {
                _state.value = _state.value
                    .withEvent(AgentEvent.Error(
                        stepNumber = _state.value.stepNumber,
                        message = "Blocked: ${toolResult.error.message}"
                    ))
                    .copy(status = AgentStatus.THINKING, lastError = toolResult.error.message)
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

    private fun ensureSystemPrompt(memoryBlock: String) {
        if (cachedSystemPrompt == null) {
            val tools = toolRegistry.getAll()
            knownToolNames = toolRegistry.getToolNames()
            cachedSystemPrompt = promptBuilder.buildSystemPrompt(tools, memoryBlock)
            Log.d(TAG, "System prompt built (${tools.size} tools, memory=${memoryBlock.isNotBlank()})")
        }
    }

    private suspend fun shouldUseVision(): Boolean {
        return withContext(Dispatchers.IO) {
            when (settingsRepository.visionMode()) {
                "ALWAYS" -> true
                "AUTO" -> {
                    // Only use vision when accessibility tree is sparse
                    val lastObs = _state.value.lastObservation
                    val actionableCount = lastObs?.uiTree
                        ?.count { it.isClickable || it.isEditable } ?: 0
                    actionableCount < 3
                }
                else -> false
            }
        }
    }

    private fun finishTask(status: AgentStatus, error: String?) {
        _state.value = _state.value.copy(
            status = status,
            endTime = System.currentTimeMillis(),
            lastError = error
        )
        saveTask()
    }

    private fun saveTask() {
        val currentState = _state.value
        taskRepository?.let { repo ->
            agentScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.insertTask(
                            com.androidagent.aiagent.data.TaskRecord(
                                goal = currentState.goal,
                                status = currentState.status.name,
                                startTime = currentState.startTime ?: System.currentTimeMillis(),
                                endTime = currentState.endTime,
                                stepCount = currentState.stepNumber,
                                result = if (currentState.status == AgentStatus.COMPLETED)
                                    "Completed" else currentState.lastError
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        }
    }
}