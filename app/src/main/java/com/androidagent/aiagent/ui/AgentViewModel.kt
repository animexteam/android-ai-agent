package com.androidagent.aiagent.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.agent.AgentRuntime
import com.androidagent.aiagent.agent.AgentState
import com.androidagent.aiagent.ai.GemmaClient
import com.androidagent.aiagent.ai.VisionAnalyzer
import com.androidagent.aiagent.data.AppDatabase
import com.androidagent.aiagent.data.SecureStorage
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.data.TaskRepository
import com.androidagent.aiagent.safety.ConfirmationManager
import com.androidagent.aiagent.safety.SafetyController
import com.androidagent.aiagent.tools.ToolExecutor
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolRegistry
import com.androidagent.aiagent.tools.android.*
import com.androidagent.aiagent.tools.vision.AnalyzeScreenTool
import com.androidagent.aiagent.tools.vision.FindVisualTargetTool
import com.androidagent.aiagent.tools.agent.AskUserTool
import com.androidagent.aiagent.tools.agent.ConfirmTool
import com.androidagent.aiagent.tools.agent.FinishTool
import com.androidagent.aiagent.tools.agent.StopTool
import kotlinx.coroutines.flow.StateFlow

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val secureStorage = SecureStorage(context)
    val settingsRepository: SettingsRepository = SettingsRepository(context, secureStorage)

    private val accessibilityObserver = AccessibilityObserver()
    private val gemmaClient = GemmaClient(settingsRepository)
    private val visionAnalyzer = VisionAnalyzer(gemmaClient)
    private val toolRegistry = ToolRegistry()
    private val confirmationManager = ConfirmationManager()
    private val safetyController = SafetyController(confirmationManager)

    private val toolHandlers: Map<String, ToolHandler> by lazy {
        buildToolHandlers()
    }

    private val toolExecutor = ToolExecutor(
        accessibilityObserver = accessibilityObserver,
        gestureController = GestureController,
        visionAnalyzer = visionAnalyzer,
        safetyController = safetyController,
        toolHandlers = toolHandlers
    )

    val agentRuntime: AgentRuntime = AgentRuntime(
        gemmaClient = gemmaClient,
        toolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        accessibilityObserver = accessibilityObserver,
        settingsRepository = settingsRepository,
        taskRepository = taskRepository
    )

    val stateFlow: StateFlow<AgentState> = agentRuntime.state

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "android_agent_db"
    ).build()

    val taskRepository: TaskRepository = TaskRepository(database)

    init {
        registerAllTools()
    }

    private fun registerAllTools() {
        val tools = listOf(
            LaunchAppTool.definition(),
            FindTool.definition(),
            ClickTool.definition(),
            LongClickTool.definition(),
            TypeTextTool.definition(),
            ClearTextTool.definition(),
            ScrollTool.definition(),
            SwipeTool.definition(),
            PressKeyTool.definition(),
            BackTool.definition(),
            HomeTool.definition(),
            RecentsTool.definition(),
            WaitTool.definition(),
            ScreenshotTool.definition(),
            InspectScreenTool.definition(),
            AnalyzeScreenTool.definition(),
            FindVisualTargetTool.definition(),
            AskUserTool.definition(),
            ConfirmTool.definition(),
            FinishTool.definition(),
            StopTool.definition()
        )
        tools.forEach { toolRegistry.register(it) }
    }

    private fun buildToolHandlers(): Map<String, ToolHandler> {
        return mapOf(
            LaunchAppTool.TOOL_NAME to LaunchAppTool(),
            FindTool.TOOL_NAME to FindTool(accessibilityObserver),
            ClickTool.TOOL_NAME to ClickTool(accessibilityObserver),
            LongClickTool.TOOL_NAME to LongClickTool(accessibilityObserver),
            TypeTextTool.TOOL_NAME to TypeTextTool(accessibilityObserver),
            ClearTextTool.TOOL_NAME to ClearTextTool(accessibilityObserver),
            ScrollTool.TOOL_NAME to ScrollTool(accessibilityObserver),
            SwipeTool.TOOL_NAME to SwipeTool(),
            PressKeyTool.TOOL_NAME to PressKeyTool(),
            BackTool.TOOL_NAME to BackTool(),
            HomeTool.TOOL_NAME to HomeTool(),
            RecentsTool.TOOL_NAME to RecentsTool(),
            WaitTool.TOOL_NAME to WaitTool(),
            ScreenshotTool.TOOL_NAME to ScreenshotTool(),
            InspectScreenTool.TOOL_NAME to InspectScreenTool(accessibilityObserver),
            AnalyzeScreenTool.TOOL_NAME to AnalyzeScreenTool(visionAnalyzer),
            FindVisualTargetTool.TOOL_NAME to FindVisualTargetTool(visionAnalyzer),
            AskUserTool.TOOL_NAME to AskUserTool(),
            ConfirmTool.TOOL_NAME to ConfirmTool(),
            FinishTool.TOOL_NAME to FinishTool(),
            StopTool.TOOL_NAME to StopTool()
        )
    }

    fun startTask(goal: String) {
        agentRuntime.startTask(goal)
    }

    fun stopAgent() {
        agentRuntime.stopAgent()
    }

    fun respondToUser(answer: String) {
        agentRuntime.respondToUser(answer)
    }

    fun respondToConfirmation(confirmed: Boolean) {
        agentRuntime.respondToConfirmation(confirmed)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return AndroidAgentAccessibilityService.isConnected.get()
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun onCleared() {
        super.onCleared()
        agentRuntime.stopAgent()
    }
}