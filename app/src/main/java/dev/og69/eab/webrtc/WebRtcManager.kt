package dev.og69.eab.webrtc

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import kotlinx.coroutines.*


class WebRtcManager(
    private val context: Context,
    private val onSignalingMessage: (JSONObject) -> Unit,
    private val onStateChange: (State) -> Unit = {},
    private val onVideoTrack: (VideoTrack) -> Unit = {},
    private val onRemoveVideoTrack: (VideoTrack) -> Unit = {},
    private val onDataChannelMessage: (String) -> Unit = {},
    private val onMediaBinaryMessage: (ByteArray) -> Unit = {},
    private val onMediaJsonMessage: (String) -> Unit = {},
    private val onMediaChannelOpen: () -> Unit = {},
    private val fetchIceServers: suspend () -> List<dev.og69.eab.network.IceServerConfig> = { emptyList() }
) {

    enum class State { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    companion object {
        private const val TAG = "WebRtcManager"
        private const val DEFAULT_STUN = "stun:stun.l.google.com:19302"

        @Volatile
        private var factoryInitialized = false

        /** Ensures PeerConnectionFactory.initialize() is called exactly once per process (#23) */
        private fun initializeOnce(context: Context) {
            if (!factoryInitialized) {
                synchronized(WebRtcManager::class.java) {
                    if (!factoryInitialized) {
                        val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                            .setEnableInternalTracer(true)
                            .createInitializationOptions()
                        PeerConnectionFactory.initialize(options)
                        factoryInitialized = true
                    }
                }
            }
        }

        fun isEmulator(): Boolean {
            return (android.os.Build.FINGERPRINT.startsWith("generic")
                    || android.os.Build.FINGERPRINT.startsWith("unknown")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == android.os.Build.PRODUCT)
        }
    }

    enum class Role { LISTENER, STREAMER, SCREEN_LISTENER, SCREEN_STREAMER, MEDIA_PROVIDER, MEDIA_CONSUMER, CAMERA_STREAMER, CAMERA_LISTENER }


    private val eglBase: EglBase by lazy { EglBase.create() } // Lazy creation (#21)
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var dataChannel: DataChannel? = null
    private var mediaChannel: DataChannel? = null
    var role: Role = Role.LISTENER
    private var videoCapturer: VideoCapturer? = null
    private var secondaryVideoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var secondarySurfaceTextureHelper: SurfaceTextureHelper? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    
    var state: State = State.IDLE
        private set

    init {
        initializeOnce(context)
        peerConnectionFactory = createPeerConnectionFactory()
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory

        if (isEmulator()) {
            Log.d(TAG, "Emulator detected: Forcing Software Video Encoding to fix black screen issue")
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        } else {
            encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        }

        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun start(role: Role, mediaProjectionIntent: android.content.Intent? = null, cameraMode: String = "front") {
        this.role = role
        state = State.CONNECTING
        onStateChange(State.CONNECTING)

        scope.launch {
            Log.d(TAG, "Starting WebRtcManager as $role (gathering ICE...)")
            
            // 1. Start with Default STUN
            val iceServers = mutableListOf(
                PeerConnection.IceServer.builder(DEFAULT_STUN).createIceServer()
            )
            
            // 2. Fetch Partner/Relay Servers (TURN) from Worker
            try {
                val turnConfigs = fetchIceServers()
                for (config in turnConfigs) {
                    val builder = PeerConnection.IceServer.builder(config.urls)
                    if (config.username != null) builder.setUsername(config.username)
                    if (config.credential != null) builder.setPassword(config.credential)
                    iceServers.add(builder.createIceServer())
                }
                Log.d(TAG, "Added ${turnConfigs.size} TURN servers to configuration")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch TURN servers, proceeding with STUN-only", e)
            }

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                // ICE gathering policy. WebRTC defaults to ALL (including relay if available).
                // "Fallback" behavior is handled by ICE scoring (P2P first, Relay last).
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                
                // OPTIMIZATIONS for P2P Priority:
                iceCandidatePoolSize = 10 // Pre-gather candidates to speed up P2P establishment
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE // Keep everything on one port
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE // Ensure RTCP muxing
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED // Prefer UDP for faster P2P discovery
            }


            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(TAG, "onIceCandidate: $candidate")
                    val json = JSONObject().apply {
                        put("type", "candidate")
                        put("label", candidate.sdpMLineIndex)
                        put("id", candidate.sdpMid)
                        put("candidate", candidate.sdp)
                    }
                    onSignalingMessage(json)
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(iceState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "onIceConnectionChange: $iceState")
                    when (iceState) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            state = State.CONNECTED
                            onStateChange(State.CONNECTED)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            state = State.DISCONNECTED
                            onStateChange(State.DISCONNECTED)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            state = State.ERROR
                            onStateChange(State.ERROR)
                        }
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                    Log.d(TAG, "onConnectionChange: $state")
                }
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "onRenegotiationNeeded")
                    if (this@WebRtcManager.role == Role.STREAMER || this@WebRtcManager.role == Role.SCREEN_STREAMER || this@WebRtcManager.role == Role.MEDIA_PROVIDER || this@WebRtcManager.role == Role.CAMERA_STREAMER) {
                        createOffer()
                    }
                }
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d(TAG, "onAddTrack")
                }
                override fun onTrack(transceiver: RtpTransceiver?) {
                    super.onTrack(transceiver)
                    val track = transceiver?.receiver?.track()
                    Log.d(TAG, "onTrack: ${track?.kind()}")
                    if (track is VideoTrack) {
                        onVideoTrack(track)
                    }
                }
                override fun onDataChannel(channel: DataChannel?) {
                    Log.d(TAG, "onDataChannel: ${channel?.label()}")
                    if (channel?.label() == "media") {
                        mediaChannel = channel
                        setupMediaChannel()
                    } else {
                        dataChannel = channel
                        setupDataChannel()
                    }
                }
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
            })

            if (this@WebRtcManager.role == Role.SCREEN_STREAMER || this@WebRtcManager.role == Role.STREAMER || this@WebRtcManager.role == Role.MEDIA_PROVIDER || this@WebRtcManager.role == Role.CAMERA_STREAMER) {
                setupDataChannelInitiator()
            }

            when (this@WebRtcManager.role) {
                Role.STREAMER -> setupStreamer()
                Role.SCREEN_STREAMER -> setupScreenStreamer(mediaProjectionIntent)
                Role.SCREEN_LISTENER -> setupScreenListener()
                Role.LISTENER -> setupListener()
                Role.MEDIA_PROVIDER -> setupMediaProvider()
                Role.MEDIA_CONSUMER -> setupMediaConsumer()
                Role.CAMERA_STREAMER -> setupCameraStreamer(cameraMode)
                Role.CAMERA_LISTENER -> setupCameraListener()
            }
        }
    }


    private fun setupMediaProvider() {
        // Just signaling
    }

    private fun setupMediaConsumer() {
        // Just signaling
    }

    private fun setupStreamer() {
        setupAudioTrack()
    }

    private fun setupAudioTrack() {
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints()
            localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        }
        
        // Ensure it's added to the peer connection exactly once (#CrashFix)
        val hasAudioTrack = peerConnection?.senders?.any { it.track()?.id() == "ARDAMSa0" } == true
        if (!hasAudioTrack && localAudioTrack != null) {
            peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
        }
    }

    private fun setupListener() {
        // Just wait for remote offer
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
    }

    private fun createOffer() {
        Log.d(TAG, "Creating offer")
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "Offer onCreateSuccess")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Offer onSetSuccess (local)")
                        val json = JSONObject().apply {
                            put("type", "offer")
                            put("sdp", sdp.description)
                        }
                        onSignalingMessage(json)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Offer onCreateFailure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun setupScreenStreamer(intent: android.content.Intent?) {
        Log.d(TAG, "setupScreenStreamer")

        // Video (Screen)
        if (intent != null) {
            videoCapturer = ScreenCapturerAndroid(intent, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection onStop called by system")
                }
            })
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            localVideoSource = peerConnectionFactory?.createVideoSource(true)
            videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
            
            // High-quality screen capture: 720p at 24fps strikes a good balance between delay and smoothness
            videoCapturer?.startCapture(720, 1280, 24)
            
            localVideoTrack = peerConnectionFactory?.createVideoTrack("ARDAMSv0", localVideoSource)
            
            // DIAGNOSTIC: Local frame probe
            localVideoTrack?.addSink(object : org.webrtc.VideoSink {
                override fun onFrame(frame: org.webrtc.VideoFrame?) {
                    val w = frame?.rotatedWidth ?: 0
                    val h = frame?.rotatedHeight ?: 0
                    if (w > 0 && h > 0) {
                        // Log every 100 frames to confirm capture is alive
                        Log.v(TAG, "LOCAL CAPTURE OK: ${w}x${h}")
                    }
                }
            })
            
            val sender = peerConnection?.addTrack(localVideoTrack, listOf("ARDAMS"))
            sender?.let { tuneScreenTrackParameters(it) }

            // Audio
            setupAudioTrack()
        }
    }

    private var currentCameraMode: String = "front"

    private fun setupCameraStreamer(mode: String) {
        currentCameraMode = mode
        Log.d(TAG, "setupCameraStreamer mode=$mode")
        
        setupAudioTrack()

        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        var front: String? = null
        var back: String? = null
        for (device in deviceNames) {
            if (enumerator.isFrontFacing(device)) front = device
            if (enumerator.isBackFacing(device)) back = device
        }

        if (mode == "front" || mode == "both") {
            front?.let { dev ->
                videoCapturer = enumerator.createCapturer(dev, null)
                surfaceTextureHelper = SurfaceTextureHelper.create("CamFront", eglBase.eglBaseContext)
                localVideoSource = peerConnectionFactory?.createVideoSource(false)
                videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
                videoCapturer?.startCapture(640, 480, 24)
                localVideoTrack = peerConnectionFactory?.createVideoTrack("CAMF0", localVideoSource)
                peerConnection?.addTrack(localVideoTrack, listOf("ARDAMS"))
            }
        }

        if (mode == "back" || mode == "both") {
            back?.let { dev ->
                val primaryCapturer = if (mode == "back") videoCapturer else secondaryVideoCapturer
                if (mode == "back") {
                    videoCapturer = enumerator.createCapturer(dev, null)
                    surfaceTextureHelper = SurfaceTextureHelper.create("CamBack", eglBase.eglBaseContext)
                    localVideoSource = peerConnectionFactory?.createVideoSource(false)
                    videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
                    videoCapturer?.startCapture(640, 480, 24)
                    localVideoTrack = peerConnectionFactory?.createVideoTrack("CAMB0", localVideoSource)
                    peerConnection?.addTrack(localVideoTrack, listOf("ARDAMS"))
                } else {
                    secondaryVideoCapturer = enumerator.createCapturer(dev, null)
                    secondarySurfaceTextureHelper = SurfaceTextureHelper.create("CamBack2", eglBase.eglBaseContext)
                    val secSource = peerConnectionFactory?.createVideoSource(false)
                    secondaryVideoCapturer?.initialize(secondarySurfaceTextureHelper, context, secSource?.capturerObserver)
                    secondaryVideoCapturer?.startCapture(480, 360, 24)
                    val secTrack = peerConnectionFactory?.createVideoTrack("CAMB1", secSource)
                    peerConnection?.addTrack(secTrack, listOf("ARDAMS"))
                }
            }
        }
    }

    private fun setupCameraListener() {
        Log.d(TAG, "setupCameraListener")
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        // allow for second video track in case of 'both'
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
    }

    fun switchCamera(mode: String) {
        if (role != Role.CAMERA_STREAMER) return
        Log.d(TAG, "switchCamera to $mode")
        
        // Full stop of video capture and tracks, and restart
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        secondaryVideoCapturer?.stopCapture()
        secondaryVideoCapturer?.dispose()
        secondaryVideoCapturer = null
        
        val senders = peerConnection?.senders ?: emptyList()
        for (sender in senders) {
            if (sender.track() is VideoTrack) {
                peerConnection?.removeTrack(sender)
            }
        }
        
        setupCameraStreamer(mode)
    }

    private fun tuneScreenTrackParameters(sender: RtpSender) {
        val parameters = sender.parameters
        if (parameters.encodings.isNotEmpty()) {
            for (encoding in parameters.encodings) {
                // High priority for screen sharing to minimize lag
                encoding.networkPriority = 3 // Priority.VERY_HIGH (3)
                encoding.bitratePriority = 2.0
                encoding.minBitrateBps = 300 * 1000 // 300kbps min (lower floor for stability)
                encoding.maxBitrateBps = 4000 * 1000 // 4Mbps max
                encoding.maxFramerate = 24
            }
            sender.parameters = parameters
        }
    }

    private fun setupScreenListener() {
        Log.d(TAG, "setupScreenListener")
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
    }

    private fun setupDataChannelInitiator() {
        Log.d(TAG, "setupDataChannelInitiator")
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("drawing", init)
        setupDataChannel()
        
        val mediaInit = DataChannel.Init()
        mediaChannel = peerConnection?.createDataChannel("media", mediaInit)
        setupMediaChannel()
    }

    private fun setupMediaChannel() {
        mediaChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "Media DataChannel State: ${mediaChannel?.state()}")
                if (mediaChannel?.state() == DataChannel.State.OPEN) {
                    onMediaChannelOpen()
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (buffer.binary) {
                    onMediaBinaryMessage(data)
                } else {
                    onMediaJsonMessage(String(data))
                }
            }
        })
    }

    private fun setupDataChannel() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel State: ${dataChannel?.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                onDataChannelMessage(String(data))
            }
        })
    }

    fun sendData(message: String) {
        val buffer = java.nio.ByteBuffer.wrap(message.toByteArray())
        dataChannel?.send(DataChannel.Buffer(buffer, false))
    }

    fun sendMediaJson(message: String) {
        val channel = mediaChannel
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            val buffer = java.nio.ByteBuffer.wrap(message.toByteArray())
            channel.send(DataChannel.Buffer(buffer, false))
        } else {
            Log.w(TAG, "Cannot sendMediaJson: Media channel not open")
        }
    }

    fun sendMediaBinary(data: ByteArray) {
        val channel = mediaChannel
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            val buffer = java.nio.ByteBuffer.wrap(data)
            channel.send(DataChannel.Buffer(buffer, true))
        } else {
            Log.w(TAG, "Cannot sendMediaBinary: Media channel not open")
        }
    }

    fun onRemoteSignalingPayload(json: JSONObject) {
        val type = json.optString("type")
        Log.d(TAG, "Received remote signaling: $type")
        when (type) {
            "offer" -> {
                val sdp = SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"))
                handleOffer(sdp)
            }
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                handleAnswer(sdp)
            }
            "candidate" -> {
                val candidate = IceCandidate(
                    json.getString("id"),
                    json.getInt("label"),
                    json.getString("candidate")
                )
                peerConnection?.addIceCandidate(candidate)
            }
            "request_audio" -> {
                Log.d(TAG, "Audio requested by partner")
                if (role == Role.STREAMER) {
                    start(Role.STREAMER)
                }
            }
            "request_screen" -> {
                Log.d(TAG, "Screen share requested by partner")
                if (role == Role.SCREEN_STREAMER) {
                    // Logic to trigger start will be in WebSocketService 
                }
            }
            "audio_disabled" -> {
                Log.d(TAG, "Partner audio is disabled or permission missing")
                onStateChange(State.ERROR)
            }
            "screen_disabled" -> {
                Log.d(TAG, "Partner screen share is disabled or permission missing")
                onStateChange(State.ERROR)
            }
        }
    }

    private fun handleOffer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Offer onSetSuccess (remote)")
                createAnswer()
            }
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "Offer setRemoteDescription onCreateFailure: $p0") }
            override fun onSetFailure(p0: String?) { Log.e(TAG, "Offer setRemoteDescription onSetFailure: $p0") }
        }, sdp)
    }

    private fun createAnswer() {
        Log.d(TAG, "Creating answer")
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "Answer onCreateSuccess")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Answer onSetSuccess (local)")
                        val json = JSONObject().apply {
                            put("type", "answer")
                            put("sdp", sdp.description)
                        }
                        onSignalingMessage(json)
                    }
                    override fun onCreateFailure(p0: String?) { Log.e(TAG, "Answer setLocalDescription onCreateFailure: $p0") }
                    override fun onSetFailure(p0: String?) { Log.e(TAG, "Answer setLocalDescription onSetFailure: $p0") }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Answer onCreateFailure: $error")
            }
            override fun onSetFailure(error: String?) { Log.e(TAG, "Answer createAnswer onSetFailure: $error") }
        }, constraints)
    }

    private fun handleAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Answer onSetSuccess (remote)")
            }
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "Answer setRemoteDescription onCreateFailure: $p0") }
            override fun onSetFailure(p0: String?) { Log.e(TAG, "Answer setRemoteDescription onSetFailure: $p0") }
        }, sdp)
    }

    fun stop() {
        Log.d(TAG, "Stopping WebRtcManager")
        state = State.IDLE
        onStateChange(State.IDLE)

        // 1. Stop and dispose capturers first (they feed into sources)
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        secondaryVideoCapturer?.stopCapture()
        secondaryVideoCapturer?.dispose()
        secondaryVideoCapturer = null
        secondarySurfaceTextureHelper?.dispose()
        secondarySurfaceTextureHelper = null

        // 2. Remove tracks from peer connection before closing it (#6)
        peerConnection?.senders?.forEach { sender ->
            try { peerConnection?.removeTrack(sender) } catch (_: Exception) {}
        }

        // 3. Close data channels
        dataChannel?.dispose()
        dataChannel = null
        mediaChannel?.dispose()
        mediaChannel = null

        // 4. Close peer connection
        peerConnection?.dispose()
        peerConnection = null

        // 5. Now safe to dispose sources and tracks
        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        remoteVideoTrack = null
    }

    fun dispose() {
        Log.d(TAG, "Disposing WebRtcManager")
        stop()
        scope.cancel()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase.release()
    }

}
