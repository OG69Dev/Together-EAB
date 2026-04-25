package dev.og69.eab.telemetry

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DeviceMetrics {

    fun batteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    fun diskStats(context: Context): Pair<Long, Long> {
        val path = Environment.getDataDirectory().path
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val total = stat.blockCountLong * blockSize
        val free = stat.availableBlocksLong * blockSize
        return free to total
    }

    /**
     * Top apps by foreground time since start of local day (requires PACKAGE_USAGE_STATS).
     * Second value is total foreground ms today (all apps), for Screen Time summary tiles.
     */
    fun topUsageToday(context: Context, limit: Int = 12): Pair<List<Triple<String, String, Long>>, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: emptyList()
        val merged = mutableMapOf<String, Long>()
        for (s in stats) {
            merged[s.packageName] = (merged[s.packageName] ?: 0L) + s.totalTimeInForeground
        }
        val totalTodayMs = merged.values.sum()
        val pm = context.packageManager
        val list = merged.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, ms) ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                Triple(pkg, label, ms)
            }
        return list to totalTodayMs
    }

    /**
     * Total foreground time across all apps for the last 7 local calendar days (including today).
     */
    fun totalForegroundLast7Days(context: Context): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return 0L
        val merged = mutableMapOf<String, Long>()
        for (s in stats) {
            merged[s.packageName] = (merged[s.packageName] ?: 0L) + s.totalTimeInForeground
        }
        return merged.values.sum()
    }

    /**
     * Best-effort “foreground” from usage events (like Screen Time / Digital Wellbeing data).
     * Requires Usage access; complements Accessibility when window events are sparse.
     */
    fun recentForegroundFromUsageEvents(context: Context): Pair<String, String>? {
        if (!UsageStatsPermission.has(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 15 * 60 * 1000L
        val events = usm.queryEvents(start, end) ?: return null
        val ev = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val type = ev.eventType
            @Suppress("DEPRECATION")
            val foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED)
            if (!foreground) continue
            val p = ev.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (p == context.packageName) continue
            lastPkg = p
        }
        val pkg = lastPkg ?: return null
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)
        return pkg to label
    }

    data class NetworkStatus(
        val type: String, // "WiFi", "Mobile", "None"
        val bars: Int,
        val maxBars: Int
    )

    fun networkStatus(context: Context): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Helper to evaluate a specific network's capabilities
        fun evaluate(caps: NetworkCapabilities?): NetworkStatus? {
            if (caps == null) return null
            
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> {
                    var level = 4
                    var max = 4
                    try {
                        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        @Suppress("DEPRECATION")
                        val info = wm.connectionInfo
                        if (info != null && info.rssi != -127) {
                            level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                wm.calculateSignalLevel(info.rssi)
                            } else {
                                @Suppress("DEPRECATION")
                                WifiManager.calculateSignalLevel(info.rssi, 5)
                            }
                            max = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                wm.maxSignalLevel
                            } else 4
                        }
                    } catch (_: Exception) {}
                    NetworkStatus("WiFi", level, max)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    var level = 4
                    try {
                        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            level = tm.signalStrength?.level ?: 4
                        }
                    } catch (_: Exception) {}
                    NetworkStatus("Mobile", level, 4)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                    // VPN hides underlying transport, just show full WiFi
                    NetworkStatus("WiFi", 4, 4)
                }
                else -> null
            }
        }

        // 1. Try active network (default)
        val active = cm.activeNetwork
        val activeCaps = cm.getNetworkCapabilities(active)
        evaluate(activeCaps)?.let { return it }

        // 2. If active is a VPN or null, look at all networks
        val allNetworks = cm.allNetworks
        for (n in allNetworks) {
            val c = cm.getNetworkCapabilities(n)
            evaluate(c)?.let { return it }
        }

        // 3. Fallback: Since telemetry is sent over WebSocket, we know we are online.
        return NetworkStatus("WiFi", 4, 4)
    }
}

fun Long.formatDurationMs(): String {
    if (this <= 0L) return "0m"
    val h = TimeUnit.MILLISECONDS.toHours(this)
    val m = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        else -> "${m.coerceAtLeast(1)}m"
    }
}
