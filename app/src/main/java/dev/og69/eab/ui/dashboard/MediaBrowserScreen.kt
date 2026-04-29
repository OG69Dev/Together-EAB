package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.og69.eab.network.WebSocketService
import dev.og69.eab.webrtc.WebRtcManager
import dev.og69.eab.data.MediaCacheManager
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserScreen(
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit
) {
    val context = LocalContext.current
    val cacheManager = remember { MediaCacheManager(context) }
    
    var items by remember { mutableStateOf<List<Triple<Long, String, String>>>(emptyList()) } // ID, name, Type
    val thumbnails = remember { mutableStateMapOf<Long, ByteArray>() }
    val requestedThumbnails = remember { mutableSetOf<Long>() }
    var isLoading by remember { mutableStateOf(true) }
    val connectionState by WebSocketService.mediaStateFlow.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            WebSocketService.stopMedia()
        }
    }

    // Handle Signaling (JSON)
    LaunchedEffect(Unit) {
        WebSocketService.requestMedia()
        WebSocketService.signalingFlow.collect { payload ->
            when (payload.optString("type")) {
                "MEDIA_LIST_CHUNK" -> {
                    val arr = payload.optJSONArray("items")
                    val isFirst = payload.optBoolean("is_first", false)
                    val isLast = payload.optBoolean("is_last", false)
                    
                    val newList = if (isFirst) mutableListOf() else items.toMutableList()
                    
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.getLong("id")
                            newList.add(Triple(id, obj.getString("name"), obj.getString("type")))
                        }
                    }
                    items = newList
                    
                    if (isLast) {
                        isLoading = false
                    }
                }
                "MEDIA_LIST" -> {
                    val arr = payload.optJSONArray("items")
                    val newList = mutableListOf<Triple<Long, String, String>>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            newList.add(Triple(obj.getLong("id"), obj.getString("name"), obj.getString("type")))
                        }
                    }
                    items = newList
                    isLoading = false
                }
            }
        }
    }

    // Handle Binary Data (Thumbnails)
    LaunchedEffect(Unit) {
        WebSocketService.mediaBinaryFlow.collect { (id, data) ->
            thumbnails[id] = data
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partner Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isLoading = true
                        items = emptyList()
                        thumbnails.clear()
                        requestedThumbnails.clear()
                        WebSocketService.requestMedia() 
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (connectionState != WebRtcManager.State.CONNECTED && isLoading && items.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connecting...")
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty() && !isLoading) {
                Text("No media found.", Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items, key = { it.first }) { (id, name, type) ->
                        MediaGridItem(
                            id = id, 
                            type = type, 
                            thumbnail = thumbnails[id], 
                            onRequestThumbnail = {
                                if (requestedThumbnails.add(id)) {
                                    val cached = cacheManager.getThumbnail(id)
                                    if (cached != null) {
                                        thumbnails[id] = cached
                                    } else {
                                        WebSocketService.sendMediaCommand(JSONObject().apply {
                                            put("type", "GET_THUMBNAIL")
                                            put("id", id)
                                            put("media_type", type)
                                        }.toString())
                                    }
                                }
                            },
                            onClick = { onOpenItem(id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridItem(id: Long, type: String, thumbnail: ByteArray?, onRequestThumbnail: () -> Unit, onClick: () -> Unit) {
    LaunchedEffect(id) {
        if (thumbnail == null) {
            onRequestThumbnail()
        }
    }

    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        if (thumbnail != null) {
            val bitmap = remember(thumbnail) {
                android.graphics.BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.size)
            }
            bitmap?.let { 
                Image(
                    bitmap = it.asImageBitmap(), 
                    contentDescription = null, 
                    modifier = Modifier.fillMaxSize(), 
                    contentScale = ContentScale.Crop
                ) 
            }
        } else {
            Icon(
                Icons.Rounded.Refresh, 
                null, 
                Modifier.align(Alignment.Center).alpha(0.3f)
            )
        }
        if (type == "video") {
            Icon(
                Icons.Rounded.PlayCircle, 
                null, 
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp), 
                tint = Color.White
            )
        }
    }
}
