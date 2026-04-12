package dev.og69.eab.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.og69.eab.data.WebHistoryHelper
import dev.og69.eab.telemetry.ForegroundAppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CouplesAccessibilityService : AccessibilityService() {

    private var lastContentThrottleMs = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        val pkg = rootInActiveWindow?.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: event.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        if (pkg == packageName) return

        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrNull()
        ForegroundAppState.update(pkg, label)
        
        if (BROWSER_PACKAGES.contains(pkg)) {
            val root = event.source ?: rootInActiveWindow
            if (root != null) {
                extractBrowserUrl(pkg, root)
            }
        }
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

    private fun extractBrowserUrl(packageName: String, root: AccessibilityNodeInfo) {
        val viewId = BROWSER_URL_BAR_IDS[packageName]
        val nodes = if (viewId != null) {
            root.findAccessibilityNodeInfosByViewId(viewId)
        } else {
            // Default generic fallback search
            root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        }

        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                val text = node.text?.toString() ?: node.contentDescription?.toString()
                if (!text.isNullOrBlank()) {
                    // Quick heuristic format: either it looks like a URL, or it's a raw search text > 2 characters
                    if (text.contains(".") || text.startsWith("http") || text.length > 2) {
                        scope.launch {
                            WebHistoryHelper.addUrl(this@CouplesAccessibilityService, text, text)
                        }
                    }
                    break
                }
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val CONTENT_THROTTLE_MS = 400L
        
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser",
            "com.brave.browser",
            "com.opera.browser",
            "com.duckduckgo.mobile.android"
        )
        
        private val BROWSER_URL_BAR_IDS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.opera.browser" to "com.opera.browser:id/url_bar",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput"
        )
    }
}
