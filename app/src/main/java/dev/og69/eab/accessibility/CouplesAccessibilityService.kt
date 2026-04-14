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
        } else if (pkg == "com.google.android.youtube") {
            val root = event.source ?: rootInActiveWindow
            if (root != null) {
                extractYoutubeTitle(root)
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
        var foundText: String? = null

        if (packageName == "com.google.android.googlequicksearchbox") {
            val searchBoxIds = listOf(
                "com.google.android.googlequicksearchbox:id/googleapp_search_box",
                "com.google.android.googlequicksearchbox:id/search_box"
            )
            for (id in searchBoxIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    foundText = nodes.firstOrNull()?.text?.toString() ?: nodes.firstOrNull()?.contentDescription?.toString()
                    if (!foundText.isNullOrBlank()) break
                }
            }
            if (foundText.isNullOrBlank()) {
                foundText = findEditTextOrGoogleTitle(root, isGoogleApp = true)
            }
        }

        if (foundText.isNullOrBlank()) {
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
                        foundText = text
                        break
                    }
                }
            }
        }

        // If 'url_bar' shows "google.com" or a base domain, check the UI tree for a title like "some query - Google Search"
        // This handles Chrome Custom Tabs or Chrome instances where only the domain is shown,
        // and we want to capture the actual search query.
        if (foundText != null && (foundText == "google.com" || foundText == "www.google.com" || foundText.contains("search?"))) {
            val titleQuery = findEditTextOrGoogleTitle(root, isGoogleApp = false)
            if (!titleQuery.isNullOrBlank()) {
                foundText = titleQuery
            }
        }

        val finalUrl = foundText
        if (!finalUrl.isNullOrBlank()) {
            // Quick heuristic format: either it looks like a URL, or it's a raw search text > 2 characters
            if (finalUrl.contains(".") || finalUrl.startsWith("http") || finalUrl.length > 2) {
                scope.launch {
                    WebHistoryHelper.addUrl(this@CouplesAccessibilityService, finalUrl, finalUrl)
                }
            }
        }
    }

    private fun findEditTextOrGoogleTitle(node: AccessibilityNodeInfo, isGoogleApp: Boolean): String? {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (text != null) {
            if (text.endsWith(" - Google Search")) {
                return text.removeSuffix(" - Google Search")
            }
            // In Google app, Edit boxes often hold the current search typed text
            if (isGoogleApp && node.className?.toString()?.contains("EditText") == true) {
                return text
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditTextOrGoogleTitle(child, isGoogleApp)
            if (result != null) return result
        }
        return null
    }

    private fun extractYoutubeTitle(root: AccessibilityNodeInfo) {
        // Simple heuristic: ensure there's an element indicating we are in the watch view
        if (!isInWatchView(root)) return

        val titleNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title")
        if (!titleNodes.isNullOrEmpty()) {
            val text = titleNodes.firstOrNull()?.text?.toString()
            if (!text.isNullOrBlank()) {
                scope.launch {
                    dev.og69.eab.data.YoutubeHistoryHelper.addVideo(this@CouplesAccessibilityService, text)
                }
            }
        }
    }

    private fun isInWatchView(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("fullscreen") || desc.contains("pause video") || desc.contains("minimize") || desc.contains("play video") || desc.contains("next video")) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = isInWatchView(child)
            if (result) return true
        }
        return false
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
            "com.duckduckgo.mobile.android",
            "com.google.android.googlequicksearchbox"
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
