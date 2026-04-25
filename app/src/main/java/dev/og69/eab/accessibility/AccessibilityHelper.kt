package dev.og69.eab.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityHelper {

    fun isOurServiceEnabled(context: Context): Boolean {
        // Method 1: Check AccessibilityManager (Reliable if service is currently active/running)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        if (enabledServices != null) {
            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo.serviceInfo
                if (serviceInfo.packageName == context.packageName && 
                    serviceInfo.name == CouplesAccessibilityService::class.java.name) {
                    return true
                }
            }
        }

        // Method 2: Check Settings.Secure (Catch if it's enabled in settings but maybe stuck/crashed/not-bound)
        val expected = ComponentName(context, CouplesAccessibilityService::class.java).flattenToString()
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        
        return raw.split(':', ';', '|')
            .map { it.trim() }
            .any { it.equals(expected, ignoreCase = true) || it.endsWith(CouplesAccessibilityService::class.java.name) }
    }

    /**
     * Watchdog: If the service is enabled in settings but not actually active/running,
     * it might have crashed. We no longer toggle the component state here because Android
     * will permanently remove it from the enabled services list in Settings.Secure,
     * forcing the user to manually re-enable it. We just log the occurrence.
     */
    fun ensureServiceRunning(context: Context) {
        val expected = ComponentName(context, CouplesAccessibilityService::class.java).flattenToString()
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        
        val isEnabledInSettings = raw.split(':', ';', '|')
            .map { it.trim() }
            .any { it.equals(expected, ignoreCase = true) || it.endsWith(CouplesAccessibilityService::class.java.name) }

        if (isEnabledInSettings) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val running = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true
            
            if (!running) {
                android.util.Log.w("AccessibilityHelper", "Service enabled in settings but not running. System might have killed it. User intervention required to re-enable safely without dropping permissions.")
            }
        }
    }
}
