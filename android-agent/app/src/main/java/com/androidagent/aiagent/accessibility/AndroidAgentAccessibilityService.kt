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


class AndroidAgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentAccessibilitySvc"
        @Volatile var instance: AndroidAgentAccessibilityService? = null; private set
        val isConnected: Boolean get() = instance != null && connectionState.get()
        private val connectionState = AtomicBoolean(false)
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this; connectionState.set(true); Log.i(TAG, "Connected") }
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() { Log.w(TAG, "Interrupted") }
    override fun onDestroy() { super.onDestroy(); instance = null; connectionState.set(false) }

    fun safeGetRootInActiveWindow(): AccessibilityNodeInfo? = try { rootInActiveWindow } catch (e: Exception) { null }

    // --- Node actions ---
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (!node.isClickable) findClickableParent(node)?.let { val r = it.performAction(AccessibilityNodeInfo.ACTION_CLICK); it.recycle(); r } ?: node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) { false }
    }
    fun performLongClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (!node.isLongClickable) findLongClickableParent(node)?.let { val r = it.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK); it.recycle(); r } ?: node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            else node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } catch (e: Exception) { false }
    }
    fun performSetText(node: AccessibilityNodeInfo, text: String): Boolean = try {
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
    } catch (e: Exception) { false }
    fun performScroll(node: AccessibilityNodeInfo, direction: Int): Boolean = try {
        node.performAction(if (direction >= 0) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    } catch (e: Exception) { false }
    fun performSelectAll(node: AccessibilityNodeInfo): Boolean = try { node.performAction(AccessibilityNodeInfo.ACTION_SELECT_ALL) } catch (e: Exception) { false }
    fun performCopy(node: AccessibilityNodeInfo): Boolean = try { node.performAction(AccessibilityNodeInfo.ACTION_COPY) } catch (e: Exception) { false }
    fun performCut(node: AccessibilityNodeInfo): Boolean = try { node.performAction(AccessibilityNodeInfo.ACTION_CUT) } catch (e: Exception) { false }
    fun performPaste(node: AccessibilityNodeInfo): Boolean = try { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) } catch (e: Exception) { false }

    // --- Global actions ---
    fun pressBack(): Boolean = try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (e: Exception) { false }
    fun pressHome(): Boolean = try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (e: Exception) { false }
    fun pressRecents(): Boolean = try { performGlobalAction(GLOBAL_ACTION_RECENTS) } catch (e: Exception) { false }
    fun openNotifications(): Boolean = try { performGlobalAction(GLOBAL_ACTION_NOTIFICATION_SHADE) } catch (e: Exception) { false }
    fun openQuickSettings(): Boolean = try { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) } catch (e: Exception) { false }
    fun showPowerMenu(): Boolean = try { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) } catch (e: Exception) { false }
    fun toggleSplitScreen(): Boolean = try { performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) } catch (e: Exception) { false }
    fun lockScreen(): Boolean = try { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) } catch (e: Exception) { false }
    fun takeScreenshotGlobal(): Boolean = try { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) } catch (e: Exception) { false }

    // --- Gesture ---
    fun performGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean =
        GestureController.performSwipe(this, startX, startY, endX, endY, durationMs)

    // --- Screenshot ---
    fun takeScreenshot(): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) takeScreenshotApi34() else null

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun takeScreenshotApi34(): Bitmap? = try {
        val latch = CountDownLatch(1); var bmp: Bitmap? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) { val hb = r.hardwareBuffer; bmp = Bitmap.wrapHardwareBuffer(hb, null); hb.close(); latch.countDown() }
            override fun onFailure(c: Int) { latch.countDown() }
        })
        if (latch.await(3, TimeUnit.SECONDS)) bmp else null
    } catch (e: Exception) { null }

    // --- Helpers ---
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c = node.parent; var d = 0
        while (c != null && d < 20) { if (c.isClickable) return c; val n = c.parent; if (n !== c) { c.recycle(); c = n } else { c.recycle(); break }; d++ }
        return null
    }
    private fun findLongClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c = node.parent; var d = 0
        while (c != null && d < 20) { if (c.isLongClickable) return c; val n = c.parent; if (n !== c) { c.recycle(); c = n } else { c.recycle(); break }; d++ }
        return null
    }
}