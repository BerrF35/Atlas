package com.berrf35.atlas.feature.history

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
import com.berrf35.atlas.core.common.DateFormatter
import com.berrf35.atlas.core.common.DistanceFormatter
import com.berrf35.atlas.core.common.DurationFormatter
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.EmptyStateView
import com.berrf35.atlas.core.designsystem.component.FilterBar
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.component.TimelineRow
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.TransportMode

/**
 * S06: History Screen.
 * Browse movement archive with calendar date navigation, mode filters, and search.
 */
@Composable
fun HistoryScreen(
    onOpenJourney: (JourneyId) -> Unit,
    modifier: Modifier = Modifier
) {
    val journeyRepo = remember { AtlasDataLocator.journeyRepository }
    val colors = AtlasTheme.colors

    var selectedDayIndex by remember { mutableIntStateOf(6) } // Index 6 is 13 Oct (Today)
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filterOptions = listOf("ALL", "WALK", "MOTORCYCLE", "AUTO-RICKSHAW", "METRO")
    val filterMode = when (selectedFilterIndex) {
        1 -> TransportMode.WALK
        2 -> TransportMode.MOTORCYCLE
        3 -> TransportMode.AUTO_RICKSHAW
        4 -> TransportMode.METRO
        else -> null
    }

    val days = listOf(
        Triple("2026-10-07", "07", "W"),
        Triple("2026-10-08", "08", "T"),
        Triple("2026-10-09", "09", "F"),
        Triple("2026-10-10", "10", "S"),
        Triple("2026-10-11", "11", "S"),
        Triple("2026-10-12", "12", "M"),
        Triple("2026-10-13", "13", "T")
    )

    val selectedDate = days[selectedDayIndex].first
    val journeys by journeyRepo.getJourneysForDate(selectedDate).collectAsState(initial = emptyList())

    val filteredJourneys = remember(journeys, filterMode, searchQuery) {
        journeys.filter { j ->
            val matchesMode = filterMode == null || j.primaryMode == filterMode
            val matchesQuery = searchQuery.isBlank() ||
                    j.originPlaceName?.contains(searchQuery, ignoreCase = true) == true ||
                    j.destinationPlaceName?.contains(searchQuery, ignoreCase = true) == true
            matchesMode && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "HISTORY",
            subtitle = "October 2026"
        )

        // Mini calendar week row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEachIndexed { index, (_, dayNum, dayLetter) ->
                val isSelected = index == selectedDayIndex
                Column(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (isSelected) colors.ink else colors.rule,
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (isSelected) colors.ink else colors.surface,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedDayIndex = index }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayLetter,
                        style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                        color = if (isSelected) colors.surface else colors.muted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayNum,
                        style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp),
                        color = if (isSelected) colors.surface else colors.ink
                    )
                }
            }
        }

        HairlineRule()

        // Filter bar
        FilterBar(
            items = filterOptions,
            selectedIndex = selectedFilterIndex,
            onItemSelected = { selectedFilterIndex = it },
            resultCount = filteredJourneys.size
        )

        HairlineRule()

        // Journey list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionHeader(
                title = if (selectedDayIndex == 6) "TODAY (13 OCT)" else "OCTOBER ${days[selectedDayIndex].second}",
                actionLabel = "${filteredJourneys.size} JOURNEYS"
            )

            if (filteredJourneys.isEmpty()) {
                EmptyStateView(
                    title = "No Matching Journeys",
                    message = "No movements recorded for this date and mode filter."
                )
            } else {
                filteredJourneys.forEach { journey ->
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
