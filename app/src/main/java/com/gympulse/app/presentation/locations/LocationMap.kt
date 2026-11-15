package com.gympulse.app.presentation.locations

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/**
 * OpenStreetMap view (no API key): touch the map to place the location. The marker and the
 * detection radius follow the coordinates held by the form.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun LocationMap(
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Int,
    onPick: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pick = rememberUpdatedState(onPick)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            controller.setZoom(if (latitude != null && longitude != null) 16.0 else 5.0)
            controller.setCenter(GeoPoint(latitude ?: DEFAULT_LATITUDE, longitude ?: DEFAULT_LONGITUDE))
            // The map lives in a scrolling screen: let it handle its own drags.
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        pick.value(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                })
            )
        }
    }
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    Column(modifier) {
        AndroidView(
            factory = { map },
            update = { view ->
                view.overlays.removeAll { it is Marker || it is Polygon }
                if (latitude != null && longitude != null) {
                    val point = GeoPoint(latitude, longitude)
                    view.overlays.add(
                        Polygon(view).apply {
                            points = Polygon.pointsAsCircle(point, radiusMeters.toDouble())
                            fillPaint.color = 0x332BB7E8
                            outlinePaint.color = 0xFF2BB7E8.toInt()
                            outlinePaint.strokeWidth = 3f
                        }
                    )
                    view.overlays.add(
                        Marker(view).apply {
                            position = point
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setInfoWindow(null)
                        }
                    )
                    view.controller.animateTo(point)
                }
                view.invalidate()
            },
            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)),
        )
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val DEFAULT_LATITUDE = 46.6
private const val DEFAULT_LONGITUDE = 2.4
