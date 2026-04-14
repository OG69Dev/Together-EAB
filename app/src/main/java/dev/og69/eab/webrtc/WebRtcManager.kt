package dev.og69.eab.webrtc

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val onSignalingMessage: (JSONObject) -> Unit,
    private val onStateChange: (State) -> Unit = {}
) {
    enum class State { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    companion object {
        private const val TAG = "WebRtcManager"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
    }

    enum class Role { LISTENER, STREAMER }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var role: Role = Role.LISTENER

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
        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun start(role: Role) {
        this.role = role
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
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> onStateChange(State.CONNECTED)
                    PeerConnection.IceConnectionState.DISCONNECTED -> onStateChange(State.DISCONNECTED)
                    PeerConnection.IceConnectionState.FAILED -> onStateChange(State.ERROR)
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
                if (role == Role.STREAMER) {
                    createOffer()
                }
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack")
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
                Log.d(TAG, "onTrack: ${transceiver?.receiver?.track()?.kind()}")
            }
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
        })

        if (role == Role.STREAMER) {
            setupStreamer()
        } else {
            setupListener()
        }
    }

    private fun setupStreamer() {
        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
        // RenegotiationNeeded will trigger createOffer
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
                    // Already streamer? Just restart? 
                    // Usually we start streamer on this message.
                    start(Role.STREAMER)
                }
            }
            "audio_disabled" -> {
                Log.d(TAG, "Partner audio is disabled or permission missing")
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
        peerConnection?.dispose()
        peerConnection = null
        localAudioSource?.dispose()
        localAudioSource = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        onStateChange(State.IDLE)
    }

    fun dispose() {
        stop()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}
