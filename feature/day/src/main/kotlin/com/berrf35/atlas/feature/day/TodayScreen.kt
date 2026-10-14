package com.berrf35.atlas.feature.day

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.common.DateFormatter
import com.berrf35.atlas.core.common.DistanceFormatter
import com.berrf35.atlas.core.common.DurationFormatter
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.CoverageTimelineStrip
import com.berrf35.atlas.core.designsystem.component.DistributionBar
import com.berrf35.atlas.core.designsystem.component.EmptyStateView
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.HealthChip
import com.berrf35.atlas.core.designsystem.component.Metric
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.component.TimelineRow
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.map.AtlasMap
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.TrackingHealth

/**
 * S04: Today Screen.
 * Movement instrument dashboard with live status, viewport map, metrics, and journey timeline.
 * Clearly separates real recording from demo/sample mode.
 */
@Composable
fun TodayScreen(
    onOpenJourney: (JourneyId) -> Unit,
    onOpenMap: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()

    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val trackingRepo = remember { AtlasDataLocator.trackingRepository }
    val diagRepo = remember { AtlasDataLocator.diagnosticsRepository }

    val todaySummary by journeyRepo.getDaySummary("2026-10-13").collectAsState(
        initial = DaySummary(
            localDate = "2026-10-13",
            distanceMeters = 0L,
            movingDurationMs = 0L,
            stoppedDurationMs = 0L,
            journeyCount = 0,
            coveragePercent = 0,
            journeys = emptyList(),
            modeDistances = emptyMap(),
            modeDurations = emptyMap(),
            health = TrackingHealth.OFF
        )
    )

    val trackingHealth by trackingRepo.health.collectAsState()
    val currentPosition by trackingRepo.currentPosition.collectAsState()
    val coverageTimeline by diagRepo.getCoverageTimeline("2026-10-13").collectAsState(
        initial = com.berrf35.atlas.core.model.CoverageTimelineStrip("2026-10-13", emptyList())
    )

    val isOnline = remember(context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            true
        }
    }

    val colors = AtlasTheme.colors

    // Collect all points from today's journeys
    val allSegments = remember(todaySummary) {
        todaySummary.journeys.flatMap { it.modeSegments }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        // Top bar with honest status indicator
        AtlasTopBar(
            title = "TODAY",
            subtitle = "13 Oct 2026",
            actions = {
                HealthChip(
                    health = if (settings.isDemoMode) TrackingHealth.OFF else trackingHealth,
                    overrideLabel = if (settings.isDemoMode) "DEMO DATA" else null,
                    onClick = onOpenDiagnostics
                )
            }
        )

        // Demo data indicator banner
        if (settings.isDemoMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAMPLE DATA · Bangalore multimodal movements",
                    style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                    color = colors.muted
                )
            }
            HairlineRule()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Interactive Map Viewport (240dp) with user location and expand action
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                AtlasMap(
                    segments = allSegments,
                    currentPosition = currentPosition,
                    interactive = true,
                    useMapLibre = true,
                    isBasemapAvailable = isOnline
                )

                // Overlay "EXPAND MAP ↗" pill button at top right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surface.copy(alpha = 0.92f))
                        .border(1.dp, colors.rule, RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenMap)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "EXPAND MAP ↗",
                        style = AtlasTheme.typography.label,
                        color = colors.ink
                    )
                }
            }

            HairlineRule()

            // Metrics summary strip (with width-weighted columns to prevent collisions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Metric(
                    value = DistanceFormatter.format(todaySummary.distanceMeters),
                    label = "TOTAL DISTANCE",
                    isLarge = true,
                    modifier = Modifier.weight(1.2f)
                )
                Metric(
                    value = DurationFormatter.format(todaySummary.movingDurationMs),
                    label = "MOVING TIME",
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    value = "${todaySummary.coveragePercent}%",
                    label = "COVERAGE",
                    modifier = Modifier.weight(0.8f)
                )
            }

            // Mode breakdown distribution bar
            val totalDist = todaySummary.distanceMeters.coerceAtLeast(1L).toFloat()
            val modeDistribution = remember(todaySummary) {
                todaySummary.modeDistances.mapValues { it.value.toFloat() / totalDist }
            }

            if (modeDistribution.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    DistributionBar(distribution = modeDistribution)
                }
            }

            // 24h Coverage strip
            if (coverageTimeline.intervals.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionHeader(title = "DAY COVERAGE")
                    CoverageTimelineStrip(intervals = coverageTimeline.intervals)
                }
            }

            HairlineRule()

            // Journey timeline section
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                SectionHeader(
                    title = "JOURNEYS",
                    actionLabel = "${todaySummary.journeyCount} RECORDED"
                )

                if (todaySummary.journeys.isEmpty()) {
                    EmptyStateView(
                        title = "No Journeys Today",
                        message = if (settings.isDemoMode) "Sample data loaded." else "Movement detection is running. When you depart, new journey segments will be reconstructed automatically."
                    )
                } else {
                    todaySummary.journeys.forEach { journey ->
                        val origin = journey.originPlaceName ?: "Departure"
                        val dest = journey.destinationPlaceName ?: "Arrival"
                        val timeStr = DateFormatter.formatTime(journey.range.fromUtcMs)

                        TimelineRow(
                            timeDisplay = timeStr,
                            title = "$origin → $dest",
                            subtitle = journey.primaryMode.label,
                            mode = journey.primaryMode,
                            distanceDisplay = DistanceFormatter.format(journey.distanceMeters),
                            durationDisplay = DurationFormatter.format(journey.movingDurationMs),
                            onClick = { onOpenJourney(journey.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
