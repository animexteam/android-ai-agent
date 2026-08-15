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
import com.androidagent.aiagent.agent.AgentStatus
import com.androidagent.aiagent.ai.GemmaClient
import com.androidagent.aiagent.ai.VisionAnalyzer
import com.androidagent.aiagent.data.AppDatabase
import com.androidagent.aiagent.data.SecureStorage
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.data.TaskRepository
import com.androidagent.aiagent.data.UserMemory
import com.androidagent.aiagent.safety.ConfirmationManager
import com.androidagent.aiagent.safety.SafetyController
import com.androidagent.aiagent.service.AgentForegroundService
import com.androidagent.aiagent.service.OverlayService
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
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val secureStorage = SecureStorage(context)
    val settingsRepository: SettingsRepository = SettingsRepository(context, secureStorage)
    private val userMemory = UserMemory(context)

    private val accessibilityObserver = AccessibilityObserver()
    private val gemmaClient = GemmaClient(settingsRepository)
    private val visionAnalyzer = VisionAnalyzer(gemmaClient)
    private val toolRegistry = ToolRegistry()
    private val confirmationManager = ConfirmationManager()
    private val safetyController = SafetyController(confirmationManager)

    private val toolHandlers: Map<String, ToolHandler> by lazy { buildToolHandlers() }

    private val toolExecutor = ToolExecutor(
        accessibilityObserver = accessibilityObserver,
        gestureController = GestureController,
        visionAnalyzer = visionAnalyzer,
        safetyController = safetyController,
        toolHandlers = toolHandlers
    )

    private val database: AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, "android_agent_db"
    ).build()

    val taskRepository: TaskRepository = TaskRepository(database)

    private val agentRuntime = AgentRuntime(
        gemmaClient = gemmaClient,
        toolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        accessibilityObserver = accessibilityObserver,
        settingsRepository = settingsRepository,
        taskRepository = taskRepository
    )

    val stateFlow: StateFlow<AgentState> = agentRuntime.state

    init {
        registerAllTools()
        AgentForegroundService.createChannel(context)
        observeAgentState()
    }

    private fun observeAgentState() {
        viewModelScope.launch {
            stateFlow
                .distinctUntilChanged { old, new -> old.status == new.status && old.goal == new.goal }
                .collect { state ->
                    when (state.status) {
                        AgentStatus.IDLE, AgentStatus.COMPLETED, AgentStatus.FAILED, AgentStatus.CANCELLED -> {
                            if (state.status != AgentStatus.IDLE) {
                                AgentForegroundService.updateNotification(context, state.status, state.goal)
                            }
                            AgentForegroundService.stop(context)
                            OverlayService.stop(context)
                        }
                        AgentStatus.EXECUTING -> {
                            AgentForegroundService.updateNotification(context, state.status, state.goal)
                            val statusText = when (state.status) {
                                AgentStatus.EXECUTING -> "Android-Use is working..."
                                AgentStatus.THINKING -> "Analyzing screen..."
                                else -> "Working..."
                            }
                            if (OverlayService.canDrawOverlays(context)) {
                                val intent = Intent(context, OverlayService::class.java).apply {
                                    action = OverlayService.ACTION_SHOW_STATUS
                                    putExtra(OverlayService.EXTRA_STATUS_TEXT, statusText)
                                }
                                context.startService(intent)
                            }
                        }
                        else -> {
                            AgentForegroundService.updateNotification(context, state.status, state.goal)
                        }
                    }
                }
        }
    }

    private fun registerAllTools() {
        val tools = listOf(
            // ---- Touch Gestures ----
            LaunchAppTool.definition(),
            FindTool.definition(),
            ClickTool.definition(),
            DoubleClickTool.definition(),
            LongClickTool.definition(),
            TypeTextTool.definition(),
            ClearTextTool.definition(),
            ScrollTool.definition(),
            SwipeTool.definition(),
            DragTool.definition(),
            PinchZoomTool.definition(),
            FlingTool.definition(),
            // ---- Navigation ----
            BackTool.definition(),
            HomeTool.definition(),
            RecentsTool.definition(),
            PressKeyTool.definition(),
            WaitTool.definition(),
            // ---- System Controls ----
            OpenNotificationsTool.definition(),
            OpenQuickSettingsTool.definition(),
            PowerMenuTool.definition(),
            LockScreenTool.definition(),
            SplitScreenTool.definition(),
            VolumeControlTool.definition(),
            ToggleAutoRotateTool.definition(),
            // ---- Text Operations ----
            SelectAllTool.definition(),
            CopyTextTool.definition(),
            PasteTextTool.definition(),
            SetClipboardTool.definition(),
            GetClipboardTool.definition(),
            // ---- Intents ----
            OpenUrlTool.definition(),
            MakeCallTool.definition(),
            SendSmsTool.definition(),
            ShareContentTool.definition(),
            SendEmailTool.definition(),
            // ---- App Management ----
            GetAppListTool.definition(),
            GetRunningAppsTool.definition(),
            ForceStopAppTool.definition(),
            OpenAppInfoTool.definition(),
            UninstallAppTool.definition(),
            ClearAppDataTool.definition(),
            OpenSettingsTool.definition(),
            // ---- Connectivity ----
            ToggleWifiTool.definition(),
            ToggleBluetoothTool.definition(),
            // ---- Display ----
            SetBrightnessTool.definition(),
            // ---- Screen ----
            ScreenshotTool.definition(),
            ScreenshotAndSaveTool.definition(),
            InspectScreenTool.definition(),
            AnalyzeScreenTool.definition(),
            FindVisualTargetTool.definition(),
            // ---- Device Info ----
            GetDeviceInfoTool.definition(),
            GetBatteryInfoTool.definition(),
            GetNetworkInfoTool.definition(),
            GetStorageInfoTool.definition(),
            GetLocationTool.definition(),
            // ---- Files ----
            ReadFileTool.definition(),
            WriteFileTool.definition(),
            ListFilesTool.definition(),
            DeleteFileTool.definition(),
            // ---- Contacts ----
            GetContactsTool.definition(),
            CreateContactTool.definition(),
            // ---- Notifications ----
            GetNotificationsTool.definition(),
            DismissNotificationTool.definition(),
            SendNotificationTool.definition(),
            // ---- Media ----
            MediaControlTool.definition(),
            // ---- Utility ----
            ToastTool.definition(),
            VibrateTool.definition(),
            SetAlarmTool.definition(),
            SetTimerTool.definition(),
            OpenCameraTool.definition(),
            // ---- Shell ----
            ShellCommandTool.definition(),
            // ---- Agent ----
            AskUserTool.definition(),
            ConfirmTool.definition(),
            FinishTool.definition(),
            StopTool.definition()
        )
        tools.forEach { toolRegistry.register(it) }
    }

    private fun buildToolHandlers(): Map<String, ToolHandler> = mapOf(
        // ---- Touch Gestures ----
        LaunchAppTool.TOOL_NAME to LaunchAppTool(),
        FindTool.TOOL_NAME to FindTool(),
        ClickTool.TOOL_NAME to ClickTool(),
        DoubleClickTool.TOOL_NAME to DoubleClickTool(),
        LongClickTool.TOOL_NAME to LongClickTool(),
        TypeTextTool.TOOL_NAME to TypeTextTool(),
        ClearTextTool.TOOL_NAME to ClearTextTool(),
        ScrollTool.TOOL_NAME to ScrollTool(),
        SwipeTool.TOOL_NAME to SwipeTool(),
        DragTool.TOOL_NAME to DragTool(),
        PinchZoomTool.TOOL_NAME to PinchZoomTool(),
        FlingTool.TOOL_NAME to FlingTool(),
        // ---- Navigation ----
        BackTool.TOOL_NAME to BackTool(),
        HomeTool.TOOL_NAME to HomeTool(),
        RecentsTool.TOOL_NAME to RecentsTool(),
        PressKeyTool.TOOL_NAME to PressKeyTool(),
        WaitTool.TOOL_NAME to WaitTool(),
        // ---- System Controls ----
        OpenNotificationsTool.TOOL_NAME to OpenNotificationsTool(),
        OpenQuickSettingsTool.TOOL_NAME to OpenQuickSettingsTool(),
        PowerMenuTool.TOOL_NAME to PowerMenuTool(),
        LockScreenTool.TOOL_NAME to LockScreenTool(),
        SplitScreenTool.TOOL_NAME to SplitScreenTool(),
        VolumeControlTool.TOOL_NAME to VolumeControlTool(),
        ToggleAutoRotateTool.TOOL_NAME to ToggleAutoRotateTool(),
        // ---- Text Operations ----
        SelectAllTool.TOOL_NAME to SelectAllTool(),
        CopyTextTool.TOOL_NAME to CopyTextTool(),
        PasteTextTool.TOOL_NAME to PasteTextTool(),
        SetClipboardTool.TOOL_NAME to SetClipboardTool(),
        GetClipboardTool.TOOL_NAME to GetClipboardTool(),
        // ---- Intents ----
        OpenUrlTool.TOOL_NAME to OpenUrlTool(),
        MakeCallTool.TOOL_NAME to MakeCallTool(),
        SendSmsTool.TOOL_NAME to SendSmsTool(),
        ShareContentTool.TOOL_NAME to ShareContentTool(),
        SendEmailTool.TOOL_NAME to SendEmailTool(),
        // ---- App Management ----
        GetAppListTool.TOOL_NAME to GetAppListTool(),
        GetRunningAppsTool.TOOL_NAME to GetRunningAppsTool(),
        ForceStopAppTool.TOOL_NAME to ForceStopAppTool(),
        OpenAppInfoTool.TOOL_NAME to OpenAppInfoTool(),
        UninstallAppTool.TOOL_NAME to UninstallAppTool(),
        ClearAppDataTool.TOOL_NAME to ClearAppDataTool(),
        OpenSettingsTool.TOOL_NAME to OpenSettingsTool(),
        // ---- Connectivity ----
        ToggleWifiTool.TOOL_NAME to ToggleWifiTool(),
        ToggleBluetoothTool.TOOL_NAME to ToggleBluetoothTool(),
        // ---- Display ----
        SetBrightnessTool.TOOL_NAME to SetBrightnessTool(),
        // ---- Screen ----
        ScreenshotTool.TOOL_NAME to ScreenshotTool(),
        ScreenshotAndSaveTool.TOOL_NAME to ScreenshotAndSaveTool(),
        InspectScreenTool.TOOL_NAME to InspectScreenTool(),
        AnalyzeScreenTool.TOOL_NAME to AnalyzeScreenTool(visionAnalyzer),
        FindVisualTargetTool.TOOL_NAME to FindVisualTargetTool(visionAnalyzer),
        // ---- Device Info ----
        GetDeviceInfoTool.TOOL_NAME to GetDeviceInfoTool(),
        GetBatteryInfoTool.TOOL_NAME to GetBatteryInfoTool(),
        GetNetworkInfoTool.TOOL_NAME to GetNetworkInfoTool(),
        GetStorageInfoTool.TOOL_NAME to GetStorageInfoTool(),
        GetLocationTool.TOOL_NAME to GetLocationTool(),
        // ---- Files ----
        ReadFileTool.TOOL_NAME to ReadFileTool(),
        WriteFileTool.TOOL_NAME to WriteFileTool(),
        ListFilesTool.TOOL_NAME to ListFilesTool(),
        DeleteFileTool.TOOL_NAME to DeleteFileTool(),
        // ---- Contacts ----
        GetContactsTool.TOOL_NAME to GetContactsTool(),
        CreateContactTool.TOOL_NAME to CreateContactTool(),
        // ---- Notifications ----
        GetNotificationsTool.TOOL_NAME to GetNotificationsTool(),
        DismissNotificationTool.TOOL_NAME to DismissNotificationTool(),
        SendNotificationTool.TOOL_NAME to SendNotificationTool(),
        // ---- Media ----
        MediaControlTool.TOOL_NAME to MediaControlTool(),
        // ---- Utility ----
        ToastTool.TOOL_NAME to ToastTool(),
        VibrateTool.TOOL_NAME to VibrateTool(),
        SetAlarmTool.TOOL_NAME to SetAlarmTool(),
        SetTimerTool.TOOL_NAME to SetTimerTool(),
        OpenCameraTool.TOOL_NAME to OpenCameraTool(),
        // ---- Shell ----
        ShellCommandTool.TOOL_NAME to ShellCommandTool(),
        // ---- Agent ----
        AskUserTool.TOOL_NAME to AskUserTool(),
        ConfirmTool.TOOL_NAME to ConfirmTool(),
        FinishTool.TOOL_NAME to FinishTool(),
        StopTool.TOOL_NAME to StopTool()
    )

    fun startTask(goal: String) {
        viewModelScope.launch {
            val memoryBlock = userMemory.buildMemoryBlock()
            AgentForegroundService.start(context, goal)
            agentRuntime.startTask(goal, memoryBlock)
        }
    }

    fun continueChat(message: String) {
        viewModelScope.launch {
            val memoryBlock = userMemory.buildMemoryBlock()
            agentRuntime.continueChat(message, memoryBlock)
        }
    }

    fun newChat() {
        agentRuntime.newChat()
        AgentForegroundService.stop(context)
        OverlayService.stop(context)
    }

    fun stopAgent() {
        agentRuntime.stopAgent()
        AgentForegroundService.stop(context)
        OverlayService.stop(context)
    }

    fun respondToUser(answer: String) = agentRuntime.respondToUser(answer)
    fun respondToConfirmation(confirmed: Boolean) = agentRuntime.respondToConfirmation(confirmed)

    fun isAccessibilityServiceEnabled(): Boolean {
        return try { AndroidAgentAccessibilityService.isConnected } catch (_: Exception) { false }
    }
    fun openAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
    fun isOverlayPermissionGranted(): Boolean = OverlayService.canDrawOverlays(context)
    fun openOverlaySettings() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = android.net.Uri.parse("package:${context.packageName}")
        })
    }

    override fun onCleared() {
        super.onCleared()
        agentRuntime.stopAgent()
        AgentForegroundService.stop(context)
        OverlayService.stop(context)
    }
}