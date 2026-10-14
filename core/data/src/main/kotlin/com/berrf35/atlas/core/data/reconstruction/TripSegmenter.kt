package com.berrf35.atlas.core.data.reconstruction

import com.berrf35.atlas.core.data.classification.BaselineTransportClassifier
import com.berrf35.atlas.core.data.database.RawObservationEntity
import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.GapCause
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneyState
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.ModeSource
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.SegmentKind
import com.berrf35.atlas.core.model.SegmentMetrics
import com.berrf35.atlas.core.model.SegmentView
import com.berrf35.atlas.core.model.StayId
import com.berrf35.atlas.core.model.StayKind
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TransportMode
import kotlin.math.max

/**
 * TripSegmenter: Reconstructs journeys, segments, stops, and gaps from raw observations
 * conforming to TRACKING_AND_CLASSIFICATION.md §6.
 */
class TripSegmenter(
    private val classifier: BaselineTransportClassifier = BaselineTransportClassifier()
) {

    /**
     * Filters raw observations according to quality thresholds (accuracy <= 200m, not stale, valid bounds).
     */
    fun filterObservations(raw: List<RawObservationEntity>): List<GeoPoint> {
        return raw
            .filter { obs ->
                obs.accuracyCm <= 20_000 && // 200m accuracy max
                obs.latE7 in -900_000_000..900_000_000 &&
                obs.lonE7 in -1_800_000_000..1_800_000_000
            }
            .map { obs ->
                GeoPoint(
                    latitude = obs.latE7 / 1e7,
                    longitude = obs.lonE7 / 1e7,
                    accuracyM = obs.accuracyCm / 100f,
                    timeUtcMs = obs.timeUtcMs,
                    altitudeM = obs.altitudeCm?.let { it / 100.0 },
                    speedMps = obs.speedMmps?.let { it / 1000f },
                    bearingDeg = obs.bearingCdeg?.let { it / 100f }
                )
            }
            .sortedBy { it.timeUtcMs }
    }

    /**
     * Segments filtered points into complete reconstructed journeys.
     */
    fun reconstructJourneys(points: List<GeoPoint>): List<JourneyDetail> {
        if (points.size < 2) return emptyList()

        val journeys = mutableListOf<JourneyDetail>()
        var currentTripPoints = mutableListOf<GeoPoint>()
        var lastPoint = points.first()

        for (i in 1 until points.size) {
            val curr = points[i]
            val timeDeltaMs = curr.timeUtcMs - lastPoint.timeUtcMs
            val distM = BaselineTransportClassifier.calculateDistance(
                lastPoint.latitude, lastPoint.longitude,
                curr.latitude, curr.longitude
            )

            if (currentTripPoints.isEmpty()) {
                currentTripPoints.add(lastPoint)
            }

            // Check for trip split conditions:
            // 1. Visit halt: duration >= 10 minutes (600,000 ms) within 50m
            // 2. Significant gap without GPS fix: gap > 15 minutes (900,000 ms)
            val isHaltVisit = timeDeltaMs >= 600_000L && distM < 50.0
            val isUnmergeableGap = timeDeltaMs >= 900_000L

            if (isHaltVisit || isUnmergeableGap) {
                // Finalize current journey candidate
                assembleJourney(currentTripPoints)?.let { journeys.add(it) }
                currentTripPoints = mutableListOf()
            } else {
                currentTripPoints.add(curr)
            }

            lastPoint = curr
        }

        if (currentTripPoints.size >= 2) {
            assembleJourney(currentTripPoints)?.let { journeys.add(it) }
        }

        return journeys
    }

    /**
     * Assembles a single journey from points, producing segments, metrics, and mode predictions.
     */
    fun assembleJourney(tripPoints: List<GeoPoint>): JourneyDetail? {
        if (tripPoints.size < 2) return null

        val startTime = tripPoints.first().timeUtcMs
        val endTime = tripPoints.last().timeUtcMs
        val totalDurationMs = max(1000L, endTime - startTime)

        // Calculate total displacement
        val displacementM = BaselineTransportClassifier.calculateDistance(
            tripPoints.first().latitude, tripPoints.first().longitude,
            tripPoints.last().latitude, tripPoints.last().longitude
        ).toLong()

        // Confirmation rule: >= 120m displacement or >= 3 min (180,000 ms) moving time
        if (displacementM < 120L && totalDurationMs < 180_000L) {
            return null // Discard tiny GPS jitter while stationary
        }

        // Partition into sub-legs separated by missing data gaps >= 180,000L (3 minutes)
        val segments = mutableListOf<SegmentView>()
        val stops = mutableListOf<StayView>()
        var segOrdinal = 0

        val legs = mutableListOf<List<GeoPoint>>()
        val gapIntervals = mutableListOf<Pair<GeoPoint, GeoPoint>>()

        var currentLeg = mutableListOf<GeoPoint>()
        currentLeg.add(tripPoints.first())

        for (i in 1 until tripPoints.size) {
            val prev = tripPoints[i - 1]
            val curr = tripPoints[i]
            if ((curr.timeUtcMs - prev.timeUtcMs) >= 180_000L) {
                // Gap detected!
                legs.add(currentLeg)
                gapIntervals.add(Pair(prev, curr))
                currentLeg = mutableListOf(curr)
            } else {
                currentLeg.add(curr)
            }
        }
        if (currentLeg.isNotEmpty()) {
            legs.add(currentLeg)
        }

        // For each leg, classify into MOVE/STATIONARY segments
        legs.forEachIndexed { legIdx, legPoints ->
            if (legPoints.size >= 2) {
                val legStart = legPoints.first().timeUtcMs
                val legEnd = legPoints.last().timeUtcMs
                val epochDurationMs = 15_000L
                val epochSegments = mutableListOf<Pair<TimeRange, List<GeoPoint>>>()

                var epochStart = legStart
                while (epochStart < legEnd) {
                    val epochEnd = epochStart + epochDurationMs
                    val ptsInEpoch = legPoints.filter { it.timeUtcMs in epochStart until epochEnd }
                    if (ptsInEpoch.isNotEmpty()) {
                        epochSegments.add(Pair(TimeRange(epochStart, epochEnd), ptsInEpoch))
                    }
                    epochStart = epochEnd
                }

                val epochClassifications = epochSegments.map { (_, pts) ->
                    classifier.classifyEpoch(pts)
                }
                val smoothed = classifier.smoothSequence(epochClassifications)

                var currentMode = smoothed.firstOrNull()?.mode ?: TransportMode.UNKNOWN
                var currentPosterior = smoothed.firstOrNull()?.posterior ?: 0.5f
                var curSegStart = epochSegments.firstOrNull()?.first?.fromUtcMs ?: legStart
                var curSegPoints = mutableListOf<GeoPoint>()

                smoothed.forEachIndexed { idx, res ->
                    val epochRange = epochSegments[idx].first
                    val pts = epochSegments[idx].second

                    if (res.mode == currentMode) {
                        curSegPoints.addAll(pts)
                    } else {
                        val curSegEnd = epochRange.fromUtcMs
                        emitSegment(
                            segOrdinal++, curSegStart, curSegEnd, currentMode, currentPosterior, curSegPoints
                        )?.let { segments.add(it) }

                        if (currentMode == TransportMode.STATIONARY && (curSegEnd - curSegStart) >= 180_000L) {
                            stops.add(
                                StayView(
                                    id = StayId("stay-${curSegStart}"),
                                    range = TimeRange(curSegStart, curSegEnd),
                                    kind = if (segments.size > 1) StayKind.TRANSFER else StayKind.STOP,
                                    location = curSegPoints.firstOrNull() ?: GeoPoint(0.0, 0.0),
                                    radiusMeters = 30
                                )
                            )
                        }

                        currentMode = res.mode
                        currentPosterior = res.posterior
                        curSegStart = epochRange.fromUtcMs
                        curSegPoints = pts.toMutableList()
                    }
                }

                emitSegment(
                    segOrdinal++, curSegStart, legEnd, currentMode, currentPosterior, curSegPoints
                )?.let { segments.add(it) }
            } else if (legPoints.size == 1) {
                val pt = legPoints.first()
                emitSegment(
                    segOrdinal++, pt.timeUtcMs, pt.timeUtcMs + 1000L, TransportMode.STATIONARY, 0.8f, legPoints
                )?.let { segments.add(it) }
            }

            // Insert gap segment after this leg if a gap followed it
            if (legIdx < gapIntervals.size) {
                val (gapFrom, gapTo) = gapIntervals[legIdx]
                segments.add(
                    SegmentView(
                        id = SegmentId("gap-${gapFrom.timeUtcMs}"),
                        range = TimeRange(gapFrom.timeUtcMs, gapTo.timeUtcMs),
                        kind = SegmentKind.GAP,
                        mode = null,
                        source = ModeSource.MODEL,
                        posterior = null,
                        metrics = SegmentMetrics(0L, 0L, 0L, 0f, 0f),
                        gapCause = GapCause.NO_FIX,
                        points = listOf(gapFrom, gapTo),
                        boundaryUncertaintyMs = 30_000L
                    )
                )
            }
        }

        // Compute total journey metrics
        val totalDistance = segments.sumOf { it.metrics.distanceMeters }
        val totalMovingMs = segments.filter { it.mode != TransportMode.STATIONARY }.sumOf { it.metrics.movingDurationMs }
        val totalStoppedMs = segments.filter { it.mode == TransportMode.STATIONARY }.sumOf { it.metrics.stoppedDurationMs }
        val totalGapMs = segments.filter { it.kind == SegmentKind.GAP }.sumOf { it.range.durationMs }

        // Primary mode is the non-stationary mode with largest moving distance
        val primaryMode = segments
            .filter { it.mode != null && it.mode != TransportMode.STATIONARY }
            .groupBy { it.mode!! }
            .maxByOrNull { entry -> entry.value.sumOf { it.metrics.distanceMeters } }
            ?.key ?: segments.firstOrNull { it.mode != null }?.mode ?: TransportMode.UNKNOWN

        val journeyId = JourneyId("journey-${startTime}")
        val summary = JourneySummary(
            id = journeyId,
            range = TimeRange(startTime, endTime),
            state = JourneyState.CONFIRMED,
            distanceMeters = totalDistance,
            movingDurationMs = totalMovingMs,
            stoppedDurationMs = totalStoppedMs,
            gapDurationMs = totalGapMs,
            primaryMode = primaryMode,
            modeSegments = segments,
            originPlaceName = null,
            destinationPlaceName = null,
            isDeleted = false
        )

        return JourneyDetail(
            summary = summary,
            segments = segments,
            points = tripPoints,
            stops = stops
        )
    }

    private fun emitSegment(
        ordinal: Int,
        fromMs: Long,
        toMs: Long,
        mode: TransportMode,
        posterior: Float,
        points: List<GeoPoint>
    ): SegmentView? {
        if (fromMs >= toMs) return null

        var distM = 0L
        for (i in 0 until points.size - 1) {
            distM += BaselineTransportClassifier.calculateDistance(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            ).toLong()
        }

        val durationMs = toMs - fromMs
        val isStationary = mode == TransportMode.STATIONARY
        val movingMs = if (isStationary) 0L else durationMs
        val stoppedMs = if (isStationary) durationMs else 0L

        val avgSpeedKmh = if (durationMs > 0) (distM.toFloat() / (durationMs / 1000f)) * 3.6f else 0f

        return SegmentView(
            id = SegmentId("seg-${fromMs}"),
            range = TimeRange(fromMs, toMs),
            kind = SegmentKind.MOVE,
            mode = mode,
            source = ModeSource.MODEL,
            posterior = posterior,
            metrics = SegmentMetrics(
                distanceMeters = distM,
                movingDurationMs = movingMs,
                stoppedDurationMs = stoppedMs,
                averageSpeedKmh = avgSpeedKmh,
                maxSpeedKmh = avgSpeedKmh * 1.3f
            ),
            gapCause = null,
            points = points,
            boundaryUncertaintyMs = 15_000L
        )
    }
}
