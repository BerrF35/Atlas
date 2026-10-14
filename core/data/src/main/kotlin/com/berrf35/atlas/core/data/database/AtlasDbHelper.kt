package com.berrf35.atlas.core.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Atlas SQLite Database Manager conforming to DATA_MODEL.md.
 * - Database name: "atlas.db"
 * - WAL (Write-Ahead Logging) enabled
 * - Foreign key constraints enabled
 * - Strict separation of raw append-only layer and derived journey layer
 */
class AtlasDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "atlas.db"
        const val DATABASE_VERSION = 1
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            // 1. Raw layer: tracking_session
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tracking_session (
                    id TEXT PRIMARY KEY,
                    started_utc_ms INTEGER NOT NULL,
                    ended_utc_ms INTEGER,
                    end_reason TEXT,
                    app_version TEXT NOT NULL,
                    device_fingerprint_hash TEXT NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_started ON tracking_session(started_utc_ms);")

            // 2. Raw layer: observation (append-only)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS observation (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    time_utc_ms INTEGER NOT NULL,
                    lat_e7 INTEGER NOT NULL,
                    lon_e7 INTEGER NOT NULL,
                    accuracy_cm INTEGER NOT NULL,
                    altitude_cm INTEGER,
                    speed_mmps INTEGER,
                    bearing_cdeg INTEGER,
                    vertical_accuracy_cm INTEGER,
                    provider TEXT NOT NULL,
                    is_mock INTEGER NOT NULL DEFAULT 0,
                    source TEXT NOT NULL DEFAULT 'LIVE',
                    zone_id TEXT NOT NULL,
                    quality_flags INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (session_id) REFERENCES tracking_session(id) ON DELETE RESTRICT
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_obs_time ON observation(time_utc_ms);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_obs_session_time ON observation(session_id, time_utc_ms);")

            // 3. Raw layer: health_event
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS health_event (
                    id TEXT PRIMARY KEY,
                    time_utc_ms INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    detail_code TEXT,
                    session_id TEXT,
                    redacted_detail_json TEXT
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_time ON health_event(time_utc_ms);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_type_time ON health_event(type, time_utc_ms);")

            // 4. Raw layer: liveness_span
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS liveness_span (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    from_utc_ms INTEGER NOT NULL,
                    to_utc_ms INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    last_observation_utc_ms INTEGER
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_liveness_range ON liveness_span(from_utc_ms, to_utc_ms);")

            // 5. Derived layer: journey
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS journey (
                    id TEXT PRIMARY KEY,
                    from_utc_ms INTEGER NOT NULL,
                    to_utc_ms INTEGER,
                    state TEXT NOT NULL DEFAULT 'CONFIRMED',
                    distance_m INTEGER NOT NULL DEFAULT 0,
                    moving_duration_ms INTEGER NOT NULL DEFAULT 0,
                    stopped_duration_ms INTEGER NOT NULL DEFAULT 0,
                    gap_duration_ms INTEGER NOT NULL DEFAULT 0,
                    primary_mode TEXT NOT NULL DEFAULT 'UNKNOWN',
                    origin_place_name TEXT,
                    destination_place_name TEXT,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    deleted_utc_ms INTEGER,
                    created_utc_ms INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_journey_from ON journey(from_utc_ms);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_journey_state_to ON journey(state, to_utc_ms);")

            // 6. Derived layer: segment
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS segment (
                    id TEXT PRIMARY KEY,
                    journey_id TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    from_utc_ms INTEGER NOT NULL,
                    to_utc_ms INTEGER NOT NULL,
                    kind TEXT NOT NULL DEFAULT 'MOVE',
                    mode TEXT,
                    mode_source TEXT,
                    posterior REAL,
                    distance_m INTEGER NOT NULL DEFAULT 0,
                    moving_duration_ms INTEGER NOT NULL DEFAULT 0,
                    stopped_duration_ms INTEGER NOT NULL DEFAULT 0,
                    gap_cause TEXT,
                    points_json TEXT NOT NULL DEFAULT '[]',
                    boundary_uncertainty_ms INTEGER NOT NULL DEFAULT 0,
                    created_utc_ms INTEGER NOT NULL,
                    FOREIGN KEY (journey_id) REFERENCES journey(id) ON DELETE CASCADE
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_segment_journey_ord ON segment(journey_id, ordinal);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_segment_journey_from ON segment(journey_id, from_utc_ms);")

            // 7. Derived layer: stay
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stay (
                    id TEXT PRIMARY KEY,
                    from_utc_ms INTEGER NOT NULL,
                    to_utc_ms INTEGER NOT NULL,
                    kind TEXT NOT NULL DEFAULT 'VISIT',
                    lat_e7 INTEGER NOT NULL,
                    lon_e7 INTEGER NOT NULL,
                    radius_m INTEGER NOT NULL DEFAULT 30,
                    place_id TEXT,
                    place_name TEXT,
                    created_utc_ms INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stay_range ON stay(from_utc_ms, to_utc_ms);")

            // 8. User layer: place
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS place (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    lat_e7 INTEGER NOT NULL,
                    lon_e7 INTEGER NOT NULL,
                    radius_m INTEGER NOT NULL DEFAULT 50,
                    status TEXT NOT NULL DEFAULT 'CONFIRMED',
                    is_privacy_zone INTEGER NOT NULL DEFAULT 0,
                    visit_count INTEGER NOT NULL DEFAULT 1,
                    last_visit_utc_ms INTEGER,
                    created_utc_ms INTEGER NOT NULL,
                    updated_utc_ms INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_place_status ON place(status);")

            // 9. User layer: route_cluster
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS route_cluster (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    origin_place_name TEXT NOT NULL,
                    destination_place_name TEXT NOT NULL,
                    primary_mode TEXT NOT NULL,
                    member_count INTEGER NOT NULL DEFAULT 3,
                    sample_points_json TEXT NOT NULL DEFAULT '[]',
                    updated_utc_ms INTEGER NOT NULL
                );
                """.trimIndent()
            )

            // 10. User layer: user_correction
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_correction (
                    id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    from_utc_ms INTEGER NOT NULL,
                    to_utc_ms INTEGER NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_utc_ms INTEGER NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1
                );
                """.trimIndent()
            )

            // 11. Derived layer: daily_aggregate
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_aggregate (
                    local_date TEXT PRIMARY KEY,
                    distance_m INTEGER NOT NULL DEFAULT 0,
                    journey_count INTEGER NOT NULL DEFAULT 0,
                    moving_ms INTEGER NOT NULL DEFAULT 0,
                    stopped_ms INTEGER NOT NULL DEFAULT 0,
                    gap_ms INTEGER NOT NULL DEFAULT 0,
                    coverage_percent INTEGER NOT NULL DEFAULT 0,
                    mode_distances_json TEXT NOT NULL DEFAULT '{}',
                    mode_durations_json TEXT NOT NULL DEFAULT '{}',
                    updated_utc_ms INTEGER NOT NULL
                );
                """.trimIndent()
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations framework (incremental, resilient)
        for (v in oldVersion until newVersion) {
            when (v) {
                // Add future schema version migrations here
                1 -> { /* migration from 1 to 2 */ }
            }
        }
    }
}
