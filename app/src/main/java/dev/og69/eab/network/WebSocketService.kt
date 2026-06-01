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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.graphics.PixelFormat
import android.view.WindowManager
import dev.og69.eab.overlay.DrawingCanvasView
import dev.og69.eab.data.MediaHelper
import dev.og69.eab.data.MediaItem
import dev.og69.eab.data.MediaCacheManager
import dev.og69.eab.webrtc.WebRtcManager
import kotlinx.coroutines.channels.Channel
import org.webrtc.CameraVideoCapturer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that maintains a persistent WebSocket connection
 * to the Cloudflare Durable Object for real-time partner updates.
 */
class WebSocketService : Service() {

    companion object {
        private const val TAG = "WebSocketService"
        private const val CHANNEL_ID = "eab_sync"
        private const val CHANNEL_ALERTS_ID = "eab_alerts"
        private const val NOTIF_ID = 9001
        private const val NOTIF_ID_ALERT = 9002

        private const val EXTRA_COUPLE_ID = "couple_id"
        private const val EXTRA_DEVICE_TOKEN = "device_token"

        val signalingFlow = kotlinx.coroutines.flow.MutableSharedFlow<org.json.JSONObject>(extraBufferCapacity = 64)
        val audioStateFlow = kotlinx.coroutines.flow.MutableStateFlow(dev.og69.eab.webrtc.WebRtcManager.State.IDLE)
        val screenStateFlow = kotlinx.coroutines.flow.MutableStateFlow(dev.og69.eab.webrtc.WebRtcManager.State.IDLE)
        val cameraStateFlow = kotlinx.coroutines.flow.MutableStateFlow(dev.og69.eab.webrtc.WebRtcManager.State.IDLE)
        val mediaStateFlow = kotlinx.coroutines.flow.MutableStateFlow(dev.og69.eab.webrtc.WebRtcManager.State.IDLE)
        val remoteVideoTracksFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<String, org.webrtc.VideoTrack>>(emptyMap())
        val speakerphoneFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
        
        val mediaBinaryFlow = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Long, ByteArray>>(extraBufferCapacity = 16)
        val brightnessFlow = kotlinx.coroutines.flow.MutableStateFlow(-1) // -1 = unknown
        val flashlightFlow = kotlinx.coroutines.flow.MutableStateFlow(0) // 0=off, 1-max=strength
        val flashlightMaxFlow = kotlinx.coroutines.flow.MutableStateFlow(1) // max torch strength
        val forceCallErrorFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
        val forceEndCallErrorFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
        val partnerCallStateFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

        private var instance: WebSocketService? = null
        private var screenCaptureIntent: android.content.Intent? = null

        /** Public API for checking if the service is alive (#11) */
        fun isRunning(): Boolean = instance != null

        fun requestAudio() {
            instance?.let { srv ->
                srv.ensureWebRtc()
                srv.webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.LISTENER)
                srv.sendSignaling(org.json.JSONObject().put("type", "request_audio"))
            }
        }

        fun stopAudio() {
            instance?.stopAudio()
            instance?.sendSignaling(org.json.JSONObject().put("type", "stop_audio"))
        }

        fun requestScreen() {
            instance?.let { srv ->
                srv.ensureWebRtc()
                srv.webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.SCREEN_LISTENER)
                srv.sendSignaling(org.json.JSONObject().put("type", "request_screen"))
            }
        }

        fun stopScreen() {
            instance?.stopScreen()
            instance?.sendSignaling(org.json.JSONObject().put("type", "stop_screen"))
        }

        fun requestCamera(mode: String = "front") {
            instance?.let { srv ->
                srv.ensureWebRtc()
                srv.webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.CAMERA_LISTENER)
                srv.sendSignaling(org.json.JSONObject().apply {
                    put("type", "request_camera")
                    put("mode", mode)
                })
            }
        }

        fun switchCamera(mode: String) {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "switch_camera")
                put("mode", mode)
            })
        }

        fun setBrightness(level: Int) {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "set_brightness")
                put("level", level.coerceIn(0, 255))
            })
        }

        fun setFlashlight(level: Int) {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "set_flashlight")
                put("level", level)
            })
        }

        fun stopCamera() {
            instance?.stopCamera()
            instance?.sendSignaling(org.json.JSONObject().put("type", "stop_camera"))
        }

        fun sendSignaling(data: org.json.JSONObject) {
            instance?.sendSignaling(data)
        }

        fun requestMedia() {
            instance?.requestMedia()
        }

        fun stopMedia() {
            instance?.stopMedia()
        }

        fun sendMediaCommand(json: String) {
            instance?.webRtcManager?.sendMediaJson(json)
        }

        fun setScreenCaptureResult(resultCode: Int, data: android.content.Intent) {
            screenCaptureIntent = data
            instance?.let { srv ->
                srv.ensureWebRtc()
                srv.updateForegroundService("Screen Share Active")
                srv.scope.launch {
                    delay(500)
                    srv.webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.SCREEN_STREAMER, data)
                }
                srv.startOverlay()
            }
        }

        fun sendDrawingData(json: String) {
            instance?.webRtcManager?.sendData(json)
        }

        fun setSpeakerphone(on: Boolean) {
            instance?.let { srv ->
                speakerphoneFlow.value = on
                srv.audioRouter?.setSpeakerphoneOn(on)
            }
        }

        private const val MAX_BACKOFF_MS = 120_000L

        fun start(context: Context, session: Session) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                putExtra(EXTRA_COUPLE_ID, session.coupleId)
                putExtra(EXTRA_DEVICE_TOKEN, session.deviceToken)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebSocketService::class.java))
        }

        fun restart(context: Context, session: Session) {
            // Reconnect the WebSocket without tearing down the entire service (#5)
            instance?.let { srv ->
                srv.ws?.close(1000, "Restarting")
                srv.ws = null
                srv.backoffMs = 1_000L
                srv.coupleId = session.coupleId
                srv.deviceToken = session.deviceToken
                srv.connect()
            } ?: start(context, session)
        }

        fun sendAppBlockAttempt(appLabel: String) {
            instance?.let { srv ->
                srv.sendSignaling(org.json.JSONObject().apply {
                    put("type", "app_block_attempt")
                    put("app_label", appLabel)
                })
            }
        }

        fun sendVibrate(durationMs: Long = 500L) {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "vibrate")
                put("duration", durationMs)
            })
        }

        fun sendVibrateRepeat() {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "vibrate_repeat")
            })
        }

        fun sendVibrateStop() {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "vibrate_stop")
            })
        }

        fun forceCall(number: String) {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "force_call")
                put("number", number)
            })
        }

        fun forceEndCall() {
            instance?.sendSignaling(org.json.JSONObject().apply {
                put("type", "end_call")
            })
        }
    }


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var ws: WebSocket? = null
    private var backoffMs = 1_000L
    private var webRtcManager: dev.og69.eab.webrtc.WebRtcManager? = null
    private var audioRouter: dev.og69.eab.webrtc.AudioRouter? = null
    private var mediaHelper: MediaHelper? = null
    private var cacheManager: MediaCacheManager? = null
    private var mediaObserveJob: kotlinx.coroutines.Job? = null
    private var coupleId: String = ""
    private var deviceToken: String = ""
    
    // Binary state tracking
    private val pendingMediaIds = Channel<Long>(Channel.UNLIMITED)
    private var activeDownloadId: Long = -1L

    // Media lifecycle management
    private var mediaActiveCount = 0
    private var mediaStopJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private lateinit var windowManager: WindowManager
    private var canvasView: DrawingCanvasView? = null
    private val api by lazy { CoupleApi(client) }
    private lateinit var sessionRepo: SessionRepository

    // SMS Observer
    private var smsObserver: android.database.ContentObserver? = null
    private var lastSmsSyncJob: kotlinx.coroutines.Job? = null


    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionRepo = SessionRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        audioRouter = dev.og69.eab.webrtc.AudioRouter(applicationContext)

        try {
            val initialBrightness = android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            )
            brightnessFlow.value = initialBrightness
        } catch (_: Exception) {}

        registerSmsObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        coupleId = intent?.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        deviceToken = intent?.getStringExtra(EXTRA_DEVICE_TOKEN) ?: ""
        if (coupleId.isBlank() || deviceToken.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateForegroundService("Connecting…")
        if (running.compareAndSet(false, true)) {
            connect()
        }
        startLocationTracking()
        
        // Broadcast brightness and flashlight changes to partner
        scope.launch {
            brightnessFlow.collect { level ->
                if (level != -1) {
                    sendSignaling(org.json.JSONObject().apply {
                        put("type", "brightness_changed")
                        put("level", level)
                    })
                }
            }
        }
        scope.launch {
            flashlightFlow.collect { level ->
                sendSignaling(org.json.JSONObject().apply {
                    put("type", "flashlight_changed")
                    put("level", level)
                })
            }
        }
        scope.launch {
            flashlightMaxFlow.collect { max ->
                sendSignaling(org.json.JSONObject().apply {
                    put("type", "flashlight_max")
                    put("max", max)
                })
            }
        }

        // IMMEDIATE POLICY APPLICATION (from cache)
        scope.launch {
            val blocked = sessionRepo.getBlockedPackages()
            val uninstall = sessionRepo.uninstallBlockedFlow.first()
            
            // Re-apply uninstall block to DPC
            dev.og69.eab.dpc.CouplesDeviceAdminReceiver.setUninstallBlocked(
                applicationContext,
                packageName,
                uninstall
            )
            // The AccessibilityService monitors blockedPackagesFlow automatically
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Send restart broadcast to ourselves
        val restartIntent = Intent("dev.og69.eab.RESTART_SYNC")
        restartIntent.setPackage(packageName)
        sendBroadcast(restartIntent)
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        stopLocationTracking()
        ws?.close(1000, "Service destroyed")
        ws = null
        stopAudio()
        actuallyStopMedia()
        webRtcManager?.dispose()
        webRtcManager = null
        unregisterSmsObserver()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun registerSmsObserver() {
        if (smsObserver != null) return
        smsObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                syncSmsRealtime()
            }
        }
        try {
            contentResolver.registerContentObserver(
                android.net.Uri.parse("content://sms"),
                true,
                smsObserver!!
            )
            Log.d(TAG, "ContentObserver registered for content://sms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS ContentObserver", e)
        }
    }

    private fun unregisterSmsObserver() {
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        smsObserver = null
        lastSmsSyncJob?.cancel()
    }

    private fun syncSmsRealtime() {
        lastSmsSyncJob?.cancel()
        lastSmsSyncJob = scope.launch {
            delay(2000L) // Debounce rapid consecutive database modifications
            try {
                val session = sessionRepo.getSession()
                val profile = sessionRepo.cachedProfileFlow.first()
                if (session != null && profile?.shareSms == true &&
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val sms = dev.og69.eab.data.SmsHelper.getLocalSms(applicationContext)
                    val hash = dev.og69.eab.data.SmsHelper.hashSms(sms)
                    val lastHash = sessionRepo.getLatestSmsHash()
                    if (hash != "empty" && hash != lastHash) {
                        api.postSmsHistory(session, sms)
                        sessionRepo.saveLatestSmsHash(hash)
                        // Broadcast update event so companion app UI updates immediately
                        sendSignaling(org.json.JSONObject().apply {
                            put("type", "sms_history_updated")
                        })
                        Log.d(TAG, "Realtime SMS database changed; successfully synced SMS history")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal debounce cancellation – rethrow to preserve cooperative cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Realtime SMS sync failed", e)
            }
        }
    }

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
                backoffMs = 1_000L
                updateNotification("Connected")
                startTelemetryPolling()
                // Proactively upload current wallpapers so partner can view them instantly
                scope.launch { uploadWallpaperToKv("home") }
                scope.launch { uploadWallpaperToKv("lock") }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { handleMessage(text) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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

    private var telemetryJob: kotlinx.coroutines.Job? = null
    private fun startTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                // WATCHDOG: Ensure Accessibility Service is running
                dev.og69.eab.accessibility.AccessibilityHelper.ensureServiceRunning(applicationContext)
                
                sendFullTelemetry()
                delay(20_000)
            }
        }
    }

    private suspend fun sendFullTelemetry() {
        if (ws == null) return
        try {
            val ctx = applicationContext
            val ut = kotlinx.coroutines.withContext(Dispatchers.Default) {
                if (dev.og69.eab.telemetry.UsageStatsPermission.has(ctx)) {
                    val (list, todayTotal) = dev.og69.eab.telemetry.DeviceMetrics.topUsageToday(ctx)
                    val week = dev.og69.eab.telemetry.DeviceMetrics.totalForegroundLast7Days(ctx)
                    Triple(list, todayTotal, week)
                } else {
                    Triple(emptyList<Triple<String, String, Long>>(), 0L, 0L)
                }
            }
            val (free, total) = kotlinx.coroutines.withContext(Dispatchers.Default) {
                dev.og69.eab.telemetry.DeviceMetrics.diskStats(ctx)
            }
            val batt = kotlinx.coroutines.withContext(Dispatchers.Default) {
                dev.og69.eab.telemetry.DeviceMetrics.batteryPercent(ctx)
            }
            val (fgPkg, fgLabel) = kotlinx.coroutines.withContext(Dispatchers.Default) {
                dev.og69.eab.telemetry.ForegroundResolver.resolve(ctx)
            }
            val net = kotlinx.coroutines.withContext(Dispatchers.Default) {
                dev.og69.eab.telemetry.DeviceMetrics.networkStatus(ctx)
            }
            val inCall = kotlinx.coroutines.withContext(Dispatchers.Default) {
                dev.og69.eab.telemetry.DeviceMetrics.isInCall(ctx)
            }
            val fullTelemetryJson = dev.og69.eab.network.CoupleApi.buildTelemetryJson(
                batteryPct = batt, diskFreeBytes = free, diskTotalBytes = total,
                foregroundPackage = fgPkg, foregroundAppLabel = fgLabel,
                usageStats = ut.first, usageTodayTotalMs = ut.second, usageWeekTotalMs = ut.third,
                usageDailyAvgMs = if (ut.third > 0L) ut.third / 7L else 0L,
                networkType = net.type, networkBars = net.bars, networkMaxBars = net.maxBars,
                isInCall = inCall
            )
            val msg = org.json.JSONObject().apply {
                put("type", "telemetry")
                put("payload", fullTelemetryJson)
            }
            ws?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed telemetry send", e)
        }
    }

    private fun startLocationTracking() {
        if (locationCallback != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).setMinUpdateDistanceMeters(10f).build()
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
        } catch (e: SecurityException) { /* ignored */ }
    }

    private fun stopLocationTracking() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
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
            ws?.send(org.json.JSONObject().apply {
                put("type", "telemetry")
                put("payload", payload)
            }.toString())
        } catch (e: Exception) { Log.e(TAG, "sendFullTelemetry failed", e) }
    }

    fun sendSignaling(data: org.json.JSONObject) {
        val socket = ws ?: return
        try {
            socket.send(org.json.JSONObject().apply {
                put("type", "signaling")
                put("payload", data)
            }.toString())
        } catch (e: Exception) { Log.e(TAG, "sendSignaling failed", e) }
    }

    /**
     * Silently uploads the current wallpaper for [target] ("home" or "lock") to KV
     * so that the partner can view it instantly without a signaling roundtrip.
     */
    private suspend fun uploadWallpaperToKv(target: String) {
        try {
            val profile = sessionRepo.cachedProfileFlow.first()
            if (profile?.shareWallpaper != true) return
            val bytes = dev.og69.eab.data.WallpaperHelper.getCurrentWallpaper(applicationContext, target) ?: return
            api.postWallpaper(Session(coupleId, "", deviceToken), target, bytes)
            Log.d(TAG, "Proactively uploaded $target wallpaper to KV")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to proactively upload $target wallpaper", e)
        }
    }

    private fun ensureWebRtc() {
        if (webRtcManager == null) {
            webRtcManager = dev.og69.eab.webrtc.WebRtcManager(applicationContext, 
                onSignalingMessage = { data -> sendSignaling(data) },
                onStateChange = { state -> 
                    // Route state to the correct flow based on the active role (#3)
                    when (webRtcManager?.role) {
                        WebRtcManager.Role.STREAMER, WebRtcManager.Role.LISTENER -> audioStateFlow.value = state
                        WebRtcManager.Role.SCREEN_STREAMER, WebRtcManager.Role.SCREEN_LISTENER -> screenStateFlow.value = state
                        WebRtcManager.Role.CAMERA_STREAMER, WebRtcManager.Role.CAMERA_LISTENER -> cameraStateFlow.value = state
                        WebRtcManager.Role.MEDIA_PROVIDER, WebRtcManager.Role.MEDIA_CONSUMER -> mediaStateFlow.value = state
                        null -> {
                            // Fallback: role not yet assigned, update all
                            audioStateFlow.value = state
                            screenStateFlow.value = state
                            cameraStateFlow.value = state
                            mediaStateFlow.value = state
                        }
                    }
                    
                    if (state == dev.og69.eab.webrtc.WebRtcManager.State.CONNECTED) {
                        val role = webRtcManager?.role
                        if (role != dev.og69.eab.webrtc.WebRtcManager.Role.MEDIA_PROVIDER && role != dev.og69.eab.webrtc.WebRtcManager.Role.MEDIA_CONSUMER) {
                            audioRouter?.activate()
                            if (role == dev.og69.eab.webrtc.WebRtcManager.Role.LISTENER || role == dev.og69.eab.webrtc.WebRtcManager.Role.SCREEN_LISTENER || role == dev.og69.eab.webrtc.WebRtcManager.Role.CAMERA_LISTENER) {
                                audioRouter?.setSpeakerphoneOn(speakerphoneFlow.value)
                            } else {
                                // For streamers, keep speakerphone true to use the better VoIP mic array
                                audioRouter?.setSpeakerphoneOn(true)
                            }
                        }
                    } else if (state == dev.og69.eab.webrtc.WebRtcManager.State.IDLE || state == dev.og69.eab.webrtc.WebRtcManager.State.ERROR) {
                        audioRouter?.deactivate()
                    }
                },
                onVideoTrack = { track -> 
                    val map = remoteVideoTracksFlow.value.toMutableMap()
                    map[track.id()] = track
                    remoteVideoTracksFlow.value = map
                },
                onRemoveVideoTrack = { track ->
                    val map = remoteVideoTracksFlow.value.toMutableMap()
                    map.remove(track.id())
                    remoteVideoTracksFlow.value = map
                },
                onDataChannelMessage = { msg -> handleDrawingMessage(msg) },
                onMediaJsonMessage = { msg -> handleMediaCommand(msg) },
                onMediaBinaryMessage = { data -> handleMediaBinary(data) },
                onMediaChannelOpen = { 
                    if (webRtcManager?.role == dev.og69.eab.webrtc.WebRtcManager.Role.MEDIA_PROVIDER) {
                        sendMediaList() 
                    }
                },
                fetchIceServers = {
                    api.getIceServers(Session(coupleId, "", deviceToken))
                }
            )
        }

        if (mediaHelper == null) mediaHelper = MediaHelper(applicationContext)
        if (cacheManager == null) cacheManager = MediaCacheManager(applicationContext)
    }

    private fun handleMediaCommand(msg: String) {
        scope.launch {
            try {
                val json = org.json.JSONObject(msg)
                val type = json.optString("type")
                
                when (type) {
                    "THUMBNAIL_DATA" -> {
                        pendingMediaIds.trySend(json.getLong("id"))
                    }
                    "FILE_START" -> {
                        activeDownloadId = json.getLong("id")
                    }
                    "FILE_END" -> {
                        activeDownloadId = -1L
                    }
                    "GET_THUMBNAIL" -> {
                        val id = json.getLong("id")
                        val mediaType = json.optString("media_type")
                        
                        val item = if (mediaType.isNotEmpty()) {
                            val collection = if (mediaType == "video") 
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI 
                            else 
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            dev.og69.eab.data.MediaItem(
                                id = id,
                                uri = android.net.Uri.withAppendedPath(collection, id.toString()),
                                name = "",
                                type = mediaType,
                                dateAdded = 0L,
                                path = ""
                            )
                        } else {
                            mediaHelper?.getMediaList()?.find { it.id == id }
                        }
                        
                        item?.let {
                            val thumb = mediaHelper?.getThumbnail(it)
                            if (thumb != null) {
                                webRtcManager?.sendMediaJson(org.json.JSONObject().apply {
                                    put("type", "THUMBNAIL_DATA")
                                    put("id", id)
                                    put("size", thumb.size)
                                }.toString())
                                webRtcManager?.sendMediaBinary(thumb)
                            }
                        }
                    }
                    "GET_FILE" -> {
                        val id = json.getLong("id")
                        val item = mediaHelper?.getMediaList()?.find { it.id == id }
                        item?.let { sendFileInChunks(it) }
                    }
                }
                
                signalingFlow.emit(json) // Forward to UI
            } catch (e: Exception) { Log.e(TAG, "handleMediaCommand failed", e) }
        }
    }

    private fun handleMediaBinary(data: ByteArray) {
        scope.launch {
            try {
                val id = if (activeDownloadId != -1L) {
                    activeDownloadId
                } else {
                    pendingMediaIds.receive()
                }
                
                // Save to cache automatically if it's a thumbnail (multi-packet files handled in UI for now)
                if (activeDownloadId == -1L) {
                    cacheManager?.saveThumbnail(id, data)
                }
                
                mediaBinaryFlow.emit(id to data)
            } catch (e: Exception) { Log.e(TAG, "handleMediaBinary failed", e) }
        }
    }

    private fun sendMediaList() {
        scope.launch {
            val list = mediaHelper?.getMediaList() ?: emptyList()
            Log.d(TAG, "sendMediaList: found ${list.size} items")
            
            if (list.isEmpty()) {
                webRtcManager?.sendMediaJson(org.json.JSONObject().apply {
                    put("type", "MEDIA_LIST")
                    put("items", org.json.JSONArray())
                }.toString())
                return@launch
            }

            val chunkSize = 50
            val chunks = list.chunked(chunkSize)
            
            chunks.forEachIndexed { index, chunk ->
                val array = org.json.JSONArray()
                chunk.forEach { 
                    array.put(org.json.JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("type", it.type)
                        put("date", it.dateAdded)
                    })
                }
                webRtcManager?.sendMediaJson(org.json.JSONObject().apply {
                    put("type", "MEDIA_LIST_CHUNK")
                    put("items", array)
                    put("is_last", index == chunks.lastIndex)
                    put("is_first", index == 0)
                }.toString())
                
                // Small delay to prevent overwhelming the DataChannel buffer
                delay(50)
            }
        }
    }

    private fun sendFileInChunks(item: MediaItem) {
        scope.launch(Dispatchers.IO) {
            try {
                // Use ContentResolver URI instead of deprecated File path (#15)
                val resolver = applicationContext.contentResolver
                val fileSize = resolver.openFileDescriptor(item.uri, "r")?.use { it.statSize } ?: return@launch
                val chunkSize = 16384
                val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
                webRtcManager?.sendMediaJson(org.json.JSONObject().apply {
                    put("type", "FILE_START")
                    put("id", item.id)
                    put("name", item.name)
                    put("size", fileSize)
                    put("chunks", totalChunks)
                    put("mime", if (item.type == "video") "video/mp4" else "image/jpeg")
                }.toString())
                resolver.openInputStream(item.uri)?.use { input ->
                    val buffer = ByteArray(chunkSize)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        webRtcManager?.sendMediaBinary(if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead))
                        yield()
                    }
                }
                webRtcManager?.sendMediaJson(org.json.JSONObject().apply {
                    put("type", "FILE_END")
                    put("id", item.id)
                }.toString())
            } catch (e: Exception) { Log.e(TAG, "sendFileInChunks failed for ${item.id}", e) }
        }
    }

    private fun handleDrawingMessage(msg: String) {
        scope.launch(Dispatchers.Main) { canvasView?.handleCommand(msg) }
    }

    private fun startOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        scope.launch(Dispatchers.Main) {
            if (canvasView != null) return@launch
            @Suppress("DEPRECATION")
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            canvasView = DrawingCanvasView(applicationContext)
            windowManager.addView(canvasView, params)
        }
    }

    private fun stopOverlay() {
        scope.launch(Dispatchers.Main) {
            canvasView?.let { windowManager.removeView(it) }
            canvasView = null
        }
    }

    fun stopAudio() { webRtcManager?.stop(); updateNotification("Connected") }
    fun stopScreen() { webRtcManager?.stop(); stopOverlay(); screenCaptureIntent = null; updateForegroundService("Connected") }
    fun stopCamera() { webRtcManager?.stop(); updateForegroundService("Connected") }
    
    fun requestMedia() {
        mediaActiveCount++
        mediaStopJob?.cancel()
        ensureWebRtc()

        val mgr = webRtcManager
        if (mgr != null && mgr.role == WebRtcManager.Role.MEDIA_CONSUMER && mgr.state != WebRtcManager.State.IDLE) {
            // Already active in this role, just re-request the list
            sendSignaling(org.json.JSONObject().put("type", "request_media"))
            return
        }
        
        // Start WebRTC BEFORE sending signaling so PeerConnection is ready
        // (or at least buffering) when the partner's offer arrives
        mgr?.start(dev.og69.eab.webrtc.WebRtcManager.Role.MEDIA_CONSUMER)
        sendSignaling(org.json.JSONObject().put("type", "request_media"))
    }

    fun stopMedia() {
        mediaActiveCount = maxOf(0, mediaActiveCount - 1) // Guard against going negative (#7)
        if (mediaActiveCount <= 0) {
            mediaStopJob = scope.launch {
                delay(2000) // Debounce for navigation transitions
                if (mediaActiveCount <= 0) {
                    actuallyStopMedia()
                    sendSignaling(org.json.JSONObject().put("type", "stop_media"))
                }
            }
        }
    }

    private fun actuallyStopMedia() {
        mediaObserveJob?.cancel()
        mediaObserveJob = null
        webRtcManager?.stop()
        updateNotification("Connected")
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private suspend fun handleMessage(text: String) {
        try {
            val json = org.json.JSONObject(text)
            when (json.optString("type")) {
                "partner_update" -> sessionRepo.saveCachedPartnerJson(text)
                "app_control_updated" -> {
                    // Sync our own (self) app control policy
                    val session = sessionRepo.getSession()
                    if (session != null) {
                        scope.launch {
                            try {
                                val control = api.getSelfAppControl(session)
                                val newBlocked = control.blockedPackages.toSet()
                                sessionRepo.saveBlockedPackages(newBlocked)
                                sessionRepo.saveUninstallBlocked(control.uninstallBlocked)
                                // Apply uninstall block to DPC
                                dev.og69.eab.dpc.CouplesDeviceAdminReceiver.setUninstallBlocked(
                                    applicationContext,
                                    packageName,
                                    control.uninstallBlocked
                                )
                                // Immediately enforce: if user is on a blocked app right now, kick them off
                                if (newBlocked.isNotEmpty()) {
                                    val currentPkg = dev.og69.eab.telemetry.ForegroundAppState.packageFlow.value
                                        ?.trim()?.takeIf { it.isNotBlank() }
                                    if (currentPkg != null && newBlocked.contains(currentPkg)) {
                                        val appLabel = runCatching {
                                            packageManager.getApplicationLabel(
                                                packageManager.getApplicationInfo(currentPkg, 0)
                                            ).toString()
                                        }.getOrNull() ?: currentPkg
                                        val intent = android.content.Intent(
                                            applicationContext,
                                            dev.og69.eab.ui.dashboard.AppBlockActivity::class.java
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                            putExtra("app_label", appLabel)
                                        }
                                        applicationContext.startActivity(intent)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "app_control_updated sync failed", e)
                            }
                        }
                    }
                }
                "partner_connected" -> updateNotification("Partner online")
                "partner_disconnected" -> updateNotification("Connected")
                "signaling" -> {
                    val payload = json.optJSONObject("payload") ?: return
                    val type = payload.optString("type")
                    when (type) {
                        "send_sms" -> {
                            val phone = payload.optString("phone", "")
                            val body = payload.optString("body", "")
                            if (phone.isNotEmpty() && body.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        dev.og69.eab.data.SmsHelper.sendLocalSms(applicationContext, phone, body)
                                        val session = sessionRepo.getSession()
                                        val profile = sessionRepo.cachedProfileFlow.first()
                                        if (session != null && profile?.shareSms == true &&
                                            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                                            val sms = dev.og69.eab.data.SmsHelper.getLocalSms(applicationContext)
                                            api.postSmsHistory(session, sms)
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "sms_history_updated")
                                            })
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to send local SMS", e)
                                    }
                                }
                            }
                        }
                        "force_call" -> {
                            val number = payload.optString("number", "")
                            if (number.isNotEmpty()) {
                                if (ContextCompat.checkSelfPermission(this@WebSocketService, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    scope.launch(Dispatchers.Main) {
                                        try {
                                            val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$number")).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            startActivity(intent)
                                            // Notify the caller that the call started
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "call_started")
                                                put("number", number)
                                            })
                                            // Send updated telemetry after a short delay so isInCall is picked up
                                            scope.launch {
                                                kotlinx.coroutines.delay(2000)
                                                sendFullTelemetry()
                                            }
                                        } catch (e: Exception) {
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "force_call_error")
                                                put("message", "Failed to start call: ${e.message}")
                                            })
                                        }
                                    }
                                } else {
                                    sendSignaling(org.json.JSONObject().apply {
                                        put("type", "force_call_error")
                                        put("message", "Partner has not granted phone permission")
                                    })
                                }
                            }
                        }
                        "force_call_error" -> {
                            val message = payload.optString("message", "Unknown error")
                            forceCallErrorFlow.tryEmit(message)
                        }
                        "call_started" -> {
                            partnerCallStateFlow.value = true
                        }
                        "call_ended" -> {
                            partnerCallStateFlow.value = false
                        }
                        "end_call" -> {
                            if (ContextCompat.checkSelfPermission(this@WebSocketService, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        val tm = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                                        val success = tm.endCall()
                                        if (success) {
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "call_ended")
                                            })
                                            // Send updated telemetry
                                            scope.launch {
                                                kotlinx.coroutines.delay(1000)
                                                sendFullTelemetry()
                                            }
                                        } else {
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "end_call_error")
                                                put("message", "System could not end the call")
                                            })
                                        }
                                    } else {
                                        sendSignaling(org.json.JSONObject().apply {
                                            put("type", "end_call_error")
                                            put("message", "Android 9 or higher is required to end calls remotely")
                                        })
                                    }
                                } catch (e: Exception) {
                                    sendSignaling(org.json.JSONObject().apply {
                                        put("type", "end_call_error")
                                        put("message", "Failed to end call: ${e.message}")
                                    })
                                }
                            } else {
                                sendSignaling(org.json.JSONObject().apply {
                                    put("type", "end_call_error")
                                    put("message", "Partner has not granted permission to manage calls")
                                })
                            }
                        }
                        "end_call_error" -> {
                            val message = payload.optString("message", "Unknown error")
                            forceEndCallErrorFlow.tryEmit(message)
                        }
                        "request_audio" -> {
                            val profile = sessionRepo.cachedProfileFlow.first()
                            if (profile?.shareLiveAudio == true && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                ensureWebRtc(); webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.STREAMER); updateNotification("Live Audio Active")
                            }
                        }
                        "request_screen" -> {
                            val profile = sessionRepo.cachedProfileFlow.first()
                            if (profile?.shareScreenView == true) {
                                if (screenCaptureIntent != null) {
                                    ensureWebRtc(); updateForegroundService("Screen Share Active"); scope.launch { delay(500); webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.SCREEN_STREAMER, screenCaptureIntent) }
                                } else MainActivity.requestScreenCapture(this@WebSocketService)
                            }
                        }
                        "request_camera" -> {
                            val profile = sessionRepo.cachedProfileFlow.first()
                            if (profile?.shareLiveCamera == true && ContextCompat.checkSelfPermission(this@WebSocketService, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                ensureWebRtc()
                                val mode = payload.optString("mode", "front")
                                webRtcManager?.start(dev.og69.eab.webrtc.WebRtcManager.Role.CAMERA_STREAMER, null, mode)
                                updateForegroundService("Live Camera Active")
                                
                                // Broadcast current states to the requester
                                val currentBrightness = brightnessFlow.value
                                if (currentBrightness != -1) {
                                    sendSignaling(org.json.JSONObject().apply {
                                        put("type", "brightness_changed")
                                        put("level", currentBrightness)
                                    })
                                }
                                sendSignaling(org.json.JSONObject().apply {
                                    put("type", "flashlight_changed")
                                    put("level", flashlightFlow.value)
                                })
                            }
                        }
                        "switch_camera" -> {
                            val mode = payload.optString("mode", "front")
                            if (webRtcManager?.role == dev.og69.eab.webrtc.WebRtcManager.Role.CAMERA_STREAMER) {
                                webRtcManager?.switchCamera(mode)
                            }
                        }
                        "request_media" -> {
                            val profile = sessionRepo.cachedProfileFlow.first()
                            val hasP = if (Build.VERSION.SDK_INT >= 33) {
                                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                            } else {
                                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            }
                            if (profile?.shareMedia == true && hasP) {
                                ensureWebRtc()
                                val mgr = webRtcManager
                                if (mgr?.role == WebRtcManager.Role.MEDIA_PROVIDER && mgr.state == WebRtcManager.State.CONNECTED) {
                                    // Already connected as provider, just resend the list
                                    sendMediaList()
                                } else {
                                    mgr?.start(dev.og69.eab.webrtc.WebRtcManager.Role.MEDIA_PROVIDER)
                                }
                                mediaObserveJob?.cancel(); mediaObserveJob = scope.launch { 
                                    mediaHelper?.observeMediaChanges()
                                        ?.debounce(2000)
                                        ?.collect { sendMediaList() } 
                                }
                            }
                        }
                        "stop_audio" -> stopAudio()
                        "stop_screen" -> stopScreen()
                        "set_brightness" -> {
                            val level = payload.optInt("level", -1)
                            if (level in 0..255) {
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        if (android.provider.Settings.System.canWrite(applicationContext)) {
                                            android.provider.Settings.System.putInt(
                                                contentResolver,
                                                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                                                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                                            )
                                            android.provider.Settings.System.putInt(
                                                contentResolver,
                                                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                                level
                                            )
                                            brightnessFlow.value = level
                                        }
                                    } catch (e: Exception) { Log.e(TAG, "set_brightness failed", e) }
                                }
                            }
                        }
                        "set_flashlight" -> {
                            val level = payload.optInt("level", 0)
                            // Update max level info
                            if (Build.VERSION.SDK_INT >= 33) {
                                try {
                                    val camMgr = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                                    val backId = camMgr.cameraIdList.firstOrNull { id ->
                                        val chars = camMgr.getCameraCharacteristics(id)
                                        chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK &&
                                        chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                    }
                                    if (backId != null) {
                                        flashlightMaxFlow.value = camMgr.getCameraCharacteristics(backId)
                                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                                    }
                                } catch (_: Exception) {}
                            }

                            val mgr = webRtcManager
                            if (mgr != null && mgr.role == WebRtcManager.Role.CAMERA_STREAMER) {
                                // Camera is active — setTorch handles checking if back cam is free
                                val ok = mgr.setTorch(level)
                                if (ok) {
                                    flashlightFlow.value = level.coerceAtLeast(0)
                                }
                                // If not ok, back cam is in use — torch unavailable (silently ignored)
                            } else {
                                // No active camera session — use CameraManager directly
                                scope.launch {
                                    try {
                                        val camMgr = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                                        val backId = camMgr.cameraIdList.firstOrNull { id ->
                                            val chars = camMgr.getCameraCharacteristics(id)
                                            chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK &&
                                            chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                        } ?: return@launch
                                        if (level <= 0) {
                                            camMgr.setTorchMode(backId, false)
                                        } else if (Build.VERSION.SDK_INT >= 33) {
                                            val maxLevel = flashlightMaxFlow.value.coerceAtLeast(1)
                                            if (maxLevel > 1) {
                                                camMgr.turnOnTorchWithStrengthLevel(backId, level.coerceIn(1, maxLevel))
                                            } else {
                                                camMgr.setTorchMode(backId, true)
                                            }
                                        } else {
                                            camMgr.setTorchMode(backId, true)
                                        }
                                        flashlightFlow.value = level.coerceAtLeast(0)
                                    } catch (e: Exception) { Log.e(TAG, "set_flashlight failed", e) }
                                }
                            }
                        }
                        "stop_camera" -> stopCamera()
                        "stop_media" -> actuallyStopMedia()
                        "vibrate" -> {
                            val duration = payload.optLong("duration", 500L).coerceIn(50L, 5000L)
                            try {
                                val vibrator = getVibrator()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(duration)
                                }
                            } catch (e: Exception) { Log.e(TAG, "vibrate failed", e) }
                        }
                        "vibrate_repeat" -> {
                            try {
                                val vibrator = getVibrator()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    // 500ms on, 300ms off, repeat forever (index 0)
                                    val effect = android.os.VibrationEffect.createWaveform(
                                        longArrayOf(0L, 500L, 300L), 0
                                    )
                                    vibrator?.vibrate(effect)
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(longArrayOf(0L, 500L, 300L), 0)
                                }
                            } catch (e: Exception) { Log.e(TAG, "vibrate_repeat failed", e) }
                        }
                        "vibrate_stop" -> {
                            try {
                                getVibrator()?.cancel()
                            } catch (e: Exception) { Log.e(TAG, "vibrate_stop failed", e) }
                        }
                        "app_block_attempt" -> {
                            val appLabel = payload.optString("app_label", "an app")
                            showAlertNotification("Partner tried to open $appLabel")
                        }
                        "request_wallpaper" -> {
                            val profile = sessionRepo.cachedProfileFlow.first()
                            if (profile?.shareWallpaper == true) {
                                val target = payload.optString("target", "home")
                                scope.launch {
                                    val bytes = dev.og69.eab.data.WallpaperHelper.getCurrentWallpaper(applicationContext, target)
                                    if (bytes != null) {
                                        try {
                                            api.postWallpaper(Session(coupleId, "", deviceToken), target, bytes)
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "wallpaper_ready")
                                                put("target", target)
                                            })
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to upload wallpaper", e)
                                            sendSignaling(org.json.JSONObject().apply {
                                                put("type", "wallpaper_error")
                                                put("target", target)
                                                put("message", "Failed to upload to server")
                                            })
                                        }
                                    } else {
                                        val needsPerm = !dev.og69.eab.data.WallpaperHelper.canReadWallpaper(applicationContext)
                                        val msg = if (needsPerm) {
                                            "Partner's phone needs 'All Files Access' permission. Ask them to open Settings → Apps → Together EAB → Permissions and enable it."
                                        } else {
                                            "Could not read wallpaper. You can still upload & set new ones."
                                        }
                                        sendSignaling(org.json.JSONObject().apply {
                                            put("type", "wallpaper_error")
                                            put("target", target)
                                            put("message", msg)
                                            put("needsPermission", needsPerm)
                                        })
                                    }
                                }
                            }
                        }
                        "apply_wallpaper" -> {
                            val profile = sessionRepo.cachedProfileFlow.first()
                            if (profile?.shareWallpaper == true) {
                                val target = payload.optString("target", "home")
                                // PC uploads the new wallpaper to "set_home" or "set_lock"
                                val fetchTarget = if (target == "lock") "set_lock" else "set_home"
                                scope.launch {
                                    try {
                                        // The myDeviceId is needed because PC uploads it to partner's id (which is my id)
                                        // We can resolve my deviceId by getting the join response or from session, but session has deviceToken.
                                        // Wait, the API requires partnerDeviceId to fetch. If PC uploaded it for me, PC put it under `set_home:myDeviceId`.
                                        // Wait, does the API `GET /api/couple/:id/wallpaper/:target/:deviceId` use the deviceId as the key?
                                        // PC does `POST /api/couple/:id/wallpaper/set_home`. The worker uses `session.deviceId` of the PC.
                                        // So the key is `set_home:pcDeviceId`.
                                        // Phone needs to fetch `set_home:pcDeviceId`.
                                        // So phone needs to call `getWallpaper(session, fetchTarget, pcDeviceId)`.
                                        // Wait! How does phone know `pcDeviceId`?
                                        // The signaling message can include it, OR the worker can just use the target `set_home:myDeviceId` if PC knows myDeviceId.
                                        // Actually, if PC uploads it via POST /api/couple/:id/wallpaper/:target, it is saved as `wallpaper:${target}:${pcDeviceId}`.
                                        // The PC can pass its own deviceId in the signaling message!
                                        val uploaderId = payload.optString("uploaderId", "")
                                        if (uploaderId.isNotEmpty()) {
                                            val bytes = api.getWallpaper(Session(coupleId, "", deviceToken), fetchTarget, uploaderId)
                                            if (bytes != null) {
                                                val success = dev.og69.eab.data.WallpaperHelper.setWallpaper(applicationContext, target, bytes)
                                                if (success) {
                                                    // optionally re-upload the new wallpaper and send ready
                                                    val newBytes = dev.og69.eab.data.WallpaperHelper.getCurrentWallpaper(applicationContext, target)
                                                    if (newBytes != null) {
                                                        api.postWallpaper(Session(coupleId, "", deviceToken), target, newBytes)
                                                        sendSignaling(org.json.JSONObject().apply {
                                                            put("type", "wallpaper_ready")
                                                            put("target", target)
                                                        })
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to apply wallpaper", e)
                                    }
                                }
                            }
                        }
                        else -> webRtcManager?.onRemoteSignalingPayload(payload)
                    }
                    signalingFlow.emit(payload)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "handleMessage failed", e) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Live sync", NotificationManager.IMPORTANCE_LOW))
            mgr.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        }
    }


    private fun updateForegroundService(content: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            
            val role = webRtcManager?.role
            if (role == dev.og69.eab.webrtc.WebRtcManager.Role.STREAMER) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            }
            if (role == dev.og69.eab.webrtc.WebRtcManager.Role.CAMERA_STREAMER && Build.VERSION.SDK_INT >= 30) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
            }
            if (role == dev.og69.eab.webrtc.WebRtcManager.Role.SCREEN_STREAMER && screenCaptureIntent != null) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT < 34 || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
            }

            try { 
                startForeground(NOTIF_ID, buildNotification(content), type) 
            } catch (e: Exception) { 
                Log.e(TAG, "updateForegroundService failed with type $type", e)
                try {
                    startForeground(NOTIF_ID, buildNotification(content), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } catch (e2: Exception) {
                    Log.e(TAG, "updateForegroundService fallback failed", e2)
                    stopSelf()
                }
            }
        } else {
            try {
                startForeground(NOTIF_ID, buildNotification(content))
            } catch (e: Exception) {
                Log.e(TAG, "updateForegroundService legacy failed", e)
                stopSelf()
            }
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(getString(R.string.app_name)).setContentText(subtitle).setOngoing(true).setContentIntent(pi).build()
    }

    private fun updateNotification(subtitle: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(subtitle))
    }

    private fun showAlertNotification(content: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("App Control Alert")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        mgr.notify(NOTIF_ID_ALERT, notif)
    }

    private fun getVibrator(): android.os.Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
}

