package dev.og69.eab.telemetry

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens Together’s usage / Screen Time permission UI. OEMs differ wildly; we try several intents.
 *
 * Also see [UsageStatsPrimer] and `PACKAGE_USAGE_STATS` in the manifest — some phones only list
 * apps that declare that permission or have queried usage stats once.
 */
object UsageAccessLauncher {

    fun openForOurApp(context: Context) {
        val pkg = context.packageName
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(
                    Intent(Settings.ACTION_APP_USAGE_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                    },
                )
            }
            add(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    },
                )
            }
            add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", pkg, null)
                },
            )
        }

        for (raw in candidates) {
            try {
                val intent = raw.withNewTaskIfNeeded(context)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun Intent.withNewTaskIfNeeded(context: Context): Intent = apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
