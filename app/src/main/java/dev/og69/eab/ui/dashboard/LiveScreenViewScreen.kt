package dev.og69.eab.ui.dashboard

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.og69.eab.network.WebSocketService
import dev.og69.eab.webrtc.WebRtcManager
import org.json.JSONObject
import org.webrtc.EglBase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LiveScreenViewScreen(onBack: () -> Unit) {
    val state by WebSocketService.screenStateFlow.collectAsState()
    val remoteTracks by WebSocketService.remoteVideoTracksFlow.collectAsState()
    val remoteTrack = remoteTracks.values.firstOrNull()
    
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }
    
    var isMuted by remember { mutableStateOf(false) }

    // Toolkit State
    var currentTool by remember { mutableStateOf(Tool.BRUSH) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var selectedEmoji by remember { mutableStateOf("❤️") }
    var showTextInput by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var textBuffer by remember { mutableStateOf("") }
    var isUiVisible by remember { mutableStateOf(true) }
    
    val haptic = LocalHapticFeedback.current
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val handleBack = {
        WebSocketService.stopScreen()
        onBack()
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Live Screen",
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
                        if (state == WebRtcManager.State.CONNECTED || remoteTrack != null) {
                            IconButton(onClick = { WebSocketService.setSpeakerphone(!isSpeakerOn) }) {
                                Icon(
                                    if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                                    null,
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { isMuted = !isMuted }) {
                            Icon(if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null, tint = Color.White)
                        }
                        if (state == WebRtcManager.State.CONNECTED || remoteTrack != null) {
                            IconButton(onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                WebSocketService.sendDrawingData(JSONObject().put("type", "clear").toString())
                            }) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear All", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        titleContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerSize = androidx.compose.ui.unit.IntSize(it.size.width, it.size.height) }
        ) {
            if (remoteTrack != null) {
                val track = remoteTrack
                key(track?.id() ?: "") {
                    AndroidView(
                        factory = { ctx ->
                            dev.og69.eab.webrtc.TextureViewRenderer(ctx).apply {
                                init(eglBase.eglBaseContext)
                                try { track?.addSink(this) } catch (e: Exception) {}
                            }
                        },
                        update = { },
                        onRelease = { view ->
                            try { track?.removeSink(view) } catch(e: Exception) {}
                            view.release()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                LaunchedEffect(track) {
                    try { track?.setEnabled(true) } catch(e: Exception) {}
                }

                // Interaction Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentTool, currentColor, selectedEmoji) {
                            if (currentTool == Tool.BRUSH) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val position = event.changes.first().position
                                        val normalizedX = position.x / size.width
                                        val normalizedY = position.y / size.height
                                        
                                        val type = when (event.type) {
                                            PointerEventType.Press -> "down"
                                            PointerEventType.Move -> "move"
                                            PointerEventType.Release -> "up"
                                            else -> null
                                        }
                                        
                                        if (type != null) {
                                            WebSocketService.sendDrawingData(
                                                JSONObject().apply {
                                                    put("type", "draw")
                                                    put("action", type)
                                                    put("x", normalizedX.toDouble())
                                                    put("y", normalizedY.toDouble())
                                                    put("color", currentColor.toArgb())
                                                }.toString()
                                            )
                                        }
                                    }
                                }
                            } else {
                                detectTapGestures(
                                    onTap = { isUiVisible = !isUiVisible },
                                    onDoubleTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        WebSocketService.sendDrawingData(JSONObject().put("type", "clear").toString())
                                    },
                                    onLongPress = { offset ->
                                        val nx = offset.x / size.width
                                        val ny = offset.y / size.height
                                        if (currentTool == Tool.EMOJI) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            WebSocketService.sendDrawingData(
                                                JSONObject().apply {
                                                    put("type", "emoji")
                                                    put("emoji", selectedEmoji)
                                                    put("x", nx.toDouble())
                                                    put("y", ny.toDouble())
                                                }.toString()
                                            )
                                        } else if (currentTool == Tool.TEXT) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showTextInput = offset
                                        }
                                    }
                                )
                            }
                        }
                )

                // Premium Toolbars
                AnimatedVisibility(
                    visible = isUiVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tool Selection (Glassmorphic)
                        Surface(
                            modifier = Modifier.padding(bottom = 12.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ToolIcon(Icons.Rounded.Brush, currentTool == Tool.BRUSH) { currentTool = Tool.BRUSH }
                                ToolIcon(Icons.Rounded.AddReaction, currentTool == Tool.EMOJI) { currentTool = Tool.EMOJI }
                                ToolIcon(Icons.Rounded.TextFields, currentTool == Tool.TEXT) { currentTool = Tool.TEXT }
                            }
                        }

                        // Contextual Controls
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentTool == Tool.BRUSH || currentTool == Tool.TEXT) {
                                    listOf(Color.Red, Color.Cyan, Color.Green, Color.Yellow, Color.White).forEach { color ->
                                        ColorDot(color, currentColor == color) { currentColor = color }
                                    }
                                } else if (currentTool == Tool.EMOJI) {
                                    listOf("❤️", "😂", "😮", "🔥", "👍", "😭", "💯").forEach { emoji ->
                                        EmojiButton(emoji, selectedEmoji == emoji) { selectedEmoji = emoji }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LoadingState(state) { WebSocketService.requestScreen() }
            }
        }

        // Text Input Dialog
        if (showTextInput != null && containerSize.width > 0) {
            AlertDialog(
                onDismissRequest = { showTextInput = null; textBuffer = "" },
                title = { Text("Add Note") },
                text = {
                    TextField(
                        value = textBuffer,
                        onValueChange = { textBuffer = it },
                        placeholder = { Text("Enter message...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val offset = showTextInput!!
                        val nx = offset.x / containerSize.width
                        val ny = offset.y / containerSize.height
                        WebSocketService.sendDrawingData(
                            JSONObject().apply {
                                put("type", "text")
                                put("text", textBuffer)
                                put("x", nx.toDouble())
                                put("y", ny.toDouble())
                                put("color", currentColor.toArgb())
                            }.toString()
                        )
                        showTextInput = null
                        textBuffer = ""
                    }) {
                        Text("Send")
                    }
                }
            )
        }
    }
}

enum class Tool { BRUSH, EMOJI, TEXT }

@Composable
fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(
            if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            androidx.compose.foundation.shape.CircleShape
        )
    ) {
        Icon(icon, null, tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun ColorDot(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color, androidx.compose.foundation.shape.CircleShape)
            .border(
                if (isSelected) 3.dp else 1.dp,
                if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                androidx.compose.foundation.shape.CircleShape
            )
            .pointerInput(Unit) { detectTapGestures { onClick() } }
    )
}

@Composable
fun EmojiButton(emoji: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                androidx.compose.foundation.shape.CircleShape
            )
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun LoadingState(state: WebRtcManager.State, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            WebRtcManager.State.CONNECTING -> {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Connecting to partner...", color = Color.White)
            }
            WebRtcManager.State.ERROR -> {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Connection Failed", color = Color.White)
                Button(onClick = onRetry) { Text("Retry") }
            }
            else -> {
                Button(onClick = onRetry) { Text("Start Viewing") }
            }
        }
    }
}

fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
