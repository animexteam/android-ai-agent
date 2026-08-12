package com.androidagent.aiagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.GestureResultCallback
import android.graphics.Path
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


/**
 * Singleton object that provides high-level gesture operations (tap, swipe, long-press)
 * built on top of [AccessibilityService.dispatchGesture] and [GestureDescription.Builder].
 *
 * All methods are thread-safe and block until the gesture completes or times out.
 */
object GestureController {

    private const val TAG = "GestureController"
    private const val DEFAULT_TAP_DURATION = 100L
    private const val DEFAULT_SWIPE_DURATION = 300L
    private const val DEFAULT_LONG_PRESS_DURATION = 500L
    private const val GESTURE_TIMEOUT_MS = 5000L

    /**
     * Perform a tap gesture at the given coordinates.
     *
     * The tap is implemented as a very short stroke from (x, y) to (x+1, y+1)
     * to ensure the gesture is recognized by the system.
     *
     * @param service The accessibility service instance used to dispatch the gesture.
     * @param x The X coordinate of the tap target.
     * @param y The Y coordinate of the tap target.
     * @return true if the gesture was dispatched and completed successfully.
     */
    fun performTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        if (!isServiceValid(service)) return false

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            DEFAULT_TAP_DURATION
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAndWait(service, gesture)
    }

    /**
     * Perform a swipe gesture from one point to another.
     *
     * @param service The accessibility service instance used to dispatch the gesture.
     * @param startX The starting X coordinate.
     * @param startY The starting Y coordinate.
     * @param endX The ending X coordinate.
     * @param endY The ending Y coordinate.
     * @param durationMs The duration of the swipe in milliseconds.
     * @return true if the gesture was dispatched and completed successfully.
     */
    fun performSwipe(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = DEFAULT_SWIPE_DURATION
    ): Boolean {
        if (!isServiceValid(service)) return false

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAndWait(service, gesture)
    }

    /**
     * Perform a long-press gesture at the given coordinates.
     *
     * The long-press is implemented as a stroke that holds at the target
     * position for the specified duration.
     *
     * @param service The accessibility service instance used to dispatch the gesture.
     * @param x The X coordinate of the long-press target.
     * @param y The Y coordinate of the long-press target.
     * @param durationMs How long to hold the press, in milliseconds.
     * @return true if the gesture was dispatched and completed successfully.
     */
    fun performLongPress(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long = DEFAULT_LONG_PRESS_DURATION
    ): Boolean {
        if (!isServiceValid(service)) return false

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAndWait(service, gesture)
    }

    // --- Convenience methods that use the singleton service instance ---

    fun performTap(x: Float, y: Float): Boolean {
        val service = AndroidAgentAccessibilityService.instance ?: return false
        return performTap(service, x, y)
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = DEFAULT_SWIPE_DURATION
    ): Boolean {
        val service = AndroidAgentAccessibilityService.instance ?: return false
        return performSwipe(service, startX, startY, endX, endY, durationMs)
    }

    fun performLongPress(
        x: Float,
        y: Float,
        durationMs: Long = DEFAULT_LONG_PRESS_DURATION
    ): Boolean {
        val service = AndroidAgentAccessibilityService.instance ?: return false
        return performLongPress(service, x, y, durationMs)
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Dispatch a gesture and block until the result callback fires or times out.
     */
    private fun dispatchGestureAndWait(
        service: AccessibilityService,
        gesture: GestureDescription
    ): Boolean {
        val latch = CountDownLatch(1)
        val result = booleanArrayOf(false)

        return try {
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    result[0] = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Gesture was cancelled by the system")
                    result[0] = false
                    latch.countDown()
                }
            }

            service.dispatchGesture(gesture, callback, null)

            val completed = latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                Log.w(TAG, "Gesture dispatch timed out after ${GESTURE_TIMEOUT_MS}ms")
                false
            } else {
                result[0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch gesture", e)
            false
        }
    }

    /**
     * Check if the accessibility service is valid and connected.
     */
    private fun isServiceValid(service: AccessibilityService?): Boolean {
        if (service == null) {
            Log.w(TAG, "Cannot perform gesture: service is null")
            return false
        }
        if (!AndroidAgentAccessibilityService.isConnected) {
            Log.w(TAG, "Cannot perform gesture: accessibility service is not connected")
            return false
        }
        return true
    }
}
