package com.berrf35.atlas.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.designsystem.theme.ModeVisuals
import com.berrf35.atlas.core.model.CoverageInterval
import com.berrf35.atlas.core.model.DayDistanceBar
import com.berrf35.atlas.core.model.HourWeekdayCell
import com.berrf35.atlas.core.model.IntervalStatus
import com.berrf35.atlas.core.model.TransportMode

/**
 * DistributionBar: A proportional horizontal segmented bar showing mode breakdown
 * conforming to UI_UX_SPEC §1.1 and §1.4.
 */
@Composable
fun DistributionBar(
    distribution: Map<TransportMode, Float>,
    modifier: Modifier = Modifier,
    heightDp: Int = 12
) {
    val totalWeight = distribution.values.sum().coerceAtLeast(0.001f)
    val colors = AtlasTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .background(colors.quiet, RoundedCornerShape(2.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(2.dp))
        ) {
            distribution.entries.filter { it.value > 0f }.forEach { (mode, weight) ->
                val ratio = (weight / totalWeight).coerceIn(0f, 1f)
                val visual = ModeVisuals.get(mode)
                val tint = if (colors.isDark) visual.darkTint else visual.lightTint

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(ratio)
                        .background(colors.quiet)
                        .drawBehind {
                            val stroke = visual.strokeWidthDp.dp.toPx()
                            val pathEffect = when {
                                visual.dashIntervals != null -> PathEffect.dashPathEffect(visual.dashIntervals, 0f)
                                visual.dotIntervals != null -> PathEffect.dashPathEffect(visual.dotIntervals, 0f)
                                else -> null
                            }
                            drawLine(
                                color = tint,
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = stroke.coerceAtLeast(2f),
                                pathEffect = pathEffect
                            )
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            distribution.entries.filter { it.value > 0.01f }.forEach { (mode, ratio) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeMark(mode = mode, showLabel = false)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${mode.label} ${(ratio * 100).toInt()}%",
                        style = AtlasTheme.typography.label,
                        color = colors.muted
                    )
                }
            }
        }
    }
}

/**
 * DailyDistanceChart: Vertical bar chart displaying a sequence of days (e.g. 7 days or 14 days),
 * with baseline rules, monochrome bars, incomplete indicators, and touch selection.
 */
@Composable
fun DailyDistanceChart(
    bars: List<DayDistanceBar>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onBarSelected: ((Int) -> Unit)? = null
) {
    val colors = AtlasTheme.colors
    val maxDist = (bars.maxOfOrNull { it.distanceMeters } ?: 1L).coerceAtLeast(1000L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEachIndexed { index, bar ->
                val fraction = (bar.distanceMeters.toFloat() / maxDist.toFloat()).coerceIn(0.04f, 1f)
                val isSelected = selectedIndex == index

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = onBarSelected != null) { onBarSelected?.invoke(index) }
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bar drawing area consumes remaining height above label
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .drawBehind {
                                    if (bar.isIncomplete) {
                                        drawRect(
                                            color = if (isSelected) colors.ink else colors.muted,
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                            )
                                        )
                                    } else {
                                        drawRect(
                                            color = if (isSelected) colors.ink else colors.surfaceRaised
                                        )
                                        drawRect(
                                            color = colors.rule,
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bar.label,
                        style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                        color = if (isSelected) colors.ink else colors.muted,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        HairlineRule()
    }
}

/**
 * HourWeekdayMatrix: 7x24 grid displaying historical movement concentration
 * across hours of the day (0-23) and days of the week (Mon-Sun).
 */
@Composable
fun HourWeekdayMatrix(
    cells: List<HourWeekdayCell>,
    modifier: Modifier = Modifier
) {
    val colors = AtlasTheme.colors
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")

    // Lookup table
    val cellMap = cells.associateBy { Pair(it.weekday, it.hour) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Hour header marks (every 6 hours)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("00", "06", "12", "18", "23").forEach { hr ->
                Text(
                    text = hr,
                    style = AtlasTheme.typography.label.copy(fontSize = 10.sp),
                    color = colors.muted
                )
            }
        }

        // 7 rows for Mon-Sun
        (1..7).forEach { wd ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Weekday label
                Text(
                    text = weekdays[wd - 1],
                    style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                    color = colors.muted,
                    modifier = Modifier.width(18.dp)
                )

                // 24 hour blocks
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    (0..23).forEach { hr ->
                        val cell = cellMap[Pair(wd, hr)]
                        val level = cell?.level ?: 0

                        val cellColor = when (level) {
                            0 -> colors.quiet
                            1 -> if (colors.isDark) Color(0xFF2E2E2E) else Color(0xFFE2E2DE)
                            2 -> if (colors.isDark) Color(0xFF555555) else Color(0xFFADADA8)
                            3 -> if (colors.isDark) Color(0xFF888888) else Color(0xFF6E6E69)
                            else -> colors.ink
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .background(cellColor, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * CoverageTimelineStrip: A 24-hour horizontal strip showing recorded, stationary,
 * gap, and off periods throughout a single day.
 */
@Composable
fun CoverageTimelineStrip(
    intervals: List<CoverageInterval>,
    modifier: Modifier = Modifier,
    heightDp: Int = 16
) {
    val colors = AtlasTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .background(colors.surfaceRaised, RoundedCornerShape(2.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(2.dp))
                .drawBehind {
                    val totalMinutes = 1440f // 24 hours * 60 min
                    val pxPerMin = size.width / totalMinutes

                    intervals.forEach { interval ->
                        val startX = interval.startMinuteOfDay * pxPerMin
                        val widthPx = interval.durationMinutes * pxPerMin

                        val color = when (interval.status) {
                            IntervalStatus.RECORDED -> colors.ink
                            IntervalStatus.STATIONARY -> colors.muted
                            IntervalStatus.GAP -> colors.danger
                            IntervalStatus.OFF -> colors.quiet
                        }

                        drawRect(
                            color = color,
                            topLeft = Offset(startX, 0f),
                            size = Size(widthPx, size.height)
                        )
                    }
                }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Time ticks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { tick ->
                Text(
                    text = tick,
                    style = AtlasTheme.typography.label.copy(fontSize = 10.sp),
                    color = colors.muted
                )
            }
        }
    }
}
