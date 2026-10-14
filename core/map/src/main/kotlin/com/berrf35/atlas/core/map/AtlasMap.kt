package com.berrf35.atlas.core.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.designsystem.theme.ModeVisuals
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.SegmentView
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TransportMode
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * AtlasMap: The central map component for Project Atlas.
 * Provides an interactive, vector-drawn route canvas with coordinate projection,
 * gesture panning, pinch-to-zoom, mode-encoded path strokes, stay rings,
 * playback scrubber indicators, and an honest offline state banner.
 */
@Composable
fun AtlasMap(
    modifier: Modifier = Modifier,
    segments: List<SegmentView> = emptyList(),
    points: List<GeoPoint> = emptyList(),
    stops: List<StayView> = emptyList(),
    currentPosition: GeoPoint? = null,
    playbackPosition: GeoPoint? = null,
    isBasemapAvailable: Boolean = true,
    useMapLibre: Boolean = true,
    interactive: Boolean = true
) {
    val colors = AtlasTheme.colors

    if (useMapLibre && isBasemapAvailable) {
        MapLibreNativeMap(
            modifier = modifier,
            segments = segments,
            points = points,
            stops = stops,
            currentPosition = currentPosition,
            isDark = colors.isDark,
            interactive = interactive
        )
        return
    }

    // Collect all coordinates to compute bounding box
    val allPoints = remember(segments, points, stops, currentPosition, playbackPosition) {
        val pts = mutableListOf<GeoPoint>()
        pts.addAll(points)
        segments.forEach { seg -> pts.addAll(seg.points) }
        stops.forEach { stop -> pts.add(stop.location) }
        currentPosition?.let { pts.add(it) }
        playbackPosition?.let { pts.add(it) }
        pts
    }

    // Interactive pan and zoom offsets
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceRaised)
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.2f, 15f)
                            panOffset += pan
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            if (canvasW <= 0 || canvasH <= 0) return@Canvas

            // 1. Draw subtle background coordinate grid
            val gridSpacing = 80.dp.toPx()
            val gridColor = colors.rule.copy(alpha = 0.5f)

            var x = (panOffset.x % gridSpacing)
            while (x < canvasW) {
                if (x >= 0) drawLine(gridColor, Offset(x, 0f), Offset(x, canvasH), strokeWidth = 1f)
                x += gridSpacing
            }

            var y = (panOffset.y % gridSpacing)
            while (y < canvasH) {
                if (y >= 0) drawLine(gridColor, Offset(0f, y), Offset(canvasW, y), strokeWidth = 1f)
                y += gridSpacing
            }

            // 2. Project points onto canvas
            if (allPoints.isNotEmpty()) {
                var minLat = Double.MAX_VALUE
                var maxLat = -Double.MAX_VALUE
                var minLon = Double.MAX_VALUE
                var maxLon = -Double.MAX_VALUE

                allPoints.forEach { pt ->
                    minLat = min(minLat, pt.latitude)
                    maxLat = max(maxLat, pt.latitude)
                    minLon = min(minLon, pt.longitude)
                    maxLon = max(maxLon, pt.longitude)
                }

                // Add margin padding to bounding box
                val latSpan = (maxLat - minLat).coerceAtLeast(0.002)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.002)
                val centerLat = (minLat + maxLat) / 2.0
                val centerLon = (minLon + maxLon) / 2.0

                // Equirectangular projection ratio
                val latRad = Math.toRadians(centerLat)
                val cosLat = cos(latRad).coerceAtLeast(0.1)

                val paddingFactor = 0.8f
                val scaleX = (canvasW * paddingFactor) / (lonSpan * cosLat)
                val scaleY = (canvasH * paddingFactor) / latSpan
                val baseScale = min(scaleX, scaleY).toFloat()

                fun project(lat: Double, lon: Double): Offset {
                    val dx = ((lon - centerLon) * cosLat * baseScale * scale).toFloat()
                    val dy = (-((lat - centerLat) * baseScale * scale)).toFloat()
                    return Offset(
                        x = (canvasW / 2f) + dx + panOffset.x,
                        y = (canvasH / 2f) + dy + panOffset.y
                    )
                }

                // 3. Draw segments with mode-encoded stroke patterns
                if (segments.isNotEmpty()) {
                    segments.forEach { segment ->
                        val segPts = segment.points
                        if (segPts.size >= 2) {
                            val mode = segment.mode ?: TransportMode.UNKNOWN
                            val visual = ModeVisuals.get(mode)
                            val strokeColor = if (colors.isDark) visual.darkTint else visual.lightTint
                            val strokeWidth = (visual.strokeWidthDp * 1.5f).dp.toPx()

                            val dashes = visual.dashIntervals
                            val dots = visual.dotIntervals
                            val pathEffect = when {
                                dashes != null -> PathEffect.dashPathEffect(dashes, 0f)
                                dots != null -> PathEffect.dashPathEffect(dots, 0f)
                                else -> null
                            }

                            val path = Path()
                            val first = project(segPts.first().latitude, segPts.first().longitude)
                            path.moveTo(first.x, first.y)
                            for (i in 1 until segPts.size) {
                                val pt = project(segPts[i].latitude, segPts[i].longitude)
                                path.lineTo(pt.x, pt.y)
                            }

                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    pathEffect = pathEffect
                                )
                            )
                        }
                    }
                } else if (points.size >= 2) {
                    // Fallback to plain route if no segments
                    val path = Path()
                    val first = project(points.first().latitude, points.first().longitude)
                    path.moveTo(first.x, first.y)
                    for (i in 1 until points.size) {
                        val pt = project(points[i].latitude, points[i].longitude)
                        path.lineTo(pt.x, pt.y)
                    }
                    drawPath(
                        path = path,
                        color = colors.ink,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // 4. Draw stops/stays
                stops.forEach { stop ->
                    val center = project(stop.location.latitude, stop.location.longitude)
                    // Outer ring
                    drawCircle(
                        color = colors.ink,
                        radius = 8.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    // Inner dot
                    drawCircle(
                        color = colors.surface,
                        radius = 4.dp.toPx(),
                        center = center
                    )
                }

                // 5. Draw playback scrubber position
                playbackPosition?.let { pos ->
                    val scrubCenter = project(pos.latitude, pos.longitude)
                    drawCircle(
                        color = colors.ink.copy(alpha = 0.25f),
                        radius = 16.dp.toPx(),
                        center = scrubCenter
                    )
                    drawCircle(
                        color = colors.ink,
                        radius = 6.dp.toPx(),
                        center = scrubCenter
                    )
                    drawCircle(
                        color = colors.surface,
                        radius = 2.dp.toPx(),
                        center = scrubCenter
                    )
                }

                // 6. Draw current GPS position
                currentPosition?.let { curr ->
                    val currCenter = project(curr.latitude, curr.longitude)
                    drawCircle(
                        color = colors.ink.copy(alpha = 0.2f),
                        radius = 20.dp.toPx(),
                        center = currCenter
                    )
                    drawCircle(
                        color = colors.ink,
                        radius = 7.dp.toPx(),
                        center = currCenter
                    )
                    drawCircle(
                        color = colors.surface,
                        radius = 3.dp.toPx(),
                        center = currCenter
                    )
                }
            } else {
                // Empty state crosshair
                val center = Offset(canvasW / 2f, canvasH / 2f)
                drawLine(colors.rule, Offset(center.x - 20f, center.y), Offset(center.x + 20f, center.y), 1.5f)
                drawLine(colors.rule, Offset(center.x, center.y - 20f), Offset(center.x, center.y + 20f), 1.5f)
            }
        }

        // Basemap fallback notice / Honest offline badge
        if (!isBasemapAvailable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(colors.surface.copy(alpha = 0.90f), RoundedCornerShape(4.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(colors.muted, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Local Route Vector · Offline",
                        style = AtlasTheme.typography.label.copy(fontSize = 10.sp),
                        color = colors.muted
                    )
                }
            }
        }
    }
}
