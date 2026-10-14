package com.berrf35.atlas.core.data.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.berrf35.atlas.core.data.database.AtlasDao
import com.berrf35.atlas.core.data.database.AtlasDbHelper
import com.berrf35.atlas.core.data.database.RawObservationEntity
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.data.repository.impl.TrackingRepositoryImpl
import com.berrf35.atlas.core.model.BatteryProfile
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.HealthEventView
import com.berrf35.atlas.core.model.TrackingHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Atlas Background Location Recorder Service conforming to DEVICE_AND_PLATFORM.md.
 * - Operates as an ongoing Foreground Service with location type.
 * - Uses adaptive sampling governed by BatteryProfile.
 * - Writes buffered observations to AtlasDao every 10s or 20 fixes.
 * - Emits health events on GPS provider disabled/enabled.
 */
class AtlasTrackingService : Service(), LocationListener {

    companion object {
        const val CHANNEL_ID = "atlas_tracking_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.berrf35.atlas.action.START_TRACKING"
        const val ACTION_STOP = "com.berrf35.atlas.action.STOP_TRACKING"
        const val ACTION_PAUSE = "com.berrf35.atlas.action.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.berrf35.atlas.action.RESUME_TRACKING"

        fun start(context: Context) {
            val intent = Intent(context, AtlasTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AtlasTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, AtlasTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, AtlasTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationManager: LocationManager? = null
    private var sessionId: String = UUID.randomUUID().toString()

    private val observationBuffer = mutableListOf<RawObservationEntity>()
    private var lastFlushTimeMs: Long = System.currentTimeMillis()

    private val dbHelper by lazy { AtlasDbHelper(applicationContext) }
    private val dao by lazy { AtlasDao(dbHelper) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        sessionId = UUID.randomUUID().toString()
        dao.startSession(sessionId, System.currentTimeMillis(), "1.0.0", Build.FINGERPRINT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseTracking()
            }
            ACTION_RESUME, ACTION_START -> {
                startForegroundTracking()
            }
        }

        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = buildNotification("Recording movement · Balanced profile")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        try {
            val settings = AtlasDataLocator.preferencesDataSource.settings.value
            val (minTimeMs, minDistanceM) = when (settings.batteryProfile) {
                BatteryProfile.PRECISE -> Pair(2000L, 2f)
                BatteryProfile.BALANCED -> Pair(5000L, 5f)
                BatteryProfile.SAVER -> Pair(15000L, 15f)
            }

            locationManager?.let { lm ->
                val gpsFix = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                val netFix = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                val best = when {
                    gpsFix != null && netFix != null -> if (gpsFix.time > netFix.time) gpsFix else netFix
                    gpsFix != null -> gpsFix
                    else -> netFix
                }
                if (best != null) {
                    TrackingRepositoryImpl.updateLivePosition(GeoPoint(best.latitude, best.longitude))
                }

                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        this
                    )
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs * 2,
                        minDistanceM * 2,
                        this
                    )
                }
            }
        } catch (_: SecurityException) {
            dao.insertHealthEvent(
                HealthEventView(
                    id = "evt-${System.currentTimeMillis()}",
                    timeUtcMs = System.currentTimeMillis(),
                    type = "PERMISSION_MISSING",
                    severity = "ERROR",
                    message = "Location permission denied while starting background recorder"
                )
            )
        }
    }

    private fun pauseTracking() {
        locationManager?.removeUpdates(this)
        val notification = buildNotification("Tracking paused")
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun stopTracking() {
        locationManager?.removeUpdates(this)
        flushObservations(force = true)
        dao.endSession(sessionId, System.currentTimeMillis(), "USER_STOPPED")
    }

    override fun onLocationChanged(loc: Location) {
        // Immediately stream live position to UI
        TrackingRepositoryImpl.updateLivePosition(GeoPoint(loc.latitude, loc.longitude))

        // Quality checks
        if (loc.accuracy > 200f) return // Ignore noisy fixes > 200m
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) loc.isMock else loc.isFromMockProvider

        val entity = RawObservationEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            timeUtcMs = loc.time,
            latE7 = (loc.latitude * 1e7).toInt(),
            lonE7 = (loc.longitude * 1e7).toInt(),
            accuracyCm = (loc.accuracy * 100).toInt(),
            altitudeCm = if (loc.hasAltitude()) (loc.altitude * 100).toInt() else null,
            speedMmps = if (loc.hasSpeed()) (loc.speed * 1000).toInt() else null,
            bearingCdeg = if (loc.hasBearing()) (loc.bearing * 100).toInt() else null,
            provider = loc.provider ?: "GPS",
            isMock = isMock
        )

        synchronized(observationBuffer) {
            observationBuffer.add(entity)
        }

        // Flush batch every 10 seconds or 20 observations
        val now = System.currentTimeMillis()
        if (observationBuffer.size >= 20 || (now - lastFlushTimeMs) >= 10_000L) {
            flushObservations(force = false)
        }
    }

    private fun flushObservations(force: Boolean) {
        serviceScope.launch {
            val toInsert = synchronized(observationBuffer) {
                if (observationBuffer.isEmpty()) return@launch
                val copy = observationBuffer.toList()
                observationBuffer.clear()
                lastFlushTimeMs = System.currentTimeMillis()
                copy
            }
            dao.insertObservations(toInsert)
        }
    }

    override fun onProviderEnabled(provider: String) {
        dao.insertHealthEvent(
            HealthEventView(
                id = "evt-${System.currentTimeMillis()}",
                timeUtcMs = System.currentTimeMillis(),
                type = "PROVIDER_ENABLED",
                severity = "INFO",
                message = "Location provider $provider enabled"
            )
        )
    }

    override fun onProviderDisabled(provider: String) {
        dao.insertHealthEvent(
            HealthEventView(
                id = "evt-${System.currentTimeMillis()}",
                timeUtcMs = System.currentTimeMillis(),
                type = "PROVIDER_DISABLED",
                severity = "WARNING",
                message = "Location provider $provider disabled"
            )
        )
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Atlas Movement Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground recording service for Project Atlas"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Atlas")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}

/**
 * Boot receiver to resume background tracking after phone restart if tracking is enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val settings = AtlasDataLocator.preferencesDataSource.settings.value
            if (settings.trackingEnabled) {
                AtlasTrackingService.start(context)
            }
        }
    }
}
