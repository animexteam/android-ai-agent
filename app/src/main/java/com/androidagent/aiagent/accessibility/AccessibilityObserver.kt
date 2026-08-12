package com.androidagent.aiagent.accessibility

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.androidagent.aiagent.agent.AndroidObservation
import java.io.ByteArrayOutputStream
import kotlin.random.Random


class AccessibilityObserver {

    companion object {
        private const val TAG = "AccessibilityObserver"
        private const val SCREENSHOT_QUALITY = 70

        @Volatile
        var instance: AccessibilityObserver? = null
            private set
    }

    init {
        instance = this
    }

    /**
     * Observe the current screen state without taking a screenshot.
     * @return [AndroidObservation] containing the UI tree and metadata.
     */
    fun observe(): AndroidObservation {
        val rootNode = AndroidAgentAccessibilityService.instance?.safeGetRootInActiveWindow()
        val packageName = resolvePackageName(rootNode)
        val activityName = resolveActivityName(rootNode)
        val windowTitle = resolveWindowTitle(rootNode)
        val uiTree = AccessibilityNodeMapper.mapNodeTree(rootNode)
        val observationId = generateObservationId()

        Log.d(TAG, "Observation $observationId: package=$packageName, activity=$activityName, nodes=${uiTree.size}")

        return AndroidObservation(
            id = observationId,
            packageName = packageName,
            activityName = activityName,
            windowTitle = windowTitle,
            uiTree = uiTree,
            screenshotBase64 = null,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Observe the current screen state, optionally including a screenshot.
     * @param takeScreenshot Whether to capture and encode a screenshot as base64.
     * @return [AndroidObservation] with optional screenshot data.
     */
    fun observeWithScreenshot(takeScreenshot: Boolean = false): AndroidObservation {
        val rootNode = AndroidAgentAccessibilityService.instance?.safeGetRootInActiveWindow()
        val packageName = resolvePackageName(rootNode)
        val activityName = resolveActivityName(rootNode)
        val windowTitle = resolveWindowTitle(rootNode)
        val uiTree = AccessibilityNodeMapper.mapNodeTree(rootNode)
        val observationId = generateObservationId()

        val screenshotBase64: String? = if (takeScreenshot) {
            captureScreenshotBase64()
        } else {
            null
        }

        Log.d(
            TAG,
            "Observation $observationId: package=$packageName, activity=$activityName, " +
                    "nodes=${uiTree.size}, screenshot=${screenshotBase64 != null}"
        )

        return AndroidObservation(
            id = observationId,
            packageName = packageName,
            activityName = activityName,
            windowTitle = windowTitle,
            uiTree = uiTree,
            screenshotBase64 = screenshotBase64,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get the current foreground application package name.
     * @return Package name string, or null if unavailable.
     */
    fun getCurrentPackage(): String? {
        val service = AndroidAgentAccessibilityService.instance
            ?: return null
        val rootNode = service.safeGetRootInActiveWindow()
            ?: return null
        return try {
            rootNode.packageName?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get current package", e)
            null
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Get the current activity class name.
     * @return Activity name string, or null if unavailable.
     */
    fun getCurrentActivity(): String? {
        val service = AndroidAgentAccessibilityService.instance
            ?: return null
        val rootNode = service.safeGetRootInActiveWindow()
            ?: return null
        return try {
            resolveActivityName(rootNode)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get current activity", e)
            null
        } finally {
            rootNode.recycle()
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun resolvePackageName(rootNode: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return try {
            rootNode.packageName?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve package name from root node", e)
            null
        }
    }

    private fun resolveActivityName(rootNode: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return try {
            // On some devices/OEMs the root node's class name reflects the activity.
            // This is the most reliable approach without using reflection or hidden APIs.
            val className = rootNode.className?.toString()
            if (className != null && className.contains("Activity", ignoreCase = true)) {
                className
            } else {
                // Fallback: check the source class name from the window info
                val windowInfo = AndroidAgentAccessibilityService.instance?.windows
                windowInfo?.firstOrNull()?.let { window ->
                    try {
                        window.layer?.toString()
                    } catch (e: Exception) {
                        null
                    }
                }
                // Return className even if it doesn't contain "Activity" —
                // it may still be useful context for the AI model.
                    ?: className
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve activity name", e)
            null
        }
    }

    private fun resolveWindowTitle(rootNode: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return try {
            // Try to get the window title from the root node's text or content description
            rootNode.text?.toString()
                ?: rootNode.contentDescription?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun generateObservationId(): String {
        val timestamp = System.currentTimeMillis()
        val randomInt = Random.nextInt(0, 10000)
        return "observation_${timestamp}_$randomInt"
    }

    private fun captureScreenshotBase64(): String? {
        val service = AndroidAgentAccessibilityService.instance
            ?: run {
                Log.w(TAG, "Cannot take screenshot: accessibility service not connected")
                return null
            }

        val bitmap: Bitmap? = service.takeScreenshot()
        if (bitmap == null) {
            Log.w(TAG, "Screenshot capture returned null")
            return null
        }

        return try {
            val stream = ByteArrayOutputStream()
            // Use PNG for lossless quality; JPEG with quality is also an option.
            bitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, stream)
            bitmap.recycle()
            val bytes = stream.toByteArray()
            stream.close()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode screenshot to base64", e)
            try {
                bitmap.recycle()
            } catch (_: Exception) { /* already recycled or null */ }
            null
        }
    }
}
