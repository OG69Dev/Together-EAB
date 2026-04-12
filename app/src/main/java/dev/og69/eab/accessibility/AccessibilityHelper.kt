package dev.og69.eab.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityHelper {

    fun isOurServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, CouplesAccessibilityService::class.java).flattenToString()
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return raw.split(':', ';', '|')
            .map { it.trim() }
            .any { it.equals(expected, ignoreCase = true) }
    }
}
