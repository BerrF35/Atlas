package com.berrf35.atlas.core.data.database

import android.content.ContentValues
import android.database.Cursor
import com.berrf35.atlas.core.model.ConfidenceBand
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.GapCause
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.HealthEventView
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneyState
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.ModeSource
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RawObservationEntity(
    val id: String,
    val sessionId: String,
    val timeUtcMs: Long,
    val latE7: Int,
    val lonE7: Int,
    val accuracyCm: Int,
    val altitudeCm: Int? = null,
    val speedMmps: Int? = null,
    val bearingCdeg: Int? = null,
    val provider: String = "FLP",
    val isMock: Boolean = false,
    val source: String = "LIVE",
    val zoneId: String = TimeZone.getDefault().id,
    val qualityFlags: Int = 0
)

class AtlasDao(private val dbHelper: AtlasDbHelper) {

    private val json = Json { ignoreUnknownKeys = true }

    // --- 1. Raw Observations ---
    fun insertObservations(observations: List<RawObservationEntity>) {
        if (observations.isEmpty()) return
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            observations.forEach { obs ->
                val values = ContentValues().apply {
                    put("id", obs.id)
                    put("session_id", obs.sessionId)
                    put("time_utc_ms", obs.timeUtcMs)
                    put("lat_e7", obs.latE7)
                    put("lon_e7", obs.lonE7)
                    put("accuracy_cm", obs.accuracyCm)
                    put("altitude_cm", obs.altitudeCm)
                    put("speed_mmps", obs.speedMmps)
                    put("bearing_cdeg", obs.bearingCdeg)
                    put("provider", obs.provider)
                    put("is_mock", if (obs.isMock) 1 else 0)
                    put("source", obs.source)
                    put("zone_id", obs.zoneId)
                    put("quality_flags", obs.qualityFlags)
                }
                db.insertWithOnConflict(
                    "observation",
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getObservations(fromUtcMs: Long, toUtcMs: Long): List<RawObservationEntity> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RawObservationEntity>()
        val cursor = db.rawQuery(
            "SELECT * FROM observation WHERE time_utc_ms >= ? AND time_utc_ms <= ? ORDER BY time_utc_ms ASC",
            arrayOf(fromUtcMs.toString(), toUtcMs.toString())
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    RawObservationEntity(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        timeUtcMs = c.getLong(c.getColumnIndexOrThrow("time_utc_ms")),
                        latE7 = c.getInt(c.getColumnIndexOrThrow("lat_e7")),
                        lonE7 = c.getInt(c.getColumnIndexOrThrow("lon_e7")),
                        accuracyCm = c.getInt(c.getColumnIndexOrThrow("accuracy_cm")),
                        altitudeCm = if (c.isNull(c.getColumnIndexOrThrow("altitude_cm"))) null else c.getInt(c.getColumnIndexOrThrow("altitude_cm")),
                        speedMmps = if (c.isNull(c.getColumnIndexOrThrow("speed_mmps"))) null else c.getInt(c.getColumnIndexOrThrow("speed_mmps")),
                        bearingCdeg = if (c.isNull(c.getColumnIndexOrThrow("bearing_cdeg"))) null else c.getInt(c.getColumnIndexOrThrow("bearing_cdeg")),
                        provider = c.getString(c.getColumnIndexOrThrow("provider")),
                        isMock = c.getInt(c.getColumnIndexOrThrow("is_mock")) == 1,
                        source = c.getString(c.getColumnIndexOrThrow("source")),
                        zoneId = c.getString(c.getColumnIndexOrThrow("zone_id")),
                        qualityFlags = c.getInt(c.getColumnIndexOrThrow("quality_flags"))
                    )
                )
            }
        }
        return list
    }

    // --- 2. Tracking Session ---
    fun startSession(sessionId: String, startTimeUtcMs: Long, appVersion: String, deviceFingerprint: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", sessionId)
            put("started_utc_ms", startTimeUtcMs)
            put("app_version", appVersion)
            put("device_fingerprint_hash", deviceFingerprint)
        }
        db.insertWithOnConflict("tracking_session", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun endSession(sessionId: String, endTimeUtcMs: Long, reason: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ended_utc_ms", endTimeUtcMs)
            put("end_reason", reason)
        }
        db.update("tracking_session", values, "id = ?", arrayOf(sessionId))
    }

    // --- 3. Derived Journeys & Segments ---
    fun insertJourneyWithSegments(
        journey: JourneySummary,
        segments: List<SegmentView>,
        stops: List<StayView>
    ) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val jValues = ContentValues().apply {
                put("id", journey.id.value)
                put("from_utc_ms", journey.range.fromUtcMs)
                put("to_utc_ms", journey.range.toUtcMs)
                put("state", journey.state.code)
                put("distance_m", journey.distanceMeters)
                put("moving_duration_ms", journey.movingDurationMs)
                put("stopped_duration_ms", journey.stoppedDurationMs)
                put("gap_duration_ms", journey.gapDurationMs)
                put("primary_mode", journey.primaryMode.code)
                put("origin_place_name", journey.originPlaceName)
                put("destination_place_name", journey.destinationPlaceName)
                put("is_deleted", if (journey.isDeleted) 1 else 0)
                put("created_utc_ms", System.currentTimeMillis())
            }
            db.insertWithOnConflict("journey", null, jValues, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)

            // Insert segments
            segments.forEachIndexed { idx, seg ->
                val sValues = ContentValues().apply {
                    put("id", seg.id.value)
                    put("journey_id", journey.id.value)
                    put("ordinal", idx)
                    put("from_utc_ms", seg.range.fromUtcMs)
                    put("to_utc_ms", seg.range.toUtcMs)
                    put("kind", seg.kind.code)
                    put("mode", seg.mode?.code)
                    put("mode_source", seg.source?.code)
                    put("posterior", seg.posterior)
                    put("distance_m", seg.metrics.distanceMeters)
                    put("moving_duration_ms", seg.metrics.movingDurationMs)
                    put("stopped_duration_ms", seg.metrics.stoppedDurationMs)
                    put("gap_cause", seg.gapCause?.code)
                    put("points_json", json.encodeToString(seg.points))
                    put("boundary_uncertainty_ms", seg.boundaryUncertaintyMs)
                    put("created_utc_ms", System.currentTimeMillis())
                }
                db.insertWithOnConflict("segment", null, sValues, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }

            // Insert stays
            stops.forEach { stay ->
                val stValues = ContentValues().apply {
                    put("id", stay.id.value)
                    put("from_utc_ms", stay.range.fromUtcMs)
                    put("to_utc_ms", stay.range.toUtcMs)
                    put("kind", stay.kind.code)
                    put("lat_e7", (stay.location.latitude * 1e7).toInt())
                    put("lon_e7", (stay.location.longitude * 1e7).toInt())
                    put("radius_m", stay.radiusMeters)
                    put("place_id", stay.placeId?.value)
                    put("place_name", stay.placeName)
                    put("created_utc_ms", System.currentTimeMillis())
                }
                db.insertWithOnConflict("stay", null, stValues, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getJourneys(includeDeleted: Boolean = false): List<JourneySummary> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<JourneySummary>()
        val sql = if (includeDeleted) "SELECT * FROM journey ORDER BY from_utc_ms DESC"
                  else "SELECT * FROM journey WHERE is_deleted = 0 ORDER BY from_utc_ms DESC"
        val cursor = db.rawQuery(sql, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val jId = JourneyId(c.getString(c.getColumnIndexOrThrow("id")))
                val segs = getSegmentsForJourney(jId)
                list.add(
                    JourneySummary(
                        id = jId,
                        range = TimeRange(
                            c.getLong(c.getColumnIndexOrThrow("from_utc_ms")),
                            c.getLong(c.getColumnIndexOrThrow("to_utc_ms"))
                        ),
                        state = JourneyState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),
                        distanceMeters = c.getLong(c.getColumnIndexOrThrow("distance_m")),
                        movingDurationMs = c.getLong(c.getColumnIndexOrThrow("moving_duration_ms")),
                        stoppedDurationMs = c.getLong(c.getColumnIndexOrThrow("stopped_duration_ms")),
                        gapDurationMs = c.getLong(c.getColumnIndexOrThrow("gap_duration_ms")),
                        primaryMode = TransportMode.fromCode(c.getString(c.getColumnIndexOrThrow("primary_mode"))),
                        modeSegments = segs,
                        originPlaceName = c.getString(c.getColumnIndexOrThrow("origin_place_name")),
                        destinationPlaceName = c.getString(c.getColumnIndexOrThrow("destination_place_name")),
                        isDeleted = c.getInt(c.getColumnIndexOrThrow("is_deleted")) == 1
                    )
                )
            }
        }
        return list
    }

    fun getJourneyDetail(journeyId: JourneyId): JourneyDetail? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM journey WHERE id = ?", arrayOf(journeyId.value))
        cursor.use { c ->
            if (!c.moveToNext()) return null
            val segments = getSegmentsForJourney(journeyId)
            val allPoints = segments.flatMap { it.points }
            val fromMs = c.getLong(c.getColumnIndexOrThrow("from_utc_ms"))
            val toMs = c.getLong(c.getColumnIndexOrThrow("to_utc_ms"))
            val stops = getStays(fromMs, toMs)

            val summary = JourneySummary(
                id = journeyId,
                range = TimeRange(fromMs, toMs),
                state = JourneyState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),
                distanceMeters = c.getLong(c.getColumnIndexOrThrow("distance_m")),
                movingDurationMs = c.getLong(c.getColumnIndexOrThrow("moving_duration_ms")),
                stoppedDurationMs = c.getLong(c.getColumnIndexOrThrow("stopped_duration_ms")),
                gapDurationMs = c.getLong(c.getColumnIndexOrThrow("gap_duration_ms")),
                primaryMode = TransportMode.fromCode(c.getString(c.getColumnIndexOrThrow("primary_mode"))),
                modeSegments = segments,
                originPlaceName = c.getString(c.getColumnIndexOrThrow("origin_place_name")),
                destinationPlaceName = c.getString(c.getColumnIndexOrThrow("destination_place_name")),
                isDeleted = c.getInt(c.getColumnIndexOrThrow("is_deleted")) == 1
            )
            return JourneyDetail(summary = summary, segments = segments, points = allPoints, stops = stops)
        }
    }

    private fun getSegmentsForJourney(journeyId: JourneyId): List<SegmentView> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<SegmentView>()
        val cursor = db.rawQuery("SELECT * FROM segment WHERE journey_id = ? ORDER BY ordinal ASC", arrayOf(journeyId.value))
        cursor.use { c ->
            while (c.moveToNext()) {
                val ptsStr = c.getString(c.getColumnIndexOrThrow("points_json"))
                val pts: List<GeoPoint> = try { json.decodeFromString(ptsStr) } catch (_: Exception) { emptyList() }
                list.add(
                    SegmentView(
                        id = SegmentId(c.getString(c.getColumnIndexOrThrow("id"))),
                        range = TimeRange(
                            c.getLong(c.getColumnIndexOrThrow("from_utc_ms")),
                            c.getLong(c.getColumnIndexOrThrow("to_utc_ms"))
                        ),
                        kind = SegmentKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
                        mode = c.getString(c.getColumnIndexOrThrow("mode"))?.let { TransportMode.fromCode(it) },
                        source = c.getString(c.getColumnIndexOrThrow("mode_source"))?.let { ModeSource.valueOf(it) },
                        posterior = if (c.isNull(c.getColumnIndexOrThrow("posterior"))) null else c.getFloat(c.getColumnIndexOrThrow("posterior")),
                        metrics = SegmentMetrics(
                            distanceMeters = c.getLong(c.getColumnIndexOrThrow("distance_m")),
                            movingDurationMs = c.getLong(c.getColumnIndexOrThrow("moving_duration_ms")),
                            stoppedDurationMs = c.getLong(c.getColumnIndexOrThrow("stopped_duration_ms")),
                            averageSpeedKmh = 0f,
                            maxSpeedKmh = 0f
                        ),
                        gapCause = c.getString(c.getColumnIndexOrThrow("gap_cause"))?.let { GapCause.valueOf(it) },
                        points = pts,
                        boundaryUncertaintyMs = c.getLong(c.getColumnIndexOrThrow("boundary_uncertainty_ms"))
                    )
                )
            }
        }
        return list
    }

    private fun getStays(fromUtcMs: Long, toUtcMs: Long): List<StayView> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<StayView>()
        val cursor = db.rawQuery("SELECT * FROM stay WHERE from_utc_ms <= ? AND to_utc_ms >= ?", arrayOf(toUtcMs.toString(), fromUtcMs.toString()))
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    StayView(
                        id = StayId(c.getString(c.getColumnIndexOrThrow("id"))),
                        range = TimeRange(c.getLong(c.getColumnIndexOrThrow("from_utc_ms")), c.getLong(c.getColumnIndexOrThrow("to_utc_ms"))),
                        kind = StayKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
                        location = GeoPoint(c.getInt(c.getColumnIndexOrThrow("lat_e7")) / 1e7, c.getInt(c.getColumnIndexOrThrow("lon_e7")) / 1e7),
                        placeId = c.getString(c.getColumnIndexOrThrow("place_id"))?.let { PlaceId(it) },
                        placeName = c.getString(c.getColumnIndexOrThrow("place_name")),
                        radiusMeters = c.getInt(c.getColumnIndexOrThrow("radius_m"))
                    )
                )
            }
        }
        return list
    }

    // --- 4. Corrections ---
    fun updateSegmentMode(journeyId: JourneyId, segmentId: SegmentId, newMode: TransportMode, source: ModeSource) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Update segment mode
            val values = ContentValues().apply {
                put("mode", newMode.code)
                put("mode_source", source.code)
            }
            db.update("segment", values, "id = ?", arrayOf(segmentId.value))

            // Record immutable user_correction audit record
            val corrValues = ContentValues().apply {
                put("id", "corr-${System.currentTimeMillis()}")
                put("kind", "SET_MODE")
                put("from_utc_ms", System.currentTimeMillis())
                put("to_utc_ms", System.currentTimeMillis())
                put("payload_json", "{\"segment_id\":\"${segmentId.value}\",\"mode\":\"${newMode.code}\"}")
                put("created_utc_ms", System.currentTimeMillis())
                put("is_active", 1)
            }
            db.insert("user_correction", null, corrValues)

            // Recompute journey primary mode
            val jVals = ContentValues().apply {
                put("primary_mode", newMode.code)
            }
            db.update("journey", jVals, "id = ?", arrayOf(journeyId.value))

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markJourneyDeleted(journeyId: JourneyId, isDeleted: Boolean) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_deleted", if (isDeleted) 1 else 0)
            put("deleted_utc_ms", if (isDeleted) System.currentTimeMillis() else null)
        }
        db.update("journey", values, "id = ?", arrayOf(journeyId.value))
    }

    // --- 5. Places ---
    fun getPlaces(): List<PlaceView> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<PlaceView>()
        val cursor = db.rawQuery("SELECT * FROM place WHERE status != 'DELETED' ORDER BY visit_count DESC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    PlaceView(
                        id = PlaceId(c.getString(c.getColumnIndexOrThrow("id"))),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        center = GeoPoint(c.getInt(c.getColumnIndexOrThrow("lat_e7")) / 1e7, c.getInt(c.getColumnIndexOrThrow("lon_e7")) / 1e7),
                        radiusMeters = c.getInt(c.getColumnIndexOrThrow("radius_m")),
                        status = PlaceStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
                        isPrivacyZone = c.getInt(c.getColumnIndexOrThrow("is_privacy_zone")) == 1,
                        visitCount = c.getInt(c.getColumnIndexOrThrow("visit_count")),
                        lastVisitUtcMs = if (c.isNull(c.getColumnIndexOrThrow("last_visit_utc_ms"))) null else c.getLong(c.getColumnIndexOrThrow("last_visit_utc_ms"))
                    )
                )
            }
        }
        return list
    }

    fun insertOrUpdatePlace(place: PlaceView) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", place.id.value)
            put("name", place.name)
            put("lat_e7", (place.center.latitude * 1e7).toInt())
            put("lon_e7", (place.center.longitude * 1e7).toInt())
            put("radius_m", place.radiusMeters)
            put("status", place.status.code)
            put("is_privacy_zone", if (place.isPrivacyZone) 1 else 0)
            put("visit_count", place.visitCount)
            put("last_visit_utc_ms", place.lastVisitUtcMs)
            put("created_utc_ms", System.currentTimeMillis())
            put("updated_utc_ms", System.currentTimeMillis())
        }
        db.insertWithOnConflict("place", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePlace(placeId: PlaceId) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", PlaceStatus.DELETED.code)
            put("updated_utc_ms", System.currentTimeMillis())
        }
        db.update("place", values, "id = ?", arrayOf(placeId.value))
    }

    // --- 6. Health & Diagnostics ---
    fun insertHealthEvent(event: HealthEventView) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", event.id)
            put("time_utc_ms", event.timeUtcMs)
            put("type", event.type)
            put("severity", event.severity)
            put("detail_code", event.message)
        }
        db.insertWithOnConflict("health_event", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHealthEvents(): List<HealthEventView> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<HealthEventView>()
        val cursor = db.rawQuery("SELECT * FROM health_event ORDER BY time_utc_ms DESC LIMIT 50", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    HealthEventView(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        timeUtcMs = c.getLong(c.getColumnIndexOrThrow("time_utc_ms")),
                        type = c.getString(c.getColumnIndexOrThrow("type")),
                        severity = c.getString(c.getColumnIndexOrThrow("severity")),
                        message = c.getString(c.getColumnIndexOrThrow("detail_code")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getLatestObservation(): RawObservationEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM observation ORDER BY time_utc_ms DESC LIMIT 1", null)
        cursor.use { c ->
            if (c.moveToNext()) {
                return RawObservationEntity(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                    timeUtcMs = c.getLong(c.getColumnIndexOrThrow("time_utc_ms")),
                    latE7 = c.getInt(c.getColumnIndexOrThrow("lat_e7")),
                    lonE7 = c.getInt(c.getColumnIndexOrThrow("lon_e7")),
                    accuracyCm = c.getInt(c.getColumnIndexOrThrow("accuracy_cm")),
                    altitudeCm = if (c.isNull(c.getColumnIndexOrThrow("altitude_cm"))) null else c.getInt(c.getColumnIndexOrThrow("altitude_cm")),
                    speedMmps = if (c.isNull(c.getColumnIndexOrThrow("speed_mmps"))) null else c.getInt(c.getColumnIndexOrThrow("speed_mmps")),
                    bearingCdeg = if (c.isNull(c.getColumnIndexOrThrow("bearing_cdeg"))) null else c.getInt(c.getColumnIndexOrThrow("bearing_cdeg")),
                    provider = c.getString(c.getColumnIndexOrThrow("provider")),
                    isMock = c.getInt(c.getColumnIndexOrThrow("is_mock")) == 1,
                    source = c.getString(c.getColumnIndexOrThrow("source")),
                    zoneId = c.getString(c.getColumnIndexOrThrow("zone_id")),
                    qualityFlags = c.getInt(c.getColumnIndexOrThrow("quality_flags"))
                )
            }
        }
        return null
    }

    fun getAllObservations(): List<RawObservationEntity> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RawObservationEntity>()
        val cursor = db.rawQuery("SELECT * FROM observation ORDER BY time_utc_ms ASC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    RawObservationEntity(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        timeUtcMs = c.getLong(c.getColumnIndexOrThrow("time_utc_ms")),
                        latE7 = c.getInt(c.getColumnIndexOrThrow("lat_e7")),
                        lonE7 = c.getInt(c.getColumnIndexOrThrow("lon_e7")),
                        accuracyCm = c.getInt(c.getColumnIndexOrThrow("accuracy_cm")),
                        altitudeCm = if (c.isNull(c.getColumnIndexOrThrow("altitude_cm"))) null else c.getInt(c.getColumnIndexOrThrow("altitude_cm")),
                        speedMmps = if (c.isNull(c.getColumnIndexOrThrow("speed_mmps"))) null else c.getInt(c.getColumnIndexOrThrow("speed_mmps")),
                        bearingCdeg = if (c.isNull(c.getColumnIndexOrThrow("bearing_cdeg"))) null else c.getInt(c.getColumnIndexOrThrow("bearing_cdeg")),
                        provider = c.getString(c.getColumnIndexOrThrow("provider")),
                        isMock = c.getInt(c.getColumnIndexOrThrow("is_mock")) == 1,
                        source = c.getString(c.getColumnIndexOrThrow("source")),
                        zoneId = c.getString(c.getColumnIndexOrThrow("zone_id")),
                        qualityFlags = c.getInt(c.getColumnIndexOrThrow("quality_flags"))
                    )
                )
            }
        }
        return list
    }

    fun getAllJourneyDetails(includeDeleted: Boolean = false): List<JourneyDetail> {
        val summaries = getJourneys(includeDeleted)
        return summaries.mapNotNull { getJourneyDetail(it.id) }
    }

    fun getJourneysForDate(date: String): List<JourneySummary> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return getJourneys(includeDeleted = false).filter { j ->
            val jDate = sdf.format(Date(j.range.fromUtcMs))
            jDate == date
        }
    }

    // --- 7. Purge / Clear Data ---
    fun clearAllData() {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("segment", null, null)
            db.delete("journey", null, null)
            db.delete("stay", null, null)
            db.delete("observation", null, null)
            db.delete("tracking_session", null, null)
            db.delete("health_event", null, null)
            db.delete("liveness_span", null, null)
            db.delete("place", null, null)
            db.delete("route_cluster", null, null)
            db.delete("user_correction", null, null)
            db.delete("daily_aggregate", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
