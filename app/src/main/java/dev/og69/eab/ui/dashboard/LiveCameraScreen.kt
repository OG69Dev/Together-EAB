package dev.og69.eab.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.og69.eab.network.WebSocketService
import dev.og69.eab.webrtc.WebRtcManager
import org.webrtc.EglBase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCameraScreen(onBack: () -> Unit) {
    val state by WebSocketService.cameraStateFlow.collectAsState()
    val remoteTracks by WebSocketService.remoteVideoTracksFlow.collectAsState()
    
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }
    
    var isMuted by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf("front") } // front, back, both
    
    val haptic = LocalHapticFeedback.current

    val handleBack = {
        WebSocketService.stopCamera()
        onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Live Camera",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = Shadow(color = Color.Black, blurRadius = 8f)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    val isSpeakerOn by WebSocketService.speakerphoneFlow.collectAsState()
                    if (state == WebRtcManager.State.CONNECTED || remoteTracks.isNotEmpty()) {
                        IconButton(onClick = { WebSocketService.setSpeakerphone(!isSpeakerOn) }) {
                            Icon(
                                if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                                null,
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.3f),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (remoteTracks.isNotEmpty()) {
                val trackList = remoteTracks.values.toList()
                if (trackList.size == 1) {
                    val track = trackList[0]
                    key(track.id()) {
                        AndroidView(
                            factory = { ctx ->
                                dev.og69.eab.webrtc.TextureViewRenderer(ctx).apply {
                                    init(eglBase.eglBaseContext)
                                    try { track.addSink(this) } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            update = { },
                            onRelease = { view -> 
                                try { track.removeSink(view) } catch (e: Exception) { e.printStackTrace() }
                                view.release() 
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    LaunchedEffect(track) { try { track.setEnabled(true) } catch (e: Exception) {} }
                } else if (trackList.size >= 2) {
                    // Dual camera view
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val track0 = trackList[0]
                            key(track0.id()) {
                                AndroidView(
                                    factory = { ctx -> 
                                        dev.og69.eab.webrtc.TextureViewRenderer(ctx).apply { 
                                            init(eglBase.eglBaseContext) 
                                            try { track0.addSink(this) } catch (e: Exception) {}
                                        } 
                                    },
                                    update = { },
                                    onRelease = { view -> 
                                        try { track0.removeSink(view) } catch (e: Exception) {}
                                        view.release() 
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            LaunchedEffect(track0) { try { track0.setEnabled(true) } catch(e: Exception) {} }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val track1 = trackList[1]
                            key(track1.id()) {
                                AndroidView(
                                    factory = { ctx -> 
                                        dev.og69.eab.webrtc.TextureViewRenderer(ctx).apply { 
                                            init(eglBase.eglBaseContext) 
                                            try { track1.addSink(this) } catch (e: Exception) {}
                                        } 
                                    },
                                    update = { },
                                    onRelease = { view -> 
                                        try { track1.removeSink(view) } catch(e: Exception) {}
                                        view.release() 
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            LaunchedEffect(track1) { try { track1.setEnabled(true) } catch(e: Exception) {} }
                        }
                    }
                }

                // Camera Controls Toolbar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Mode:",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                            listOf("front" to Icons.Rounded.CameraFront, "back" to Icons.Rounded.CameraRear, "both" to Icons.Rounded.Cameraswitch).forEach { (mode, icon) ->
                                val isSelected = cameraMode == mode
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        cameraMode = mode
                                        WebSocketService.switchCamera(mode)
                                    },
                                    modifier = Modifier.background(
                                        if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                                ) {
                                    Icon(icon, contentDescription = mode, tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            } else {
                LoadingState(state) { WebSocketService.requestCamera(cameraMode) }
            }
        }
    }
}
