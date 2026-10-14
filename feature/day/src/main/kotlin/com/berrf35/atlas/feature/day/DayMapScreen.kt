package com.berrf35.atlas.feature.day

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berrf35.atlas.core.common.DistanceFormatter
import com.berrf35.atlas.core.common.DurationFormatter
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.map.AtlasMap
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.TrackingHealth

/**
 * S05: Day Map Screen.
 * Full bleed interactive vector map showing today's entire route network.
 */
@Composable
fun DayMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val context = LocalContext.current
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

    val trackingRepo = remember { AtlasDataLocator.trackingRepository }
    val currentPosition by trackingRepo.currentPosition.collectAsState()

    val colors = AtlasTheme.colors
    val allSegments = remember(todaySummary) {
        todaySummary.journeys.flatMap { it.modeSegments }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full bleed interactive map
        AtlasMap(
            segments = allSegments,
            currentPosition = currentPosition,
            interactive = true,
            useMapLibre = true,
            isBasemapAvailable = isOnline
        )

        // Overlay Top Bar
        AtlasTopBar(
            title = "DAY MAP",
            subtitle = "13 Oct 2026 · ${todaySummary.journeyCount} journeys",
            navigationIcon = {
                Text(
                    text = "Close",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Floating bottom summary badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL DISTANCE",
                        style = AtlasTheme.typography.label,
                        color = colors.muted
                    )
                    Text(
                        text = DistanceFormatter.format(todaySummary.distanceMeters),
                        style = AtlasTheme.typography.numeric,
                        color = colors.ink
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MOVING TIME",
                        style = AtlasTheme.typography.label,
                        color = colors.muted
                    )
                    Text(
                        text = DurationFormatter.format(todaySummary.movingDurationMs),
                        style = AtlasTheme.typography.numeric,
                        color = colors.ink
                    )
                }
            }
        }
    }
}
