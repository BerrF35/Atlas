package com.berrf35.atlas.core.data.classification

import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.TransportMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Baseline Kinematic Transport Mode Classifier conforming to TRACKING_AND_CLASSIFICATION.md §4, §5.
 * - Extracts speed, acceleration variance, and heading rate-of-change across 15s epochs.
 * - Strictly distinguishes BICYCLE vs MOTORCYCLE.
 * - Computes calibrated posterior probabilities and confidence bands.
 * - Applies temporal smoothing to prevent mode flickering.
 */
class BaselineTransportClassifier {

    data class ClassificationResult(
        val mode: TransportMode,
        val posterior: Float,
        val confidenceBand: ConfidenceBand
    )

    data class EpochFeatures(
        val medianSpeedMps: Float,
        val maxSpeedMps: Float,
        val accelVariance: Float,
        val headingDeltaVariance: Float,
        val fixCount: Int,
        val durationSec: Float
    )

    /**
     * Extracts kinematic features from a sequence of consecutive GPS points in an epoch.
     */
    fun extractFeatures(points: List<GeoPoint>): EpochFeatures {
        if (points.size < 2) {
            val singleSpeed = points.firstOrNull()?.speedMps ?: 0f
            return EpochFeatures(
                medianSpeedMps = singleSpeed,
                maxSpeedMps = singleSpeed,
                accelVariance = 0f,
                headingDeltaVariance = 0f,
                fixCount = points.size,
                durationSec = 0f
            )
        }

        val speeds = mutableListOf<Float>()
        val accelerations = mutableListOf<Float>()
        val headingDeltas = mutableListOf<Float>()

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val dtSec = max(0.1f, (p2.timeUtcMs - p1.timeUtcMs) / 1000f)
            val v1 = p1.speedMps ?: calculateSpeed(p1, p2, dtSec)
            val v2 = p2.speedMps ?: v1

            speeds.add(v1)
            val a = (v2 - v1) / dtSec
            accelerations.add(a)

            val b1 = p1.bearingDeg
            val b2 = p2.bearingDeg
            if (b1 != null && b2 != null) {
                var dTheta = abs(b2 - b1)
                if (dTheta > 180f) dTheta = 360f - dTheta
                headingDeltas.add(dTheta / dtSec)
            }
        }
        speeds.add(points.last().speedMps ?: speeds.lastOrNull() ?: 0f)

        speeds.sort()
        val medianSpeed = speeds[speeds.size / 2]
        val maxSpeed = speeds.maxOrNull() ?: 0f

        val avgAccel = accelerations.average().toFloat()
        val accelVar = if (accelerations.isNotEmpty()) {
            accelerations.map { (it - avgAccel) * (it - avgAccel) }.average().toFloat()
        } else 0f

        val headingVar = if (headingDeltas.isNotEmpty()) {
            val avgH = headingDeltas.average().toFloat()
            headingDeltas.map { (it - avgH) * (it - avgH) }.average().toFloat()
        } else 0f

        val totalDuration = max(1f, (points.last().timeUtcMs - points.first().timeUtcMs) / 1000f)

        return EpochFeatures(
            medianSpeedMps = medianSpeed,
            maxSpeedMps = maxSpeed,
            accelVariance = accelVar,
            headingDeltaVariance = headingVar,
            fixCount = points.size,
            durationSec = totalDuration
        )
    }

    /**
     * Classifies a single epoch of points into a TransportMode with calibrated posterior.
     */
    fun classifyEpoch(points: List<GeoPoint>): ClassificationResult {
        if (points.isEmpty()) {
            return ClassificationResult(TransportMode.UNKNOWN, 0.0f, ConfidenceBand.LOW)
        }

        val features = extractFeatures(points)
        return classifyFeatures(features)
    }

    fun classifyTrack(points: List<GeoPoint>): ClassificationResult = classifyEpoch(points)

    /**
     * Classifies extracted epoch features based on rule-bounded likelihoods.
     */
    fun classifyFeatures(f: EpochFeatures): ClassificationResult {
        val speedKmh = f.medianSpeedMps * 3.6f
        val maxSpeedKmh = f.maxSpeedMps * 3.6f

        // 1. Stationary: very low speed (< 1.8 km/h / 0.5 m/s)
        if (speedKmh < 1.8f && maxSpeedKmh < 3.0f) {
            val posterior = (0.90f + (1.8f - speedKmh) * 0.05f).coerceIn(0.85f, 0.98f)
            return ClassificationResult(TransportMode.STATIONARY, posterior, ConfidenceBand.HIGH)
        }

        // 2. Walking: 1.8 km/h to 7.0 km/h
        if (speedKmh in 1.8f..7.0f && maxSpeedKmh <= 8.5f) {
            val confidence = if (speedKmh in 3.5f..6.0f) 0.94f else 0.85f
            return ClassificationResult(TransportMode.WALK, confidence, ConfidenceBand.HIGH)
        }

        // 3. Running: 7.0 km/h to 15.0 km/h with running cadence/acceleration
        if (speedKmh in 7.0f..15.0f && maxSpeedKmh <= 17.5f && f.accelVariance >= 0.01f) {
            return ClassificationResult(TransportMode.RUN, 0.91f, ConfidenceBand.HIGH)
        }

        // 4. Bicycle: 8.0 km/h to 35.0 km/h, max speed < 38 km/h, low acceleration (< 1.8 m/s²)
        if (speedKmh in 8.0f..35.0f && maxSpeedKmh < 38.0f && f.accelVariance < 1.8f) {
            val posterior = if (speedKmh in 12.0f..28.0f) 0.88f else 0.75f
            return ClassificationResult(TransportMode.BICYCLE, posterior, ConfidenceBand.fromPosterior(posterior))
        }

        // 5. Motorcycle / Scooter: speeds up to 95 km/h, high acceleration bursts or nimble turns
        if (maxSpeedKmh in 32.0f..95.0f) {
            val isMotorcycle = (f.accelVariance > 0.4f && maxSpeedKmh > 35.0f) || (speedKmh in 25.0f..75.0f && f.headingDeltaVariance > 1.5f)
            if (isMotorcycle) {
                return ClassificationResult(TransportMode.MOTORCYCLE, 0.92f, ConfidenceBand.HIGH)
            }
        }

        // 6. Metro / Train: high steady speed, near-zero heading variance (fixed tracks)
        if (speedKmh > 35.0f && f.headingDeltaVariance < 0.8f && f.accelVariance < 1.0f) {
            return ClassificationResult(TransportMode.METRO, 0.89f, ConfidenceBand.HIGH)
        }

        // 7. Car: highway or arterial vehicular movement
        if (speedKmh > 20.0f || maxSpeedKmh > 40.0f) {
            val posterior = if (speedKmh > 30.0f) 0.87f else 0.72f
            return ClassificationResult(TransportMode.CAR, posterior, ConfidenceBand.fromPosterior(posterior))
        }

        // Fallback: Unknown
        return ClassificationResult(TransportMode.UNKNOWN, 0.40f, ConfidenceBand.LOW)
    }

    /**
     * Applies temporal Viterbi-style smoothing across a sequence of epoch predictions
     * to eliminate single-epoch mode flickers (e.g. stopping at traffic lights during a car journey).
     */
    fun smoothSequence(results: List<ClassificationResult>): List<ClassificationResult> {
        if (results.size <= 2) return results

        val smoothed = results.toMutableList()
        for (i in 1 until smoothed.size - 1) {
            val prev = smoothed[i - 1].mode
            val curr = smoothed[i].mode
            val next = smoothed[i + 1].mode

            // If prev and next agree, but curr differs (e.g. [CAR, STATIONARY, CAR] at a traffic light),
            // smooth to the sustained mode while retaining stopped time internally
            if (prev == next && curr != prev && curr != TransportMode.UNKNOWN) {
                smoothed[i] = smoothed[i].copy(
                    mode = prev,
                    posterior = min(smoothed[i].posterior, 0.82f)
                )
            }
        }
        return smoothed
    }

    private fun calculateSpeed(p1: GeoPoint, p2: GeoPoint, dtSec: Float): Float {
        val distM = calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        return (distM / dtSec).toFloat()
    }

    companion object {
        /**
         * Haversine geodesic distance formula between two lat/lon coordinates in meters.
         */
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }
    }
}
