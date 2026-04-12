package dev.og69.eab.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.OverlayItem

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
                    WebSocketService.start(context, session)
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

    Surface(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(17.0)
                    mapViewInstance = this
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                val geoPoint = GeoPoint(partnerLocation.lat, partnerLocation.lng)
                mapView.controller.setCenter(geoPoint)

                val items = ArrayList<OverlayItem>()
                val title = partner?.partnerName ?: "Partner"
                items.add(OverlayItem(title, "Updated just now", geoPoint))

                val overlay = ItemizedOverlayWithFocus(
                    items,
                    object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                        override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                            return true
                        }
                        override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                            return false
                        }
                    },
                    context
                )
                overlay.setFocusItemsOnTap(true)
                mapView.overlays.add(overlay)
                mapView.invalidate()
            }
        )
    }
}
