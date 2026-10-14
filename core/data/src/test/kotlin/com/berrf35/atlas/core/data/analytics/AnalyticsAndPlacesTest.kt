package com.berrf35.atlas.core.data.analytics

import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneyState
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.StayId
import com.berrf35.atlas.core.model.StayKind
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TransportMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsAndPlacesTest {

    private fun createSummary(
        id: String,
        distanceMeters: Long,
        movingDurationMs: Long,
        mode: TransportMode,
        origin: String = "Origin",
        dest: String = "Dest"
    ): JourneySummary {
        return JourneySummary(
            id = JourneyId(id),
            range = TimeRange(1791964800000L, 1791964800000L + movingDurationMs),
            state = JourneyState.CONFIRMED,
            distanceMeters = distanceMeters,
            movingDurationMs = movingDurationMs,
            stoppedDurationMs = 0L,
            gapDurationMs = 0L,
            primaryMode = mode,
            modeSegments = emptyList(),
            originPlaceName = origin,
            destinationPlaceName = dest,
            isDeleted = false
        )
    }

    @Test
    fun testComputePeriodAnalyticsSumsDistancesAndComputesModeShare() {
        val journeys = listOf(
            createSummary("j1", 10000L, 1800000L, TransportMode.BICYCLE),
            createSummary("j2", 20000L, 2400000L, TransportMode.METRO),
            createSummary("j3", 10000L, 1200000L, TransportMode.BICYCLE)
        )

        val analytics = AnalyticsAndPlaces.computePeriodAnalytics("WEEK", journeys)

        assertThat(analytics.periodTitle).isEqualTo("WEEK")
        assertThat(analytics.totalDistanceMeters).isEqualTo(40000L)
        assertThat(analytics.totalMovingMs).isEqualTo(5400000L)
        assertThat(analytics.journeyCount).isEqualTo(3)

        // Mode share: Bicycle is 20000 / 40000 = 0.5f (50%)
        val bikeShare = analytics.modeShare[TransportMode.BICYCLE]
        assertThat(bikeShare).isEqualTo(0.5f)

        // Metro is 20000 / 40000 = 0.5f (50%)
        val metroShare = analytics.modeShare[TransportMode.METRO]
        assertThat(metroShare).isEqualTo(0.5f)
    }

    @Test
    fun testDetectRecurringCorridorsGroupsFrequentTrips() {
        val journeys = listOf(
            createSummary("j1", 5000L, 600000L, TransportMode.METRO, "Home", "Office"),
            createSummary("j2", 5000L, 600000L, TransportMode.METRO, "Home", "Office"),
            createSummary("j3", 5000L, 600000L, TransportMode.METRO, "Home", "Office"),
            createSummary("j4", 2000L, 300000L, TransportMode.WALK, "Home", "Park")
        )

        val corridors = AnalyticsAndPlaces.detectRecurringCorridors(journeys)

        // Only corridors with >= 3 journeys should be detected
        assertThat(corridors).hasSize(1)
        val corridor = corridors.first()
        assertThat(corridor.originPlaceName).isEqualTo("Home")
        assertThat(corridor.destinationPlaceName).isEqualTo("Office")
        assertThat(corridor.memberCount).isEqualTo(3)
    }

    @Test
    fun testClusterPlacesFromStaysGroupsNearbyVisits() {
        val stays = listOf(
            StayView(StayId("s1"), TimeRange(1000L, 700_000L), StayKind.VISIT, GeoPoint(12.9716, 77.5946), radiusMeters = 50),
            StayView(StayId("s2"), TimeRange(3000L, 700_000L), StayKind.VISIT, GeoPoint(12.9717, 77.5947), radiusMeters = 50),
            StayView(StayId("s3"), TimeRange(5000L, 700_000L), StayKind.VISIT, GeoPoint(12.9716, 77.5946), radiusMeters = 50),
            // Isolated visit far away
            StayView(StayId("s4"), TimeRange(7000L, 700_000L), StayKind.VISIT, GeoPoint(13.1000, 77.7000), radiusMeters = 50)
        )

        val candidatePlaces = AnalyticsAndPlaces.clusterPlacesFromStays(stays = stays, existingPlaces = emptyList())

        // Cluster of visits should produce candidate places
        assertThat(candidatePlaces).isNotEmpty()
        val place = candidatePlaces.first()
        assertThat(place.visitCount).isAtLeast(1)
    }
}
