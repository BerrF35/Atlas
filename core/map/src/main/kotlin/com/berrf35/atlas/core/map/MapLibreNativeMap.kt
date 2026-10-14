package com.berrf35.atlas.core.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.SegmentView
import com.berrf35.atlas.core.model.StayView
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * MapLibreNativeMap: Interactive vector street map powered by MapLibre Native & OpenFreeMap tiles.
 * Provides Google Maps-style live user location puck, pulsing blue accuracy halo, smooth
 * auto-centering, floating recenter FAB, zoom controls, and route polylines.
 */
@Composable
fun MapLibreNativeMap(
    modifier: Modifier = Modifier,
    segments: List<SegmentView> = emptyList(),
    points: List<GeoPoint> = emptyList(),
    stops: List<StayView> = emptyList(),
    currentPosition: GeoPoint? = null,
    isDark: Boolean = false,
    interactive: Boolean = true,
    tileServerUrl: String = "https://tiles.openfreemap.org/styles/liberty"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = AtlasTheme.colors

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // Initialize MapLibre engine once
    remember(context) {
        try {
            MapLibre.getInstance(context)
        } catch (_: Exception) {}
    }

    // Determine best initial user position from GPS or network provider
    val initialUserLocation = remember(context, currentPosition) {
        val lastFix = try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gps = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                gps != null && net != null -> if (gps.time > net.time) gps else net
                gps != null -> gps
                else -> net
            }
        } catch (_: SecurityException) { null }

        when {
            lastFix != null -> LatLng(lastFix.latitude, lastFix.longitude)
            currentPosition != null -> LatLng(currentPosition.latitude, currentPosition.longitude)
            else -> null
        }
    }

    // Reactively fly camera to live user location when it locks in
    androidx.compose.runtime.LaunchedEffect(currentPosition, mapInstance) {
        val pos = currentPosition ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val allPts = points.ifEmpty { segments.flatMap { it.points } }
        if (allPts.isEmpty()) {
            val userLatLng = LatLng(pos.latitude, pos.longitude)
            if (map.cameraPosition.zoom < 14.0) {
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 16.0), 900)
                try {
                    map.locationComponent.cameraMode = CameraMode.TRACKING
                } catch (_: Exception) {}
            }
        }
    }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapInstance = map

                // Configure modern UI gestures
                map.uiSettings.apply {
                    isScrollGesturesEnabled = interactive
                    isZoomGesturesEnabled = interactive
                    isTiltGesturesEnabled = interactive
                    isRotateGesturesEnabled = interactive
                    isCompassEnabled = true
                    isAttributionEnabled = true
                }

                val styleUrl = if (tileServerUrl.isNotBlank()) tileServerUrl else "https://tiles.openfreemap.org/styles/liberty"
                map.setStyle(styleUrl) { style ->
                    // 1. Enable Google Maps-style blue location dot & pulsing halo
                    val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                    if (hasFine || hasCoarse) {
                        try {
                            val locationComponentOptions = LocationComponentOptions.builder(context)
                                .pulseEnabled(true)
                                .pulseColor(android.graphics.Color.parseColor("#4285F4")) // Google Maps blue
                                .pulseAlpha(0.28f)
                                .accuracyAlpha(0.18f)
                                .accuracyColor(android.graphics.Color.parseColor("#4285F4"))
                                .foregroundTintColor(android.graphics.Color.parseColor("#1A73E8")) // Blue puck
                                .bearingTintColor(android.graphics.Color.parseColor("#1A73E8")) // Heading cone
                                .elevation(6f)
                                .build()

                            val activationOptions = LocationComponentActivationOptions.builder(context, style)
                                .locationComponentOptions(locationComponentOptions)
                                .useDefaultLocationEngine(true)
                                .build()

                            val locationComponent = map.locationComponent
                            locationComponent.activateLocationComponent(activationOptions)
                            locationComponent.isLocationComponentEnabled = true
                            locationComponent.renderMode = RenderMode.COMPASS
                        } catch (_: Exception) {}
                    }

                    // 2. Draw route polylines if segments exist
                    val allPts = points.ifEmpty { segments.flatMap { it.points } }
                    if (allPts.isNotEmpty()) {
                        val boundsBuilder = LatLngBounds.Builder()
                        allPts.forEach { pt ->
                            boundsBuilder.include(LatLng(pt.latitude, pt.longitude))
                        }

                        // Add route line overlay
                        val polylinePts = allPts.map { LatLng(it.latitude, it.longitude) }
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(polylinePts)
                                .color(if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#111111"))
                                .width(4.5f)
                        )

                        try {
                            val bounds = boundsBuilder.build()
                            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                        } catch (_: Exception) {
                            val first = allPts.first()
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(first.latitude, first.longitude))
                                .zoom(15.0)
                                .build()
                        }
                    } else {
                        // 3. No route points: Smoothly zoom directly to user's real street location
                        val target = initialUserLocation ?: LatLng(12.9716, 77.5946) // Bangalore city fallback
                        map.cameraPosition = CameraPosition.Builder()
                            .target(target)
                            .zoom(if (initialUserLocation != null) 16.0 else 13.0)
                            .build()

                        if (initialUserLocation != null) {
                            try {
                                map.locationComponent.cameraMode = CameraMode.TRACKING
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onDestroy()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        if (interactive) {
            // Google Maps-style Floating Controls (Recenter FAB + Zoom Buttons)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            // Google Maps-style Recenter / My Location FAB
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.rule, CircleShape)
                    .clickable {
                        val currentLoc = mapInstance?.locationComponent?.lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
                            ?: initialUserLocation
                            ?: currentPosition?.let { LatLng(it.latitude, it.longitude) }

                        if (currentLoc != null) {
                            mapInstance?.easeCamera(CameraUpdateFactory.newLatLngZoom(currentLoc, 16.5), 800)
                            try {
                                mapInstance?.locationComponent?.cameraMode = CameraMode.TRACKING
                            } catch (_: Exception) {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // GPS Crosshair Target Icon
                Canvas(modifier = Modifier.size(20.dp)) {
                    val stroke = 1.8.dp.toPx()
                    val centerPt = Offset(size.width / 2, size.height / 2)
                    val radius = size.width * 0.32f

                    // Outer target ring
                    drawCircle(
                        color = Color(0xFF1A73E8), // Google blue
                        radius = radius,
                        center = centerPt,
                        style = Stroke(width = stroke)
                    )
                    // Center location dot
                    drawCircle(
                        color = Color(0xFF1A73E8),
                        radius = 2.5.dp.toPx(),
                        center = centerPt
                    )
                    // 4 Crosshair ticks (top, bottom, left, right)
                    drawLine(Color(0xFF1A73E8), Offset(centerPt.x, 0f), Offset(centerPt.x, centerPt.y - radius + 1f), strokeWidth = stroke)
                    drawLine(Color(0xFF1A73E8), Offset(centerPt.x, size.height), Offset(centerPt.x, centerPt.y + radius - 1f), strokeWidth = stroke)
                    drawLine(Color(0xFF1A73E8), Offset(0f, centerPt.y), Offset(centerPt.x - radius + 1f, centerPt.y), strokeWidth = stroke)
                    drawLine(Color(0xFF1A73E8), Offset(size.width, centerPt.y), Offset(centerPt.x + radius - 1f, centerPt.y), strokeWidth = stroke)
                }
            }

            // Zoom in / Zoom out floating controls
            Column(
                modifier = Modifier
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
            ) {
                // Zoom In (+)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { mapInstance?.easeCamera(CameraUpdateFactory.zoomIn(), 300) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(colors.ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = stroke)
                        drawLine(colors.ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = stroke)
                    }
                }
                Box(modifier = Modifier.size(width = 36.dp, height = 1.dp).background(colors.rule))
                // Zoom Out (-)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { mapInstance?.easeCamera(CameraUpdateFactory.zoomOut(), 300) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(colors.ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = stroke)
                    }
                }
            }
        }
        }
    }
}
