#!/usr/bin/env python3
"""Generate all new Android API tool files for the comprehensive v5.0 ecosystem."""
import os

TOOLS_DIR = "/home/z/my-project/android-agent/app/src/main/java/com/androidagent/aiagent/tools/android"

HEADER = '''package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
'''

def no_service(tool_name):
    return f'''    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )
'''

def write_file(filename, content):
    path = os.path.join(TOOLS_DIR, filename)
    with open(path, 'w') as f:
        f.write(content)
    print(f"  Created {filename}")

# ============================================================================
# 1. GetDeviceInfoTool
# ============================================================================
write_file("GetDeviceInfoTool.kt", HEADER + '''
class GetDeviceInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val packageInfo = service.packageManager.getPackageInfo(service.packageName, 0)
                val displayMetrics = service.resources.displayMetrics
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("manufacturer", android.os.Build.MANUFACTURER)
                        put("model", android.os.Build.MODEL)
                        put("device", android.os.Build.DEVICE)
                        put("product", android.os.Build.PRODUCT)
                        put("brand", android.os.Build.BRAND)
                        put("android_version", android.os.Build.VERSION.RELEASE)
                        put("sdk_int", android.os.Build.VERSION.SDK_INT)
                        put("screen_width", displayMetrics.widthPixels)
                        put("screen_height", displayMetrics.heightPixels)
                        put("density", displayMetrics.density)
                        put("app_version", packageInfo.versionName ?: "unknown")
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device info", e)
            errorResult(e)
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.get_device_info"
        private const val TAG = "GetDeviceInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Get device information: manufacturer, model, Android version, screen size, etc.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "DEVICE_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 2. GetBatteryInfoTool
# ============================================================================
write_file("GetBatteryInfoTool.kt", HEADER + '''
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class GetBatteryInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val bm = service.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager
                val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val isCharging = bm?.isCharging ?: false
                val chargeType = when {
                    bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) > 0 -> "plugged"
                    else -> "unknown"
                }
                val batteryIntent = service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10.0) ?: 0.0
                val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.div(1000.0) ?: 0.0
                val health = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    else -> "unknown"
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("level", level)
                        put("is_charging", isCharging)
                        put("health", health)
                        put("temperature_c", temperature)
                        put("voltage_v", voltage)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery info", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_battery_info"
        private const val TAG = "GetBatteryInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get battery level, charging status, health, temperature, and voltage.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "BATTERY_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 3. GetNetworkInfoTool
# ============================================================================
write_file("GetNetworkInfoTool.kt", HEADER + '''
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class GetNetworkInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val cm = service.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(network)
                val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                val hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                val downstreamMbps = caps?.getLinkDownstreamBandwidthKbps()?.div(1000) ?: 0
                val wifiManager = service.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                val ssid = try { wifiInfo?.ssid?.removeSurrounding(\"\"\") } catch (_: Exception) { null }
                val ip = try { java.net.InetAddress.getByAddress(
                    java.math.BigInteger.valueOf(wifiInfo?.ipAddress?.toLong() ?: 0).toByteArray()
                ).hostAddress } catch (_: Exception) { null }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("is_connected", isConnected)
                        put("has_wifi", hasWifi)
                        put("has_cellular", hasCellular)
                        put("has_ethernet", hasEthernet)
                        put("wifi_ssid", ssid ?: "not connected")
                        put("ip_address", ip ?: "unknown")
                        put("downstream_mbps", downstreamMbps)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network info", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_network_info"
        private const val TAG = "GetNetworkInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get network connectivity info: WiFi SSID, IP address, connection type, speed.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "NETWORK_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 4. GetStorageInfoTool
# ============================================================================
write_file("GetStorageInfoTool.kt", HEADER + '''
import android.os.Environment
import android.os.StatFs

class GetStorageInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val path = Environment.getExternalStorageDirectory()
                val stat = StatFs(path.absolutePath)
                val total = stat.totalBytes
                val available = stat.availableBytes
                val used = total - available
                val totalGB = String.format("%.2f", total / (1024.0 * 1024.0 * 1024.0))
                val availableGB = String.format("%.2f", available / (1024.0 * 1024.0 * 1024.0))
                val usedGB = String.format("%.2f", used / (1024.0 * 1024.0 * 1024.0))
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("total_gb", totalGB)
                        put("used_gb", usedGB)
                        put("available_gb", availableGB)
                        put("used_percent", (used * 100 / total).toInt())
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get storage info", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_storage_info"
        private const val TAG = "GetStorageInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get device storage information: total, used, and available space.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "STORAGE_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 5. GetClipboardTool
# ============================================================================
write_file("GetClipboardTool.kt", HEADER + '''
import android.content.ClipData
import android.content.ClipboardManager

class GetClipboardTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                val text = if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(service).toString()
                } else null
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("has_content", text != null)
                        put("content", text ?: "")
                        put("length", text?.length ?: 0)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get clipboard", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_clipboard"
        private const val TAG = "GetClipboardTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Read the current clipboard content.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CLIPBOARD_READ_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 6. MediaControlTool
# ============================================================================
write_file("MediaControlTool.kt", HEADER + '''
import android.content.Intent
import android.view.KeyEvent

class MediaControlTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val action = args["action"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'action' is required: play, pause, next, previous, stop"))
        return try {
            withContext(Dispatchers.IO) {
                val keycode = when (action.lowercase()) {
                    "play", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                    else -> return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "INVALID_ACTION", message = "Unknown action: $action"))
                }
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keycode)
                service.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply { putExtra(Intent.EXTRA_KEY_EVENT, downEvent) })
                kotlinx.coroutines.delay(50)
                service.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply { putExtra(Intent.EXTRA_KEY_EVENT, upEvent) })
                ToolResult(success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("action", action.lowercase()); put("status", "sent") },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media control failed", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.media_control"
        private const val TAG = "MediaControlTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Control media playback: play, pause, next, previous, stop.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("play"); add("pause"); add("next"); add("previous"); add("stop") })
                        put("description", "Media action to perform")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("action")) })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "MEDIA_CONTROL_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 7. OpenSettingsTool
# ============================================================================
write_file("OpenSettingsTool.kt", HEADER + '''
import android.content.Intent
import android.provider.Settings

class OpenSettingsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val page = args["page"]?.jsonPrimitive?.content
        return try {
            val intent = when (page?.lowercase()) {
                "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "display", "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                "sound", "volume", "audio" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                "apps", "applications" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                "location", "gps" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                "date", "time" -> Intent(Settings.ACTION_DATE_SETTINGS)
                "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "network", "data_usage" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                "about" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                "nfc" -> Intent(Settings.ACTION_NFC_SETTINGS)
                "default_apps" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                "special_access" -> Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
                "locale", "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("page", page ?: "main"); put("action", "opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.open_settings"
        private const val TAG = "OpenSettingsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open a specific Android Settings page. Pages: wifi, bluetooth, display, sound, battery, storage, apps, notifications, security, location, date, network, about, airplane, nfc, accessibility, language.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("page", buildJsonObject {
                        put("type", "string")
                        put("description", "Settings page name (wifi, bluetooth, display, sound, battery, storage, apps, notifications, security, location, etc.)")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "OPEN_SETTINGS_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 8. SendEmailTool
# ============================================================================
write_file("SendEmailTool.kt", HEADER + '''
import android.content.Intent
import android.net.Uri

class SendEmailTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val to = args["to"]?.jsonPrimitive?.content
        val subject = args["subject"]?.jsonPrimitive?.content ?: ""
        val body = args["body"]?.jsonPrimitive?.content ?: ""
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$to")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(Intent.createChooser(intent, "Send email").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("to", to ?: ""); put("subject", subject); put("action", "email_chooser_opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.send_email"
        private const val TAG = "SendEmailTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open email composer with pre-filled to, subject, and body.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("to", buildJsonObject { put("type", "string"); put("description", "Recipient email address") })
                    put("subject", buildJsonObject { put("type", "string"); put("description", "Email subject") })
                    put("body", buildJsonObject { put("type", "string"); put("description", "Email body text") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "EMAIL_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 9. SetAlarmTool
# ============================================================================
write_file("SetAlarmTool.kt", HEADER + '''
import android.content.Intent
import android.provider.AlarmClock

class SetAlarmTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val hour = args["hour"]?.jsonPrimitive?.content?.toIntOrNull()
        val minute = args["minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val message = args["message"]?.jsonPrimitive?.content
        if (hour == null || hour !in 0..23)
            return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'hour' (0-23) is required"))
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("hour", hour); put("minute", minute)
                    put("message", message ?: ""); put("action", "alarm_set")
                },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.set_alarm"
        private const val TAG = "SetAlarmTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Set an alarm via the system Alarm app. Provide hour (0-23) and optionally minute and message.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("hour", buildJsonObject { put("type", "integer"); put("description", "Hour (0-23)") })
                    put("minute", buildJsonObject { put("type", "integer"); put("description", "Minute (0-59), default 0") })
                    put("message", buildJsonObject { put("type", "string"); put("description", "Optional alarm label") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("hour")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "SET_ALARM_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 10. SetTimerTool
# ============================================================================
write_file("SetTimerTool.kt", HEADER + '''
import android.content.Intent
import android.provider.AlarmClock

class SetTimerTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val seconds = args["seconds"]?.jsonPrimitive?.content?.toIntOrNull()
        val message = args["message"]?.jsonPrimitive?.content
        if (seconds == null || seconds <= 0)
            return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'seconds' (positive integer) is required"))
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("seconds", seconds); put("message", message ?: ""); put("action", "timer_set") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.set_timer"
        private const val TAG = "SetTimerTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Set a countdown timer via the system Clock app.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("seconds", buildJsonObject { put("type", "integer"); put("description", "Timer duration in seconds") })
                    put("message", buildJsonObject { put("type", "string"); put("description", "Optional timer label") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("seconds")) })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "SET_TIMER_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 11. ToastTool
# ============================================================================
write_file("ToastTool.kt", HEADER + '''
import android.widget.Toast

class ToastTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val message = args["message"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'message' is required"))
        return try {
            withContext(Dispatchers.Main) {
                Toast.makeText(service, message, Toast.LENGTH_LONG).show()
            }
            kotlinx.coroutines.delay(500)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("message", message); put("action", "toast_shown") },
                observationRequired = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.toast"
        private const val TAG = "ToastTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Show a brief toast message on screen.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("message", buildJsonObject { put("type", "string"); put("description", "Message to show") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("message")) })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "TOAST_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 12. VibrateTool
# ============================================================================
write_file("VibrateTool.kt", HEADER + '''
import android.os.VibrationEffect

class VibrateTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val durationMs = args["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 200L
        val pattern = args["pattern"]?.jsonPrimitive?.content
        return try {
            val vibrator = service.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                ?: return ToolResult(success = false, toolName = TOOL_NAME,
                    error = ToolError(code = "NO_VIBRATOR", message = "Device has no vibrator"))
            withContext(Dispatchers.IO) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (pattern != null) {
                        val timings = pattern.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                        if (timings.size >= 2) {
                            val effect = VibrationEffect.createWaveform(timings, -1)
                            vibrator.vibrate(effect)
                        } else {
                            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(durationMs)
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("duration_ms", durationMs); put("action", "vibrated") },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate failed", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.vibrate"
        private const val TAG = "VibrateTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Vibrate the device. Specify duration in ms or a pattern like '100,50,100'.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("duration", buildJsonObject { put("type", "integer"); put("description", "Duration in ms (default 200)") })
                    put("pattern", buildJsonObject { put("type", "string"); put("description", "Vibration pattern: comma-separated ms values e.g. '100,50,100'") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "VIBRATE_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 13. GetContactsTool
# ============================================================================
write_file("GetContactsTool.kt", HEADER + '''
import android.provider.ContactsContract

class GetContactsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val limit = args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 20
        val query = args["query"]?.jsonPrimitive?.content
        return try {
            withContext(Dispatchers.IO) {
                val contacts = mutableListOf<kotlin.Pair<String, String>>()
                val selection = if (query != null) "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?" else null
                val selectionArgs = if (query != null) arrayOf("%$query%") else null
                val cursor = service.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                    selection, selectionArgs,
                    "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit"
                )
                cursor?.use {
                    while (it.moveToNext() && contacts.size < limit) {
                        val id = it.getString(0)
                        val name = it.getString(1)
                        val phoneCursor = service.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id), null
                        )
                        val phone = phoneCursor?.use { pc ->
                            if (pc.moveToFirst()) pc.getString(0) else null
                        }
                        contacts.add(name to (phone ?: "no phone"))
                    }
                }
                val resultArray = buildJsonArray {
                    for ((name, phone) in contacts) {
                        add(buildJsonObject { put("name", name); put("phone", phone) })
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("contacts", resultArray); put("count", contacts.size) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get contacts", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_contacts"
        private const val TAG = "GetContactsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get contacts list with names and phone numbers. Optionally filter by query or limit count.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject { put("type", "string"); put("description", "Search query to filter contacts by name") })
                    put("limit", buildJsonObject { put("type", "integer"); put("description", "Max contacts to return (default 20)") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CONTACTS_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 14. ReadFileTool
# ============================================================================
write_file("ReadFileTool.kt", HEADER + '''

class ReadFileTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'path' is required"))
        val maxChars = args["max_chars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5000
        return try {
            withContext(Dispatchers.IO) {
                val file = if (path.startsWith("/")) java.io.File(path) else java.io.File(service.filesDir, path)
                if (!file.exists())
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))
                val text = file.readText().take(maxChars)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", file.absolutePath); put("size_bytes", file.length())
                        put("content", text); put("truncated", text.length >= maxChars)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.read_file"
        private const val TAG = "ReadFileTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Read a text file from device storage. Supports app-private files and (with permissions) shared storage.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "Absolute or relative file path") })
                    put("max_chars", buildJsonObject { put("type", "integer"); put("description", "Max characters to read (default 5000)") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "READ_FILE_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 15. WriteFileTool
# ============================================================================
write_file("WriteFileTool.kt", HEADER + '''

class WriteFileTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'path' is required"))
        val content = args["content"]?.jsonPrimitive?.content ?: ""
        val append = args["append"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        return try {
            withContext(Dispatchers.IO) {
                val file = if (path.startsWith("/")) java.io.File(path) else java.io.File(service.filesDir, path)
                file.parentFile?.mkdirs()
                if (append) file.appendText(content) else file.writeText(content)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", file.absolutePath); put("size_bytes", file.length())
                        put("action", if (append) "appended" else "written")
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.write_file"
        private const val TAG = "WriteFileTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Write text to a file. Create directories as needed. Set append=true to append instead of overwrite.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "File path") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "Content to write") })
                    put("append", buildJsonObject { put("type", "boolean"); put("description", "Append to existing file (default false)") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("path")); add(JsonPrimitive("content")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "WRITE_FILE_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 16. ListFilesTool
# ============================================================================
write_file("ListFilesTool.kt", HEADER + '''

class ListFilesTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: android.os.Environment.getExternalStorageDirectory().absolutePath
        return try {
            withContext(Dispatchers.IO) {
                val dir = java.io.File(path)
                if (!dir.exists() || !dir.isDirectory)
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "DIR_NOT_FOUND", message = "Directory not found: $path"))
                val items = buildJsonArray {
                    val files = (dir.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }.take(100)
                    for (f in files) {
                        add(buildJsonObject {
                            put("name", f.name)
                            put("is_directory", f.isDirectory)
                            put("size_bytes", if (f.isFile) f.length() else 0)
                            put("last_modified", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(f.lastModified())))
                        })
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", dir.absolutePath); put("items", items)
                        put("total", dir.listFiles()?.size ?: 0)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.list_files"
        private const val TAG = "ListFilesTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "List files and directories at a given path. Defaults to external storage root.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "Directory path (default: external storage root)") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "LIST_FILES_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 17. DeleteFileTool
# ============================================================================
write_file("DeleteFileTool.kt", HEADER + '''

class DeleteFileTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'path' is required"))
        return try {
            withContext(Dispatchers.IO) {
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
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.delete_file"
        private const val TAG = "DeleteFileTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Delete a file or directory recursively.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "File or directory path to delete") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "DELETE_FILE_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 18. UninstallAppTool
# ============================================================================
write_file("UninstallAppTool.kt", HEADER + '''
import android.content.Intent
import android.net.Uri

class UninstallAppTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val packageName = args["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'package_name' is required"))
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("package", packageName); put("action", "uninstall_dialog_opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall app", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.uninstall_app"
        private const val TAG = "UninstallAppTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open the system uninstall dialog for a package.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("package_name", buildJsonObject { put("type", "string"); put("description", "Package name to uninstall") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("package_name")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "UNINSTALL_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 19. ClearAppDataTool
# ============================================================================
write_file("ClearAppDataTool.kt", HEADER + '''
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class ClearAppDataTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val packageName = args["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'package_name' is required"))
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("package", packageName); put("action", "opened_app_settings_for_clear_data") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.clear_app_data"
        private const val TAG = "ClearAppDataTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open app settings page for clearing app data/cache.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("package_name", buildJsonObject { put("type", "string"); put("description", "Package name") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("package_name")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CLEAR_DATA_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 20. GetNotificationsTool
# ============================================================================
write_file("GetNotificationsTool.kt", HEADER + '''
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.app.NotificationManager

class GetNotificationsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val nm = service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? NotificationManager
                val activeNotifications = nm?.activeNotifications ?: arrayOf()
                val notifs = buildJsonArray {
                    for (sbNotif in activeNotifications) {
                        val extras = sbNotif.notification?.extras
                        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        val pkg = sbNotif.packageName
                        val key = sbNotif.key
                        if (title.isNotEmpty() || text.isNotEmpty()) {
                            add(buildJsonObject {
                                put("package", pkg); put("key", key)
                                put("title", title); put("text", text)
                            })
                        }
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("notifications", notifs); put("count", notifs.size) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get notifications", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_notifications"
        private const val TAG = "GetNotificationsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get currently active notifications with titles and text.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "GET_NOTIFICATIONS_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 21. DismissNotificationTool
# ============================================================================
write_file("DismissNotificationTool.kt", HEADER + '''
import android.service.notification.StatusBarNotification

class DismissNotificationTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val key = args["key"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'key' (notification key) is required")
        return try {
            withContext(Dispatchers.IO) {
                val nm = service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                val sbNotifs = nm?.activeNotifications ?: arrayOf()
                var dismissed = false
                for (sbNotif in sbNotifs) {
                    if (sbNotif.key == key) {
                        sbNotif.notification?.let { nm.cancel(sbNotif.id, sbNotif.notification.tag ?: "") }
                        dismissed = true
                        break
                    }
                }
                ToolResult(
                    success = dismissed, toolName = TOOL_NAME,
                    result = if (dismissed) buildJsonObject { put("key", key); put("action", "dismissed") } else null,
                    error = if (!dismissed) ToolError(code = "NOT_FOUND", message = "Notification key not found: $key") else null,
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss notification", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.dismiss_notification"
        private const val TAG = "DismissNotificationTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Dismiss a notification by its key (obtained from android.get_notifications).",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("key", buildJsonObject { put("type", "string"); put("description", "Notification key to dismiss") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("key")) })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "DISMISS_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 22. SendNotificationTool
# ============================================================================
write_file("SendNotificationTool.kt", HEADER + '''
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat

@Suppress("DEPRECATION")
class SendNotificationTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val title = args["title"]?.jsonPrimitive?.content ?: "Android-Use"
        val text = args["text"]?.jsonPrimitive?.content ?: ""
        return try {
            withContext(Dispatchers.IO) {
                val nm = service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channelId = "agent_custom"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(channelId, "Agent", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Notifications from Android-Use agent"
                    }
                    nm?.createNotificationChannel(channel)
                }
                val notifId = (System.currentTimeMillis() % 100000).toInt()
                val intent = service.packageManager.getLaunchIntentForPackage(service.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pi = PendingIntent.getActivity(service, notifId, intent, PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(service, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(notifId, notification)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("title", title); put("text", text); put("id", notifId) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.send_notification"
        private const val TAG = "SendNotificationTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Post a notification to the device notification bar with a title and text.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("title", buildJsonObject { put("type", "string"); put("description", "Notification title") })
                    put("text", buildJsonObject { put("type", "string"); put("description", "Notification body text") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "SEND_NOTIFICATION_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 23. OpenCameraTool
# ============================================================================
write_file("OpenCameraTool.kt", HEADER + '''
import android.content.Intent
import android.provider.MediaStore

class OpenCameraTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val mode = args["mode"]?.jsonPrimitive?.content
        return try {
            val intent = when (mode?.lowercase()) {
                "video" -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                else -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("mode", mode ?: "photo"); put("action", "camera_opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.open_camera"
        private const val TAG = "OpenCameraTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open the device camera app. Mode: 'photo' (default) or 'video'.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("mode", buildJsonObject { put("type", "string"); put("description", "'photo' or 'video'") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CAMERA_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 24. GetLocationTool
# ============================================================================
write_file("GetLocationTool.kt", HEADER + '''
import android.location.Location
import android.location.LocationManager

class GetLocationTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val lm = service.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
                var bestLocation: Location? = null
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                    try {
                        if (lm?.isProviderEnabled(provider) == true) {
                            val loc = lm.getLastKnownLocation(provider)
                            if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                                bestLocation = loc
                            }
                        }
                    } catch (_: SecurityException) { /* no permission */ }
                }
                if (bestLocation != null) {
                    ToolResult(
                        success = true, toolName = TOOL_NAME,
                        result = buildJsonObject {
                            put("latitude", bestLocation.latitude)
                            put("longitude", bestLocation.longitude)
                            put("accuracy_m", bestLocation.accuracy)
                            put("provider", bestLocation.provider ?: "unknown")
                        },
                        observationRequired = false
                    )
                } else {
                    ToolResult(
                        success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "NO_LOCATION", message = "No location available. Enable GPS or check permissions.")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_location"
        private const val TAG = "GetLocationTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get the device's last known GPS/location coordinates.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "LOCATION_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 25. ShellCommandTool
# ============================================================================
write_file("ShellCommandTool.kt", HEADER + '''

class ShellCommandTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val command = args["command"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'command' is required"))
        val timeoutMs = args["timeout"]?.jsonPrimitive?.content?.toLongOrNull() ?: 10000L
        val dangerousPrefixes = listOf("rm -rf /", "format ", "dd if=")
        for (prefix in dangerousPrefixes) {
            if (command.contains(prefix))
                return ToolResult(success = false, toolName = TOOL_NAME,
                    error = ToolError(code = "DANGEROUS_COMMAND", message = "Command blocked for safety: $prefix"))
        }
        return try {
            withContext(Dispatchers.IO) {
                val proc = ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText().take(10000)
                val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "TIMEOUT", message = "Command timed out after ${timeoutMs}ms"))
                } else {
                    ToolResult(
                        success = true, toolName = TOOL_NAME,
                        result = buildJsonObject {
                            put("exit_code", proc.exitValue())
                            put("output", output)
                        },
                        observationRequired = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.shell"
        private const val TAG = "ShellCommandTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Execute a shell command. Use for custom operations not covered by other tools. Dangerous commands are blocked.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("command", buildJsonObject { put("type", "string"); put("description", "Shell command to execute") })
                    put("timeout", buildJsonObject { put("type", "integer"); put("description", "Timeout in ms (default 10000)") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("command")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "SHELL_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 26. GetRunningAppsTool
# ============================================================================
write_file("GetRunningAppsTool.kt", HEADER + '''
import android.app.ActivityManager

@Suppress("DEPRECATION")
class GetRunningAppsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val am = service.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager
                val processes = am?.runningAppProcesses ?: emptyList()
                val apps = buildJsonArray {
                    for (proc in processes.sortedByDescending { it.importance }) {
                        add(buildJsonObject {
                            put("package", proc.processName)
                            put("importance", when (proc.importance) {
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND -> "background"
                                else -> "other"
                            })
                        })
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("running_apps", apps); put("count", apps.size) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running apps", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_running_apps"
        private const val TAG = "GetRunningAppsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get list of currently running apps/processes with their importance level.",
            inputSchema = buildJsonObject { put("type", "object") },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "RUNNING_APPS_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 27. ToggleAutoRotateTool
# ============================================================================
write_file("ToggleAutoRotateTool.kt", HEADER + '''
import android.provider.Settings

class ToggleAutoRotateTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val state = args["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        return try {
            withContext(Dispatchers.IO) {
                if (state != null) {
                    Settings.System.putInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (state) 1 else 0)
                } else {
                    val current = Settings.System.getInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) 0 else 1)
                }
                val final = Settings.System.getInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("auto_rotate", final) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle auto-rotate", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.toggle_auto_rotate"
        private const val TAG = "ToggleAutoRotateTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Toggle auto-rotate screen or set to on/off.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("enabled", buildJsonObject { put("type", "boolean"); put("description", "Set to true/false. Omit to toggle.") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "AUTO_ROTATE_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

# ============================================================================
# 28. CreateContactTool
# ============================================================================
write_file("CreateContactTool.kt", HEADER + '''
import android.content.ContentValues
import android.provider.ContactsContract

class CreateContactTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val name = args["name"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'name' is required"))
        val phone = args["phone"]?.jsonPrimitive?.content
        val email = args["email"]?.jsonPrimitive?.content
        return try {
            withContext(Dispatchers.IO) {
                val ops = ArrayList<android.content.ContentProviderOperation>()
                val rawContactIndex = 0
                ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build())
                ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build())
                if (phone != null) {
                    ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
                if (email != null) {
                    ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build())
                }
                val results = service.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("name", name); put("phone", phone ?: ""); put("email", email ?: ""); put("action", "contact_created") },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create contact", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.create_contact"
        private const val TAG = "CreateContactTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Create a new contact with name, optional phone and email.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("name", buildJsonObject { put("type", "string"); put("description", "Contact display name") })
                    put("phone", buildJsonObject { put("type", "string"); put("description", "Phone number") })
                    put("email", buildJsonObject { put("type", "string"); put("description", "Email address") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("name")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
    ''' + no_service("TOOL_NAME") + '''
    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CREATE_CONTACT_FAILED", message = e.message ?: "Unknown error")
    )
}
''')

print("\nAll 28 new tool files generated successfully!")
