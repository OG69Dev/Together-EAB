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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.delay

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
    var switchingMode by remember { mutableStateOf(false) }
    var isFrontPrimary by remember { mutableStateOf(false) }

    // Dimmer / flashlight state
    var showDimmer by remember { mutableStateOf(false) }
    val rawBrightness by WebSocketService.brightnessFlow.collectAsState()
    var brightnessLevel by remember { mutableFloatStateOf(50f) }
    val flashlightLevel by WebSocketService.flashlightFlow.collectAsState()
    val flashlightMax by WebSocketService.flashlightMaxFlow.collectAsState()
    var flashSlider by remember { mutableFloatStateOf(0f) }

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
        WebSocketService.stopCamera()
        onBack()
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
                val frontEntry = remoteTracks.entries.find { it.key.contains("CAMF") }
                val backEntry = remoteTracks.entries.find { it.key.contains("CAMB") }
                val entryList = remoteTracks.entries.toList()

                if (entryList.size == 1 || (frontEntry != null && backEntry == null) || (frontEntry == null && backEntry != null)) {
                    val entry = frontEntry ?: backEntry ?: entryList[0]
                    val track = entry.value
                    key(entry.key) {
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
                        key("primary-${primaryEntry.key}") {
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
                                modifier = Modifier.fillMaxSize()
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
                            key("secondary-${secondaryEntry.key}") {
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
                            val flashUnavailable = cameraMode == "back" || cameraMode == "both"
                            
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(10.dp))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(if (flashUnavailable) 0.5f else 1f)
                            ) {
                                Text(
                                    "Flashlight",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (flashUnavailable) {
                                    Text(
                                        "Unavailable when back camera is active",
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
                                        enabled = !flashUnavailable,
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

                            // Flashlight quick toggle (tap to toggle full/off)
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (flashlightLevel > 0) {
                                        flashSlider = 0f
                                        WebSocketService.setFlashlight(0)
                                    } else {
                                        val maxVal = flashlightMax.toFloat().coerceAtLeast(1f)
                                        flashSlider = maxVal
                                        WebSocketService.setFlashlight(flashlightMax)
                                    }
                                },
                                modifier = Modifier.background(
                                    if (flashlightLevel > 0) Color(0xFFFF9800).copy(alpha = 0.25f) else Color.Transparent,
                                    CircleShape
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.FlashlightOn,
                                    contentDescription = "Flashlight",
                                    tint = if (flashlightLevel > 0) Color(0xFFFF9800) else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                LoadingState(state) { WebSocketService.requestCamera(cameraMode) }
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
        }
    }
}
