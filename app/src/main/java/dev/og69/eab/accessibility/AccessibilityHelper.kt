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
     * toggle the component state to force Android to re-bind it.
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
                android.util.Log.w("AccessibilityHelper", "Service enabled in settings but not running. Nudging...")
                val pm = context.packageManager
                val component = ComponentName(context, CouplesAccessibilityService::class.java)
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            }
        }
    }
}
