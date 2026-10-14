package com.berrf35.atlas.core.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneyState
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.ModeSource
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceStatus
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.SegmentKind
import com.berrf35.atlas.core.model.SegmentMetrics
import com.berrf35.atlas.core.model.SegmentView
import com.berrf35.atlas.core.model.StayId
import com.berrf35.atlas.core.model.StayKind
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TransportMode
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtlasDaoTest {

    private lateinit var dbHelper: AtlasDbHelper
    private lateinit var dao: AtlasDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbHelper = AtlasDbHelper(context)
        dao = AtlasDao(dbHelper)
        dao.clearAllData()
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun testInsertAndQueryObservations() {
        val sessionId = "session-test-1"
        dao.startSession(sessionId, 1000L, "1.0.0", "test-device")

        val obsList = listOf(
            RawObservationEntity(
                id = "obs-1",
                sessionId = sessionId,
                timeUtcMs = 1000L,
                latE7 = 129716000,
                lonE7 = 775946000,
                accuracyCm = 400
            ),
            RawObservationEntity(
                id = "obs-2",
                sessionId = sessionId,
                timeUtcMs = 2000L,
                latE7 = 129726000,
                lonE7 = 775956000,
                accuracyCm = 350
            )
        )

        dao.insertObservations(obsList)

        val retrieved = dao.getObservations(500L, 2500L)
        assertThat(retrieved).hasSize(2)
        assertThat(retrieved.first().id).isEqualTo("obs-1")
        assertThat(retrieved.last().id).isEqualTo("obs-2")

        val latest = dao.getLatestObservation()
        assertThat(latest).isNotNull()
        assertThat(latest?.id).isEqualTo("obs-2")
    }

    @Test
    fun testInsertJourneyWithSegmentsAndStays() {
        val journeyId = JourneyId("journey-1")
        val summary = JourneySummary(
            id = journeyId,
            range = TimeRange(1000L, 5000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 3500L,
            movingDurationMs = 4000L,
            stoppedDurationMs = 0L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.METRO,
            modeSegments = emptyList(),
            originPlaceName = "Majestic",
            destinationPlaceName = "MG Road",
            isDeleted = false
        )

        val segments = listOf(
            SegmentView(
                id = SegmentId("seg-1"),
                range = TimeRange(1000L, 5000L),
                kind = SegmentKind.MOVE,
                mode = TransportMode.METRO,
                source = ModeSource.MODEL,
                posterior = 0.95f,
                metrics = SegmentMetrics(3500L, 4000L, 0L, 31.5f, 45.0f),
                points = listOf(GeoPoint(12.9766, 77.5713), GeoPoint(12.9756, 77.6066))
            )
        )

        val stays = listOf(
            StayView(
                id = StayId("stay-1"),
                range = TimeRange(5000L, 6000L),
                kind = StayKind.VISIT,
                location = GeoPoint(12.9756, 77.6066),
                placeName = "MG Road Station",
                radiusMeters = 50
            )
        )

        dao.insertJourneyWithSegments(summary, segments, stays)

        val journeys = dao.getJourneys(includeDeleted = false)
        assertThat(journeys).hasSize(1)
        assertThat(journeys.first().primaryMode).isEqualTo(TransportMode.METRO)

        val detail = dao.getJourneyDetail(journeyId)
        assertThat(detail).isNotNull()
        assertThat(detail?.segments).hasSize(1)
        assertThat(detail?.stops).hasSize(1)
        assertThat(detail?.stops?.first()?.placeName).isEqualTo("MG Road Station")
    }

    @Test
    fun testSoftDeletePreservesObservations() {
        val journeyId = JourneyId("j-to-delete")
        val summary = JourneySummary(
            id = journeyId,
            range = TimeRange(1000L, 2000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 500L,
            movingDurationMs = 1000L,
            stoppedDurationMs = 0L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.WALK,
            modeSegments = emptyList(),
            isDeleted = false
        )

        dao.insertJourneyWithSegments(summary, emptyList(), emptyList())
        assertThat(dao.getJourneys(includeDeleted = false)).hasSize(1)

        // Soft delete
        dao.markJourneyDeleted(journeyId, true)
        assertThat(dao.getJourneys(includeDeleted = false)).isEmpty()
        assertThat(dao.getJourneys(includeDeleted = true)).hasSize(1)

        // Restore
        dao.markJourneyDeleted(journeyId, false)
        assertThat(dao.getJourneys(includeDeleted = false)).hasSize(1)
    }

    @Test
    fun testUserCorrectionUpdatesModeAndRecordsAudit() {
        val journeyId = JourneyId("j-correction")
        val segId = SegmentId("seg-corr")
        val summary = JourneySummary(
            id = journeyId,
            range = TimeRange(1000L, 2000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 1000L,
            movingDurationMs = 1000L,
            stoppedDurationMs = 0L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.UNKNOWN,
            modeSegments = emptyList(),
            isDeleted = false
        )
        val segments = listOf(
            SegmentView(
                id = segId,
                range = TimeRange(1000L, 2000L),
                kind = SegmentKind.MOVE,
                mode = TransportMode.UNKNOWN,
                source = ModeSource.MODEL,
                posterior = 0.5f,
                metrics = SegmentMetrics(1000L, 1000L, 0L, 20f, 25f),
                points = emptyList()
            )
        )
        dao.insertJourneyWithSegments(summary, segments, emptyList())

        // User manually corrects segment to BICYCLE
        dao.updateSegmentMode(journeyId, segId, TransportMode.BICYCLE, ModeSource.USER)

        val updated = dao.getJourneyDetail(journeyId)
        assertThat(updated?.summary?.primaryMode).isEqualTo(TransportMode.BICYCLE)
        assertThat(updated?.segments?.first()?.mode).isEqualTo(TransportMode.BICYCLE)
        assertThat(updated?.segments?.first()?.source).isEqualTo(ModeSource.USER)
    }

    @Test
    fun testPlacesCrudAndPrivacyZoneFlag() {
        val place = PlaceView(
            id = PlaceId("place-1"),
            name = "Indiranagar Home",
            center = GeoPoint(12.9784, 77.6408),
            radiusMeters = 80,
            status = PlaceStatus.CONFIRMED,
            isPrivacyZone = true,
            visitCount = 15,
            lastVisitUtcMs = 2000L
        )

        dao.insertOrUpdatePlace(place)

        val places = dao.getPlaces()
        assertThat(places).hasSize(1)
        assertThat(places.first().name).isEqualTo("Indiranagar Home")
        assertThat(places.first().isPrivacyZone).isTrue()

        // Soft delete place
        dao.deletePlace(PlaceId("place-1"))
        assertThat(dao.getPlaces()).isEmpty()
    }
}
