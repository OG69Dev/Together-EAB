package dev.og69.eab.dpc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.og69.eab.R

class CouplesDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Administration Mode Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Administration Mode Disabled", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, CouplesDeviceAdminReceiver::class.java)
        }

        fun setUninstallBlocked(context: Context, packageName: String, blocked: Boolean) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)
            if (dpm.isAdminActive(admin)) {
                try {
                    // This requires Device Owner or Profile Owner status to actually work.
                    // On normal Device Admin, it throws SecurityException.
                    val isOwner = dpm.isDeviceOwnerApp(context.packageName) ||
                            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && dpm.isProfileOwnerApp(context.packageName))
                    
                    if (isOwner) {

                        dpm.setUninstallBlocked(admin, packageName, blocked)
                    } else {
                        android.util.Log.w("CouplesDPC", "Cannot setUninstallBlocked: App is not Device Owner or Profile Owner")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CouplesDPC", "Failed to setUninstallBlocked", e)
                }
            }

        }
    }
}
