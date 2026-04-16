package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import dev.og69.eab.network.WebSocketService
import dev.og69.eab.data.MediaCacheManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cacheManager = remember { MediaCacheManager(context) }
    
    var fullData by remember { mutableStateOf<ByteArray?>(null) }
    var mimeType by remember { mutableStateOf("") }
    var totalSize by remember { mutableLongStateOf(0L) }
    var isDownloading by remember { mutableStateOf(true) }
    
    val receivedBytes = remember { ByteArrayOutputStream() }
    var currentReceivedSize by remember { mutableLongStateOf(0L) }
    
    val connectionState by WebSocketService.mediaStateFlow.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            WebSocketService.stopMedia()
        }
    }

    // Init: Check cache
    LaunchedEffect(mediaId) {
        WebSocketService.requestMedia()
        val cached = cacheManager.getFullFile(mediaId)
        if (cached != null) {
            fullData = cached
            isDownloading = false
            // Guest mime type based on magic bytes or just default to image if we don't know
            // In a real app we'd store metadata (mime) in a local DB too.
            // For now, if it was cached, we'll try to decode it as bitmap first.
            mimeType = "image/jpeg" 
        } else {
            // Not in cache, request from partner
            WebSocketService.sendMediaCommand(JSONObject().apply {
                put("type", "GET_FILE")
                put("id", mediaId)
            }.toString())
        }
    }

    // Signaling handling
    LaunchedEffect(mediaId) {
        WebSocketService.signalingFlow.collect { payload ->
            if (payload.optLong("id") != mediaId) return@collect
            
            when (payload.optString("type")) {
                "FILE_START" -> {
                    mimeType = payload.getString("mime")
                    totalSize = payload.getLong("size")
                    receivedBytes.reset()
                    currentReceivedSize = 0
                    fullData = null
                    isDownloading = true
                }
                "FILE_END" -> {
                    val finalData = receivedBytes.toByteArray()
                    fullData = finalData
                    isDownloading = false
                    // Save to cache
                    cacheManager.saveFullFile(mediaId, finalData)
                }
            }
        }
    }

    // Binary chunk handling
    LaunchedEffect(mediaId) {
        WebSocketService.mediaBinaryFlow.collect { (id, data) ->
            if (id == mediaId && isDownloading) {
                receivedBytes.write(data)
                currentReceivedSize += data.size
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View Media") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isDownloading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = if (totalSize > 0) currentReceivedSize.toFloat() / totalSize else 0f,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (totalSize > 0) 
                            "Downloading: ${(currentReceivedSize / 1024)} / ${(totalSize / 1024)} KB" 
                            else "Connecting to Partner...",
                        color = Color.White
                    )
                }
            } else if (fullData != null) {
                // Try to show as image first
                val bitmap = remember(fullData) {
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(fullData, 0, fullData!!.size)
                    } catch (e: Exception) { null }
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Video playback or unknown format
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Media Cached Locally", color = Color.White)
                        Text("Use a video player to view this file.", color = Color.White.copy(alpha = 0.7f))
                        
                        Button(onClick = {
                            val file = cacheManager.getFile(mediaId)
                            if (file != null) {
                                // Simple intent to open video
                                try {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Text("Open in Player")
                        }
                    }
                }
            }
        }
    }
}
