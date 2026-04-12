package dev.og69.eab.network

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.og69.eab.ApiConfig
import dev.og69.eab.MainActivity
import dev.og69.eab.R
import dev.og69.eab.data.Session
import dev.og69.eab.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that maintains a persistent WebSocket connection
 * to the Cloudflare Durable Object for real-time partner updates.
 *
 * Lifecycle:
 *   - Started after login (couple create / join + profile completed)
 *   - Stopped on sign-out
 *   - Reconnects with exponential backoff on disconnect/error
 */
class WebSocketService : Service() {

    companion object {
        private const val TAG = "WebSocketService"
        private const val CHANNEL_ID = "eab_sync"
        private const val NOTIF_ID = 9001
        private const val EXTRA_COUPLE_ID = "couple_id"
        private const val EXTRA_DEVICE_TOKEN = "device_token"

        /** Maximum backoff delay (2 min). */
        private const val MAX_BACKOFF_MS = 120_000L

        fun start(context: Context, session: Session) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                putExtra(EXTRA_COUPLE_ID, session.coupleId)
                putExtra(EXTRA_DEVICE_TOKEN, session.deviceToken)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebSocketService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var ws: WebSocket? = null
    private var backoffMs = 1_000L
    private var coupleId: String = ""
    private var deviceToken: String = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)      // WebSocket: no read timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)     // Keep-alive pings
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        coupleId = intent?.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        deviceToken = intent?.getStringExtra(EXTRA_DEVICE_TOKEN) ?: ""
        if (coupleId.isBlank() || deviceToken.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
            }
            try {
                startForeground(NOTIF_ID, buildNotification("Connecting…"), type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed startForeground with type", e)
                try {
                    startForeground(NOTIF_ID, buildNotification("Connecting…"))
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed startForeground without type", e2)
                }
            }
        } else {
            startForeground(NOTIF_ID, buildNotification("Connecting…"))
        }

        if (running.compareAndSet(false, true)) {
            connect()
            startLocationTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        stopLocationTracking()
        ws?.close(1000, "Service stopped")
        ws = null
        scope.cancel()
        super.onDestroy()
    }

    /* ── WebSocket connection ─────────────────────────── */

    private fun connect() {
        if (!running.get()) return

        val base = ApiConfig.WORKER_BASE_URL.trim().trimEnd('/')
        val scheme = if (base.startsWith("https")) "wss" else "ws"
        val host = base.removePrefix("https://").removePrefix("http://")
        val wsUrl = "$scheme://$host/api/couple/$coupleId/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $deviceToken")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                backoffMs = 1_000L                      // Reset backoff
                updateNotification("Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!running.get()) return
        updateNotification("Reconnecting…")
        scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            connect()
        }
    }

    /* ── Tracking & Message handling ─────────────────────────────── */

    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    scope.launch {
                        val profile = SessionRepository(applicationContext).cachedProfileFlow.first()
                        if (profile?.shareLocation != false) {
                            sendLocationOverWs(loc.latitude, loc.longitude, loc.accuracy, loc.time)
                        }
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    private fun stopLocationTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun sendLocationOverWs(lat: Double, lng: Double, acc: Float, time: Long) {
        if (ws == null) return
        try {
            val payload = org.json.JSONObject().apply {
                put("location", org.json.JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("acc", acc.toDouble())
                    put("t", time)
                })
            }
            val msg = org.json.JSONObject().apply {
                put("type", "telemetry")
                put("payload", payload)
            }
            ws?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send location", e)
        }
    }

    private suspend fun handleMessage(text: String) {
        try {
            val json = org.json.JSONObject(text)
            when (json.optString("type")) {
                "partner_update" -> {
                    // Persist the partner JSON into DataStore so UI can observe via Flow
                    val repo = SessionRepository(applicationContext)
                    repo.saveCachedPartnerJson(text)
                }
                "partner_connected" -> {
                    Log.d(TAG, "Partner connected")
                    updateNotification("Partner online")
                }
                "partner_disconnected" -> {
                    Log.d(TAG, "Partner disconnected")
                    updateNotification("Connected")
                }
                "pong" -> { /* keepalive response */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WS message: ${e.message}")
        }
    }

    /* ── Notification helpers ─────────────────────────── */

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Live sync",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the real-time connection to your partner alive."
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(subtitle: String) {
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NOTIF_ID, buildNotification(subtitle))
        } catch (_: Exception) { /* ignore if channel missing somehow */ }
    }
}
