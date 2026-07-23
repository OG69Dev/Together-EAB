package dev.og69.eab.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCameraScreen(onBack: () -> Unit) {
    val state by WebSocketService.cameraStateFlow.collectAsState()
    val remoteTracks by WebSocketService.remoteVideoTracksFlow.collectAsState()
    
    var retryCount by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(state) {
        if (state == WebRtcManager.State.CONNECTED) {
            retryCount = 0
        } else if (state == WebRtcManager.State.DISCONNECTED || state == WebRtcManager.State.ERROR) {
            if (retryCount < 3) {
                delay(3000)
                if (state == WebRtcManager.State.DISCONNECTED || state == WebRtcManager.State.ERROR) {
                    retryCount++
                    WebSocketService.requestCamera()
                }
            }
        }
    }
    
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }
    
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }
    
    var isMuted by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf("front") } // front, back, both
    
    val haptic = LocalHapticFeedback.current
    var switchingMode by remember { mutableStateOf(false) }
    var isFrontPrimary by remember { mutableStateOf(false) }
    var isBuzzingRepeat by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(cameraMode, isFrontPrimary) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val zoomModifier = Modifier
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offsetX,
            translationY = offsetY
        )
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                if (scale > 1f) {
                    val maxX = (size.width * (scale - 1)) / 2f
                    val maxY = (size.height * (scale - 1)) / 2f
                    offsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                    offsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                } else {
                    offsetX = 0f
                    offsetY = 0f
                }
            }
        }

    // Dimmer / flashlight state
    var showDimmer by remember { mutableStateOf(false) }
    var dimmerTab by remember { mutableIntStateOf(0) }
    val rawBrightness by WebSocketService.brightnessFlow.collectAsState()
    var brightnessLevel by remember { mutableFloatStateOf(50f) }
    val flashlightLevel by WebSocketService.flashlightFlow.collectAsState()
    val flashlightMax by WebSocketService.flashlightMaxFlow.collectAsState()
    var flashSlider by remember { mutableFloatStateOf(0f) }

    val volumeLevel by WebSocketService.volumeFlow.collectAsState()
    val volumeMax by WebSocketService.volumeMaxFlow.collectAsState()
    val playingSound by WebSocketService.playingSoundFlow.collectAsState()
    var volumeSlider by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(volumeLevel) {
        if (volumeLevel >= 0) {
            volumeSlider = volumeLevel.toFloat()
        }
    }

    LaunchedEffect(rawBrightness) {
        if (rawBrightness >= 0) {
            if (rawBrightness == 0) {
                brightnessLevel = 0f
            } else {
                val min = 2.0
                val max = 255.0
                val value = rawBrightness.toDouble().coerceAtLeast(min)
                brightnessLevel = ((Math.log(value / min) / Math.log(max / min)) * 100.0).toFloat()
            }
        }
    }

    LaunchedEffect(flashlightLevel) {
        flashSlider = flashlightLevel.toFloat()
    }

    val handleBack = {
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            WebSocketService.stopCamera()
            eglBase.release()
        }
    }

    // Permission check for Remote Brightness Control (WRITE_SETTINGS)
    var showPermissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!android.provider.Settings.System.canWrite(context)) {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Rounded.BrightnessLow, null, tint = Color(0xFFFBBF24)) },
            title = { Text("Remote Brightness Control") },
            text = { 
                Text("To allow your partner to remotely adjust this phone's screen brightness, you need to grant the 'Modify System Settings' permission.") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback if the specific package URI fails
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color.Black)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Not Now", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    val isSpeakerOn by WebSocketService.speakerphoneFlow.collectAsState()
                    if (state == WebRtcManager.State.CONNECTED || remoteTracks.isNotEmpty()) {
                        IconButton(onClick = { WebSocketService.setSpeakerphone(!isSpeakerOn) }) {
                            Icon(
                                if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Hearing,
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
                val frontEntry = remoteTracks.entries.find { it.key.contains("CAMF") }
                val backEntry = remoteTracks.entries.find { it.key.contains("CAMB") }
                val entryList = remoteTracks.entries.toList()

                if (entryList.size == 1 || (frontEntry != null && backEntry == null) || (frontEntry == null && backEntry != null)) {
                    val entry = frontEntry ?: backEntry ?: entryList[0]
                    val track = entry.value
                    key(System.identityHashCode(track)) {
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
                            modifier = Modifier.fillMaxSize().then(zoomModifier)
                        )
                    }
                    LaunchedEffect(track) { try { track.setEnabled(true) } catch (e: Exception) {} }
                } else if (entryList.size >= 2) {
                    // PiP Dual View
                    val primaryEntry = if (isFrontPrimary) (frontEntry ?: entryList[0]) else (backEntry ?: entryList.getOrNull(1) ?: entryList[0])
                    val secondaryEntry = if (isFrontPrimary) (backEntry ?: entryList.getOrNull(1) ?: entryList[0]) else (frontEntry ?: entryList[0])
                    val primary = primaryEntry.value
                    val secondary = secondaryEntry.value

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFrontPrimary = !isFrontPrimary
                            }
                    ) {
                        // Main View (Bottom Layer)
                        key(System.identityHashCode(primary)) {
                            AndroidView(
                                factory = { ctx -> 
                                    dev.og69.eab.webrtc.TextureViewRenderer(ctx).apply { 
                                        init(eglBase.eglBaseContext) 
                                        try { primary.addSink(this) } catch (e: Exception) {}
                                    } 
                                },
                                update = { },
                                onRelease = { view -> 
                                    try { primary.removeSink(view) } catch (e: Exception) {}
                                    view.release() 
                                },
                                modifier = Modifier.fillMaxSize().then(zoomModifier)
                            )
                        }
                        LaunchedEffect(primary) { try { primary.setEnabled(true) } catch(e: Exception) {} }

                        // PiP View (Top Layer / Floating)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .width(120.dp)
                                .height(180.dp)
                                .background(Color.Black, RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            key(System.identityHashCode(secondary)) {
                                AndroidView(
                                    factory = { ctx -> 
                                        dev.og69.eab.webrtc.TextureViewRenderer(ctx).apply { 
                                            init(eglBase.eglBaseContext) 
                                            try { secondary.addSink(this) } catch (e: Exception) {}
                                        } 
                                    },
                                    update = { },
                                    onRelease = { view -> 
                                        try { secondary.removeSink(view) } catch (e: Exception) {}
                                        view.release() 
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            LaunchedEffect(secondary) { try { secondary.setEnabled(true) } catch(e: Exception) {} }

                            // Click overlay to ensure the tap isn't consumed by the TextureViewRenderer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isFrontPrimary = !isFrontPrimary
                                    }
                            )
                        }
                    }
                }

                // Dimmer Panel (collapsible, above toolbar)
                AnimatedVisibility(
                    visible = showDimmer,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TabRow(
                                selectedTabIndex = dimmerTab,
                                containerColor = Color.Transparent,
                                contentColor = Color.White,
                                divider = {}
                            ) {
                                Tab(
                                    selected = dimmerTab == 0,
                                    onClick = { dimmerTab = 0 },
                                    text = { Text("Display") }
                                )
                                Tab(
                                    selected = dimmerTab == 1,
                                    onClick = { dimmerTab = 1 },
                                    text = { Text("Soundboard") }
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            if (dimmerTab == 0) {
                                // Screen Brightness
                                val canWriteSettings = android.provider.Settings.System.canWrite(context)
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(if (canWriteSettings) 1f else 0.5f)
                            ) {
                                Text(
                                    "Screen Brightness",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (!canWriteSettings) {
                                    Text(
                                        "Permission required for remote control",
                                        color = Color(0xFFF87171),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Icon(
                                        Icons.Rounded.BrightnessLow,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Slider(
                                        value = brightnessLevel,
                                        onValueChange = { brightnessLevel = it },
                                        onValueChangeFinished = {
                                            val backlight = if (brightnessLevel <= 0f) 0 else {
                                                val min = 2.0
                                                val max = 255.0
                                                (min * Math.pow(max / min, brightnessLevel / 100.0)).toInt().coerceIn(0, 255)
                                            }
                                            WebSocketService.setBrightness(backlight)
                                        },
                                        enabled = canWriteSettings,
                                        valueRange = 0f..100f,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFFBBF24),
                                            activeTrackColor = Color(0xFFFBBF24),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    Icon(
                                        Icons.Rounded.BrightnessHigh,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${brightnessLevel.toInt()}%",
                                    color = Color(0xFFFBBF24),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Flashlight Dimmer
                            Spacer(Modifier.height(14.dp))
                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(10.dp))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Flashlight",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Icon(
                                        Icons.Rounded.FlashlightOff,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Slider(
                                        value = flashSlider,
                                        onValueChange = { flashSlider = it },
                                        onValueChangeFinished = {
                                            WebSocketService.setFlashlight(flashSlider.toInt())
                                        },
                                        valueRange = 0f..flashlightMax.toFloat().coerceAtLeast(1f),
                                        steps = (flashlightMax - 1).coerceAtLeast(0),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFFF9800),
                                            activeTrackColor = Color(0xFFFF9800),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    Icon(
                                        Icons.Rounded.FlashlightOn,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    if (flashSlider.toInt() == 0) "Off" else "${flashSlider.toInt()}/${flashlightMax}",
                                    color = Color(0xFFFF9800),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Volume Dimmer
                            Spacer(Modifier.height(14.dp))
                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(10.dp))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(if (volumeMax > 0) 1f else 0.5f)
                            ) {
                                Text(
                                    "Device Volume",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Icon(
                                        Icons.Rounded.VolumeMute,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Slider(
                                        value = volumeSlider,
                                        onValueChange = { volumeSlider = it },
                                        onValueChangeFinished = {
                                            WebSocketService.setRemoteVolume(volumeSlider.toInt())
                                        },
                                        enabled = volumeMax > 0,
                                        valueRange = 0f..volumeMax.toFloat().coerceAtLeast(1f),
                                        steps = (volumeMax - 1).coerceAtLeast(0),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF4CAF50),
                                            activeTrackColor = Color(0xFF4CAF50),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    Icon(
                                        Icons.Rounded.VolumeUp,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    "${volumeSlider.toInt()}/${volumeMax}",
                                    color = Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Screen Power
                            Spacer(Modifier.height(14.dp))
                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OutlinedButton(
                                    onClick = { WebSocketService.sendScreenAction("on") },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Wake", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { WebSocketService.sendScreenAction("off") },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sleep", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            } else {
                                val sounds = listOf(
                                    "Creepy Girl" to "creepy_little_girl_talking",
                                    "Freddy" to "freddys_coming_for_you",
                                    "Hello Hello" to "hello_hello",
                                    "I See You" to "i_see_you",
                                    "Right Behind You" to "right_behind_you"
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sounds.chunked(2).forEach { rowSounds ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowSounds.forEach { (label, rawName) ->
                                                val isPlaying = playingSound == rawName
                                                OutlinedButton(
                                                    onClick = { 
                                                        if (isPlaying) WebSocketService.sendStopSound()
                                                        else WebSocketService.sendPlaySound(rawName)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = Color.White,
                                                        containerColor = if (isPlaying) Color(0xFF4CAF50) else Color.Transparent
                                                    ),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isPlaying) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f)),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                                ) {
                                                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (rowSounds.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Camera mode buttons
                            listOf("front" to Icons.Rounded.CameraFront, "back" to Icons.Rounded.CameraRear, "both" to Icons.Rounded.Cameraswitch).forEach { (mode, icon) ->
                                val isSelected = cameraMode == mode
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        cameraMode = mode
                                        switchingMode = true
                                        WebSocketService.switchCamera(mode)
                                    },
                                    enabled = !switchingMode,
                                    modifier = Modifier.background(
                                        if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                                ) {
                                    Icon(icon, contentDescription = mode, tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
                                }
                            }

                            // Separator
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )

                            // Dimmer toggle
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDimmer = !showDimmer
                                },
                                modifier = Modifier.background(
                                    if (showDimmer) Color(0xFFFBBF24).copy(alpha = 0.25f) else Color.Transparent,
                                    CircleShape
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.BrightnessHigh,
                                    contentDescription = "Dimmer",
                                    tint = if (showDimmer) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.5f)
                                )
                            }

                            // Buzz toggle (tap for single, long press for continuous)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isBuzzingRepeat) Color(0xFFF44336).copy(alpha = 0.25f) else Color.Transparent
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (isBuzzingRepeat) {
                                                    WebSocketService.sendVibrateStop()
                                                    isBuzzingRepeat = false
                                                    android.widget.Toast.makeText(context, "Stopped buzzing ⏹️", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    WebSocketService.sendVibrate()
                                                    android.widget.Toast.makeText(context, "Buzz sent! 📳", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!isBuzzingRepeat) {
                                                    WebSocketService.sendVibrateRepeat()
                                                    isBuzzingRepeat = true
                                                    android.widget.Toast.makeText(context, "Continuous buzz started! 🔁", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Vibration,
                                    contentDescription = "Buzz",
                                    tint = if (isBuzzingRepeat) Color(0xFFF44336) else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                if (!switchingMode) {
                    LoadingState(state, retryCount > 0) { 
                        retryCount = 0
                        WebSocketService.requestCamera(cameraMode) 
                    }
                }
            }

            // Switching Overlay
            if (switchingMode) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Switching Camera Mode...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Link remoteTracks change to switchingMode clearing
            LaunchedEffect(remoteTracks) {
                if (remoteTracks.isNotEmpty()) {
                    delay(800) // Small buffer to ensure first frames are ready
                    switchingMode = false
                }
            }

            // Safety timeout: never leave switchingMode stuck for more than 6 seconds
            LaunchedEffect(switchingMode) {
                if (switchingMode) {
                    delay(6000)
                    switchingMode = false
                }
            }
        }
    }
}
