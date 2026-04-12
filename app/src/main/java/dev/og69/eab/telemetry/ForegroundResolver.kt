package dev.og69.eab.telemetry

import android.content.Context

/**
 * Picks the best local guess for “current app”: Accessibility first, then Usage (Screen Time) events.
 */
object ForegroundResolver {

    fun resolve(context: Context): Pair<String?, String?> {
        val fromA11yPkg = ForegroundAppState.packageFlow.value?.trim()?.takeIf { it.isNotBlank() }
        val fromA11yLabel = ForegroundAppState.labelFlow.value?.trim()?.takeIf { it.isNotBlank() }
        if (fromA11yPkg != null) {
            return fromA11yPkg to fromA11yLabel
        }
        val fromUsage = DeviceMetrics.recentForegroundFromUsageEvents(context)
        if (fromUsage != null) {
            return fromUsage.first to fromUsage.second
        }
        return null to null
    }
}
