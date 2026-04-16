package dev.og69.eab.webrtc

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val onSignalingMessage: (JSONObject) -> Unit,
    private val onStateChange: (State) -> Unit = {},
    private val onVideoTrack: (VideoTrack?) -> Unit = {},
    private val onDataChannelMessage: (String) -> Unit = {},
    private val onMediaBinaryMessage: (ByteArray) -> Unit = {},
    private val onMediaJsonMessage: (String) -> Unit = {},
    private val onMediaChannelOpen: () -> Unit = {}
) {
    enum class State { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    companion object {
        private const val TAG = "WebRtcManager"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
    }

    enum class Role { LISTENER, STREAMER, SCREEN_LISTENER, SCREEN_STREAMER, MEDIA_PROVIDER, MEDIA_CONSUMER }


    private val eglBase = EglBase.create()
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
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    var state: State = State.IDLE
        private set

    init {
        initPeerConnectionFactory(context)
        peerConnectionFactory = createPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val encoderFactory = SoftwareVideoEncoderFactory()
        val decoderFactory = SoftwareVideoDecoderFactory()

        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun start(role: Role, mediaProjectionIntent: android.content.Intent? = null) {
        this.role = role
        state = State.CONNECTING
        onStateChange(State.CONNECTING)
        Log.d(TAG, "Starting WebRtcManager as $role")
        
        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
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
                if (role == Role.STREAMER || role == Role.SCREEN_STREAMER || role == Role.MEDIA_PROVIDER) {
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
                    remoteVideoTrack = track
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

        if (role == Role.SCREEN_STREAMER || role == Role.STREAMER || role == Role.MEDIA_PROVIDER) {
            setupDataChannelInitiator()
        }

        when (role) {
            Role.STREAMER -> setupStreamer()
            Role.SCREEN_STREAMER -> setupScreenStreamer(mediaProjectionIntent)
            Role.SCREEN_LISTENER -> setupScreenListener()
            Role.LISTENER -> setupListener()
            Role.MEDIA_PROVIDER -> setupMediaProvider()
            Role.MEDIA_CONSUMER -> setupMediaConsumer()
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
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
    }

    private fun setupAudioTrack() {
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints()
            localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
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
            
            // Portrait resolution (720x1280) is more reliable for phone screen capture
            videoCapturer?.startCapture(720, 1280, 30)
            
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
            
            peerConnection?.addTrack(localVideoTrack, listOf("ARDAMS"))

            // Audio
            setupAudioTrack()
            peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
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
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
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
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Answer onCreateFailure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun handleAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Answer onSetSuccess (remote)")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    fun stop() {
        Log.d(TAG, "Stopping WebRtcManager")
        state = State.IDLE
        onStateChange(State.IDLE)
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        dataChannel?.dispose()
        dataChannel = null
        mediaChannel?.dispose()
        mediaChannel = null
        peerConnection?.dispose()
        peerConnection = null
        localAudioSource?.dispose()
        localAudioSource = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        remoteVideoTrack = null
        onVideoTrack(null)
        onStateChange(State.IDLE)
    }

    fun dispose() {
        Log.d(TAG, "Disposing WebRtcManager")
        stop()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase.release()
    }
}
