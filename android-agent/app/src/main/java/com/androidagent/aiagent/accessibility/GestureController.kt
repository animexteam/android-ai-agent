package com.androidagent.aiagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.graphics.Path
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


object GestureController {

    private const val TAG = "GestureController"
    private const val DEFAULT_TAP_DURATION = 100L
    private const val DEFAULT_SWIPE_DURATION = 300L
    private const val DEFAULT_LONG_PRESS_DURATION = 500L
    private const val DEFAULT_DOUBLE_TAP_GAP = 120L
    private const val GESTURE_TIMEOUT_MS = 5000L

    fun performTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        if (!isServiceValid(service)) return false
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, DEFAULT_TAP_DURATION)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAndWait(service, gesture)
    }

    fun performDoubleTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        if (!isServiceValid(service)) return false
        val path1 = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        val path2 = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, DEFAULT_TAP_DURATION)
        val stroke2 = GestureDescription.StrokeDescription(path2, DEFAULT_TAP_DURATION + DEFAULT_DOUBLE_TAP_GAP, DEFAULT_TAP_DURATION)
        val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()
        return dispatchGestureAndWait(service, gesture)
    }

    fun performSwipe(service: AccessibilityService, startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = DEFAULT_SWIPE_DURATION): Boolean {
        if (!isServiceValid(service)) return false
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAndWait(service, gesture)
    }

    fun performFling(service: AccessibilityService, startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        return performSwipe(service, startX, startY, endX, endY, 80L)
    }

    fun performLongPress(service: AccessibilityService, x: Float, y: Float, durationMs: Long = DEFAULT_LONG_PRESS_DURATION): Boolean {
        if (!isServiceValid(service)) return false
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAndWait(service, gesture)
    }

    fun performDrag(service: AccessibilityService, startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 500L): Boolean {
        return performSwipe(service, startX, startY, endX, endY, durationMs)
    }

    fun performPinchZoom(service: AccessibilityService, cx: Float, cy: Float, scaleFactor: Float, durationMs: Long = 400L): Boolean {
        if (!isServiceValid(service)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Pinch zoom requires API 30+")
            return false
        }
        val halfSpread = 100f * scaleFactor
        // Finger 1: starts far, ends close
        val path1 = Path().apply {
            moveTo(cx - halfSpread, cy - halfSpread)
            lineTo(cx - halfSpread * 0.3f, cy - halfSpread * 0.3f)
        }
        // Finger 2: starts far, ends close
        val path2 = Path().apply {
            moveTo(cx + halfSpread, cy + halfSpread)
            lineTo(cx + halfSpread * 0.3f, cy + halfSpread * 0.3f)
        }
        val s1 = GestureDescription.StrokeDescription(path1, 0L, durationMs)
        val s2 = GestureDescription.StrokeDescription(path2, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(s1).addStroke(s2).build()
        return dispatchGestureAndWait(service, gesture)
    }

    // --- Convenience (singleton instance) ---
    fun performTap(x: Float, y: Float): Boolean = AndroidAgentAccessibilityService.instance?.let { performTap(it, x, y) } ?: false
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = DEFAULT_SWIPE_DURATION): Boolean =
        AndroidAgentAccessibilityService.instance?.let { performSwipe(it, startX, startY, endX, endY, durationMs) } ?: false
    fun performLongPress(x: Float, y: Float, durationMs: Long = DEFAULT_LONG_PRESS_DURATION): Boolean =
        AndroidAgentAccessibilityService.instance?.let { performLongPress(it, x, y, durationMs) } ?: false
    fun performDoubleTap(x: Float, y: Float): Boolean = AndroidAgentAccessibilityService.instance?.let { performDoubleTap(it, x, y) } ?: false
    fun performFling(startX: Float, startY: Float, endX: Float, endY: Float): Boolean =
        AndroidAgentAccessibilityService.instance?.let { performFling(it, startX, startY, endX, endY) } ?: false
    fun performDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 500L): Boolean =
        AndroidAgentAccessibilityService.instance?.let { performDrag(it, startX, startY, endX, endY, durationMs) } ?: false

    private fun dispatchGestureAndWait(service: AccessibilityService, gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        val result = booleanArrayOf(false)
        return try {
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) { result[0] = true; latch.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription) { Log.w(TAG, "Gesture cancelled"); latch.countDown() }
            }
            service.dispatchGesture(gesture, callback, null)
            val completed = latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) { Log.w(TAG, "Gesture timed out"); false } else result[0]
        } catch (e: Exception) { Log.e(TAG, "Gesture failed", e); false }
    }

    private fun isServiceValid(service: AccessibilityService?): Boolean {
        if (service == null || !AndroidAgentAccessibilityService.isConnected) return false
        return true
    }
}