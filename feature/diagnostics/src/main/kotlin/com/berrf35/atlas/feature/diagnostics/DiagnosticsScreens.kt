package com.berrf35.atlas.feature.diagnostics

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.common.DateFormatter
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.CoverageTimelineStrip
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme

/**
 * S23: Diagnostics Screen.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenGap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val diagRepo = remember { AtlasDataLocator.diagnosticsRepository }
    val trackingRepo = remember { AtlasDataLocator.trackingRepository }
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }

    val health by trackingRepo.health.collectAsState()
    val settings by settingsRepo.settings.collectAsState()
    val events by diagRepo.getHealthEvents().collectAsState(initial = emptyList())
    val coverageTimeline by diagRepo.getCoverageTimeline("2026-10-13").collectAsState(
        initial = com.berrf35.atlas.core.model.CoverageTimelineStrip("2026-10-13", emptyList())
    )

    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "DIAGNOSTICS",
            subtitle = "Engine Health & Interruption Logs",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Engine Status Card
            SectionHeader(title = "RECORDING ENGINE STATE")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                    .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Sensor Engine", style = AtlasTheme.typography.label, color = colors.muted)
                    Text(text = health.label.uppercase(), style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp), color = colors.ink)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Battery Profile", style = AtlasTheme.typography.label, color = colors.muted)
                    Text(text = settings.batteryProfile.label, style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp), color = colors.ink)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Target Platform", style = AtlasTheme.typography.label, color = colors.muted)
                    Text(text = "OnePlus Nord 5 · OxygenOS 16", style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp), color = colors.ink)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // 24h Coverage strip
            SectionHeader(title = "24-HOUR RECORDING COVERAGE")
            CoverageTimelineStrip(intervals = coverageTimeline.intervals)

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // System Events & Gaps
            SectionHeader(title = "SYSTEM EVENTS (${events.size})")
            events.forEach { evt ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                        .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                        .clickable { onOpenGap(evt.id) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = evt.type,
                            style = AtlasTheme.typography.title,
                            color = colors.ink
                        )
                        Text(
                            text = DateFormatter.formatTime(evt.timeUtcMs),
                            style = AtlasTheme.typography.numeric.copy(fontSize = 12.sp),
                            color = colors.muted
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = evt.message,
                        style = AtlasTheme.typography.label,
                        color = colors.muted
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * S24: Gap Detail Screen.
 */
@Composable
fun GapDetailScreen(
    gapId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diagRepo = remember { AtlasDataLocator.diagnosticsRepository }
    val gapDetail by diagRepo.getGapDetail(gapId).collectAsState(initial = null)
    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "GAP ANALYSIS",
            subtitle = "Tracking Interruption Detail",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            val gap = gapDetail
            if (gap != null) {
                Text(text = gap.cause.label, style = AtlasTheme.typography.heading, color = colors.ink)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = gap.description, style = AtlasTheme.typography.body, color = colors.muted)

                Spacer(modifier = Modifier.height(24.dp))
                HairlineRule()
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "DIAGNOSTIC ADVICE", style = AtlasTheme.typography.heading, color = colors.ink)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = gap.advice, style = AtlasTheme.typography.body, color = colors.muted)
            } else {
                Text(text = "Loading gap detail...", style = AtlasTheme.typography.body, color = colors.muted)
            }
        }
    }
}

/**
 * S25: About Screen.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "ABOUT ATLAS",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(text = "PROJECT ATLAS", style = AtlasTheme.typography.display, color = colors.ink)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Private, local-first movement recording instrument.", style = AtlasTheme.typography.title, color = colors.muted)

            Spacer(modifier = Modifier.height(24.dp))
            HairlineRule()
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "TARGET HARDWARE", style = AtlasTheme.typography.heading, color = colors.ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "OnePlus Nord 5 (Indian Variant · OxygenOS 16 / Android 16)", style = AtlasTheme.typography.body, color = colors.muted)

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "OPEN SOURCE LICENCES", style = AtlasTheme.typography.heading, color = colors.ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "• IBM Plex Sans & IBM Plex Mono — SIL Open Font License\n• MapLibre Native — BSD 2-Clause License\n• KotlinX Coroutines & Serialization — Apache 2.0\n• Jetpack Compose & AndroidX — Apache 2.0", style = AtlasTheme.typography.body, color = colors.muted)

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "REPOSITORY & SPECIFICATIONS", style = AtlasTheme.typography.heading, color = colors.ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "https://github.com/BerrF35/Atlas · MIT License", style = AtlasTheme.typography.body, color = colors.muted)
        }
    }
}
