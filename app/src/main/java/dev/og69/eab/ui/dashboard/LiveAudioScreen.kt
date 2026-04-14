package dev.og69.eab.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.og69.eab.network.WebSocketService
import dev.og69.eab.webrtc.WebRtcManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAudioScreen(onBack: () -> Unit) {
    val state by WebSocketService.audioStateFlow.collectAsState()
    
    // Status text and color based on WebRTC state
    val statusText = when (state) {
        WebRtcManager.State.IDLE -> "Ready to Connect"
        WebRtcManager.State.CONNECTING -> "Establishing Peer Connection..."
        WebRtcManager.State.CONNECTED -> "Live Audio Stream Active"
        WebRtcManager.State.DISCONNECTED -> "Partner Disconnected"
        WebRtcManager.State.ERROR -> "Connection Error"
    }
    
    val statusColor by animateColorAsState(
        targetValue = when (state) {
            WebRtcManager.State.IDLE -> MaterialTheme.colorScheme.outline
            WebRtcManager.State.CONNECTING -> Color(0xFFEAB308) // Amber
            WebRtcManager.State.CONNECTED -> Color(0xFF22C55E) // Green
            WebRtcManager.State.DISCONNECTED, WebRtcManager.State.ERROR -> MaterialTheme.colorScheme.error
        },
        label = "statusColor"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Live Audio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Headset, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Visualization
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                if (state == WebRtcManager.State.CONNECTED || state == WebRtcManager.State.CONNECTING) {
                    RippleEffect(statusColor)
                }
                
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(160.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state == WebRtcManager.State.CONNECTED) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = statusColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = if (state == WebRtcManager.State.CONNECTED) 
                    "You are listening to your partner's microphone" 
                else 
                    "Partner must have app in background and permission granted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
            )

            Spacer(Modifier.height(64.dp))

            if (state == WebRtcManager.State.IDLE || state == WebRtcManager.State.DISCONNECTED || state == WebRtcManager.State.ERROR) {
                Button(
                    onClick = { WebSocketService.requestAudio() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Start Listening", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { WebSocketService.stopAudio() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Stop Listening", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RippleEffect(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(color, CircleShape)
    )
}
