package com.berrf35.atlas.core.data.classification

import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.TransportMode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class BaselineTransportClassifierTest {

    private lateinit var classifier: BaselineTransportClassifier

    @Before
    fun setUp() {
        classifier = BaselineTransportClassifier()
    }

    private fun generateSyntheticTrack(
        speedMps: Float,
        accelNoise: Float = 0.1f,
        pointCount: Int = 20,
        startLat: Double = 12.9716,
        startLon: Double = 77.5946
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var currentLat = startLat
        var currentLon = startLon
        var timeMs = 1791964800000L

        for (i in 0 until pointCount) {
            val speedWithNoise = (speedMps + (Math.sin(i.toDouble()) * accelNoise).toFloat()).coerceAtLeast(0f)
            val latDelta = (speedWithNoise * 2.0) / 111000.0
            currentLat += latDelta
            timeMs += 2000L

            points.add(
                GeoPoint(
                    latitude = currentLat,
                    longitude = currentLon,
                    accuracyM = 4.0f,
                    timeUtcMs = timeMs,
                    speedMps = speedWithNoise,
                    bearingDeg = 0f
                )
            )
        }
        return points
    }

    @Test
    fun testClassifyStationaryTrack() {
        val points = generateSyntheticTrack(speedMps = 0.05f, accelNoise = 0.02f)
        val result = classifier.classifyTrack(points)

        assertThat(result.mode).isEqualTo(TransportMode.STATIONARY)
        assertThat(result.posterior).isAtLeast(0.80f)
        assertThat(result.confidenceBand).isEqualTo(ConfidenceBand.HIGH)
    }

    @Test
    fun testClassifyWalkingTrack() {
        val points = generateSyntheticTrack(speedMps = 1.3f, accelNoise = 0.2f)
        val result = classifier.classifyTrack(points)

        assertThat(result.mode).isEqualTo(TransportMode.WALK)
        assertThat(result.posterior).isAtLeast(0.70f)
    }

    @Test
    fun testClassifyRunningTrack() {
        val points = generateSyntheticTrack(speedMps = 3.2f, accelNoise = 0.4f)
        val result = classifier.classifyTrack(points)

        assertThat(result.mode).isEqualTo(TransportMode.RUN)
    }

    @Test
    fun testClassifyBicycleTrack() {
        val points = generateSyntheticTrack(speedMps = 5.5f, accelNoise = 0.3f)
        val result = classifier.classifyTrack(points)

        assertThat(result.mode).isEqualTo(TransportMode.BICYCLE)
        assertThat(result.mode).isNotEqualTo(TransportMode.MOTORCYCLE)
    }

    @Test
    fun testClassifyMotorcycleTrack() {
        val points = generateSyntheticTrack(speedMps = 14.0f, accelNoise = 3.0f)
        val result = classifier.classifyTrack(points)

        assertThat(result.mode).isEqualTo(TransportMode.MOTORCYCLE)
        assertThat(result.mode).isNotEqualTo(TransportMode.BICYCLE)
    }

    @Test
    fun testViterbiSmoothingDuringTrafficStop() {
        val rawModes = listOf(
            TransportMode.CAR,
            TransportMode.CAR,
            TransportMode.CAR,
            TransportMode.STATIONARY,
            TransportMode.CAR,
            TransportMode.CAR
        ).map { BaselineTransportClassifier.ClassificationResult(it, 0.9f, ConfidenceBand.HIGH) }

        val smoothed = classifier.smoothSequence(rawModes)
        assertThat(smoothed[3].mode).isEqualTo(TransportMode.CAR)
    }
}
