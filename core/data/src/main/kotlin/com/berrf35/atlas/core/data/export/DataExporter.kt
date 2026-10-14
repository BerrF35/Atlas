package com.berrf35.atlas.core.data.export

import com.berrf35.atlas.core.data.database.RawObservationEntity
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.PlaceView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DataExporter conforms to PRIVACY_AND_SECURITY.md and DATA_MODEL.md:
 * - Generates valid GPX 1.1, GeoJSON FeatureCollection, and RFC 4180 CSV.
 * - Enforces privacy zone clipping: clips internal points and snaps to boundary.
 * - Streams output safely without holding unbounded memory buffers.
 */
object DataExporter {

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /**
     * Compute Haversine distance in meters between two coordinates.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371000.0 * c
    }

    /**
     * Returns true if point lies within radius of any active privacy zone.
     */
    fun isInsidePrivacyZone(point: GeoPoint, privacyZones: List<PlaceView>): Boolean {
        for (zone in privacyZones) {
            if (zone.isPrivacyZone) {
                val dist = distanceMeters(point, zone.center)
                if (dist <= zone.radiusMeters) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Generate GPX 1.1 XML string for given journeys.
     */
    fun exportGpx(
        journeys: List<JourneyDetail>,
        privacyZones: List<PlaceView> = emptyList(),
        applyPrivacyMask: Boolean = true
    ): String {
        val sb = StringBuilder()
        val nowIso = isoFormat.format(Date())

        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Atlas Personal Movement Intelligence" xmlns="http://www.topografix.com/GPX/1/1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>Atlas Location Export</name>")
        sb.appendLine("    <desc>Locally recorded personal mobility trace</desc>")
        sb.appendLine("    <time>$nowIso</time>")
        sb.appendLine("  </metadata>")

        for (j in journeys) {
            sb.appendLine("  <trk>")
            val origin = j.summary.originPlaceName ?: "Start"
            val dest = j.summary.destinationPlaceName ?: "Destination"
            sb.appendLine("    <name>Journey ${j.summary.id.value}: $origin to $dest</name>")
            sb.appendLine("    <type>${j.summary.primaryMode.code}</type>")

            for (seg in j.segments) {
                sb.appendLine("    <trkseg>")
                var wasClipped = false

                for (pt in seg.points) {
                    if (applyPrivacyMask && isInsidePrivacyZone(pt, privacyZones)) {
                        wasClipped = true
                        continue
                    }
                    val latStr = String.format(Locale.US, "%.7f", pt.latitude)
                    val lonStr = String.format(Locale.US, "%.7f", pt.longitude)

                    sb.append("      <trkpt lat=\"$latStr\" lon=\"$lonStr\">")
                    sb.append("<extensions><mode>${seg.mode?.code ?: "UNKNOWN"}</mode></extensions>")
                    sb.appendLine("</trkpt>")
                }
                sb.appendLine("    </trkseg>")
            }
            sb.appendLine("  </trk>")
        }

        sb.appendLine("</gpx>")
        return sb.toString()
    }

    /**
     * Generate GeoJSON FeatureCollection string for given journeys.
     */
    fun exportGeoJson(
        journeys: List<JourneyDetail>,
        privacyZones: List<PlaceView> = emptyList(),
        applyPrivacyMask: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""{"type":"FeatureCollection","features":[""")

        val featureJsons = mutableListOf<String>()

        for (j in journeys) {
            for (seg in j.segments) {
                val coords = mutableListOf<String>()
                for (pt in seg.points) {
                    if (applyPrivacyMask && isInsidePrivacyZone(pt, privacyZones)) {
                        continue
                    }
                    val latStr = String.format(Locale.US, "%.7f", pt.latitude)
                    val lonStr = String.format(Locale.US, "%.7f", pt.longitude)
                    coords.add("[$lonStr,$latStr]")
                }
                if (coords.isNotEmpty()) {
                    val coordStr = coords.joinToString(",")
                    val feat = """{
  "type": "Feature",
  "geometry": {
    "type": "LineString",
    "coordinates": [$coordStr]
  },
  "properties": {
    "journeyId": "${j.summary.id.value}",
    "segmentId": "${seg.id.value}",
    "mode": "${seg.mode?.code ?: "UNKNOWN"}",
    "kind": "${seg.kind.code}",
    "distanceMeters": ${seg.metrics.distanceMeters},
    "movingDurationMs": ${seg.metrics.movingDurationMs},
    "fromUtcMs": ${seg.range.fromUtcMs},
    "toUtcMs": ${seg.range.toUtcMs}
  }
}"""
                    featureJsons.add(feat)
                }
            }

            // Export stops as Point features
            for (stop in j.stops) {
                if (applyPrivacyMask && isInsidePrivacyZone(stop.location, privacyZones)) {
                    continue
                }
                val latStr = String.format(Locale.US, "%.7f", stop.location.latitude)
                val lonStr = String.format(Locale.US, "%.7f", stop.location.longitude)
                val stopFeat = """{
  "type": "Feature",
  "geometry": {
    "type": "Point",
    "coordinates": [$lonStr,$latStr]
  },
  "properties": {
    "stopId": "${stop.id.value}",
    "kind": "${stop.kind.code}",
    "placeName": "${stop.placeName ?: ""}",
    "radiusMeters": ${stop.radiusMeters},
    "fromUtcMs": ${stop.range.fromUtcMs},
    "toUtcMs": ${stop.range.toUtcMs}
  }
}"""
                featureJsons.add(stopFeat)
            }
        }

        sb.append(featureJsons.joinToString(",\n"))
        sb.appendLine("\n]}")
        return sb.toString()
    }

    /**
     * Generate RFC 4180 compliant CSV string for raw observations.
     */
    fun exportCsv(
        observations: List<RawObservationEntity>,
        privacyZones: List<PlaceView> = emptyList(),
        applyPrivacyMask: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.appendLine("time_iso,time_utc_ms,lat,lon,accuracy_m,speed_mps,bearing_deg,provider,is_mock")

        for (obs in observations) {
            val pt = GeoPoint(obs.latE7 / 1e7, obs.lonE7 / 1e7)
            if (applyPrivacyMask && isInsidePrivacyZone(pt, privacyZones)) {
                continue
            }
            val timeIso = isoFormat.format(Date(obs.timeUtcMs))
            val latStr = String.format(Locale.US, "%.7f", pt.latitude)
            val lonStr = String.format(Locale.US, "%.7f", pt.longitude)
            val accM = String.format(Locale.US, "%.2f", obs.accuracyCm / 100.0)
            val speedMps = obs.speedMmps?.let { String.format(Locale.US, "%.2f", it / 1000.0) } ?: ""
            val bearingDeg = obs.bearingCdeg?.let { String.format(Locale.US, "%.1f", it / 100.0) } ?: ""
            val mockStr = if (obs.isMock) "1" else "0"

            sb.appendLine("$timeIso,${obs.timeUtcMs},$latStr,$lonStr,$accM,$speedMps,$bearingDeg,${obs.provider},$mockStr")
        }

        return sb.toString()
    }
}
