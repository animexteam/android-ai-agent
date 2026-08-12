# Android AI Computer-Use Agent

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2024.12-4285F4?logo=android&logoColor=white)](https://developer.android.com/develop/ui/compose)
[![Material 3](https://img.shields.io/badge/Material_3-1.3.1-2196F3)](https://m3.material.io)
[![Ollama](https://img.shields.io/badge/Ollama_Cloud_API-gemma4%3A31b-000000?logo=ollama)](https://ollama.com)
[![Gemma](https://img.shields.io/badge/Powered_by-Gemma_4_31B-4285F4?logo=google&logoColor=white)](https://ai.google.dev/gemma)
[![Android SDK](https://img.shields.io/badge/Android_SDK-35-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min_SDK-28-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## Overview

**Android AI Computer-Use Agent** is a native Android application that functions as a private, on-device AI computer-use agent. Unlike a chatbot, this app operates an iterative **observe → reason → act → verify** loop to autonomously complete tasks on the device.

The AI observes the device screen via Android's `AccessibilityService`, reasons about the current state using **Gemma 4 31B** served through the **Ollama Cloud API**, selects and executes tools dynamically, observes the result, and continues until the user's task is completed — all running locally on your phone with no middleman server.

### Key Differentiators

- **Not a chatbot.** The agent is a goal-driven autonomous system that takes real actions on the device.
- **Fully private.** The API key is encrypted at rest. Screenshots are off by default and never leave the device unless vision mode is enabled (and even then, they go only to your configured API endpoint).
- **No dangerous permissions.** The app requests only `INTERNET`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, and `SYSTEM_ALERT_WINDOW`. No `READ_CONTACTS`, no `READ_SMS`, no `WRITE_SETTINGS`.
- **No arbitrary code execution.** The AI can only invoke a predefined, registered set of tools. It cannot run shell commands, install APKs, or access arbitrary Android APIs.

---

## Architecture

The agent follows a classic sense-think-act cycle. Below is the end-to-end flow:

```
┌──────────────┐     ┌──────────────────┐     ┌────────────────────────────┐
│  User Goal   │────▶│  Agent Runtime   │────▶│  Gemma 4 31B (Ollama Cloud) │
│  (natural    │     │  (AgentLoop)     │     │  https://ollama.com/api/chat│
│   language)  │     │                  │     │  Model: gemma4:31b          │
└──────────────┘     └───────┬──────────┘     └──────────────┬─────────────┘
                             │                               │
                    ┌────────▼──────────┐          ┌────────▼─────────┐
                    │  Tool Call        │          │  Tool Call JSON  │
                    │  (DecisionParser) │◀─────────│  Response        │
                    └───────┬──────────┘          └──────────────────┘
                            │
                   ┌────────▼──────────┐
                   │  SafetyController │
                   │  (allow/confirm/  │
                   │   block)          │
                   └───────┬──────────┘
                            │
                   ┌────────▼──────────┐
                   │  ToolExecutor     │
                   │  (dispatches to   │
                   │   registered      │
                   │   handlers)       │
                   └───────┬──────────┘
                            │
            ┌───────────────▼───────────────┐
            │     Android Device            │
            │  (AccessibilityService,       │
            │   GestureController,          │
            │   TakeScreenshot API)         │
            └───────────────┬───────────────┘
                            │
                   ┌────────▼──────────┐
                   │  AccessibilityObserver    │
                   │  (new observation:        │
                   │   UI tree + screenshot)   │
                   └───────┬──────────┘
                            │
                   ┌────────▼──────────┐     (loop back to Gemma)
                   │  Next Iteration   │────────────────────────▶
                   └──────────────────┘
```

### Key Modules

| Module | Package | Description |
|--------|---------|-------------|
| **Accessibility Layer** | `accessibility` | `AndroidAgentAccessibilityService`, `AccessibilityObserver`, `AccessibilityNodeMapper`, `GestureController` — reads UI trees, performs gestures, captures screenshots |
| **AI Layer** | `ai` | `GemmaClient`, `VisionAnalyzer`, `OllamaModels` — communicates with Ollama Cloud API, handles multimodal vision input |
| **Tool Registry** | `tools` | `ToolRegistry`, `AgentTool`, `ToolExecutor`, `ToolHandler` — dynamic tool registration, dispatch, and execution |
| **Agent Runtime** | `agent` | `AgentRuntime`, `AgentState`, `AgentLoopGuard`, `AgentPromptBuilder`, `DecisionParser` — the core observe-reason-act loop |
| **Safety Controller** | `safety` | `SafetyController`, `ConfirmationManager`, `RiskLevel` — policy-driven safety checks before tool execution |
| **UI Layer** | `ui` | `MainActivity`, `MainScreen`, `SettingsScreen`, `HistoryScreen`, `DebugScreen`, `AgentViewModel` — Jetpack Compose Material 3 interface |
| **Data Layer** | `data` | `SettingsRepository`, `SecureStorage`, `TaskRepository` — encrypted preferences, DataStore settings, task persistence |

---

## How It Works

### Gemma Integration

The app communicates with **Gemma 4 31B** via the Ollama Cloud API:

- **Endpoint:** `https://ollama.com/api/chat`
- **Model:** `gemma4:31b`
- **Authentication:** Bearer token (API key) stored in encrypted shared preferences
- **Request format:** JSON with `model`, `messages`, `options` (temperature), and `stream: false`
- **Supports both text-only and multimodal (vision) inputs**

For multimodal requests, the user message content becomes an array containing a text part and an `image_url` part with the base64-encoded screenshot:

```json
{
  "role": "user",
  "content": [
    { "type": "text", "text": "Analyze this screen..." },
    { "type": "image_url", "image_url": { "url": "data:image/png;base64,..." } }
  ]
}
```

### Vision System

Screenshots are captured via Android's `TakeScreenshot` API (requires **API 34+ / Android 14**). The workflow:

1. The `AccessibilityService` calls `takeScreenshot()` (declared in `accessibility_service_config.xml` with `android:canTakeScreenshot="true"`)
2. The resulting `Bitmap` is compressed to JPEG at 70% quality
3. The byte array is base64-encoded
4. The base64 string is attached to the model request as a multimodal image input
5. Gemma reasons about the visual content and selects the appropriate tool call

**Vision Modes:**

| Mode | Behavior |
|------|----------|
| `AUTO` (default) | Screenshots are taken when the accessibility tree has fewer than 3 actionable nodes (clickable/editable), or when the last tool execution failed |
| `ALWAYS` | A screenshot is captured and sent with every model request |
| `WHEN_NEEDED` | Vision is available as a tool (`vision.analyze_screen`, `vision.find_visual_target`) but not automatically attached |
| `OFF` | No screenshots are ever captured or sent |

### AccessibilityService

The `AndroidAgentAccessibilityService` is the backbone of the agent's ability to observe and interact with the device. It:

- **Reads the UI hierarchy** via `AccessibilityNodeInfo`, extracting:
  - `text`, `contentDescription`, `resourceId` (view identifier)
  - `className` (e.g., `android.widget.Button`)
  - `bounds` (screen coordinates: left, top, right, bottom)
  - `isClickable`, `isEditable`, `isScrollable`, `isFocusable`, `isEnabled`
  - Parent-child relationships for tree structure
- **Performs actions** on nodes: `click()`, `longClick()`, `setText()`, `performAction(ACTION_SCROLL_FORWARD/BACKWARD)`
- **Dispatches gestures** via `GestureController` for swipe, scroll, and tap operations when node-level actions are insufficient
- **Configured flags:** `flagReportViewIds`, `flagRetrieveInteractiveWindows`, `flagIncludeNotImportantViews`, `canPerformGestures`, `canRetrieveWindowContent`, `canTakeScreenshot`

---

## Tool System

The agent has access to **20+ registered tools** organized by namespace. Tools are discovered and registered at startup via the `ToolRegistry`. Each tool exposes a JSON schema (`inputSchema`) that is injected into the system prompt so the model knows exactly what arguments to provide.

### Tool Call Format

Every model response must be a single JSON object. For tool calls:

```json
{
  "type": "tool_call",
  "tool_name": "android.click",
  "arguments": {
    "node_id": "node_42"
  }
}
```

### All Registered Tools

#### `android.*` — Device Interaction

| Tool Name | Description |
|-----------|-------------|
| `android.launch_app` | Launch an application by package name or display name |
| `android.find` | Search the accessibility tree for nodes matching text, content description, or resource ID |
| `android.click` | Click on a UI element by its `node_id` from the most recent observation |
| `android.long_click` | Long-press on a UI element by its `node_id` |
| `android.type_text` | Type text into an editable field by its `node_id` |
| `android.clear_text` | Clear the text content of an editable field by its `node_id` |
| `android.scroll` | Scroll a scrollable container forward or backward |
| `android.swipe` | Perform a swipe gesture between two coordinates |
| `android.press_key` | Simulate a hardware key press (e.g., Enter, Backspace, Search) |
| `android.back` | Press the system Back button |
| `android.home` | Press the system Home button |
| `android.recents` | Open the recent apps screen |
| `android.wait` | Wait for a specified duration (milliseconds) to allow UI transitions to complete |
| `android.screenshot` | Manually capture and return a screenshot (for use when vision is in `WHEN_NEEDED` mode) |
| `android.inspect_screen` | Return the current accessibility tree observation for analysis |

#### `vision.*` — Visual Analysis

| Tool Name | Description |
|-----------|-------------|
| `vision.analyze_screen` | Capture a screenshot and ask the model to describe what it sees visually |
| `vision.find_visual_target` | Search the screenshot for a visual element described in natural language (e.g., "the blue submit button") and return its coordinates |

#### `agent.*` — Meta / Control

| Tool Name | Description |
|-----------|-------------|
| `agent.ask_user` | Ask the user a clarifying question and pause the loop until they respond |
| `agent.confirm` | Request explicit user confirmation before proceeding with a sensitive action |
| `agent.finish` | Signal task completion (with `success: true/false` and a summary message) |
| `agent.stop` | Abort the current task immediately |

### Decision Types

The `DecisionParser` supports five response types from the model:

| Type | JSON Format | Behavior |
|------|------------|----------|
| `tool_call` | `{"type":"tool_call","tool_name":"...","arguments":{...}}` | Execute the named tool |
| `message` | `{"type":"message","content":"..."}` | Status update; loop continues |
| `ask_user` | `{"type":"ask_user","question":"..."}` | Pause and wait for user input |
| `finish` | `{"type":"finish","success":true/false,"message":"..."}` | End the task |
| `error` | `{"type":"error","message":"..."}` | Model-reported error; loop continues |

---

## Features

### Dynamic Tool Discovery and Registry

Tools are registered at startup via `ToolRegistry`. New tools can be added by implementing the `ToolHandler` interface and registering the `AgentTool` definition (name, description, inputSchema, riskLevel). The model automatically receives updated tool definitions in every prompt — no prompt hardcoding required.

### Stale Node Protection

Every observation is assigned a unique `observation_id` (e.g., `observation_1719000000000_4217`). Node IDs are **only valid within that specific observation**. The system prompt instructs the model to never reuse node IDs from older observations. If the agent attempts to reference a node from a stale observation, the executor returns a `NODE_STALE` error, and the agent automatically re-observes.

### Intelligent Retry on Action Failure

When a tool execution fails, the error is fed back to the model in the next iteration. The model can then reason about the failure and choose a different approach — whether that's finding a different node, scrolling to reveal new content, or asking the user for help.

### Loop Detection and Prevention

The `AgentLoopGuard` tracks two signals:

1. **Consecutive identical actions:** If the same tool + arguments are executed 3 times in a row, a loop warning is injected into the prompt
2. **Unchanged observations:** If the screen state hash doesn't change for 4 consecutive observations, a loop warning is triggered

When a loop is detected, the model receives a message like:

```
### ⚠️ Loop Warning
The screen state has not changed after 4 consecutive observations.
The same action has been repeated 3 time(s).
Choose a different strategy, try a different approach, or stop.
```

### Context Compaction for Long Conversations

The `AgentPromptBuilder.buildHistoryMessages()` method compacts the event history for multi-turn conversations, summarizing tool executions and truncating model responses to 200 characters. This keeps the context window manageable for long tasks.

### Safety and Confirmation System

Every tool call passes through the `SafetyController` before execution:

- **BLOCKED tools** are never executed (e.g., `android.install_app`, `android.uninstall_app`, `android.factory_reset`, `system.root_device`)
- **Blocked keywords** in tool names or arguments trigger automatic blocking (e.g., "install", "root", "flash_firmware")
- **Sensitive keywords** (send, post, delete, share, purchase, payment, account, password, login, logout) escalate to confirmation
- **Confirmation policies** are configurable (see Configuration section)

### Emergency Stop Button

A prominent stop button in the UI immediately cancels the running agent coroutine via `Job.cancel()`. The agent status transitions to `CANCELLED` and the loop halts.

### Task History Persistence

Completed, failed, and cancelled tasks are saved to local storage via `TaskRepository` with goal, status, step count, timing, and error information. View past tasks in the History screen.

### Debug Mode with Redacted Reports

When debug logging is enabled in settings, the Debug screen shows detailed step-by-step traces with API keys and sensitive data automatically redacted.

### Secure API Key Storage

The Ollama API key is stored using `EncryptedSharedPreferences` with:
- **MasterKey scheme:** `AES256_GCM`
- **Key encryption:** `AES256_SIV`
- **Value encryption:** `AES256_GCM`

The key is never logged, never included in source code, and never sent in crash reports.

### Dark Material 3 Theme

The UI is built with Jetpack Compose and Material 3, featuring a dark theme consistent with modern Android design language.

---

## Prerequisites

| Requirement | Version / Details |
|-------------|-------------------|
| **Android Studio** | Hedgehog (2023.1.1) or newer |
| **Android SDK** | Compile SDK 35, Min SDK 28, Target SDK 35 |
| **Kotlin** | 2.1.0 |
| **JDK** | Java 17 |
| **Ollama Cloud API Key** | Required — obtain from [ollama.com](https://ollama.com) |
| **Physical Android device** | API 28+ (API 34+ required for screenshot/vision features) |
| **Emulator** | Android 14+ emulator works but gesture dispatch and screenshots may be limited |
| **Internet connection** | Required for API calls to Ollama Cloud |

---

## Setup Instructions

### Step 1: Clone and Open the Project

```bash
git clone <repository-url>
cd android-agent
```

Open the project in Android Studio (Hedgehog 2023.1.1 or newer).

### Step 2: Sync Gradle

Android Studio will prompt you to sync Gradle. Click **Sync Now**. Ensure you have:
- Android SDK 35 installed (via SDK Manager)
- JDK 17 configured

### Step 3: Build the Debug APK

```
Build > Build Bundle(s)/APK(s) > Build APK(s)
```

The APK will be generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Install on Device

Connect your Android device via USB with **USB Debugging** enabled, then:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Open the App

Launch **Android AI Agent** from your home screen or app drawer.

### Step 6: Configure API Settings

1. Tap the **Settings** tab (gear icon) in the bottom navigation bar
2. Enter your **Ollama API Key** in the API Key field
3. Verify the **Endpoint** is set to `https://ollama.com/api/chat`
4. Verify the **Model** is set to `gemma4:31b`
5. Adjust other settings as desired (temperature, max steps, timeout, vision mode)

### Step 7: Enable the Accessibility Service

1. On the main screen, tap the **Accessibility Service status card** — it will redirect you to system Accessibility settings
2. Alternatively, navigate manually: **Settings > Accessibility > Android AI Agent > Enable**
3. Toggle the service **On**
4. Return to the app — the status card should now show "Connected"

### Step 8: Grant Notification Permission

On Android 13+, the app will request notification permission on first launch. If you missed it:

```
Settings > Apps > Android AI Agent > Notifications > Allow notifications
```

### Step 9: Run Your First Task

Return to the main screen, type a task in the text field, and press **Send**.

---

## Running Your First Task

Let's walk through a concrete example:

**Task:** `Open Chrome and search Google for cats`

Here's what the agent does step by step:

| Step | Agent Status | Action |
|------|-------------|--------|
| 1 | **THINKING** | Observes the current screen (homescreen). Gets the accessibility tree with all visible UI elements. |
| 2 | **THINKING** | Sends observation + task goal to Gemma 4 31B. Model decides to launch Chrome. |
| 3 | **EXECUTING** | Executes `android.launch_app` with the Chrome package name. Chrome opens. |
| 4 | **THINKING** | Re-observes the screen. Sees the Chrome browser UI (address bar, homepage). |
| 5 | **THINKING** | Model decides to click the address bar to focus it. |
| 6 | **EXECUTING** | Executes `android.click` on the address bar node. |
| 7 | **THINKING** | Re-observes. Address bar is focused and editable. |
| 8 | **EXECUTING** | Executes `android.type_text` with `"cats"` and then `android.press_key` with Enter. |
| 9 | **THINKING** | Re-observes. Google search results for "cats" are displayed. |
| 10 | **COMPLETED** | Model calls `agent.finish` with `success: true` and message "Searched Google for cats successfully." |

The entire process typically completes in 30–90 seconds depending on model latency and network speed.

---

## Configuration

All settings are accessible from the **Settings** screen and persisted via `DataStore` (general settings) and `EncryptedSharedPreferences` (API key).

### Settings Reference

| Setting | Key | Default | Description |
|---------|-----|---------|-------------|
| **API Key** | `secure_api_key` | — | Ollama Cloud API key (encrypted with AES256_GCM) |
| **Endpoint** | `endpoint` | `https://ollama.com/api/chat` | Ollama API endpoint URL |
| **Model** | `model` | `gemma4:31b` | Model identifier for the Ollama API |
| **Temperature** | `temperature` | `0.3` | Sampling temperature (lower = more deterministic) |
| **Max Steps** | `max_steps` | `50` | Maximum agent loop iterations before auto-stop |
| **Timeout** | `timeout_ms` | `120000` (2 min) | Per-request timeout in milliseconds |
| **Vision Mode** | `vision_mode` | `AUTO` | When to capture screenshots (see below) |
| **Confirmation Policy** | `confirmation_policy` | `SENSITIVE_ONLY` | When to ask for user confirmation (see below) |
| **Save Screenshots** | `save_screenshots` | `false` | Whether to persist captured screenshots to disk |
| **Debug Logging** | `debug_logging` | `false` | Enable verbose debug output in the Debug screen |
| **Screenshot Resolution** | `screenshot_resolution` | `1024` | Target max dimension for downscaled screenshots |

### Vision Modes

| Mode | When Screenshots Are Sent |
|------|--------------------------|
| `AUTO` | Sent when the accessibility tree has < 3 actionable (clickable/editable) nodes, or when the previous tool call failed. Best balance of performance and capability. |
| `ALWAYS` | Sent with every model request. Slowest but most capable — the model always sees the actual screen. |
| `WHEN_NEEDED` | Not sent automatically. The model can explicitly call `vision.analyze_screen` or `vision.find_visual_target` when it needs visual information. |
| `OFF` | Never captures or sends screenshots. Vision tools are unavailable. Fastest mode but limited to accessibility tree only. |

### Confirmation Policies

| Policy | Behavior |
|--------|----------|
| `SENSITIVE_ONLY` | Only actions involving sensitive keywords (send, post, delete, share, purchase, payment, account, password, login, logout) require confirmation. Default and recommended for most users. |
| `ASK_EVERY_TIME` | **Every** tool call requires explicit user approval before execution. Slowest but safest — full manual oversight. |
| `MANUAL_MODE` | Similar to `ASK_EVERY_TIME` — all actions require confirmation. Useful for auditing or learning how the agent operates. |

---

## Troubleshooting

### Accessibility Service Not Connecting

**Symptoms:** The status card on the main screen shows "Disconnected" or the agent reports "Failed to observe screen."

**Solutions:**
1. Go to **Settings > Accessibility** and verify **Android AI Agent** is toggled **On**
2. Some OEMs (Xiaomi, Samsung, OnePlus) aggressively kill background services. Add the app to the battery optimization whitelist:
   - **Settings > Battery > Battery Optimization > Android AI Agent > Don't optimize**
3. On Samsung devices, also check **Settings > Apps > Android AI Agent > Battery > Unrestricted**
4. Restart the device if the service was recently enabled
5. Check that no other accessibility service is conflicting

### API Connection Errors

**Symptoms:** Agent shows "Model call failed" or "HTTP 401/403/429/500."

**Solutions:**
1. Verify your API key is correct in Settings
2. Check your internet connection
3. Ensure the endpoint URL is correct: `https://ollama.com/api/chat`
4. If you receive HTTP 429, you may have hit rate limits — wait and retry
5. If you receive HTTP 401/403, your API key is invalid or expired
6. Check the **Timeout** setting — increase to 180000ms (3 min) if the model is slow to respond

### Agent Stuck in Loops

**Symptoms:** The agent repeats the same action without making progress.

**Solutions:**
1. The `AgentLoopGuard` should detect this automatically after 3 repeated actions or 4 unchanged observations
2. Press the **Stop** button to manually abort
3. Rephrase your task to be more specific
4. If the target app uses custom views with poor accessibility, enable vision mode (`ALWAYS`) so the model can see the screen

### Screenshots Not Working

**Symptoms:** Vision mode is enabled but the agent never sends screenshots.

**Solutions:**
1. **Android 14+ (API 34) is required** for the `TakeScreenshot` API. Check your Android version.
2. Ensure the Accessibility Service has `canTakeScreenshot` enabled in its config (included by default)
3. On some devices, the first screenshot after enabling the service may fail — try again
4. Check logcat for errors tagged `AccessibilityObserver`

### Model Returning Malformed JSON

**Symptoms:** Agent shows "Failed to parse model response" errors.

**Solutions:**
1. The `DecisionParser` automatically strips markdown fences and extracts JSON objects from responses
2. Lower the **temperature** setting (try `0.1` or `0.2`) for more deterministic outputs
3. Ensure the model is `gemma4:31b` — other models may not follow the JSON response format
4. This is sometimes caused by context overflow — try reducing the max steps or enabling debug mode to inspect raw responses

### App Crashes

**Symptoms:** The app unexpectedly closes.

**Solutions:**
1. Check logcat: `adb logcat -s AndroidRuntime:E *:F`
2. Ensure you're running on a device with **API 28+**
3. If using an emulator, ensure it has sufficient RAM (at least 2GB) and storage
4. Clear app data: **Settings > Apps > Android AI Agent > Storage > Clear Data** (this will erase your API key and settings)
5. File a bug report with the full logcat output

---

## Security Considerations

### API Key Storage

- Stored in `EncryptedSharedPreferences` using the **AES256_GCM** master key scheme
- Key encryption uses **AES256_SIV**; value encryption uses **AES256_GCM**
- The key is **never** logged, **never** included in source code, and **never** transmitted in crash reports
- The `GemmaClient` deliberately omits the API key from all log statements

### Screenshot Privacy

- Screenshots are **off by default** (vision mode defaults to `AUTO`, which only captures when needed)
- When captured, screenshots are compressed to JPEG at 70% quality and base64-encoded
- Screenshots are sent **only** to the configured Ollama API endpoint
- Screenshots are **not** persisted to disk unless "Save Screenshots" is explicitly enabled in settings
- Screenshots may contain sensitive information (notifications, messages, passwords on screen) — use vision with caution

### Permission Model

The app requests **only four permissions**:

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Required for API calls to Ollama Cloud |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the Accessibility Service alive during task execution |
| `POST_NOTIFICATIONS` | Displays task status notifications |
| `SYSTEM_ALERT_WINDOW` | Required for overlay features on some devices |

**No** access to contacts, SMS, call logs, camera, microphone, location, or storage.

### Safety System

- **Blocked tools:** `install_app`, `uninstall_app`, `grant_permission`, `revoke_permission`, `factory_reset`, `enable_developer_options`, `enable_usb_debugging`, `root_device`, `flash_firmware`, and any tool containing blocked keywords
- **Sensitive actions** (involving keywords like send, delete, share, purchase, payment, password, login, etc.) require explicit user confirmation
- **No arbitrary code execution:** The AI can only invoke registered tools. It cannot run shell commands, install APKs, or access arbitrary Android APIs
- **Max step limit:** The agent automatically stops after the configured maximum number of steps (default: 50) to prevent runaway execution

---

## Known Android Limitations

| Limitation | Details |
|-----------|--------|
| **Screenshot API** | Requires Android 14+ (API 34). On older devices, only the accessibility tree is available for observation. |
| **Custom UI frameworks** | Apps that render their own UI (games, canvas-based apps, some custom Views) may not expose meaningful accessibility nodes. |
| **WebView content** | Web content rendered in `WebView` has limited accessibility support. Some web elements may not be discoverable via the accessibility tree. |
| **App restrictions** | Some apps (banking apps, DRM-protected content) explicitly disable accessibility services or restrict node information. |
| **Gesture dispatch** | `canPerformGestures` may not work reliably on all devices and OEM skins. Some manufacturers restrict gesture accessibility features. |
| **OEM battery optimization** | Chinese OEMs (Xiaomi, Huawei, Oppo, Vivo) aggressively kill background services, which can disconnect the Accessibility Service. |
| **Multi-window / split-screen** | The accessibility tree may not accurately reflect the visual layout in multi-window mode. |
| **Overlay dialogs** | System dialogs (permission prompts, app overlays) may not appear in the accessibility tree immediately. |

---

## Project Structure

```
android-agent/
├── build.gradle.kts                          # Root build configuration
├── settings.gradle.kts                       # Module inclusion
├── gradle.properties                         # Gradle properties
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties         # Gradle wrapper version
│   └── libs.versions.toml                    # Centralized dependency versions
└── app/
    ├── build.gradle.kts                      # App module build config (compileSdk 35, minSdk 28)
    ├── proguard-rules.pro                    # ProGuard/R8 rules for release builds
    └── src/main/
        ├── AndroidManifest.xml               # Permissions, service declarations
        ├── res/
        │   ├── xml/
        │   │   └── accessibility_service_config.xml  # AccessibilityService flags & config
        │   ├── values/
        │   │   ├── strings.xml                # App strings & accessibility description
        │   │   └── themes.xml                 # Material 3 theme
        │   └── ...                            # Drawable resources, etc.
        └── java/com/androidagent/aiagent/
            ├── AgentApplication.kt            # Application class, dependency initialization
            ├── accessibility/
            │   ├── AndroidAgentAccessibilityService.kt  # Core service: observes & controls device
            │   ├── AccessibilityObserver.kt   # Captures observations (UI tree + screenshots)
            │   ├── AccessibilityNodeMapper.kt # Converts AccessibilityNodeInfo → UiNode tree
                       │   └── GestureController.kt      # Dispatches touch gestures (swipe, tap)
            ├── ai/
            │   ├── GemmaClient.kt             # HTTP client for Ollama Cloud API
            │   ├── VisionAnalyzer.kt          # Vision-based screen analysis
            │   └── OllamaModels.kt            # Model metadata and capabilities
            ├── agent/
            │   ├── AgentRuntime.kt            # Core agent loop (observe→reason→act→verify)
            │   ├── AgentState.kt              # State types: AgentStatus, AndroidObservation, UiNode, etc.
            │   ├── AgentLoopGuard.kt          # Loop detection: repeated actions, unchanged observations
            │   ├── AgentPromptBuilder.kt      # System prompt & user message construction
            │   └── DecisionParser.kt          # Parses model JSON responses into AgentDecision
            ├── safety/
            │   ├── SafetyController.kt        # Policy engine: ALLOWED / REQUIRES_CONFIRMATION / BLOCKED
            │   ├── ConfirmationManager.kt     # Configurable confirmation policies
            │   └── RiskLevel.kt               # Risk enum: SAFE, CONFIRM, BLOCKED
            ├── tools/
            │   ├── AgentTool.kt               # Data classes: AgentTool, ToolResult, ToolCall, AgentDecision
            │   ├── ToolRegistry.kt            # Dynamic tool registration & schema serialization
            │   ├── ToolExecutor.kt            # Central dispatcher with safety checks
            │   ├── android/
            │   │   ├── LaunchAppTool.kt        # android.launch_app
            │   │   ├── FindTool.kt             # android.find
            │   │   ├── ClickTool.kt            # android.click
            │   │   ├── LongClickTool.kt        # android.long_click
            │   │   ├── TypeTextTool.kt         # android.type_text
            │   │   ├── ClearTextTool.kt        # android.clear_text
            │   │   ├── ScrollTool.kt           # android.scroll
            │   │   ├── SwipeTool.kt            # android.swipe
            │   │   ├── PressKeyTool.kt         # android.press_key
            │   │   ├── BackTool.kt             # android.back
            │   │   ├── HomeTool.kt             # android.home
            │   │   ├── RecentsTool.kt          # android.recents
            │   │   ├── WaitTool.kt             # android.wait
            │   │   ├── ScreenshotTool.kt       # android.screenshot
            │   │   └── InspectScreenTool.kt   # android.inspect_screen
            │   ├── vision/
            │   │   ├── AnalyzeScreenTool.kt    # vision.analyze_screen
            │   │   └── FindVisualTargetTool.kt # vision.find_visual_target
            │   └── agent/
            │       ├── AskUserTool.kt          # agent.ask_user
            │       ├── ConfirmTool.kt          # agent.confirm
            │       ├── FinishTool.kt           # agent.finish
            │       └── StopTool.kt             # agent.stop
            ├── data/
            │   ├── SettingsRepository.kt       # DataStore-backed user preferences
            │   ├── SecureStorage.kt           # EncryptedSharedPreferences (AES256_GCM)
            │   └── TaskRepository.kt          # Task history persistence
            └── ui/
                ├── MainActivity.kt            # Single-activity entry point
                ├── MainScreen.kt              # Main chat/task screen
                ├── SettingsScreen.kt          # Configuration screen
                ├── HistoryScreen.kt           # Past task list
                ├── DebugScreen.kt             # Debug trace viewer
                ├── Components.kt              # Reusable Compose components
                ├── AgentViewModel.kt          # ViewModel bridging UI and AgentRuntime
                └── Theme.kt                   # Material 3 dark theme definition
```

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| AndroidX Core KTX | 1.15.0 | Kotlin extensions for Android core |
| Jetpack Compose BOM | 2024.12.01 | Compose UI framework |
| Material 3 | 1.3.1 | Material Design 3 components |
| Lifecycle | 2.8.7 | ViewModel and lifecycle-aware components |
| Navigation Compose | 2.8.5 | In-app navigation |
| Room | 2.6.1 | Local database for task persistence |
| OkHttp | 4.12.0 | HTTP client for Ollama API |
| Kotlinx Coroutines | 1.9.0 | Asynchronous programming |
| Kotlinx Serialization | 1.7.3 | JSON parsing and serialization |
| Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| DataStore Preferences | 1.1.1 | Type-safe key-value storage |
| Coil Compose | 2.7.0 | Image loading (screenshots, icons) |

---

## Build Variants

| Variant | Minification | Description |
|---------|-------------|-------------|
| `debug` | Off | Faster builds, full debug info, no obfuscation |
| `release` | R8 enabled | Shrunk resources, obfuscated code, optimized APK |

---

## License

```
MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
