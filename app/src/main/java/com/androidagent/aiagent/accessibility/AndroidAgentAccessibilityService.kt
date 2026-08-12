package com.androidagent.aiagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt


class AndroidAgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentAccessibilitySvc"

        @Volatile
        var instance: AndroidAgentAccessibilityService? = null
            private set

        val isConnected: Boolean
            get() = instance != null && connectionState.get()

        private val connectionState = AtomicBoolean(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectionState.set(true)
        Log.i(TAG, "AccessibilityService connected and ready")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // Observation is performed on-demand, not reactively on events.
        // No-op to avoid unnecessary processing.
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        connectionState.set(false)
        Log.i(TAG, "AccessibilityService destroyed and disconnected")
    }

    // ---------------------------------------------------------------------------
    // Node tree access
    // ---------------------------------------------------------------------------

    fun safeGetRootInActiveWindow(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get root in active window", e)
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Node actions
    // ---------------------------------------------------------------------------

    fun performClick(nodeInfo: AccessibilityNodeInfo): Boolean {
        return try {
            if (!nodeInfo.isClickable) {
                // Attempt to find a clickable parent
                val clickableParent = findClickableParent(nodeInfo)
                if (clickableParent != null) {
                    val result = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clickableParent.recycle()
                    result
                } else {
                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            } else {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to perform click on node", e)
            false
        }
    }

    fun performLongClick(nodeInfo: AccessibilityNodeInfo): Boolean {
        return try {
            if (!nodeInfo.isLongClickable) {
                val longClickableParent = findLongClickableParent(nodeInfo)
                if (longClickableParent != null) {
                    val result = longClickableParent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    longClickableParent.recycle()
                    result
                } else {
                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                }
            } else {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to perform long click on node", e)
            false
        }
    }

    fun performSetText(nodeInfo: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set text on node", e)
            false
        }
    }

    /**
     * Perform a scroll action on a node.
     * @param direction FORWARD (1) to scroll down/right, BACKWARD (-1) to scroll up/left.
     */
    fun performScroll(nodeInfo: AccessibilityNodeInfo, direction: Int): Boolean {
        return try {
            val action = when {
                direction >= 0 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            nodeInfo.performAction(action)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to perform scroll on node", e)
            false
        }
    }

    // ---------------------------------------------------------------------------
    // Global actions
    // ---------------------------------------------------------------------------

    fun pressBack(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to press back", e)
            false
        }
    }

    fun pressHome(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to press home", e)
            false
        }
    }

    fun pressRecents(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to press recents", e)
            false
        }
    }

    // ---------------------------------------------------------------------------
    // Gesture
    // ---------------------------------------------------------------------------

    /**
     * Perform a custom gesture from (startX, startY) to (endX, endY).
     * @return true if the gesture was dispatched successfully.
     */
    fun performGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300
    ): Boolean {
        return GestureController.performSwipe(this, startX, startY, endX, endY, durationMs)
    }

    // ---------------------------------------------------------------------------
    // Screenshot
    // ---------------------------------------------------------------------------

    /**
     * Take a screenshot of the current screen. Requires API 34+.
     * @return Bitmap of the screenshot, or null if not supported / failed.
     */
    fun takeScreenshot(): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            takeScreenshotApi34()
        } else {
            Log.w(TAG, "takeScreenshot requires API 34 (current: ${Build.VERSION.SDK_INT})")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun takeScreenshotApi34(): Bitmap? {
        return try {
            val latch = CountDownLatch(1)
            var capturedBitmap: Bitmap? = null

            val callback = @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    capturedBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                    hardwareBuffer.close()
                    latch.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "takeScreenshot failed with errorCode=$errorCode")
                    latch.countDown()
                }
            }

            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                applicationContext.mainExecutor,
                callback
            )

            // Wait up to 3 seconds for the screenshot callback
            val completed = latch.await(3, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "takeScreenshot timed out waiting for callback")
                null
            } else {
                capturedBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot threw an exception", e)
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Key events
    // ---------------------------------------------------------------------------

    fun dispatchKeyEvent(keyCode: Int): Boolean {
        return try {
            // AccessibilityService cannot directly inject key events.
            // This method is a no-op placeholder that logs the attempt.
            Log.w(TAG, "dispatchKeyEvent: direct key injection not supported via AccessibilityService (keyCode=$keyCode)")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch key event (keyCode=$keyCode)", e)
            false
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 20) {
            if (current.isClickable) {
                return current
            }
            val next = current.parent
            if (next !== current) {
                current.recycle()
                current = next
            } else {
                // Parent cycle detected
                current.recycle()
                break
            }
            depth++
        }
        return null
    }

    private fun findLongClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 20) {
            if (current.isLongClickable) {
                return current
            }
            val next = current.parent
            if (next !== current) {
                current.recycle()
                current = next
            } else {
                current.recycle()
                break
            }
            depth++
        }
        return null
    }
}
