import re

BASE = '/home/z/my-project/android-agent/app/src/main/java/com/androidagent/aiagent/tools/android'

# Fix 1: GemmaClient CancellationException import
with open('/home/z/my-project/android-agent/app/src/main/java/com/androidagent/aiagent/ai/GemmaClient.kt', 'r') as f:
    content = f.read()
content = content.replace('import kotlin.coroutines.CancellationException', 'import kotlinx.coroutines.CancellationException')
with open('/home/z/my-project/android-agent/app/src/main/java/com/androidagent/aiagent/ai/GemmaClient.kt', 'w') as f:
    f.write(content)
print('Fixed GemmaClient import')

# Fix 2: DeleteFileTool - remove early return from withContext
with open(f'{BASE}/DeleteFileTool.kt', 'r') as f:
    content = f.read()
old = '''            withContext(Dispatchers.IO) {
                val file = java.io.File(path)
                if (!file.exists())
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))
                val deleted = file.deleteRecursively()
                ToolResult(
                    success = deleted, toolName = TOOL_NAME,
                    result = if (deleted) buildJsonObject { put("path", path); put("action", "deleted") } else null,
                    error = if (!deleted) ToolError(code = "DELETE_FAILED", message = "Could not delete: $path") else null,
                    observationRequired = false
                )'''
new = '''            withContext(Dispatchers.IO) {
                val file = java.io.File(path)
                if (!file.exists()) {
                    ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))
                } else {
                    val deleted = file.deleteRecursively()
                    ToolResult(
                        success = deleted, toolName = TOOL_NAME,
                        result = if (deleted) buildJsonObject { put("path", path); put("action", "deleted") } else null,
                        error = if (!deleted) ToolError(code = "DELETE_FAILED", message = "Could not delete: $path") else null,
                        observationRequired = false
                    )
                }'''
content = content.replace(old, new)
with open(f'{BASE}/DeleteFileTool.kt', 'w') as f:
    f.write(content)
print('Fixed DeleteFileTool')

# Fix 3: ListFilesTool - same pattern
with open(f'{BASE}/ListFilesTool.kt', 'r') as f:
    content = f.read()
old = '''            withContext(Dispatchers.IO) {
                val dir = java.io.File(path)
                if (!dir.exists() || !dir.isDirectory)
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "DIR_NOT_FOUND", message = "Directory not found: $path"))'''
new = '''            withContext(Dispatchers.IO) {
                val dir = java.io.File(path)
                if (!dir.exists() || !dir.isDirectory) {
                    ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "DIR_NOT_FOUND", message = "Directory not found: $path"))
                } else {'''
content = content.replace(old, new)
# Also fix closing brace
old2 = '''                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", dir.absolutePath); put("items", items)
                        put("total", dir.listFiles()?.size ?: 0)
                    },
                    observationRequired = false
                )
            }
        }'''
new2 = '''                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", dir.absolutePath); put("items", items)
                        put("total", dir.listFiles()?.size ?: 0)
                    },
                    observationRequired = false
                )
                }
            }
        }'''
content = content.replace(old2, new2)
with open(f'{BASE}/ListFilesTool.kt', 'w') as f:
    f.write(content)
print('Fixed ListFilesTool')

# Fix 4: ReadFileTool
with open(f'{BASE}/ReadFileTool.kt', 'r') as f:
    content = f.read()
old = '''            withContext(Dispatchers.IO) {
                val file = if (path.startsWith("/")) java.io.File(path) else java.io.File(service.filesDir, path)
                if (!file.exists())
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))'''
new = '''            withContext(Dispatchers.IO) {
                val file = if (path.startsWith("/")) java.io.File(path) else java.io.File(service.filesDir, path)
                if (!file.exists()) {
                    ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))
                } else {'''
content = content.replace(old, new)
# Fix closing
old2 = '''                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", file.absolutePath); put("size_bytes", file.length())
                        put("content", text); put("truncated", text.length >= maxChars)
                    },
                    observationRequired = false
                )
            }
        }'''
new2 = '''                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", file.absolutePath); put("size_bytes", file.length())
                        put("content", text); put("truncated", text.length >= maxChars)
                    },
                    observationRequired = false
                )
                }
            }
        }'''
content = content.replace(old2, new2)
with open(f'{BASE}/ReadFileTool.kt', 'w') as f:
    f.write(content)
print('Fixed ReadFileTool')

# Fix 5: MediaControlTool - fix return and add("play") -> add(JsonPrimitive("play"))
with open(f'{BASE}/MediaControlTool.kt', 'r') as f:
    content = f.read()
# Fix early return
old = '''                    else -> return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "INVALID_ACTION", message = "Unknown action: $action"))'''
new = '''                    else -> {
                        ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "INVALID_ACTION", message = "Unknown action: $action"))
                    }'''
content = content.replace(old, new)
# Fix add("play") -> add(JsonPrimitive("play"))
content = content.replace('add("play"); add("pause"); add("next"); add("previous"); add("stop")',
    'add(JsonPrimitive("play")); add(JsonPrimitive("pause")); add(JsonPrimitive("next")); add(JsonPrimitive("previous")); add(JsonPrimitive("stop"))')
with open(f'{BASE}/MediaControlTool.kt', 'w') as f:
    f.write(content)
print('Fixed MediaControlTool')

# Fix 6: DismissNotificationTool - fix nm.cancel() call and nullability
with open(f'{BASE}/DismissNotificationTool.kt', 'r') as f:
    content = f.read()
old = '''                        sbNotif.notification?.let { nm.cancel(sbNotif.id, sbNotif.notification.tag ?: "") }'''
new = '''                        nm?.cancel(sbNotif.notification?.tag, sbNotif.id)'''
content = content.replace(old, new)
with open(f'{BASE}/DismissNotificationTool.kt', 'w') as f:
    f.write(content)
print('Fixed DismissNotificationTool')

# Fix 7: GetBatteryInfoTool - fix nullable div
with open(f'{BASE}/GetBatteryInfoTool.kt', 'r') as f:
    content = f.read()
content = content.replace('batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10.0)',
    '(batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0')
content = content.replace('batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.div(1000.0)',
    '(batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0')
with open(f'{BASE}/GetBatteryInfoTool.kt', 'w') as f:
    f.write(content)
print('Fixed GetBatteryInfoTool')

# Fix 8: GetNetworkInfoTool - fix removeSurrounding
with open(f'{BASE}/GetNetworkInfoTool.kt', 'r') as f:
    content = f.read()
content = content.replace('String(charArrayOf(34))', '"\\""')
with open(f'{BASE}/GetNetworkInfoTool.kt', 'w') as f:
    f.write(content)
print('Fixed GetNetworkInfoTool')

print('\nAll fixes applied!')
