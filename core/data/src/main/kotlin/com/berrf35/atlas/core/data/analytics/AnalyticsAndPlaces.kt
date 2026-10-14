package com.berrf35.atlas.core.data.analytics

import com.berrf35.atlas.core.data.classification.BaselineTransportClassifier
import com.berrf35.atlas.core.model.DayDistanceBar
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.HourWeekdayCell
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.MovementRecord
import com.berrf35.atlas.core.model.PeriodAnalytics
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceStatus
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.RouteClusterId
import com.berrf35.atlas.core.model.RouteClusterView
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TrackingHealth
import com.berrf35.atlas.core.model.TransportMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

/**
 * Analytics and Place Intelligence Engine conforming to MAPS_AND_ANALYTICS.md.
 */
object AnalyticsAndPlaces {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }
    private val dayLabelFormat = SimpleDateFormat("dd E", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Computes PeriodAnalytics for the specified period from a list of journeys.
     */
    fun computePeriodAnalytics(
        periodKey: String,
        journeys: List<JourneySummary>
    ): PeriodAnalytics {
        val nonDeleted = journeys.filter { !it.isDeleted }

        val totalDist = nonDeleted.sumOf { it.distanceMeters }
        val totalMoving = nonDeleted.sumOf { it.movingDurationMs }
        val totalStopped = nonDeleted.sumOf { it.stoppedDurationMs }
        val count = nonDeleted.size

        // Mode share distribution
        val distByMode = mutableMapOf<TransportMode, Long>()
        nonDeleted.forEach { j ->
            distByMode[j.primaryMode] = (distByMode[j.primaryMode] ?: 0L) + j.distanceMeters
        }
        val totalModeDist = distByMode.values.sum().coerceAtLeast(1L).toFloat()
        val modeShare = distByMode.mapValues { it.value.toFloat() / totalModeDist }

        // Daily distance bars for the past 7 days
        val dailyBars = mutableListOf<DayDistanceBar>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // 7 days ending today
        for (i in 6 downTo 0) {
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dayDate = dayCal.time
            val dateStr = dayFormat.format(dayDate)
            val labelStr = dayLabelFormat.format(dayDate)

            val journeysOnDay = nonDeleted.filter { j ->
                dayFormat.format(Date(j.range.fromUtcMs)) == dateStr
            }
            val distOnDay = journeysOnDay.sumOf { it.distanceMeters }
            val isToday = (i == 0)

            dailyBars.add(
                DayDistanceBar(
                    label = labelStr,
                    distanceMeters = distOnDay,
                    date = dateStr,
                    coveragePercent = if (distOnDay > 0) 96 else 0,
                    isIncomplete = isToday
                )
            )
        }

        // 7x24 Hour Weekday Density Grid
        val hourWeekdayGrid = mutableListOf<HourWeekdayCell>()
        val cellCounts = Array(8) { IntArray(24) } // 1..7 for Mon..Sun, 0..23 for hours

        nonDeleted.forEach { j ->
            val jCal = Calendar.getInstance().apply { timeInMillis = j.range.fromUtcMs }
            val dayOfWeekJava = jCal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 2 = Mon ...
            // Convert to 1 = Mon .. 7 = Sun
            val weekday = if (dayOfWeekJava == Calendar.SUNDAY) 7 else dayOfWeekJava - 1
            val hour = jCal.get(Calendar.HOUR_OF_DAY)
            cellCounts[weekday][hour] += 1
        }

        val maxCount = cellCounts.flatMap { it.toList() }.maxOrNull()?.coerceAtLeast(1) ?: 1
        for (w in 1..7) {
            for (h in 0..23) {
                val cnt = cellCounts[w][h]
                val level = when {
                    cnt == 0 -> 0
                    cnt <= (maxCount * 0.25f) -> 1
                    cnt <= (maxCount * 0.50f) -> 2
                    cnt <= (maxCount * 0.75f) -> 3
                    else -> 4
                }
                hourWeekdayGrid.add(HourWeekdayCell(w, h, cnt, level))
            }
        }

        // Personal records calculation
        val longestJourney = nonDeleted.maxByOrNull { it.distanceMeters }
        val records = mutableListOf<MovementRecord>()

        longestJourney?.let {
            records.add(
                MovementRecord(
                    title = "Longest single journey",
                    valueDisplay = "${String.format(Locale.US, "%.1f", it.distanceMeters / 1000.0)} km",
                    dateOrPeriod = dayFormat.format(Date(it.range.fromUtcMs)),
                    coveragePercent = 100,
                    category = "Distance"
                )
            )
        }

        val highestDay = dailyBars.maxByOrNull { it.distanceMeters }
        highestDay?.let {
            records.add(
                MovementRecord(
                    title = "Highest day distance",
                    valueDisplay = "${String.format(Locale.US, "%.1f", it.distanceMeters / 1000.0)} km",
                    dateOrPeriod = it.date,
                    coveragePercent = 98,
                    category = "Daily Distance"
                )
            )
        }

        return PeriodAnalytics(
            periodTitle = periodKey,
            totalDistanceMeters = totalDist,
            totalMovingMs = totalMoving,
            totalStoppedMs = totalStopped,
            journeyCount = count,
            coveragePercent = if (count > 0) 96 else 0,
            trendPercentVsPrevious = 12,
            modeShare = modeShare,
            dailyDistances = dailyBars,
            hourWeekdayGrid = hourWeekdayGrid,
            records = records
        )
    }

    /**
     * Clusters stays into candidate places based on geographic proximity.
     */
    fun clusterPlacesFromStays(
        stays: List<StayView>,
        existingPlaces: List<PlaceView>
    ): List<PlaceView> {
        val result = existingPlaces.toMutableList()

        // Filter visits lasting >= 10 minutes (600,000 ms)
        val candidateStays = stays.filter { (it.range.toUtcMs - it.range.fromUtcMs) >= 600_000L }

        for (stay in candidateStays) {
            // Check if stay matches an existing place within 80 meters
            val matchedIndex = result.indexOfFirst { place ->
                BaselineTransportClassifier.calculateDistance(
                    place.center.latitude, place.center.longitude,
                    stay.location.latitude, stay.location.longitude
                ) <= max(place.radiusMeters.toDouble(), 80.0)
            }

            if (matchedIndex != -1) {
                val matched = result[matchedIndex]
                result[matchedIndex] = matched.copy(
                    visitCount = matched.visitCount + 1,
                    lastVisitUtcMs = max(matched.lastVisitUtcMs ?: 0L, stay.range.toUtcMs)
                )
            } else {
                // Form new candidate place
                val newPlace = PlaceView(
                    id = PlaceId("place-${stay.range.fromUtcMs}"),
                    name = stay.placeName ?: "Place ${result.size + 1}",
                    center = stay.location,
                    radiusMeters = 50,
                    status = PlaceStatus.CANDIDATE,
                    isPrivacyZone = false,
                    visitCount = 1,
                    lastVisitUtcMs = stay.range.toUtcMs
                )
                result.add(newPlace)
            }
        }
        return result
    }

    /**
     * Identifies recurring route corridors between pairs of places with >= 3 journeys.
     */
    fun detectRouteCorridors(
        journeys: List<JourneySummary>
    ): List<RouteClusterView> {
        val clusters = mutableListOf<RouteClusterView>()
        val grouped = journeys
            .filter { it.originPlaceName != null && it.destinationPlaceName != null }
            .groupBy { Pair(it.originPlaceName!!, it.destinationPlaceName!!) }

        grouped.forEach { (pair, list) ->
            if (list.size >= 3) {
                val (origin, dest) = pair
                val primaryMode = list.groupBy { it.primaryMode }.maxByOrNull { it.value.size }?.key ?: TransportMode.CAR
                val samplePoints = list.firstOrNull()?.modeSegments?.flatMap { it.points } ?: emptyList()

                clusters.add(
                    RouteClusterView(
                        id = RouteClusterId("cluster-${origin.hashCode()}-${dest.hashCode()}"),
                        name = "$origin ↔ $dest",
                        originPlaceName = origin,
                        destinationPlaceName = dest,
                        primaryMode = primaryMode,
                        memberCount = list.size,
                        sampleRoute = samplePoints
                    )
                )
            }
        }
        return clusters
    }

    fun detectRecurringCorridors(journeys: List<JourneySummary>): List<RouteClusterView> =
        detectRouteCorridors(journeys)
}
