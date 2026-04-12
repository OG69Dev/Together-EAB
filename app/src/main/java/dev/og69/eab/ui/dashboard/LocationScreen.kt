package dev.og69.eab.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.WebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

val EsriSatelliteTileSource = object : OnlineTileSourceBase(
    "EsriSatellite",
    0, 19, 256, ".png",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return "${getBaseUrl()}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
    }
}

@Suppress("DEPRECATION")
@Composable
fun LocationScreen(
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val partner by viewModel.partner.collectAsState()
    val context = LocalContext.current
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var mapType by remember { mutableStateOf(TileSourceFactory.MAPNIK) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationPermissionGranted) {
            scope.launch {
                val session = SessionRepository(context).getSession()
                if (session != null) {
                    // Full restart so the FGS picks up FOREGROUND_SERVICE_TYPE_LOCATION
                    WebSocketService.restart(context, session)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val partnerLocation = partner?.telemetry?.location
    val linked = partner?.linked == true
    val shareLocation = partner?.partnerSharing?.shareLocation != false

    if (!linked) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Wait for a partner to join to see their location.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (!shareLocation) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Partner has chosen not to share their live location.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (partnerLocation == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Waiting for partner location update...")
                Spacer(modifier = Modifier.height(16.dp))
                if (!locationPermissionGranted) {
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }) {
                        Text("Enable Location Permission")
                    }
                }
            }
        }
        return
    }
    var addressName by remember { mutableStateOf<String?>("Fetching address...") }

    LaunchedEffect(partnerLocation.lat, partnerLocation.lng) {
        addressName = "Fetching address..."
        addressName = try {
            withContext(Dispatchers.IO) {
                val geocoder = Geocoder(context)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(partnerLocation.lat, partnerLocation.lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.thoroughfare ?: address.featureName ?: address.locality ?: "Unknown road"
                } else {
                    "Location resolving..."
                }
            }
        } catch (e: Exception) {
            "Coordinates: ${String.format("%.4f", partnerLocation.lat)}, ${String.format("%.4f", partnerLocation.lng)}"
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(17.0)
                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    mapView.setTileSource(mapType)
                    mapView.overlays.clear()
                    val geoPoint = GeoPoint(partnerLocation.lat, partnerLocation.lng)
                    mapView.controller.setCenter(geoPoint)

                    val marker = Marker(mapView)
                    marker.position = geoPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = partner?.partnerName ?: "Partner"
                    marker.snippet = "Updated recently"
                    mapView.overlays.add(marker)
                    mapView.invalidate()
                }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${partner?.partnerName ?: "Partner"}'s Location",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Accuracy: ${partnerLocation.acc.toInt()}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (addressName != null) {
                    Text(
                        text = addressName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    InputChip(
                        selected = mapType == TileSourceFactory.MAPNIK,
                        onClick = { mapType = TileSourceFactory.MAPNIK },
                        label = { Text("Street") },
                        leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) }
                    )
                    @OptIn(ExperimentalMaterial3Api::class)
                    InputChip(
                        selected = mapType == TileSourceFactory.OpenTopo,
                        onClick = { mapType = TileSourceFactory.OpenTopo },
                        label = { Text("Topo") },
                        leadingIcon = { Icon(Icons.Default.Terrain, contentDescription = null) }
                    )
                    @OptIn(ExperimentalMaterial3Api::class)
                    InputChip(
                        selected = mapType == EsriSatelliteTileSource,
                        onClick = { mapType = EsriSatelliteTileSource },
                        label = { Text("Aerial") },
                        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) }
                    )
                }
            }
        }
    }
}
