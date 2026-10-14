package com.berrf35.atlas.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.common.DateFormatter
import com.berrf35.atlas.core.common.DistanceFormatter
import com.berrf35.atlas.core.common.DurationFormatter
import com.berrf35.atlas.core.common.SpeedFormatter
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasButton
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.ButtonVariant
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.Metric
import com.berrf35.atlas.core.designsystem.component.ModeMark
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.map.AtlasMap
import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.ModeSource
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.TransportMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * S07: Journey Detail Screen.
 */
@Composable
fun JourneyDetailScreen(
    journeyId: JourneyId,
    onBack: () -> Unit,
    onPlay: (JourneyId) -> Unit,
    onEdit: (JourneyId) -> Unit,
    modifier: Modifier = Modifier
) {
    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val journeyDetail by journeyRepo.getJourneyDetail(journeyId).collectAsState(initial = null)
    val colors = AtlasTheme.colors

    var selectedSegmentToCorrect by remember { mutableStateOf<SegmentId?>(null) }
    val scope = rememberCoroutineScope()

    if (journeyDetail == null) {
        Box(
            modifier = modifier.fillMaxSize().background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Loading journey...", style = AtlasTheme.typography.body, color = colors.muted)
        }
        return
    }

    val detail = journeyDetail!!
    val summary = detail.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "${summary.originPlaceName ?: "Departure"} → ${summary.destinationPlaceName ?: "Arrival"}",
            subtitle = "${DateFormatter.formatDate(summary.range.fromUtcMs)} · ${DateFormatter.formatTime(summary.range.fromUtcMs)} - ${DateFormatter.formatTime(summary.range.toUtcMs)}",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            },
            actions = {
                Text(
                    text = "Edit",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier
                        .clickable { onEdit(journeyId) }
                        .padding(4.dp)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Map header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                AtlasMap(
                    segments = detail.segments,
                    stops = detail.stops,
                    interactive = true
                )
            }

            HairlineRule()

            // Metrics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Metric(
                    value = DistanceFormatter.format(summary.distanceMeters),
                    label = "DISTANCE",
                    isLarge = true,
                    modifier = Modifier.weight(1.2f)
                )
                Metric(
                    value = DurationFormatter.format(summary.movingDurationMs),
                    label = "MOVING TIME",
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    value = DurationFormatter.format(summary.stoppedDurationMs),
                    label = "STOPPED TIME",
                    modifier = Modifier.weight(1f)
                )
            }

            HairlineRule()

            // Action button: Play route
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                AtlasButton(
                    text = "Replay Journey Route",
                    onClick = { onPlay(journeyId) },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HairlineRule()

            // Mode Segments breakdown
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                SectionHeader(
                    title = "SEGMENTS (${detail.segments.size})",
                    actionLabel = "CORRECT MODE"
                )

                detail.segments.forEachIndexed { index, seg ->
                    val mode = seg.mode ?: TransportMode.UNKNOWN
                    val timeStr = "${DateFormatter.formatTime(seg.range.fromUtcMs)} - ${DateFormatter.formatTime(seg.range.toUtcMs)}"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                            .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ModeMark(mode = mode)
                            }
                            Text(
                                text = seg.confidenceBand.name + " (" + ((seg.posterior ?: 0.9f) * 100).toInt() + "%)",
                                style = AtlasTheme.typography.label,
                                color = if (seg.confidenceBand == ConfidenceBand.HIGH) colors.ink else colors.muted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = timeStr,
                                style = AtlasTheme.typography.label,
                                color = colors.muted
                            )
                            Text(
                                text = "${DistanceFormatter.format(seg.metrics.distanceMeters)} · ${DurationFormatter.format(seg.metrics.movingDurationMs)}",
                                style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp),
                                color = colors.ink
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Source: ${seg.source?.code ?: "MODEL"}",
                                style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                                color = colors.muted
                            )
                            Text(
                                text = "Change Mode",
                                style = AtlasTheme.typography.label,
                                color = colors.ink,
                                modifier = Modifier
                                    .clickable { selectedSegmentToCorrect = seg.id }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Mode picker modal if active
        selectedSegmentToCorrect?.let { segId ->
            ModePickerSheet(
                onModeSelected = { newMode ->
                    scope.launch {
                        journeyRepo.updateSegmentMode(journeyId, segId, newMode, ModeSource.USER)
                        selectedSegmentToCorrect = null
                    }
                },
                onDismiss = { selectedSegmentToCorrect = null }
            )
        }
    }
}

/**
 * S08: Playback Screen.
 */
@Composable
fun PlaybackScreen(
    journeyId: JourneyId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val journeyDetail by journeyRepo.getJourneyDetail(journeyId).collectAsState(initial = null)
    val colors = AtlasTheme.colors

    var progress by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableIntStateOf(1) } // 1x, 2x, 4x

    val points = journeyDetail?.points ?: emptyList()
    val currentIndex = ((points.size - 1) * progress).toInt().coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val currentPoint: GeoPoint? = points.getOrNull(currentIndex)

    LaunchedEffect(isPlaying, playbackSpeed) {
        while (isPlaying) {
            delay(100L)
            progress = (progress + (0.01f * playbackSpeed)).coerceAtMost(1f)
            if (progress >= 1f) isPlaying = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "PLAYBACK",
            subtitle = journeyDetail?.summary?.let { "${it.originPlaceName ?: "Departure"} → ${it.destinationPlaceName ?: "Arrival"}" } ?: "",
            navigationIcon = {
                Text(
                    text = "Close",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AtlasMap(
                segments = journeyDetail?.segments ?: emptyList(),
                stops = journeyDetail?.stops ?: emptyList(),
                playbackPosition = currentPoint,
                interactive = true
            )

            // Current metrics HUD floating badge
            currentPoint?.let { pt ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .border(1.dp, colors.rule, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "SPEED",
                            style = AtlasTheme.typography.label,
                            color = colors.muted
                        )
                        Text(
                            text = SpeedFormatter.formatKmh(pt.speedMps?.let { it * 3.6f } ?: 0f),
                            style = AtlasTheme.typography.numeric,
                            color = colors.ink
                        )
                    }
                }
            }
        }

        HairlineRule()

        // Controls bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Slider(
                value = progress,
                onValueChange = { progress = it },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause
                AtlasButton(
                    text = if (isPlaying) "PAUSE" else "PLAY",
                    onClick = {
                        if (progress >= 1f) progress = 0f
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier.width(100.dp)
                )

                // Speed buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4).forEach { spd ->
                        val isSelected = playbackSpeed == spd
                        Box(
                            modifier = Modifier
                                .border(1.dp, if (isSelected) colors.ink else colors.rule, RoundedCornerShape(4.dp))
                                .background(if (isSelected) colors.ink else colors.surface, RoundedCornerShape(4.dp))
                                .clickable { playbackSpeed = spd }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${spd}X",
                                style = AtlasTheme.typography.label,
                                color = if (isSelected) colors.surface else colors.ink
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * S09: Journey Edit Screen.
 */
@Composable
fun JourneyEditScreen(
    journeyId: JourneyId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val journeyDetail by journeyRepo.getJourneyDetail(journeyId).collectAsState(initial = null)
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "EDIT JOURNEY",
            navigationIcon = {
                Text(
                    text = "Cancel",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Journey Management",
                    style = AtlasTheme.typography.heading,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can split this journey into two separate journeys if a stop was undetected, or discard/delete it from your history.",
                    style = AtlasTheme.typography.body,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(32.dp))

                AtlasButton(
                    text = "Split Journey at Intermediate Stop",
                    onClick = {
                        scope.launch {
                            journeyRepo.splitJourney(journeyId, System.currentTimeMillis())
                            onBack()
                        }
                    },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                AtlasButton(
                    text = "Delete Journey",
                    onClick = {
                        scope.launch {
                            journeyRepo.deleteJourney(journeyId)
                            onBack()
                        }
                    },
                    variant = ButtonVariant.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * S10: Transport Mode Picker Bottom Sheet.
 */
@Composable
fun ModePickerSheet(
    onModeSelected: (TransportMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtlasTheme.colors
    val modes = listOf(
        TransportMode.WALK,
        TransportMode.RUN,
        TransportMode.BICYCLE,
        TransportMode.MOTORCYCLE,
        TransportMode.AUTO_RICKSHAW,
        TransportMode.CAR,
        TransportMode.BUS,
        TransportMode.TRAIN,
        TransportMode.METRO,
        TransportMode.OTHER_MOTORIZED,
        TransportMode.FLIGHT
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT TRANSPORT MODE",
                    style = AtlasTheme.typography.heading,
                    color = colors.ink
                )
                Text(
                    text = "Cancel",
                    style = AtlasTheme.typography.label,
                    color = colors.muted,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HairlineRule()
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                modes.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeMark(mode = mode)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Apply",
                            style = AtlasTheme.typography.label,
                            color = colors.ink
                        )
                    }
                    HairlineRule()
                }
            }
        }
    }
}
