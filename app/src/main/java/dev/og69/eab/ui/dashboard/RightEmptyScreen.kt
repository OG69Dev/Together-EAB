package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Slider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Block
import dev.og69.eab.network.WebSocketService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RightEmptyScreen(
    onNavigateToContacts: () -> Unit,
    onNavigateToWebHistory: () -> Unit,
    onNavigateToYoutubeHistory: () -> Unit,
    onNavigateToSmsHistory: () -> Unit,
    onNavigateToCallLog: () -> Unit,
    onNavigateToLiveAudio: () -> Unit,
    onNavigateToLiveCamera: () -> Unit,
    onNavigateToLiveScreen: () -> Unit,
    onNavigateToMediaBrowser: () -> Unit,
    onNavigateToAppControl: () -> Unit,
    onNavigateToWallpaper: () -> Unit,
    modifier: Modifier = Modifier
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current
            var isBuzzingRepeat by remember { mutableStateOf(false) }
            var onDurationMs by remember { mutableFloatStateOf(500f) }
            var offDurationMs by remember { mutableFloatStateOf(300f) }

            LaunchedEffect(onDurationMs, offDurationMs) {
                if (isBuzzingRepeat) {
                    WebSocketService.sendVibrateRepeat(onDurationMs.toLong(), offDurationMs.toLong())
                }
            }
            val partnerJson by remember { dev.og69.eab.data.SessionRepository(context).cachedPartnerJsonFlow }.collectAsState(initial = null)
            val partnerSharing = remember(partnerJson) {
                try {
                    partnerJson?.let { dev.og69.eab.network.CoupleApi().parsePartnerResponse(org.json.JSONObject(it)).partnerSharing }
                } catch (e: Exception) { null }
            }

            // Quick-action: Vibrate partner's phone
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Vibration,
                            contentDescription = "Vibrate",
                            tint = if (isBuzzingRepeat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vibrate Partner's Phone",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isBuzzingRepeat) "Buzzing partner's phone continuously..." else "Hold to buzz continuously, tap for single buzz",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBuzzingRepeat) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        if (isBuzzingRepeat) {
                            Button(
                                onClick = {
                                    WebSocketService.sendVibrateStop()
                                    isBuzzingRepeat = false
                                    android.widget.Toast.makeText(context, "Stopped buzzing ⏹️", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Stop")
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                WebSocketService.sendVibrate()
                                                android.widget.Toast.makeText(context, "Buzz sent! 📳", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onLongPress = {
                                                WebSocketService.sendVibrateRepeat(onDurationMs.toLong(), offDurationMs.toLong())
                                                isBuzzingRepeat = true
                                                android.widget.Toast.makeText(context, "Continuous buzz started! 🔁", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Buzz",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = "Buzz Duration: ${if (onDurationMs >= 5000f) "5s (Max)" else String.format("%.1fs", onDurationMs / 1000f)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = onDurationMs,
                        onValueChange = { onDurationMs = it },
                        valueRange = 100f..5000f,
                        steps = 49
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "Pause Duration: ${if (offDurationMs <= 0f) "Nonstop" else String.format("%.1fs", offDurationMs / 1000f)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = offDurationMs,
                        onValueChange = { offDurationMs = it },
                        valueRange = 0f..3000f,
                        steps = 30
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            MoreMenuItem(icon = Icons.Rounded.Person, label = "Partner Contacts", isEnabled = partnerSharing?.shareContacts != false, onClick = onNavigateToContacts)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Public, label = "Web Control", isEnabled = partnerSharing?.shareWebHistory != false, onClick = onNavigateToWebHistory)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.SmartDisplay, label = "YouTube Control", isEnabled = partnerSharing?.shareYoutubeHistory != false, onClick = onNavigateToYoutubeHistory)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Sms, label = "SMS History", isEnabled = partnerSharing?.shareSms != false, onClick = onNavigateToSmsHistory)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Call, label = "Call History", isEnabled = partnerSharing?.shareCallLog != false, onClick = onNavigateToCallLog)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Mic, label = "Live Audio", isEnabled = partnerSharing?.shareLiveAudio != false, onClick = onNavigateToLiveAudio)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Camera, label = "Live Camera View", isEnabled = partnerSharing?.shareLiveCamera != false, onClick = onNavigateToLiveCamera)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.ScreenshotMonitor, label = "Live Screen View", isEnabled = partnerSharing?.shareScreenView != false, onClick = onNavigateToLiveScreen)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.PhotoLibrary, label = "View Photos", isEnabled = partnerSharing?.shareMedia != false, onClick = onNavigateToMediaBrowser)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Default.Security, label = "App Control", isEnabled = partnerSharing?.shareAppControl != false, onClick = onNavigateToAppControl)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Wallpaper, label = "Wallpapers", isEnabled = partnerSharing?.shareWallpaper != false, onClick = onNavigateToWallpaper)
        }
    }
}


@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = isEnabled, onClick = onClick),
        color = if (isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.3f))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (!isEnabled) {
                    Text("Disabled by Partner", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha=0.8f))
                }
            }
            if (isEnabled) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}
