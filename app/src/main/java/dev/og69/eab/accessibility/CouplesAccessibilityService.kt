package dev.og69.eab.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.og69.eab.telemetry.ForegroundAppState

class CouplesAccessibilityService : AccessibilityService() {

    private var lastContentThrottleMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        pushFromRootWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        val relevant = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            (Build.VERSION.SDK_INT >= 30 && type == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        if (!relevant) return

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = SystemClock.uptimeMillis()
            if (now - lastContentThrottleMs < CONTENT_THROTTLE_MS) return
            lastContentThrottleMs = now
        }

        val pkg = event.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: rootInActiveWindow?.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        if (pkg == packageName) return

        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrNull()
        ForegroundAppState.update(pkg, label)
    }

    private fun pushFromRootWindow() {
        val pkg = rootInActiveWindow?.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        if (pkg == packageName) return
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrNull()
        ForegroundAppState.update(pkg, label)
    }

    override fun onInterrupt() {}

    companion object {
        private const val CONTENT_THROTTLE_MS = 400L
    }
}
