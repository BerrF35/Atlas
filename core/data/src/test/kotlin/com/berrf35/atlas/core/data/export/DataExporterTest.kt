package com.berrf35.atlas.core.data.export

import com.berrf35.atlas.core.data.database.RawObservationEntity
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneyState
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceStatus
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.SegmentKind
import com.berrf35.atlas.core.model.SegmentMetrics
import com.berrf35.atlas.core.model.SegmentView
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TransportMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataExporterTest {

    private val samplePoints = listOf(
        GeoPoint(12.9716, 77.5946, 5f, 1000L),
        GeoPoint(12.9750, 77.5980, 5f, 2000L),
        GeoPoint(12.9800, 77.6000, 5f, 3000L)
    )

    private val sampleJourney = JourneyDetail(
        summary = JourneySummary(
            id = JourneyId("j-test-1"),
            range = TimeRange(1000L, 3000L),
            state = JourneyState.CONFIRMED,
            distanceMeters = 1200L,
            movingDurationMs = 2000L,
            stoppedDurationMs = 0L,
            gapDurationMs = 0L,
            primaryMode = TransportMode.BICYCLE,
            modeSegments = emptyList(),
            originPlaceName = "Home",
            destinationPlaceName = "Office",
            isDeleted = false
        ),
        segments = listOf(
            SegmentView(
                id = SegmentId("seg-test-1"),
                range = TimeRange(1000L, 3000L),
                kind = SegmentKind.MOVE,
                mode = TransportMode.BICYCLE,
                source = com.berrf35.atlas.core.model.ModeSource.MODEL,
                posterior = 0.95f,
                metrics = SegmentMetrics(1200L, 2000L, 0L, 21.6f, 25.0f),
                points = samplePoints
            )
        ),
        points = samplePoints,
        stops = emptyList()
    )

    private val privacyZone = PlaceView(
        id = PlaceId("place-home"),
        name = "Home Sanctuary",
        center = GeoPoint(12.9716, 77.5946),
        radiusMeters = 100, // 100m radius around first point
        status = PlaceStatus.CONFIRMED,
        isPrivacyZone = true,
        visitCount = 10,
        lastVisitUtcMs = 1000L
    )

    @Test
    fun testExportGpxGeneratesValidXml() {
        val gpx = DataExporter.exportGpx(listOf(sampleJourney), emptyList(), applyPrivacyMask = false)

        assertThat(gpx).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertThat(gpx).contains("<gpx version=\"1.1\"")
        assertThat(gpx).contains("<trk>")
        assertThat(gpx).contains("<trkseg>")
        assertThat(gpx).contains("lat=\"12.9716000\" lon=\"77.5946000\"")
        assertThat(gpx).contains("<type>BICYCLE</type>")
        assertThat(gpx).endsWith("</gpx>\n")
    }

    @Test
    fun testExportGpxAppliesPrivacyZoneClipping() {
        val gpx = DataExporter.exportGpx(listOf(sampleJourney), listOf(privacyZone), applyPrivacyMask = true)

        // Point 1 (12.9716, 77.5946) is inside the 100m privacy zone and must be clipped
        assertThat(gpx).doesNotContain("lat=\"12.9716000\" lon=\"77.5946000\"")
        // Points 2 and 3 are outside and must be preserved
        assertThat(gpx).contains("lat=\"12.9750000\" lon=\"77.5980000\"")
    }

    @Test
    fun testExportGeoJsonGeneratesValidFeatureCollection() {
        val geoJson = DataExporter.exportGeoJson(listOf(sampleJourney), emptyList(), applyPrivacyMask = false)

        assertThat(geoJson).contains("\"type\":\"FeatureCollection\"")
        assertThat(geoJson).contains("\"type\": \"LineString\"")
        assertThat(geoJson).contains("\"journeyId\": \"j-test-1\"")
        assertThat(geoJson).contains("\"mode\": \"BICYCLE\"")
        assertThat(geoJson).contains("[77.5946000,12.9716000]")
    }

    @Test
    fun testExportCsvGeneratesRfc4180Format() {
        val rawObs = listOf(
            RawObservationEntity(
                id = "obs-1",
                sessionId = "s1",
                timeUtcMs = 1791964800000L,
                latE7 = 129716000,
                lonE7 = 775946000,
                accuracyCm = 450,
                speedMmps = 5500,
                bearingCdeg = 9000,
                provider = "FLP"
            )
        )

        val csv = DataExporter.exportCsv(rawObs, emptyList(), applyPrivacyMask = false)

        assertThat(csv).startsWith("time_iso,time_utc_ms,lat,lon,accuracy_m,speed_mps,bearing_deg,provider,is_mock\n")
        assertThat(csv).contains("12.9716000,77.5946000,4.50,5.50,90.0,FLP,0")
    }
}
