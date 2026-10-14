package com.berrf35.atlas.feature.insights

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.common.DistanceFormatter
import com.berrf35.atlas.core.common.DurationFormatter
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasButton
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.ButtonVariant
import com.berrf35.atlas.core.designsystem.component.DailyDistanceChart
import com.berrf35.atlas.core.designsystem.component.DistributionBar
import com.berrf35.atlas.core.designsystem.component.FilterBar
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.HourWeekdayMatrix
import com.berrf35.atlas.core.designsystem.component.Metric
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.map.AtlasMap
import com.berrf35.atlas.core.model.PeriodAnalytics

/**
 * S11: Insights Overview Screen.
 */
@Composable
fun InsightsOverviewScreen(
    onOpenRecords: () -> Unit,
    onOpenHeatmap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analyticsRepo = remember { AtlasDataLocator.analyticsRepository }
    val periods = listOf("DAY", "WEEK", "MONTH", "YEAR", "ALL")
    var selectedPeriodIndex by remember { mutableIntStateOf(1) } // Default to "WEEK"

    val analytics by analyticsRepo.getPeriodAnalytics(periods[selectedPeriodIndex]).collectAsState(
        initial = PeriodAnalytics(
            periodTitle = "Last 7 Days",
            totalDistanceMeters = 0L,
            totalMovingMs = 0L,
            totalStoppedMs = 0L,
            journeyCount = 0,
            coveragePercent = 0,
            trendPercentVsPrevious = null,
            modeShare = emptyMap(),
            dailyDistances = emptyList(),
            hourWeekdayGrid = emptyList(),
            records = emptyList()
        )
    )

    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "INSIGHTS",
            subtitle = analytics.periodTitle
        )

        // Period filter bar
        FilterBar(
            items = periods,
            selectedIndex = selectedPeriodIndex,
            onItemSelected = { selectedPeriodIndex = it }
        )

        HairlineRule()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Trend comparison label matching selected period
            val currentPeriod = periods[selectedPeriodIndex]
            val trendComparisonLabel = when (currentPeriod) {
                "DAY" -> "vs yesterday"
                "WEEK" -> "vs last week"
                "MONTH" -> "vs last month"
                "YEAR" -> "vs last year"
                else -> null
            }
            val trendAnnotation = if (trendComparisonLabel != null && analytics.trendPercentVsPrevious != null) {
                val sign = if (analytics.trendPercentVsPrevious!! >= 0) "+" else ""
                "$sign${analytics.trendPercentVsPrevious}% $trendComparisonLabel"
            } else null

            // Primary Metric: Total Distance
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = DistanceFormatter.format(analytics.totalDistanceMeters),
                        style = AtlasTheme.typography.numericLarge,
                        color = colors.ink,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (trendAnnotation != null) {
                        Text(
                            text = trendAnnotation,
                            style = AtlasTheme.typography.label,
                            color = colors.muted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "TOTAL DISTANCE",
                    style = AtlasTheme.typography.label,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Metrics: Moving Time, Journeys, Coverage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric(
                    value = DurationFormatter.format(analytics.totalMovingMs),
                    label = "MOVING TIME",
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    value = "${analytics.journeyCount}",
                    label = "JOURNEYS",
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    value = "${analytics.coveragePercent}%",
                    label = "COVERAGE",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // Mode Share breakdown
            SectionHeader(title = "TRANSPORT MODE SHARE")
            if (analytics.modeShare.isNotEmpty()) {
                DistributionBar(distribution = analytics.modeShare)
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // Daily Distance Chart
            SectionHeader(title = "DAILY DISTANCE")
            if (analytics.dailyDistances.isNotEmpty()) {
                DailyDistanceChart(bars = analytics.dailyDistances)
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // 7x24 Hour Weekday Density
            SectionHeader(title = "MOVEMENT RHYTHM (7×24H)")
            if (analytics.hourWeekdayGrid.isNotEmpty()) {
                HourWeekdayMatrix(cells = analytics.hourWeekdayGrid)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HairlineRule()
            Spacer(modifier = Modifier.height(16.dp))

            // Navigation quick links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AtlasButton(
                    text = "Personal Records",
                    onClick = onOpenRecords,
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AtlasButton(
                    text = "Mobility Heatmap",
                    onClick = onOpenHeatmap,
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * S13: Personal Records Screen.
 */
@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analyticsRepo = remember { AtlasDataLocator.analyticsRepository }
    val analytics by analyticsRepo.getPeriodAnalytics("ALL").collectAsState(
        initial = PeriodAnalytics("", 0L, 0L, 0L, 0, 0, null, emptyMap(), emptyList(), emptyList(), emptyList())
    )
    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "RECORDS",
            subtitle = "Verified Movement Milestones",
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
            analytics.records.forEach { record ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                        .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.title,
                            style = AtlasTheme.typography.title,
                            color = colors.ink
                        )
                        Text(
                            text = record.category,
                            style = AtlasTheme.typography.label,
                            color = colors.muted
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = record.valueDisplay,
                            style = AtlasTheme.typography.numericLarge,
                            color = colors.ink
                        )
                        Text(
                            text = record.dateOrPeriod,
                            style = AtlasTheme.typography.label,
                            color = colors.muted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * S14: Mobility Heatmap Screen.
 */
@Composable
fun HeatmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val journeys by journeyRepo.getJourneysForDate("2026-10-13").collectAsState(initial = emptyList())
    val colors = AtlasTheme.colors

    var screenshotSafe by remember { mutableStateOf(false) }

    val allSegments = remember(journeys) {
        journeys.flatMap { it.modeSegments }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AtlasMap(
            segments = allSegments,
            interactive = true
        )

        AtlasTopBar(
            title = "HEATMAP",
            subtitle = if (screenshotSafe) "Screenshot-safe (Privacy Mode)" else "Cumulative Movement Traces",
            navigationIcon = {
                Text(
                    text = "Close",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            },
            actions = {
                Text(
                    text = if (screenshotSafe) "UNMASK" else "MASK",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier
                        .clickable { screenshotSafe = !screenshotSafe }
                        .padding(4.dp)
                )
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
