package com.berrf35.atlas.core.data.demo

import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.CoverageInterval
import com.berrf35.atlas.core.model.CoverageTimelineStrip
import com.berrf35.atlas.core.model.DayDistanceBar
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.GapCause
import com.berrf35.atlas.core.model.GapDetailView
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.HealthEventView
import com.berrf35.atlas.core.model.HourWeekdayCell
import com.berrf35.atlas.core.model.IntervalStatus
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneyState
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.ModeSource
import com.berrf35.atlas.core.model.MovementRecord
import com.berrf35.atlas.core.model.PeriodAnalytics
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceStatus
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.RouteClusterId
import com.berrf35.atlas.core.model.RouteClusterView
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.SegmentKind
import com.berrf35.atlas.core.model.SegmentMetrics
import com.berrf35.atlas.core.model.SegmentView
import com.berrf35.atlas.core.model.StayId
import com.berrf35.atlas.core.model.StayKind
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TrackingHealth
import com.berrf35.atlas.core.model.TransportMode

/**
 * Isolated deterministic demo dataset for Project Atlas.
 * Context: Bangalore multimodal movement on OnePlus Nord 5 (OxygenOS 16).
 * All data is deterministic and strictly separated from personal records.
 */
object AtlasDemoData {

    val placeHome = PlaceView(
        id = PlaceId("place-home"),
        name = "Home (Indiranagar)",
        center = GeoPoint(12.9784, 77.6408),
        radiusMeters = 50,
        status = PlaceStatus.CONFIRMED,
        isPrivacyZone = true,
        visitCount = 142,
        lastVisitUtcMs = 1791961200000L
    )

    val placeOffice = PlaceView(
        id = PlaceId("place-office"),
        name = "RMZ Ecospace ORR",
        center = GeoPoint(12.9260, 77.6841),
        radiusMeters = 100,
        status = PlaceStatus.CONFIRMED,
        isPrivacyZone = false,
        visitCount = 98,
        lastVisitUtcMs = 1791990000000L
    )

    val placeGym = PlaceView(
        id = PlaceId("place-gym"),
        name = "Cult Indiranagar",
        center = GeoPoint(12.9715, 77.6412),
        radiusMeters = 40,
        status = PlaceStatus.CONFIRMED,
        isPrivacyZone = false,
        visitCount = 34,
        lastVisitUtcMs = 1791910800000L
    )

    val placeCafe = PlaceView(
        id = PlaceId("place-cafe"),
        name = "Third Wave Coffee 12th Main",
        center = GeoPoint(12.9698, 77.6415),
        radiusMeters = 30,
        status = PlaceStatus.CONFIRMED,
        isPrivacyZone = false,
        visitCount = 19,
        lastVisitUtcMs = 1791975600000L
    )

    val demoPlaces = listOf(placeHome, placeOffice, placeGym, placeCafe)

    // Segment coordinates
    private fun generatePoints(startLat: Double, startLon: Double, endLat: Double, endLon: Double, count: Int): List<GeoPoint> {
        return (0 until count).map { i ->
            val fraction = i.toDouble() / (count - 1).coerceAtLeast(1)
            GeoPoint(
                latitude = startLat + (endLat - startLat) * fraction,
                longitude = startLon + (endLon - startLon) * fraction,
                accuracyM = 8f + (i % 3) * 2f
            )
        }
    }

    // Journey 1: Morning Commute
    val j1WalkPoints = generatePoints(12.9784, 77.6408, 12.9783, 77.6385, 8)
    val j1MetroPoints = generatePoints(12.9783, 77.6385, 12.9912, 77.6525, 15)
    val j1AutoPoints = generatePoints(12.9912, 77.6525, 12.9260, 77.6841, 24)

    val j1SegWalk = SegmentView(
        id = SegmentId("seg-j1-1"),
        range = TimeRange(1791964800000L, 1791965460000L), // 09:00 - 09:11
        kind = SegmentKind.MOVE,
        mode = TransportMode.WALK,
        source = ModeSource.MODEL,
        posterior = 0.94f,
        metrics = SegmentMetrics(
            distanceMeters = 850L,
            movingDurationMs = 660000L,
            stoppedDurationMs = 0L,
            averageSpeedKmh = 4.6f,
            maxSpeedKmh = 5.4f
        ),
        points = j1WalkPoints
    )

    val j1SegMetro = SegmentView(
        id = SegmentId("seg-j1-2"),
        range = TimeRange(1791965700000L, 1791966180000L), // 09:15 - 09:23
        kind = SegmentKind.MOVE,
        mode = TransportMode.METRO,
        source = ModeSource.MODEL,
        posterior = 0.91f,
        metrics = SegmentMetrics(
            distanceMeters = 3800L,
            movingDurationMs = 480000L,
            stoppedDurationMs = 60000L,
            averageSpeedKmh = 28.5f,
            maxSpeedKmh = 54.0f
        ),
        points = j1MetroPoints
    )

    val j1SegAuto = SegmentView(
        id = SegmentId("seg-j1-3"),
        range = TimeRange(1791966480000L, 1791968040000L), // 09:28 - 09:54
        kind = SegmentKind.MOVE,
        mode = TransportMode.AUTO_RICKSHAW,
        source = ModeSource.MODEL,
        posterior = 0.88f,
        metrics = SegmentMetrics(
            distanceMeters = 8400L,
            movingDurationMs = 1560000L,
            stoppedDurationMs = 300000L,
            averageSpeedKmh = 19.4f,
            maxSpeedKmh = 44.0f
        ),
        points = j1AutoPoints
    )

    val journey1 = JourneyDetail(
        summary = JourneySummary(
            id = JourneyId("journey-01"),
            range = TimeRange(1791964800000L, 1791968040000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 13050L,
            movingDurationMs = 2700000L,
            stoppedDurationMs = 540000L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.METRO,
            modeSegments = listOf(j1SegWalk, j1SegMetro, j1SegAuto),
            originPlaceName = "Home (Indiranagar)",
            destinationPlaceName = "RMZ Ecospace ORR"
        ),
        segments = listOf(j1SegWalk, j1SegMetro, j1SegAuto),
        points = j1WalkPoints + j1MetroPoints + j1AutoPoints,
        stops = listOf(
            StayView(
                id = StayId("stay-j1-1"),
                range = TimeRange(1791965460000L, 1791965700000L),
                kind = StayKind.TRANSFER,
                location = GeoPoint(12.9783, 77.6385),
                placeName = "Indiranagar Metro Station"
            ),
            StayView(
                id = StayId("stay-j1-2"),
                range = TimeRange(1791966180000L, 1791966480000L),
                kind = StayKind.TRANSFER,
                location = GeoPoint(12.9912, 77.6525),
                placeName = "Baiyappanahalli Metro Station"
            )
        )
    )

    // Journey 2: Lunch Walk
    val j2WalkOut = generatePoints(12.9260, 77.6841, 12.9240, 77.6820, 6)
    val j2SegOut = SegmentView(
        id = SegmentId("seg-j2-1"),
        range = TimeRange(1791979200000L, 1791979560000L), // 13:00 - 13:06
        kind = SegmentKind.MOVE,
        mode = TransportMode.WALK,
        source = ModeSource.MODEL,
        posterior = 0.98f,
        metrics = SegmentMetrics(450L, 360000L, 0L, 4.5f, 5.0f),
        points = j2WalkOut
    )

    val journey2 = JourneyDetail(
        summary = JourneySummary(
            id = JourneyId("journey-02"),
            range = TimeRange(1791979200000L, 1791979560000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 450L,
            movingDurationMs = 360000L,
            stoppedDurationMs = 0L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.WALK,
            modeSegments = listOf(j2SegOut),
            originPlaceName = "RMZ Ecospace ORR",
            destinationPlaceName = "Food Court"
        ),
        segments = listOf(j2SegOut),
        points = j2WalkOut,
        stops = emptyList()
    )

    // Journey 3: Evening Motorbike Ride back home
    val j3BikePoints = generatePoints(12.9260, 77.6841, 12.9784, 77.6408, 28)
    val j3SegBike = SegmentView(
        id = SegmentId("seg-j3-1"),
        range = TimeRange(1791999000000L, 1792000440000L), // 18:30 - 18:54
        kind = SegmentKind.MOVE,
        mode = TransportMode.MOTORCYCLE,
        source = ModeSource.MODEL,
        posterior = 0.96f,
        metrics = SegmentMetrics(
            distanceMeters = 11200L,
            movingDurationMs = 1440000L,
            stoppedDurationMs = 180000L,
            averageSpeedKmh = 28.0f,
            maxSpeedKmh = 62.0f
        ),
        points = j3BikePoints
    )

    val journey3 = JourneyDetail(
        summary = JourneySummary(
            id = JourneyId("journey-03"),
            range = TimeRange(1791999000000L, 1792000440000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 11200L,
            movingDurationMs = 1440000L,
            stoppedDurationMs = 180000L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.MOTORCYCLE,
            modeSegments = listOf(j3SegBike),
            originPlaceName = "RMZ Ecospace ORR",
            destinationPlaceName = "Home (Indiranagar)"
        ),
        segments = listOf(j3SegBike),
        points = j3BikePoints,
        stops = emptyList()
    )

    val demoJourneys = listOf(journey1, journey2, journey3)

    val todaySummary = DaySummary(
        localDate = "2026-10-13",
        distanceMeters = 24700L,
        movingDurationMs = 4500000L, // 1h 15m
        stoppedDurationMs = 720000L,
        journeyCount = 3,
        coveragePercent = 98,
        journeys = listOf(journey1.summary, journey2.summary, journey3.summary),
        modeDistances = mapOf(
            TransportMode.MOTORCYCLE to 11200L,
            TransportMode.AUTO_RICKSHAW to 8400L,
            TransportMode.METRO to 3800L,
            TransportMode.WALK to 1300L
        ),
        modeDurations = mapOf(
            TransportMode.MOTORCYCLE to 1440000L,
            TransportMode.AUTO_RICKSHAW to 1560000L,
            TransportMode.METRO to 480000L,
            TransportMode.WALK to 1020000L
        ),
        health = TrackingHealth.OFF
    )

    val weekBars = listOf(
        DayDistanceBar("07 W", 18200L, "2026-10-07", 96),
        DayDistanceBar("08 T", 22400L, "2026-10-08", 99),
        DayDistanceBar("09 F", 26100L, "2026-10-09", 94),
        DayDistanceBar("10 S", 24300L, "2026-10-10", 97),
        DayDistanceBar("11 S", 31000L, "2026-10-11", 98),
        DayDistanceBar("12 M", 9400L, "2026-10-12", 91),
        DayDistanceBar("13 T", 24700L, "2026-10-13", 98, isIncomplete = true)
    )

    val hourWeekdayCells = run {
        val list = mutableListOf<HourWeekdayCell>()
        for (wd in 1..7) {
            for (hr in 0..23) {
                val level = when {
                    hr in 9..10 -> 4
                    hr in 18..19 -> 4
                    hr in 12..14 -> 2
                    hr in 20..22 -> 1
                    hr in 7..8 -> 2
                    else -> 0
                }
                list.add(HourWeekdayCell(wd, hr, count = level * 3, level = level))
            }
        }
        list
    }

    val records = listOf(
        MovementRecord("Longest single journey", "34.2 km", "2026-09-24", 100, "Distance"),
        MovementRecord("Highest day distance", "62.8 km", "2026-08-15", 98, "Distance"),
        MovementRecord("Most active week", "214.5 km", "Week 38", 97, "Weekly"),
        MovementRecord("Max detected speed", "78.4 km/h", "2026-09-12 (Airport road)", 100, "Speed")
    )

    val periodAnalytics = PeriodAnalytics(
        periodTitle = "Last 7 Days",
        totalDistanceMeters = 156100L,
        totalMovingMs = 28800000L,
        totalStoppedMs = 7200000L,
        journeyCount = 21,
        coveragePercent = 96,
        trendPercentVsPrevious = 14,
        modeShare = mapOf(
            TransportMode.MOTORCYCLE to 0.45f,
            TransportMode.AUTO_RICKSHAW to 0.25f,
            TransportMode.METRO to 0.20f,
            TransportMode.WALK to 0.10f
        ),
        dailyDistances = weekBars,
        hourWeekdayGrid = hourWeekdayCells,
        records = records
    )

    val routeClusters = listOf(
        RouteClusterView(
            id = RouteClusterId("cluster-commute-1"),
            name = "Indiranagar ↔ RMZ Ecospace",
            originPlaceName = "Home (Indiranagar)",
            destinationPlaceName = "RMZ Ecospace ORR",
            primaryMode = TransportMode.MOTORCYCLE,
            memberCount = 42,
            sampleRoute = j3BikePoints
        )
    )

    val healthEvents = listOf(
        HealthEventView(
            id = "evt-1",
            timeUtcMs = 1791965800000L,
            type = "GPS_TUNNEL",
            severity = "INFO",
            message = "Brief GPS signal fade during underground metro transit"
        ),
        HealthEventView(
            id = "evt-2",
            timeUtcMs = 1791986400000L,
            type = "OXYGENOS_BATTERY_SAVER",
            severity = "WARNING",
            message = "OxygenOS Battery Saver throttled background GPS polling for 4m"
        )
    )

    val coverageTimeline = CoverageTimelineStrip(
        date = "2026-10-13",
        intervals = listOf(
            CoverageInterval(0, 540, IntervalStatus.STATIONARY),       // 00:00 - 09:00
            CoverageInterval(540, 54, IntervalStatus.RECORDED),        // 09:00 - 09:54
            CoverageInterval(594, 186, IntervalStatus.STATIONARY),     // 09:54 - 13:00
            CoverageInterval(780, 12, IntervalStatus.RECORDED),        // 13:00 - 13:12
            CoverageInterval(792, 318, IntervalStatus.STATIONARY),     // 13:12 - 18:30
            CoverageInterval(1110, 24, IntervalStatus.RECORDED),       // 18:30 - 18:54
            CoverageInterval(1134, 306, IntervalStatus.STATIONARY)     // 18:54 - 24:00
        )
    )
}
