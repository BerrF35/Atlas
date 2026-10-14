package com.berrf35.atlas.core.data.reconstruction

import com.berrf35.atlas.core.data.database.RawObservationEntity
import com.berrf35.atlas.core.model.GapCause
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.SegmentKind
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TripSegmenterTest {

    private lateinit var segmenter: TripSegmenter

    @Before
    fun setUp() {
        segmenter = TripSegmenter()
    }

    @Test
    fun testFilterObservationsRejectsInaccurateAndOutOfBounds() {
        val raw = listOf(
            RawObservationEntity(
                id = "obs-1",
                sessionId = "sess-1",
                timeUtcMs = 1000L,
                latE7 = 129716000,
                lonE7 = 775946000,
                accuracyCm = 500 // 5m - valid
            ),
            RawObservationEntity(
                id = "obs-2",
                sessionId = "sess-1",
                timeUtcMs = 2000L,
                latE7 = 129716000,
                lonE7 = 775946000,
                accuracyCm = 25000 // 250m - INVALID (>200m)
            ),
            RawObservationEntity(
                id = "obs-3",
                sessionId = "sess-1",
                timeUtcMs = 3000L,
                latE7 = 950000000, // INVALID lat (>90 deg)
                lonE7 = 775946000,
                accuracyCm = 500
            )
        )

        val filtered = segmenter.filterObservations(raw)
        assertThat(filtered).hasSize(1)
        assertThat(filtered.first().timeUtcMs).isEqualTo(1000L)
    }

    @Test
    fun testRejectsMicroJitterBelowConfirmationThreshold() {
        // Less than 120m displacement and less than 180s moving duration
        val points = listOf(
            GeoPoint(12.9716, 77.5946, 5f, 1000L, speedMps = 0.5f),
            GeoPoint(12.9717, 77.5946, 5f, 5000L, speedMps = 0.5f) // ~11 meters displacement in 4s
        )

        val journeys = segmenter.reconstructJourneys(points)
        assertThat(journeys).isEmpty()
    }

    @Test
    fun testReconstructsConfirmedMovingJourney() {
        // Build a journey spanning > 500 meters over 5 minutes (300 seconds)
        val points = mutableListOf<GeoPoint>()
        var lat = 12.9716
        var time = 1000000L

        for (i in 0..30) {
            points.add(GeoPoint(lat, 77.5946, 5f, time, speedMps = 5f))
            lat += 0.0002 // ~22 meters per step
            time += 10000L // 10s step -> 300s total
        }

        val journeys = segmenter.reconstructJourneys(points)
        assertThat(journeys).hasSize(1)

        val journey = journeys.first()
        assertThat(journey.summary.distanceMeters).isAtLeast(500L)
        assertThat(journey.summary.movingDurationMs).isAtLeast(200_000L)
        assertThat(journey.segments).isNotEmpty()
    }

    @Test
    fun testSplitsJourneyAtLongVisitStay() {
        // Trip 1 (500m), then a 15-minute visit pause (> 600s), then Trip 2 (500m)
        val points = mutableListOf<GeoPoint>()
        var lat = 12.9716
        var time = 1000000L

        // Leg 1
        for (i in 0..20) {
            points.add(GeoPoint(lat, 77.5946, 5f, time, speedMps = 5f))
            lat += 0.0003
            time += 10000L
        }

        // Long visit stay: 15 minutes (900 seconds) at the same location
        time += 900_000L

        // Leg 2
        for (i in 0..20) {
            points.add(GeoPoint(lat, 77.5946, 5f, time, speedMps = 5f))
            lat += 0.0003
            time += 10000L
        }

        val journeys = segmenter.reconstructJourneys(points)
        // Must split into 2 distinct journeys
        assertThat(journeys).hasSize(2)
    }

    @Test
    fun testPreservesMissingDataIntervalAsGapSegment() {
        // Gap of 4 minutes (240s > 180s gap threshold) between fixes during moving journey
        val points = listOf(
            GeoPoint(12.9716, 77.5946, 5f, 1000000L, speedMps = 10f),
            GeoPoint(12.9736, 77.5946, 5f, 1030000L, speedMps = 10f),
            // 4-minute missing gap
            GeoPoint(12.9786, 77.5946, 5f, 1270000L, speedMps = 10f),
            GeoPoint(12.9816, 77.5946, 5f, 1300000L, speedMps = 10f)
        )

        val journeys = segmenter.reconstructJourneys(points)
        assertThat(journeys).isNotEmpty()
        val segments = journeys.first().segments

        // Find gap segment
        val gapSegment = segments.firstOrNull { it.kind == SegmentKind.GAP }
        assertThat(gapSegment).isNotNull()
        assertThat(gapSegment?.gapCause).isEqualTo(GapCause.NO_FIX)
    }
}
