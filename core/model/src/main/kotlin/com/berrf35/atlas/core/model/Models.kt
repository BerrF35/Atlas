package com.berrf35.atlas.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeRange(
    val fromUtcMs: Long,
    val toUtcMs: Long
) {
    init {
        require(fromUtcMs <= toUtcMs) { "fromUtcMs ($fromUtcMs) must be <= toUtcMs ($toUtcMs)" }
    }
    val durationMs: Long get() = toUtcMs - fromUtcMs
}

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float? = null,
    val timeUtcMs: Long = 0L,
    val altitudeM: Double? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null
)

@Serializable
data class SegmentMetrics(
    val distanceMeters: Long,
    val movingDurationMs: Long,
    val stoppedDurationMs: Long,
    val averageSpeedKmh: Float,
    val maxSpeedKmh: Float
)

@Serializable
data class SegmentView(
    val id: SegmentId,
    val range: TimeRange,
    val kind: SegmentKind,
    val mode: TransportMode?,
    val source: ModeSource?,
    val posterior: Float?,
    val metrics: SegmentMetrics,
    val gapCause: GapCause? = null,
    val points: List<GeoPoint> = emptyList(),
    val boundaryUncertaintyMs: Long = 0L
) {
    val confidenceBand: ConfidenceBand get() = ConfidenceBand.fromPosterior(posterior)
}

@Serializable
data class StayView(
    val id: StayId,
    val range: TimeRange,
    val kind: StayKind,
    val location: GeoPoint,
    val placeId: PlaceId? = null,
    val placeName: String? = null,
    val radiusMeters: Int = 30
)

@Serializable
data class JourneySummary(
    val id: JourneyId,
    val range: TimeRange,
    val state: JourneyState,
    val distanceMeters: Long,
    val movingDurationMs: Long,
    val stoppedDurationMs: Long,
    val gapDurationMs: Long,
    val primaryMode: TransportMode,
    val modeSegments: List<SegmentView>,
    val originPlaceName: String? = null,
    val destinationPlaceName: String? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class JourneyDetail(
    val summary: JourneySummary,
    val segments: List<SegmentView>,
    val points: List<GeoPoint>,
    val stops: List<StayView>
)

@Serializable
data class DaySummary(
    val localDate: String, // YYYY-MM-DD
    val distanceMeters: Long,
    val movingDurationMs: Long,
    val stoppedDurationMs: Long,
    val journeyCount: Int,
    val coveragePercent: Int,
    val journeys: List<JourneySummary>,
    val modeDistances: Map<TransportMode, Long>,
    val modeDurations: Map<TransportMode, Long>,
    val health: TrackingHealth
)

@Serializable
data class DayDistanceBar(
    val label: String,
    val distanceMeters: Long,
    val date: String,
    val coveragePercent: Int,
    val isIncomplete: Boolean = false
)

@Serializable
data class HourWeekdayCell(
    val weekday: Int, // 1 = Mon .. 7 = Sun
    val hour: Int,    // 0 .. 23
    val count: Int,
    val level: Int    // 0 .. 4
)

@Serializable
data class MovementRecord(
    val title: String,
    val valueDisplay: String,
    val dateOrPeriod: String,
    val coveragePercent: Int,
    val category: String
)

@Serializable
data class PeriodAnalytics(
    val periodTitle: String,
    val totalDistanceMeters: Long,
    val totalMovingMs: Long,
    val totalStoppedMs: Long,
    val journeyCount: Int,
    val coveragePercent: Int,
    val trendPercentVsPrevious: Int?,
    val modeShare: Map<TransportMode, Float>,
    val dailyDistances: List<DayDistanceBar>,
    val hourWeekdayGrid: List<HourWeekdayCell>,
    val records: List<MovementRecord>
)

@Serializable
data class PlaceView(
    val id: PlaceId,
    val name: String,
    val center: GeoPoint,
    val radiusMeters: Int,
    val status: PlaceStatus,
    val isPrivacyZone: Boolean,
    val visitCount: Int,
    val lastVisitUtcMs: Long?
)

@Serializable
data class RouteClusterView(
    val id: RouteClusterId,
    val name: String,
    val originPlaceName: String,
    val destinationPlaceName: String,
    val primaryMode: TransportMode,
    val memberCount: Int,
    val sampleRoute: List<GeoPoint>
)

@Serializable
data class HealthEventView(
    val id: String,
    val timeUtcMs: Long,
    val type: String,
    val severity: String,
    val message: String
)

@Serializable
data class GapDetailView(
    val id: String,
    val range: TimeRange,
    val cause: GapCause,
    val description: String,
    val affectedJourneyId: JourneyId? = null,
    val advice: String = ""
)

@Serializable
enum class IntervalStatus {
    RECORDED,
    STATIONARY,
    GAP,
    OFF
}

@Serializable
data class CoverageInterval(
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val status: IntervalStatus
)

@Serializable
data class CoverageTimelineStrip(
    val date: String,
    val intervals: List<CoverageInterval>
)

@Serializable
data class AtlasSettings(
    val trackingEnabled: Boolean = false,
    val pauseUntilUtcMs: Long? = null,
    val batteryProfile: BatteryProfile = BatteryProfile.BALANCED,
    val theme: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val reduceMotion: Boolean = false,
    val appLockEnabled: Boolean = false,
    val isDemoMode: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val ambientCacheMb: Int = 256,
    val tileServerUrl: String = "https://tiles.openfreemap.org",
    val reverseGeocodingEnabled: Boolean = false,
    val screenshotSafeHeatmap: Boolean = false
)
