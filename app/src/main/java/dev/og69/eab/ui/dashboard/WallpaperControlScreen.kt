package dev.og69.eab.ui.dashboard

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.og69.eab.ApiConfig
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import dev.og69.eab.network.WebSocketService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SessionRepository(context) }
    var target by remember { mutableStateOf("home") } // "home" or "lock"
    var loading by remember { mutableStateOf(false) }
    var wallpaperUrl by remember { mutableStateOf<String?>(null) }
    var sessionToken by remember { mutableStateOf("") }
    var coupleId by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }

    // Listen to signaling messages for 'wallpaper_ready'
    val signalingPayload by WebSocketService.signalingFlow.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        val s = repo.getSession()
        if (s != null) {
            sessionToken = s.deviceToken
            coupleId = s.coupleId
            deviceId = s.deviceId
        }
    }

    // Auto-fetch from KV whenever screen opens or tab changes
    LaunchedEffect(target, sessionToken) {
        if (sessionToken.isNotEmpty() && coupleId.isNotEmpty()) {
            val baseUrl = ApiConfig.WORKER_BASE_URL
            wallpaperUrl = "$baseUrl/api/couple/$coupleId/wallpaper/$target/partner?token=$sessionToken&t=${System.currentTimeMillis()}"
        }
    }

    LaunchedEffect(signalingPayload) {
        if (signalingPayload != null) {
            val type = signalingPayload!!.optString("type")
            val pTarget = signalingPayload!!.optString("target")
            if (pTarget == target) {
                if (type == "wallpaper_ready") {
                    loading = false
                    val baseUrl = ApiConfig.WORKER_BASE_URL
                    wallpaperUrl = "$baseUrl/api/couple/$coupleId/wallpaper/$target/partner?token=$sessionToken&t=${System.currentTimeMillis()}"
                } else if (type == "wallpaper_error") {
                    loading = false
                    val msg = signalingPayload!!.optString("message", "Failed to fetch wallpaper")
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Manual refresh: asks partner's phone to re-capture & upload the current wallpaper
    val requestWallpaper = {
        loading = true
        scope.launch {
            val s = repo.getSession()
            if (s != null) {
                val payload = org.json.JSONObject().apply {
                    put("type", "request_wallpaper")
                    put("target", target)
                }
                WebSocketService.sendSignaling(payload)
                
                // Timeout after 15s
                delay(15000)
                if (loading) {
                    loading = false
                    Toast.makeText(context, "Request timed out", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val api = CoupleApi()
                        val s = repo.getSession()
                        if (s != null) {
                            val uploadTarget = if (target == "home") "set_home" else "set_lock"
                            api.postWallpaper(s, uploadTarget, bytes)
                            
                            val payload = org.json.JSONObject().apply {
                                put("type", "apply_wallpaper")
                                put("target", target)
                                put("uploaderId", deviceId)
                            }
                            WebSocketService.sendSignaling(payload)
                            Toast.makeText(context, "Applying wallpaper...", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to upload wallpaper", Toast.LENGTH_SHORT).show()
                } finally {
                    // Wait for wallpaper_ready or timeout
                    delay(15000)
                    if (loading) loading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallpapers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { target = "home"; wallpaperUrl = null }
                        .background(if (target == "home") MaterialTheme.colorScheme.primary else Color.Transparent)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Home Screen",
                        color = if (target == "home") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { target = "lock"; wallpaperUrl = null }
                        .background(if (target == "lock") MaterialTheme.colorScheme.primary else Color.Transparent)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Lock Screen",
                        color = if (target == "lock") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (wallpaperUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(wallpaperUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Wallpaper Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No preview loaded",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { requestWallpaper() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !loading
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
                
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upload")
                }
            }
        }
    }
}
